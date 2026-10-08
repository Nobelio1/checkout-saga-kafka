package pe.estudio.checkout.inventario;

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
import pe.estudio.checkout.contratos.MensajeSaga.LineaOrden;
import pe.estudio.checkout.contratos.MensajeSaga.ReservarStock;
import pe.estudio.checkout.contratos.MensajeSaga.StockRechazado;
import pe.estudio.checkout.contratos.MensajeSaga.StockReservado;
import pe.estudio.checkout.contratos.Topicos;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("test")
@SpringBootTest(properties = "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}")
@EmbeddedKafka(partitions = 1, topics = {Topicos.INVENTARIO_COMANDOS, Topicos.INVENTARIO_RESPUESTAS})
class InventarioKafkaTest {

    @Autowired
    private KafkaTemplate<String, Object> kafka;

    @Autowired
    private EmbeddedKafkaBroker broker;

    private Consumer<String, MensajeSaga> consumidorDeRespuestas;

    @BeforeEach
    void crearConsumidor() {
        Map<String, Object> props = KafkaTestUtils.consumerProps("test-" + UUID.randomUUID(), "true", broker);
        JsonDeserializer<MensajeSaga> json = new JsonDeserializer<>(MensajeSaga.class);
        json.addTrustedPackages("pe.estudio.checkout.contratos");
        consumidorDeRespuestas = new DefaultKafkaConsumerFactory<>(props, new StringDeserializer(), json).createConsumer();
        broker.consumeFromAnEmbeddedTopic(consumidorDeRespuestas, Topicos.INVENTARIO_RESPUESTAS);
    }

    @AfterEach
    void cerrar() {
        consumidorDeRespuestas.close();
    }

    private ConsumerRecord<String, MensajeSaga> respuestaPara(String ordenId) {

        long limite = System.currentTimeMillis() + 15_000;
        while (System.currentTimeMillis() < limite) {
            for (ConsumerRecord<String, MensajeSaga> r : KafkaTestUtils.getRecords(consumidorDeRespuestas, Duration.ofSeconds(2))) {
                if (ordenId.equals(r.key())) {
                    return r;
                }
            }
        }
        throw new AssertionError("No llegó respuesta para la orden " + ordenId);
    }

    @Test
    @DisplayName("ReservarStock con stock suficiente → StockReservado (key = ordenId)")
    void reservaExitosa() {
        String ordenId = "orden-" + UUID.randomUUID();
        kafka.send(Topicos.INVENTARIO_COMANDOS, ordenId, new ReservarStock(UUID.randomUUID().toString(), ordenId,
                List.of(new LineaOrden("TEC-002", 3, new BigDecimal("40.00")))));

        ConsumerRecord<String, MensajeSaga> respuesta = respuestaPara(ordenId);
        assertThat(respuesta.value()).isInstanceOf(StockReservado.class);
    }

    @Test
    @DisplayName("ReservarStock sin stock suficiente → StockRechazado con el motivo")
    void reservaRechazada() {
        String ordenId = "orden-" + UUID.randomUUID();
        kafka.send(Topicos.INVENTARIO_COMANDOS, ordenId, new ReservarStock(UUID.randomUUID().toString(), ordenId,
                List.of(new LineaOrden("TEC-003", 50, new BigDecimal("900.00")))));

        ConsumerRecord<String, MensajeSaga> respuesta = respuestaPara(ordenId);
        assertThat(respuesta.value()).isInstanceOfSatisfying(StockRechazado.class,
                r -> assertThat(r.motivo()).contains("TEC-003"));
    }
}
