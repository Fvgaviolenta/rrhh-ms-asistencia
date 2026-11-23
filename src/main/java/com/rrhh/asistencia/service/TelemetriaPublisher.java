package com.rrhh.asistencia.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.kinesis.KinesisClient;
import software.amazon.awssdk.services.kinesis.model.PutRecordsRequest;
import software.amazon.awssdk.services.kinesis.model.PutRecordsRequestEntry;
import software.amazon.awssdk.services.kinesis.model.PutRecordsResponse;
import software.amazon.awssdk.services.kinesis.model.PutRecordsResultEntry;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Envía la telemetría de APIs a Kinesis en lotes, de mejor esfuerzo (fase 5 de streaming, spec S4).
 *
 * <p>{@link #registrar} nunca bloquea el request: deja el registro en una cola acotada y, si está llena,
 * lo descarta y lo cuenta. Un hilo propio ({@code telemetria-kinesis}, no el scheduler de Spring que usa
 * {@code OutboxPublisher}) envía con PutRecords cada {@link #INTERVALO}, o antes si la cola llega a
 * {@link #MAX_LOTE}. Si Kinesis falla, el lote o los registros con {@code errorCode} se descartan sin
 * reintento y se cuentan en un log WARN, sin contenido. No hay outbox.
 */
public class TelemetriaPublisher implements AutoCloseable {
    public static final int CAPACIDAD = 5000;
    public static final int MAX_LOTE = 500;
    public static final Duration INTERVALO = Duration.ofSeconds(1);

    private static final Logger log = LoggerFactory.getLogger(TelemetriaPublisher.class);

    private final KinesisClient client;
    private final String streamName;
    private final BlockingQueue<RegistroTelemetria> cola;
    private final int maxLote;
    private final Duration intervalo;
    private final AtomicLong descartados = new AtomicLong();
    private final AtomicLong descartesSinReportar = new AtomicLong();
    private final AtomicBoolean iniciado = new AtomicBoolean();
    private final AtomicBoolean envioDisparado = new AtomicBoolean();
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "telemetria-kinesis");
        t.setDaemon(true);
        return t;
    });

    public TelemetriaPublisher(KinesisClient client, String streamName, int capacidad, int maxLote, Duration intervalo) {
        if (streamName == null || streamName.isBlank()) {
            throw new IllegalStateException("RRHH_TELEMETRY_ENABLED=true requiere TELEMETRY_STREAM_NAME");
        }
        this.client = client;
        this.streamName = streamName;
        this.cola = new ArrayBlockingQueue<>(capacidad);
        this.maxLote = maxLote;
        this.intervalo = intervalo;
    }

    /** Encola sin bloquear; devuelve false si la cola estaba llena y el registro se descartó. */
    public boolean registrar(RegistroTelemetria registro) {
        if (!cola.offer(registro)) {
            descartados.incrementAndGet();
            descartesSinReportar.incrementAndGet();
            return false;
        }
        if (iniciado.get() && cola.size() >= maxLote && envioDisparado.compareAndSet(false, true)) {
            try {
                executor.execute(() -> {
                    envioDisparado.set(false);
                    enviarSinPropagar();
                });
            } catch (RejectedExecutionException e) {
                envioDisparado.set(false);
            }
        }
        return true;
    }

    /** Vacía la cola en lotes de hasta maxLote; devuelve cuántos registros aceptó Kinesis. */
    public synchronized int enviarPendientes() {
        int aceptados = 0;
        while (true) {
            List<RegistroTelemetria> lote = new ArrayList<>(maxLote);
            cola.drainTo(lote, maxLote);
            if (lote.isEmpty()) break;
            int rechazados = enviar(lote);
            aceptados += lote.size() - rechazados;
            descartados.addAndGet(rechazados);
            descartesSinReportar.addAndGet(rechazados);
        }
        long sinReportar = descartesSinReportar.getAndSet(0);
        if (sinReportar > 0) {
            log.warn("Telemetría: {} registros descartados", sinReportar);
        }
        return aceptados;
    }

    /** Devuelve cuántos registros del lote se perdieron: todos si Kinesis lanzó, o los que traen errorCode. */
    private int enviar(List<RegistroTelemetria> lote) {
        List<PutRecordsRequestEntry> entries = lote.stream()
                .map(r -> PutRecordsRequestEntry.builder()
                        .partitionKey(r.tenantId() != null ? r.tenantId() : RegistroTelemetria.SERVICIO)
                        .data(SdkBytes.fromUtf8String(r.toJson()))
                        .build())
                .toList();
        PutRecordsResponse response;
        try {
            response = client.putRecords(PutRecordsRequest.builder().streamName(streamName).records(entries).build());
        } catch (RuntimeException e) {
            return lote.size();
        }
        if (response.failedRecordCount() == null || response.failedRecordCount() == 0) return 0;
        // Kinesis responde un resultado por registro, en el mismo orden del request.
        List<PutRecordsResultEntry> resultados = response.records();
        int rechazados = 0;
        for (int i = 0; i < lote.size(); i++) {
            PutRecordsResultEntry r = i < resultados.size() ? resultados.get(i) : null;
            if (r == null || r.errorCode() != null) rechazados++;
        }
        return rechazados;
    }

    public long descartados() {
        return descartados.get();
    }

    public void iniciar() {
        if (iniciado.compareAndSet(false, true)) {
            long ms = intervalo.toMillis();
            executor.scheduleWithFixedDelay(this::enviarSinPropagar, ms, ms, TimeUnit.MILLISECONDS);
        }
    }

    // Una excepción que escapa de una tarea de scheduleWithFixedDelay cancela las siguientes ejecuciones.
    private void enviarSinPropagar() {
        try {
            enviarPendientes();
        } catch (RuntimeException e) {
            log.warn("Telemetría: falló el envío programado ({})", e.getClass().getSimpleName());
        }
    }

    @Override
    public void close() {
        executor.shutdown();
        try {
            executor.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        enviarSinPropagar();
    }
}
