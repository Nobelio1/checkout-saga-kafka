package pe.estudio.checkout.orden.persistencia;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Table("ordenes")
public record OrdenEntity(
        @Id String id,
        String clienteId,
        String lineasJson,
        BigDecimal total,
        String tarjetaUltimos4,
        String estado,
        String motivo,
        String idempotencyKey,
        LocalDateTime creadaEn,
        LocalDateTime actualizadaEn) {
}
