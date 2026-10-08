package pe.estudio.checkout.orden.kafka;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;
import pe.estudio.checkout.contratos.Topicos;

@Configuration
public class KafkaConfig {

    @Bean
    NewTopic inventarioComandos() {
        return TopicBuilder.name(Topicos.INVENTARIO_COMANDOS).partitions(3).replicas(1).build();
    }

    @Bean
    NewTopic inventarioRespuestas() {
        return TopicBuilder.name(Topicos.INVENTARIO_RESPUESTAS).partitions(3).replicas(1).build();
    }

    @Bean
    NewTopic pagosComandos() {
        return TopicBuilder.name(Topicos.PAGOS_COMANDOS).partitions(3).replicas(1).build();
    }

    @Bean
    NewTopic pagosRespuestas() {
        return TopicBuilder.name(Topicos.PAGOS_RESPUESTAS).partitions(3).replicas(1).build();
    }

    @Bean
    CommonErrorHandler manejadorErroresKafka(KafkaOperations<?, ?> kafka) {
        DeadLetterPublishingRecoverer haciaDlt = new DeadLetterPublishingRecoverer(kafka);
        DefaultErrorHandler manejador = new DefaultErrorHandler(haciaDlt, new FixedBackOff(1_000L, 3));

        manejador.addNotRetryableExceptions(IllegalArgumentException.class);
        return manejador;
    }
}
