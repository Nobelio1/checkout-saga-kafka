package pe.estudio.checkout.orden.persistencia;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Mono;

public interface OrdenR2dbcRepository extends ReactiveCrudRepository<OrdenEntity, String> {

    Mono<OrdenEntity> findByIdempotencyKey(String idempotencyKey);
}
