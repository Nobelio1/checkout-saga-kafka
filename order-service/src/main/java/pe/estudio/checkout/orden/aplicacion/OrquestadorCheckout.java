package pe.estudio.checkout.orden.aplicacion;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import pe.estudio.checkout.contratos.MensajeSaga.LineaOrden;
import pe.estudio.checkout.contratos.MensajeSaga.RespuestaInventario;
import pe.estudio.checkout.contratos.MensajeSaga.RespuestaPago;
import pe.estudio.checkout.orden.dominio.Orden;
import pe.estudio.checkout.orden.dominio.SagaCheckout;
import pe.estudio.checkout.orden.dominio.SagaCheckout.Decision;
import pe.estudio.checkout.orden.persistencia.OrdenRepositorio;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.util.List;
import java.util.function.Function;

@Service
public class OrquestadorCheckout {

    private static final Logger log = LoggerFactory.getLogger(OrquestadorCheckout.class);

    private final OrdenRepositorio repositorio;
    private final SagaCheckout saga = new SagaCheckout();
    private final Clock reloj;

    public OrquestadorCheckout(OrdenRepositorio repositorio, Clock reloj) {
        this.repositorio = repositorio;
        this.reloj = reloj;
    }

    public record CrearOrden(String clienteId, List<LineaOrden> lineas, String tarjetaUltimos4, String idempotencyKey) {
    }

    public Mono<Orden> crear(CrearOrden comando) {
        return repositorio.buscarPorIdempotencyKey(comando.idempotencyKey())
                .switchIfEmpty(Mono.defer(() -> {
                    Orden orden = Orden.nueva(comando.clienteId(), comando.lineas(), comando.tarjetaUltimos4(),
                            comando.idempotencyKey(), reloj.instant());
                    Decision decision = saga.iniciar(orden);
                    log.info("Saga iniciada para la orden {} (total {})", orden.id(), orden.total());
                    return repositorio.guardarNueva(decision.orden(), decision.salientes())
                            .onErrorResume(DataIntegrityViolationException.class, e ->
                                    repositorio.buscarPorIdempotencyKey(comando.idempotencyKey())
                                            .switchIfEmpty(Mono.error(e)));
                }));
    }

    public Mono<Orden> buscar(String ordenId) {
        return repositorio.buscarPorId(ordenId)
                .switchIfEmpty(Mono.error(() -> new OrdenNoEncontradaException(ordenId)));
    }

    public Mono<Orden> procesar(RespuestaInventario respuesta) {
        return aplicar(respuesta.ordenId(), orden -> saga.alRecibir(orden, respuesta, reloj.instant()));
    }

    public Mono<Orden> procesar(RespuestaPago respuesta) {
        return aplicar(respuesta.ordenId(), orden -> saga.alRecibir(orden, respuesta, reloj.instant()));
    }

    private Mono<Orden> aplicar(String ordenId, Function<Orden, Decision> decidir) {
        return buscar(ordenId)
                .map(decidir)
                .flatMap(decision -> {
                    if (!decision.huboCambio()) {
                        log.info("Mensaje duplicado o fuera de orden para {}: se ignora (estado {})",
                                ordenId, decision.orden().estado());
                        return Mono.just(decision.orden());
                    }
                    log.info("Orden {} → {}", ordenId, decision.orden().estado());
                    return repositorio.actualizar(decision.orden(), decision.salientes());
                });
    }

    public static class OrdenNoEncontradaException extends RuntimeException {
        public OrdenNoEncontradaException(String id) {
            super("Orden " + id + " no encontrada");
        }
    }
}
