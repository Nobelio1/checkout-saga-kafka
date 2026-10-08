package pe.estudio.checkout.orden.persistencia;

import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface OutboxR2dbcRepository extends ReactiveCrudRepository<OutboxEntity, Long> {

    Flux<OutboxEntity> findTop50ByPublicadoFalseOrderByIdAsc();

    @Modifying
    @Query("UPDATE outbox SET publicado = TRUE WHERE id = :id")
    Mono<Integer> marcarPublicado(@Param("id") Long id);
}
