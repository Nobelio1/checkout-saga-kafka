package pe.estudio.checkout.orden.dominio;

import pe.estudio.checkout.contratos.MensajeSaga.LineaOrden;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record Orden(
        String id,
        String clienteId,
        List<LineaOrden> lineas,
        BigDecimal total,
        String tarjetaUltimos4,
        EstadoOrden estado,
        String motivo,
        String idempotencyKey,
        Instant creadaEn,
        Instant actualizadaEn) {

    public Orden {
        Objects.requireNonNull(id);
        Objects.requireNonNull(estado);
        if (lineas == null || lineas.isEmpty()) {
            throw new IllegalArgumentException("La orden debe tener al menos una línea");
        }
        lineas = List.copyOf(lineas);
    }

    public static Orden nueva(String clienteId, List<LineaOrden> lineas, String tarjetaUltimos4,
                              String idempotencyKey, Instant ahora) {

        BigDecimal total = lineas.stream()
                .map(l -> l.precioUnitario().multiply(BigDecimal.valueOf(l.cantidad())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new Orden(UUID.randomUUID().toString(), clienteId, lineas, total, tarjetaUltimos4,
                EstadoOrden.PENDIENTE_STOCK, null, idempotencyKey, ahora, ahora);
    }

    public Orden pasarA(EstadoOrden nuevoEstado, String nuevoMotivo, Instant ahora) {
        return new Orden(id, clienteId, lineas, total, tarjetaUltimos4, nuevoEstado, nuevoMotivo,
                idempotencyKey, creadaEn, ahora);
    }
}
