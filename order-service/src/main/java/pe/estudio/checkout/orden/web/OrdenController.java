package pe.estudio.checkout.orden.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;
import pe.estudio.checkout.contratos.MensajeSaga.LineaOrden;
import pe.estudio.checkout.orden.aplicacion.OrquestadorCheckout;
import pe.estudio.checkout.orden.aplicacion.OrquestadorCheckout.CrearOrden;
import pe.estudio.checkout.orden.aplicacion.OrquestadorCheckout.OrdenNoEncontradaException;
import pe.estudio.checkout.orden.dominio.Orden;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/ordenes")
public class OrdenController {

    private final OrquestadorCheckout orquestador;

    public OrdenController(OrquestadorCheckout orquestador) {
        this.orquestador = orquestador;
    }

    public record LineaRequest(@NotBlank @Size(max = 64) String sku,
                               @NotNull @Positive @Max(1000) Integer cantidad,
                               @NotNull @DecimalMin("0.01") @Digits(integer = 5, fraction = 2) BigDecimal precioUnitario) {
    }

    public record CrearOrdenRequest(@NotBlank @Size(max = 60) String clienteId,
                                    @NotBlank @Pattern(regexp = "\\d{4}") String tarjetaUltimos4,
                                    @NotEmpty @Size(max = 20) List<@Valid LineaRequest> lineas) {
    }

    @PostMapping
    public Mono<ResponseEntity<Orden>> crear(@RequestHeader("Idempotency-Key") String idempotencyKey,
                                             @Valid @RequestBody CrearOrdenRequest request,
                                             UriComponentsBuilder uri) {
        if (idempotencyKey.isBlank() || idempotencyKey.length() > 64) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Idempotency-Key debe tener entre 1 y 64 caracteres");
        }
        List<LineaOrden> lineas = request.lineas().stream()
                .map(l -> new LineaOrden(l.sku(), l.cantidad(), l.precioUnitario()))
                .toList();
        return orquestador.crear(new CrearOrden(request.clienteId(), lineas, request.tarjetaUltimos4(), idempotencyKey))
                .map(orden -> ResponseEntity
                        .accepted()
                        .location(uri.path("/api/ordenes/{id}").buildAndExpand(orden.id()).toUri())
                        .body(orden));
    }

    @GetMapping("/{id}")
    public Mono<Orden> buscar(@PathVariable String id) {
        return orquestador.buscar(id);
    }

    @ExceptionHandler(OrdenNoEncontradaException.class)
    public ProblemDetail noEncontrada(OrdenNoEncontradaException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
    }
}
