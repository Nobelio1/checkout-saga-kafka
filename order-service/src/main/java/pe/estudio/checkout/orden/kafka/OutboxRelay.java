package pe.estudio.checkout.orden.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import pe.estudio.checkout.orden.persistencia.OutboxEntity;
import pe.estudio.checkout.orden.persistencia.OutboxR2dbcRepository;
import reactor.core.publisher.Mono;

@Component
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    private final OutboxR2dbcRepository outbox;
    private final KafkaTemplate<String, Object> kafka;
    private final ObjectMapper json;

    public OutboxRelay(OutboxR2dbcRepository outbox, KafkaTemplate<String, Object> kafka, ObjectMapper json) {
        this.outbox = outbox;
        this.kafka = kafka;
        this.json = json;
    }

    @Scheduled(fixedDelayString = "${checkout.outbox.intervalo-ms:300}")
    public Mono<Void> publicarPendientes() {
        return outbox.findTop50ByPublicadoFalseOrderByIdAsc()
                .concatMap(fila -> enviar(fila)
                        .then(outbox.marcarPublicado(fila.id()))
                        .onErrorResume(error -> {

                            log.warn("No se pudo publicar outbox {} en {}: {}", fila.id(), fila.topico(), error.toString());
                            return Mono.empty();
                        }))
                .then();
    }

    private Mono<SendResult<String, Object>> enviar(OutboxEntity fila) {
        return Mono.fromCallable(() -> json.readValue(fila.payload(), Class.forName(fila.tipo())))
                .flatMap(mensaje -> Mono.fromFuture(() -> kafka.send(fila.topico(), fila.clave(), mensaje)))
                .doOnNext(resultado -> log.info("→ {} [partición {} offset {}] {}",
                        fila.topico(),
                        resultado.getRecordMetadata().partition(),
                        resultado.getRecordMetadata().offset(),
                        fila.tipo().substring(fila.tipo().lastIndexOf('$') + 1)));
    }
}
