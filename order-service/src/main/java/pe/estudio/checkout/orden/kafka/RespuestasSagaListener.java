package pe.estudio.checkout.orden.kafka;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import pe.estudio.checkout.contratos.MensajeSaga.RespuestaInventario;
import pe.estudio.checkout.contratos.MensajeSaga.RespuestaPago;
import pe.estudio.checkout.contratos.Topicos;
import pe.estudio.checkout.orden.aplicacion.OrquestadorCheckout;

import java.time.Duration;

@Component
public class RespuestasSagaListener {

    private static final Logger log = LoggerFactory.getLogger(RespuestasSagaListener.class);
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private final OrquestadorCheckout orquestador;

    public RespuestasSagaListener(OrquestadorCheckout orquestador) {
        this.orquestador = orquestador;
    }

    @KafkaListener(topics = Topicos.INVENTARIO_RESPUESTAS, groupId = "order-service")
    public void alRecibirInventario(@Payload RespuestaInventario respuesta,
                                    @Header(KafkaHeaders.RECEIVED_PARTITION) int particion,
                                    @Header(KafkaHeaders.OFFSET) long offset) {
        log.info("← {} orden={} [partición {} offset {}]",
                respuesta.getClass().getSimpleName(), respuesta.ordenId(), particion, offset);
        orquestador.procesar(respuesta).block(TIMEOUT);
    }

    @KafkaListener(topics = Topicos.PAGOS_RESPUESTAS, groupId = "order-service")
    public void alRecibirPago(@Payload RespuestaPago respuesta) {
        log.info("← {} orden={}", respuesta.getClass().getSimpleName(), respuesta.ordenId());
        orquestador.procesar(respuesta).block(TIMEOUT);
    }
}
