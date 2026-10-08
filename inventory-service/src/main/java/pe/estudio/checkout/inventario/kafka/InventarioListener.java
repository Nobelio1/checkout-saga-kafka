package pe.estudio.checkout.inventario.kafka;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import pe.estudio.checkout.contratos.MensajeSaga.LiberarStock;
import pe.estudio.checkout.contratos.MensajeSaga.ReservarStock;
import pe.estudio.checkout.contratos.Topicos;
import pe.estudio.checkout.inventario.servicio.InventarioService;

@Component
@KafkaListener(topics = Topicos.INVENTARIO_COMANDOS, groupId = "inventory-service")
public class InventarioListener {

    private static final Logger log = LoggerFactory.getLogger(InventarioListener.class);

    private final InventarioService servicio;

    public InventarioListener(InventarioService servicio) {
        this.servicio = servicio;
    }

    @KafkaHandler
    public void reservar(ReservarStock comando, Acknowledgment ack) {
        log.info("← ReservarStock orden={} lineas={}", comando.ordenId(), comando.lineas().size());
        servicio.reservar(comando);
        ack.acknowledge();
    }

    @KafkaHandler
    public void liberar(LiberarStock comando, Acknowledgment ack) {
        log.info("← LiberarStock orden={}", comando.ordenId());
        servicio.liberar(comando);
        ack.acknowledge();
    }

    @KafkaHandler(isDefault = true)
    public void desconocido(Object mensaje, Acknowledgment ack) {
        log.warn("Mensaje de tipo inesperado en {}: {}", Topicos.INVENTARIO_COMANDOS, mensaje);
        ack.acknowledge();
    }
}
