package pe.estudio.checkout.pagos.pasarela;

import java.math.BigDecimal;

public interface PasarelaPagos {

    sealed interface Resultado permits Aprobado, Rechazado {
    }

    record Aprobado(String transaccionId) implements Resultado {
    }

    record Rechazado(String motivo) implements Resultado {
    }

    Resultado cobrar(String claveIdempotencia, BigDecimal monto, String tarjetaUltimos4);

    class PasarelaNoDisponibleException extends RuntimeException {
        public PasarelaNoDisponibleException(String mensaje) {
            super(mensaje);
        }
    }
}
