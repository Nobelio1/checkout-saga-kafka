package pe.estudio.checkout.orden.persistencia;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

@Table("outbox")
public record OutboxEntity(@Id Long id, String topico, String clave, String tipo, String payload,
                           LocalDateTime creadoEn, boolean publicado) {

    public OutboxEntity withId(Long nuevoId) {
        return new OutboxEntity(nuevoId, topico, clave, tipo, payload, creadoEn, publicado);
    }
}
