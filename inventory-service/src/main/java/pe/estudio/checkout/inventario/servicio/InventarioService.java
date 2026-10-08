package pe.estudio.checkout.inventario.servicio;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.estudio.checkout.contratos.MensajeSaga;
import pe.estudio.checkout.contratos.MensajeSaga.LiberarStock;
import pe.estudio.checkout.contratos.MensajeSaga.LineaOrden;
import pe.estudio.checkout.contratos.MensajeSaga.ReservarStock;
import pe.estudio.checkout.contratos.MensajeSaga.StockLiberado;
import pe.estudio.checkout.contratos.MensajeSaga.StockRechazado;
import pe.estudio.checkout.contratos.MensajeSaga.StockReservado;
import pe.estudio.checkout.contratos.Topicos;
import pe.estudio.checkout.inventario.modelo.MensajeProcesado;
import pe.estudio.checkout.inventario.modelo.MensajeProcesadoRepository;
import pe.estudio.checkout.inventario.modelo.Producto;
import pe.estudio.checkout.inventario.modelo.ProductoRepository;
import pe.estudio.checkout.inventario.modelo.Reserva;
import pe.estudio.checkout.inventario.modelo.ReservaRepository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class InventarioService {

    private static final Logger log = LoggerFactory.getLogger(InventarioService.class);

    private final ProductoRepository productos;
    private final ReservaRepository reservas;
    private final MensajeProcesadoRepository procesados;
    private final KafkaTemplate<String, Object> kafka;

    public InventarioService(ProductoRepository productos, ReservaRepository reservas,
                             MensajeProcesadoRepository procesados, KafkaTemplate<String, Object> kafka) {
        this.productos = productos;
        this.reservas = reservas;
        this.procesados = procesados;
        this.kafka = kafka;
    }

    @Transactional
    public Optional<MensajeSaga> reservar(ReservarStock comando) {
        if (yaProcesado(comando.mensajeId())) {
            return Optional.empty();
        }

        List<String> skus = comando.lineas().stream().map(LineaOrden::sku).toList();
        Map<String, Producto> porSku = productos.findAllById(skus).stream()
                .collect(Collectors.toMap(Producto::getSku, Function.identity()));

        Map<String, Integer> pedidoPorSku = comando.lineas().stream()
                .collect(Collectors.groupingBy(LineaOrden::sku, java.util.LinkedHashMap::new,
                        Collectors.summingInt(LineaOrden::cantidad)));

        Optional<String> problema = pedidoPorSku.entrySet().stream()
                .filter(e -> !porSku.containsKey(e.getKey()) || !porSku.get(e.getKey()).alcanzaPara(e.getValue()))
                .map(e -> porSku.containsKey(e.getKey())
                        ? e.getKey() + " sin stock suficiente (pedido " + e.getValue() + ", disponible " + porSku.get(e.getKey()).getDisponible() + ")"
                        : e.getKey() + " no existe")
                .findFirst();

        MensajeSaga respuesta;
        if (problema.isPresent()) {
            respuesta = new StockRechazado(UUID.randomUUID().toString(), comando.ordenId(), problema.get());
        } else {
            comando.lineas().forEach(l -> {
                porSku.get(l.sku()).reservar(l.cantidad());
                reservas.save(new Reserva(comando.ordenId(), l.sku(), l.cantidad()));
            });
            respuesta = new StockReservado(UUID.randomUUID().toString(), comando.ordenId());
        }
        return Optional.of(registrarYResponder(comando.mensajeId(), comando.ordenId(), respuesta));
    }

    @Transactional
    public Optional<MensajeSaga> liberar(LiberarStock comando) {
        if (yaProcesado(comando.mensajeId())) {
            return Optional.empty();
        }
        reservas.findByOrdenIdAndEstado(comando.ordenId(), Reserva.Estado.ACTIVA).forEach(reserva -> {
            productos.findById(reserva.getSku()).ifPresent(p -> p.liberar(reserva.getCantidad()));
            reserva.marcarLiberada();
        });
        log.info("Stock liberado para la orden {} ({})", comando.ordenId(), comando.motivo());
        return Optional.of(registrarYResponder(comando.mensajeId(), comando.ordenId(),
                new StockLiberado(UUID.randomUUID().toString(), comando.ordenId())));
    }

    private boolean yaProcesado(String mensajeId) {
        if (procesados.existsById(mensajeId)) {
            log.info("Mensaje {} ya procesado: se ignora (consumidor idempotente)", mensajeId);
            return true;
        }
        return false;
    }

    private MensajeSaga registrarYResponder(String mensajeId, String ordenId, MensajeSaga respuesta) {
        procesados.save(new MensajeProcesado(mensajeId));
        try {

            kafka.send(Topicos.INVENTARIO_RESPUESTAS, ordenId, respuesta).get(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrumpido al responder", e);
        } catch (Exception e) {

            throw new IllegalStateException("No se pudo publicar la respuesta de la orden " + ordenId, e);
        }
        log.info("→ {} para la orden {}", respuesta.getClass().getSimpleName(), ordenId);
        return respuesta;
    }
}
