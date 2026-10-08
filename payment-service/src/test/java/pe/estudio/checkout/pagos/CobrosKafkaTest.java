package pe.estudio.checkout.pagos;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.ActiveProfiles;
import pe.estudio.checkout.contratos.MensajeSaga;
import pe.estudio.checkout.contratos.MensajeSaga.CobrarOrden;
import pe.estudio.checkout.contratos.MensajeSaga.PagoAprobado;
import pe.estudio.checkout.contratos.MensajeSaga.PagoRechazado;
import pe.estudio.checkout.contratos.Topicos;
import pe.estudio.checkout.pagos.persistencia.Pago;
import pe.estudio.checkout.pagos.persistencia.RegistroPagos;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("test")
@SpringBootTest(properties = "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}")
@EmbeddedKafka(partitions = 1, topics = {Topicos.PAGOS_COMANDOS, Topicos.PAGOS_RESPUESTAS})
class CobrosKafkaTest {

    @Autowired
    private KafkaTemplate<String, Object> kafka;

    @Autowired
    private EmbeddedKafkaBroker broker;

    @Autowired
    private RegistroPagos registroPagos;

    private Consumer<String, MensajeSaga> respuestas;

    @BeforeEach
    void setUp() {
        Map<String, Object> props = KafkaTestUtils.consumerProps("test-" + UUID.randomUUID(), "true", broker);
        JsonDeserializer<MensajeSaga> json = new JsonDeserializer<>(MensajeSaga.class);
        json.addTrustedPackages("pe.estudio.checkout.contratos");
        respuestas = new DefaultKafkaConsumerFactory<>(props, new StringDeserializer(), json).createConsumer();
        broker.consumeFromAnEmbeddedTopic(respuestas, Topicos.PAGOS_RESPUESTAS);
    }

    @AfterEach
    void tearDown() {
        respuestas.close();
    }

    private MensajeSaga respuestaPara(String ordenId) {
        long limite = System.currentTimeMillis() + 15_000;
        while (System.currentTimeMillis() < limite) {
            for (ConsumerRecord<String, MensajeSaga> r : KafkaTestUtils.getRecords(respuestas, Duration.ofSeconds(2))) {
                if (ordenId.equals(r.key())) {
                    return r.value();
                }
            }
        }
        throw new AssertionError("Sin respuesta para " + ordenId);
    }

    @Test
    @DisplayName("Monto normal → PagoAprobado")
    void aprobado() {
        String ordenId = "o-" + UUID.randomUUID();
        kafka.send(Topicos.PAGOS_COMANDOS, ordenId,
                new CobrarOrden(UUID.randomUUID().toString(), ordenId, "cli-1", new BigDecimal("120.00"), "4242"));

        assertThat(respuestaPara(ordenId)).isInstanceOf(PagoAprobado.class);
        assertThat(registroPagos.buscarPorOrden(ordenId)).hasValueSatisfying(p -> {
            assertThat(p.estado()).isEqualTo(Pago.Estado.APROBADO);
            assertThat(p.referencia()).startsWith("TX-");
        });
    }

    @Test
    @DisplayName("Monto > 5000 → PagoRechazado (error de negocio, sin reintentos)")
    void rechazado() {
        String ordenId = "o-" + UUID.randomUUID();
        kafka.send(Topicos.PAGOS_COMANDOS, ordenId,
                new CobrarOrden(UUID.randomUUID().toString(), ordenId, "cli-1", new BigDecimal("9000.00"), "4242"));

        assertThat(respuestaPara(ordenId)).isInstanceOfSatisfying(PagoRechazado.class,
                r -> assertThat(r.motivo()).contains("Fondos insuficientes"));
        assertThat(registroPagos.buscarPorOrden(ordenId)).hasValueSatisfying(p ->
                assertThat(p.estado()).isEqualTo(Pago.Estado.RECHAZADO));
    }
}
