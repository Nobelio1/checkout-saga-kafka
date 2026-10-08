package pe.estudio.checkout.pagos.kafka;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.RecoverableDataAccessException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.retrytopic.DltStrategy;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;
import pe.estudio.checkout.contratos.MensajeSaga;
import pe.estudio.checkout.contratos.MensajeSaga.CobrarOrden;
import pe.estudio.checkout.contratos.MensajeSaga.PagoAprobado;
import pe.estudio.checkout.contratos.MensajeSaga.PagoRechazado;
import pe.estudio.checkout.contratos.Topicos;
import pe.estudio.checkout.pagos.pasarela.PasarelaPagos;
import pe.estudio.checkout.pagos.pasarela.PasarelaPagos.Aprobado;
import pe.estudio.checkout.pagos.pasarela.PasarelaPagos.Rechazado;
import pe.estudio.checkout.pagos.persistencia.Pago;
import pe.estudio.checkout.pagos.persistencia.Pago.Estado;
import pe.estudio.checkout.pagos.persistencia.RegistroPagos;

import java.util.UUID;

@Component
public class CobrosListener {

    private static final Logger log = LoggerFactory.getLogger(CobrosListener.class);

    private final PasarelaPagos pasarela;
    private final RegistroPagos registroPagos;
    private final KafkaTemplate<String, Object> kafka;

    public CobrosListener(PasarelaPagos pasarela, RegistroPagos registroPagos, KafkaTemplate<String, Object> kafka) {
        this.pasarela = pasarela;
        this.registroPagos = registroPagos;
        this.kafka = kafka;
    }

    @RetryableTopic(
            attempts = "4",
            backoff = @Backoff(delay = 1_000, multiplier = 2.0),
            dltTopicSuffix = "-dlt",
            dltStrategy = DltStrategy.FAIL_ON_ERROR,

            include = {PasarelaPagos.PasarelaNoDisponibleException.class,
                    DataAccessResourceFailureException.class,
                    TransientDataAccessException.class,
                    RecoverableDataAccessException.class},
            traversingCauses = "true",

            topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE)
    @KafkaListener(topics = Topicos.PAGOS_COMANDOS, groupId = "payment-service")
    public void cobrar(CobrarOrden comando, @Header(KafkaHeaders.RECEIVED_TOPIC) String topico) {
        log.info("← CobrarOrden orden={} monto={} (desde {})", comando.ordenId(), comando.monto(), topico);

        MensajeSaga respuesta = switch (pasarela.cobrar(comando.ordenId(), comando.monto(), comando.tarjetaUltimos4())) {
            case Aprobado a -> {
                registroPagos.registrar(comando.ordenId(), Estado.APROBADO, comando.monto(),
                        comando.tarjetaUltimos4(), a.transaccionId(), null);
                yield new PagoAprobado(UUID.randomUUID().toString(), comando.ordenId(), a.transaccionId());
            }
            case Rechazado r -> {
                registroPagos.registrar(comando.ordenId(), Estado.RECHAZADO, comando.monto(),
                        comando.tarjetaUltimos4(), null, r.motivo());
                yield new PagoRechazado(UUID.randomUUID().toString(), comando.ordenId(), r.motivo());
            }
        };
        responder(comando.ordenId(), respuesta);
    }

    @DltHandler
    public void agotado(CobrarOrden comando, @Header(KafkaHeaders.RECEIVED_TOPIC) String topico) {
        log.error("Cobro de la orden {} agotó los reintentos (llegó a {}). Se rechaza para compensar.",
                comando.ordenId(), topico);
        String motivo = "Pasarela de pagos no disponible, intente más tarde";
        try {
            boolean registrado = registroPagos.registrar(comando.ordenId(), Estado.RECHAZADO, comando.monto(),
                    comando.tarjetaUltimos4(), null, motivo);
            if (!registrado) {

                Pago previo = registroPagos.buscarPorOrden(comando.ordenId()).orElse(null);
                if (previo != null && previo.estado() == Estado.APROBADO) {
                    log.warn("La orden {} ya estaba COBRADA: se responde PagoAprobado en lugar de rechazarla", comando.ordenId());
                    responder(comando.ordenId(), new PagoAprobado(UUID.randomUUID().toString(), comando.ordenId(), previo.referencia()));
                    return;
                }
            }
        } catch (DataAccessException e) {

            log.error("No se pudo registrar el rechazo de la orden {}", comando.ordenId(), e);
        }
        responder(comando.ordenId(), new PagoRechazado(UUID.randomUUID().toString(), comando.ordenId(), motivo));
    }

    private void responder(String ordenId, MensajeSaga respuesta) {

        kafka.send(Topicos.PAGOS_RESPUESTAS, ordenId, respuesta).join();
        log.info("→ {} orden={}", respuesta.getClass().getSimpleName(), ordenId);
    }
}
