package pe.estudio.checkout.inventario.kafka;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.ExponentialBackOff;
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
    NewTopic inventarioComandosDlt() {
        return TopicBuilder.name(Topicos.INVENTARIO_COMANDOS + ".DLT").partitions(3).replicas(1).build();
    }

    @Bean
    CommonErrorHandler manejadorErroresKafka(KafkaOperations<?, ?> kafka) {
        ExponentialBackOff backoff = new ExponentialBackOff(500L, 2.0);
        backoff.setMaxElapsedTime(10_000L);
        DefaultErrorHandler manejador = new DefaultErrorHandler(new DeadLetterPublishingRecoverer(kafka), backoff);
        manejador.addNotRetryableExceptions(IllegalArgumentException.class);
        return manejador;
    }
}
