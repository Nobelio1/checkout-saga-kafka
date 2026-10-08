package pe.estudio.checkout.pagos.persistencia;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record Pago(String id, String ordenId, Estado estado, BigDecimal monto, String tarjetaUltimos4,
                   String referencia, String motivo, OffsetDateTime creadoEn) {

    public enum Estado { APROBADO, RECHAZADO }
}
