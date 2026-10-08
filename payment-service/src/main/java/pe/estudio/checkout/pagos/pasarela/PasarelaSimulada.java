package pe.estudio.checkout.pagos.pasarela;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class PasarelaSimulada implements PasarelaPagos {

    private static final BigDecimal LIMITE = new BigDecimal("5000");
    private final Map<String, Resultado> cobrosPrevios = new ConcurrentHashMap<>();

    @Override
    public Resultado cobrar(String claveIdempotencia, BigDecimal monto, String tarjetaUltimos4) {
        if ("0000".equals(tarjetaUltimos4)) {
            throw new PasarelaNoDisponibleException("Timeout al conectar con la pasarela");
        }

        return cobrosPrevios.computeIfAbsent(claveIdempotencia, clave ->
                monto.compareTo(LIMITE) > 0
                        ? new Rechazado("Fondos insuficientes para " + monto)
                        : new Aprobado("TX-" + UUID.randomUUID().toString().substring(0, 8)));
    }
}
