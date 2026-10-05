package com.rrhh.asistencia.service;

import com.rrhh.asistencia.dto.request.CrearJornadaRequest;
import com.rrhh.asistencia.exception.DomainException;
import com.rrhh.asistencia.model.Turno;
import com.rrhh.asistencia.repository.JornadaRepository;
import com.rrhh.asistencia.repository.TurnoRepository;
import com.rrhh.asistencia.security.TenantContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HorarioServiceTest {

    @Mock
    private TurnoRepository turnoRepository;
    @Mock
    private JornadaRepository jornadaRepository;
    @Mock
    private TenantContext tenantContext;
    @InjectMocks
    private HorarioService horarioService;

    @Test
    void rechazaJornadaActivaDuplicadaElMismoDia() {
        when(tenantContext.require()).thenReturn(new TenantContext.AuthenticatedUser(
                "tenant-1", "user-1", "rrhh@rrhh.local", "Admin de RRHH", null, "sub"
        ));
        Turno turno = new Turno();
        turno.setId("turno-1");
        turno.setTenantId("tenant-1");
        turno.setNombre("Mañana");
        turno.setHoraInicio(LocalTime.of(9, 0));
        turno.setHoraFin(LocalTime.of(18, 0));
        turno.setActivo(true);
        LocalDate fecha = LocalDate.of(2026, 9, 22);
        when(turnoRepository.findByIdAndTenantId("turno-1", "tenant-1")).thenReturn(Optional.of(turno));
        when(jornadaRepository.existsByTenantIdAndTrabajadorIdAndFechaAndActivoTrue(
                "tenant-1", "trab-1", fecha
        )).thenReturn(true);

        DomainException error = assertThrows(DomainException.class, () -> horarioService.asignar(
                new CrearJornadaRequest("trab-1", "turno-1", fecha)
        ));

        assertEquals(400, error.getCodigo());
        assertEquals("fecha", error.getCampo());
    }
}
