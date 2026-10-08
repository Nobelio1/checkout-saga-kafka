package pe.estudio.checkout.contratos;

import java.math.BigDecimal;
import java.util.List;

public sealed interface MensajeSaga
        permits MensajeSaga.ComandoInventario, MensajeSaga.RespuestaInventario,
        MensajeSaga.ComandoPago, MensajeSaga.RespuestaPago {

    String mensajeId();

    String ordenId();

    record LineaOrden(String sku, int cantidad, BigDecimal precioUnitario) {
    }

    sealed interface ComandoInventario extends MensajeSaga permits ReservarStock, LiberarStock {
    }

    record ReservarStock(String mensajeId, String ordenId, List<LineaOrden> lineas) implements ComandoInventario {
    }

    record LiberarStock(String mensajeId, String ordenId, String motivo) implements ComandoInventario {
    }

    sealed interface RespuestaInventario extends MensajeSaga permits StockReservado, StockRechazado, StockLiberado {
    }

    record StockReservado(String mensajeId, String ordenId) implements RespuestaInventario {
    }

    record StockRechazado(String mensajeId, String ordenId, String motivo) implements RespuestaInventario {
    }

    record StockLiberado(String mensajeId, String ordenId) implements RespuestaInventario {
    }

    sealed interface ComandoPago extends MensajeSaga permits CobrarOrden {
    }

    record CobrarOrden(String mensajeId, String ordenId, String clienteId, BigDecimal monto,
                       String tarjetaUltimos4) implements ComandoPago {
    }

    sealed interface RespuestaPago extends MensajeSaga permits PagoAprobado, PagoRechazado {
    }

    record PagoAprobado(String mensajeId, String ordenId, String transaccionId) implements RespuestaPago {
    }

    record PagoRechazado(String mensajeId, String ordenId, String motivo) implements RespuestaPago {
    }
}
