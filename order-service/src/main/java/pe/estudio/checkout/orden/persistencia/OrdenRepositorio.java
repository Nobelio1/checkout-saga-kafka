package pe.estudio.checkout.orden.persistencia;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.ReactiveTransactionManager;
import org.springframework.transaction.reactive.TransactionalOperator;
import pe.estudio.checkout.contratos.MensajeSaga.LineaOrden;
import pe.estudio.checkout.orden.dominio.EstadoOrden;
import pe.estudio.checkout.orden.dominio.Orden;
import pe.estudio.checkout.orden.dominio.SagaCheckout.Saliente;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Component
public class OrdenRepositorio {

    private final R2dbcEntityTemplate template;
    private final OrdenR2dbcRepository ordenes;
    private final ObjectMapper json;
    private final TransactionalOperator transaccion;
    private final Clock reloj;

    public OrdenRepositorio(R2dbcEntityTemplate template, OrdenR2dbcRepository ordenes, ObjectMapper json,
                            ReactiveTransactionManager txManager, Clock reloj) {
        this.template = template;
        this.ordenes = ordenes;
        this.json = json;
        this.transaccion = TransactionalOperator.create(txManager);
        this.reloj = reloj;
    }

    public Mono<Orden> guardarNueva(Orden orden, List<Saliente> salientes) {
        Mono<Orden> operacion = template.insert(aEntidad(orden))
                .thenMany(guardarSalientes(orden.id(), salientes))
                .then(Mono.just(orden));
        return transaccion.transactional(operacion);
    }

    public Mono<Orden> actualizar(Orden orden, List<Saliente> salientes) {
        Mono<Orden> operacion = template.update(aEntidad(orden))
                .thenMany(guardarSalientes(orden.id(), salientes))
                .then(Mono.just(orden));
        return transaccion.transactional(operacion);
    }

    public Mono<Orden> buscarPorId(String id) {
        return ordenes.findById(id).map(this::aDominio);
    }

    public Mono<Orden> buscarPorIdempotencyKey(String key) {
        return ordenes.findByIdempotencyKey(key).map(this::aDominio);
    }

    private Flux<OutboxEntity> guardarSalientes(String ordenId, List<Saliente> salientes) {
        return Flux.fromIterable(salientes)
                .map(s -> new OutboxEntity(null, s.topico(), ordenId, s.mensaje().getClass().getName(),
                        aJson(s.mensaje()), LocalDateTime.now(reloj), false))
                .concatMap(fila -> template.insert(fila));
    }

    private OrdenEntity aEntidad(Orden o) {
        return new OrdenEntity(o.id(), o.clienteId(), aJson(o.lineas()), o.total(), o.tarjetaUltimos4(),
                o.estado().name(), recortar(o.motivo(), 300), o.idempotencyKey(),
                LocalDateTime.ofInstant(o.creadaEn(), ZoneOffset.UTC),
                LocalDateTime.ofInstant(o.actualizadaEn(), ZoneOffset.UTC));
    }

    private static String recortar(String texto, int maximo) {
        return texto == null || texto.length() <= maximo ? texto : texto.substring(0, maximo);
    }

    private Orden aDominio(OrdenEntity e) {
        try {
            List<LineaOrden> lineas = json.readValue(e.lineasJson(), new TypeReference<>() {
            });
            return new Orden(e.id(), e.clienteId(), lineas, e.total(), e.tarjetaUltimos4(),
                    EstadoOrden.valueOf(e.estado()), e.motivo(), e.idempotencyKey(),
                    e.creadaEn().toInstant(ZoneOffset.UTC), e.actualizadaEn().toInstant(ZoneOffset.UTC));
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Líneas corruptas en la orden " + e.id(), ex);
        }
    }

    private String aJson(Object valor) {
        try {
            return json.writeValueAsString(valor);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
