package pe.estudio.checkout.inventario.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.estudio.checkout.inventario.modelo.Producto;
import pe.estudio.checkout.inventario.modelo.ProductoRepository;

import java.util.List;

@RestController
public class StockController {

    private final ProductoRepository productos;

    public StockController(ProductoRepository productos) {
        this.productos = productos;
    }

    @GetMapping("/api/stock")
    public List<Producto> stock() {
        return productos.findAll();
    }
}
