package pe.estudio.checkout.pagos.web;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.estudio.checkout.pagos.persistencia.Pago;
import pe.estudio.checkout.pagos.persistencia.RegistroPagos;

@RestController
@RequestMapping("/api/pagos")
public class PagosController {

    private final RegistroPagos pagos;

    public PagosController(RegistroPagos pagos) {
        this.pagos = pagos;
    }

    @GetMapping("/{ordenId}")
    public ResponseEntity<Pago> buscar(@PathVariable String ordenId) {
        return pagos.buscarPorOrden(ordenId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
