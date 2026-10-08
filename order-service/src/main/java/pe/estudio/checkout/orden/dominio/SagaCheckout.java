package pe.estudio.checkout.orden.dominio;

import pe.estudio.checkout.contratos.MensajeSaga;
import pe.estudio.checkout.contratos.MensajeSaga.CobrarOrden;
import pe.estudio.checkout.contratos.MensajeSaga.LiberarStock;
import pe.estudio.checkout.contratos.MensajeSaga.PagoAprobado;
import pe.estudio.checkout.contratos.MensajeSaga.PagoRechazado;
import pe.estudio.checkout.contratos.MensajeSaga.ReservarStock;
import pe.estudio.checkout.contratos.MensajeSaga.RespuestaInventario;
import pe.estudio.checkout.contratos.MensajeSaga.RespuestaPago;
import pe.estudio.checkout.contratos.MensajeSaga.StockLiberado;
import pe.estudio.checkout.contratos.MensajeSaga.StockRechazado;
import pe.estudio.checkout.contratos.MensajeSaga.StockReservado;
import pe.estudio.checkout.contratos.Topicos;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class SagaCheckout {

    public record Saliente(String topico, MensajeSaga mensaje) {
    }

    public record Decision(Orden orden, List<Saliente> salientes, boolean huboCambio) {

        static Decision sinCambios(Orden orden) {
            return new Decision(orden, List.of(), false);
        }

        static Decision cambio(Orden orden, Saliente... salientes) {
            return new Decision(orden, List.of(salientes), true);
        }
    }

    public Decision iniciar(Orden orden) {
        return Decision.cambio(orden, new Saliente(Topicos.INVENTARIO_COMANDOS,
                new ReservarStock(nuevoId(), orden.id(), orden.lineas())));
    }

    public Decision alRecibir(Orden orden, RespuestaInventario respuesta, Instant ahora) {
        EstadoOrden estado = orden.estado();
        return switch (respuesta) {
            case StockReservado r when estado == EstadoOrden.PENDIENTE_STOCK ->
                    Decision.cambio(orden.pasarA(EstadoOrden.PENDIENTE_PAGO, null, ahora),
                            new Saliente(Topicos.PAGOS_COMANDOS, new CobrarOrden(nuevoId(), orden.id(),
                                    orden.clienteId(), orden.total(), orden.tarjetaUltimos4())));

            case StockRechazado r when estado == EstadoOrden.PENDIENTE_STOCK ->

                    Decision.cambio(orden.pasarA(EstadoOrden.CANCELADA, r.motivo(), ahora));

            case StockLiberado r when estado == EstadoOrden.COMPENSANDO ->

                    Decision.cambio(orden.pasarA(EstadoOrden.CANCELADA, orden.motivo(), ahora));

            case StockReservado r -> Decision.sinCambios(orden);
            case StockRechazado r -> Decision.sinCambios(orden);
            case StockLiberado r -> Decision.sinCambios(orden);
        };
    }

    public Decision alRecibir(Orden orden, RespuestaPago respuesta, Instant ahora) {
        EstadoOrden estado = orden.estado();
        return switch (respuesta) {
            case PagoAprobado p when estado == EstadoOrden.PENDIENTE_PAGO ->
                    Decision.cambio(orden.pasarA(EstadoOrden.CONFIRMADA, "Pago " + p.transaccionId(), ahora));

            case PagoRechazado p when estado == EstadoOrden.PENDIENTE_PAGO ->

                    Decision.cambio(orden.pasarA(EstadoOrden.COMPENSANDO, p.motivo(), ahora),
                            new Saliente(Topicos.INVENTARIO_COMANDOS,
                                    new LiberarStock(nuevoId(), orden.id(), "Pago rechazado: " + p.motivo())));

            case PagoAprobado p -> Decision.sinCambios(orden);
            case PagoRechazado p -> Decision.sinCambios(orden);
        };
    }

    private static String nuevoId() {
        return UUID.randomUUID().toString();
    }
}
