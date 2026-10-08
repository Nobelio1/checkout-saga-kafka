package pe.estudio.checkout.pagos.kafka;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import pe.estudio.checkout.contratos.Topicos;

@Configuration
public class KafkaConfig {

    @Bean
    NewTopic pagosComandos() {
        return TopicBuilder.name(Topicos.PAGOS_COMANDOS).partitions(3).replicas(1).build();
    }

    @Bean
    NewTopic pagosRespuestas() {
        return TopicBuilder.name(Topicos.PAGOS_RESPUESTAS).partitions(3).replicas(1).build();
    }
}
