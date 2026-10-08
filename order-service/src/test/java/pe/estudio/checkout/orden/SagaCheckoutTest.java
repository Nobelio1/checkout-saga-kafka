package pe.estudio.checkout.orden;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pe.estudio.checkout.contratos.MensajeSaga.CobrarOrden;
import pe.estudio.checkout.contratos.MensajeSaga.LiberarStock;
import pe.estudio.checkout.contratos.MensajeSaga.LineaOrden;
import pe.estudio.checkout.contratos.MensajeSaga.PagoAprobado;
import pe.estudio.checkout.contratos.MensajeSaga.PagoRechazado;
import pe.estudio.checkout.contratos.MensajeSaga.ReservarStock;
import pe.estudio.checkout.contratos.MensajeSaga.StockLiberado;
import pe.estudio.checkout.contratos.MensajeSaga.StockRechazado;
import pe.estudio.checkout.contratos.MensajeSaga.StockReservado;
import pe.estudio.checkout.contratos.Topicos;
import pe.estudio.checkout.orden.dominio.EstadoOrden;
import pe.estudio.checkout.orden.dominio.Orden;
import pe.estudio.checkout.orden.dominio.SagaCheckout;
import pe.estudio.checkout.orden.dominio.SagaCheckout.Decision;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SagaCheckoutTest {

    private static final Instant AHORA = Instant.parse("2026-10-07T15:00:00Z");
    private final SagaCheckout saga = new SagaCheckout();

    private Orden ordenNueva() {
        return Orden.nueva("cli-1", List.of(new LineaOrden("SKU-1", 2, new BigDecimal("50.00"))),
                "4242", "idem-1", AHORA);
    }

    @Test
    @DisplayName("Camino feliz: Reservar → StockReservado → Cobrar → PagoAprobado → CONFIRMADA")
    void caminoFeliz() {
        Orden orden = ordenNueva();

        Decision inicio = saga.iniciar(orden);
        assertThat(inicio.salientes()).singleElement().satisfies(s -> {
            assertThat(s.topico()).isEqualTo(Topicos.INVENTARIO_COMANDOS);
            assertThat(s.mensaje()).isInstanceOf(ReservarStock.class);
        });

        Decision trasStock = saga.alRecibir(orden, new StockReservado("m1", orden.id()), AHORA);
        assertThat(trasStock.orden().estado()).isEqualTo(EstadoOrden.PENDIENTE_PAGO);
        assertThat(trasStock.salientes().getFirst().mensaje())
                .isInstanceOfSatisfying(CobrarOrden.class, c -> assertThat(c.monto()).isEqualByComparingTo("100.00"));

        Decision trasPago = saga.alRecibir(trasStock.orden(), new PagoAprobado("m2", orden.id(), "TX-9"), AHORA);
        assertThat(trasPago.orden().estado()).isEqualTo(EstadoOrden.CONFIRMADA);
        assertThat(trasPago.salientes()).isEmpty();
    }

    @Test
    @DisplayName("Sin stock: la orden se cancela y no hay nada que compensar")
    void sinStock() {
        Orden orden = ordenNueva();
        Decision d = saga.alRecibir(orden, new StockRechazado("m1", orden.id(), "SKU-1 agotado"), AHORA);

        assertThat(d.orden().estado()).isEqualTo(EstadoOrden.CANCELADA);
        assertThat(d.orden().motivo()).isEqualTo("SKU-1 agotado");
        assertThat(d.salientes()).isEmpty();
    }

    @Test
    @DisplayName("Pago rechazado: COMPENSACIÓN con LiberarStock y luego CANCELADA")
    void compensacion() {
        Orden pendientePago = ordenNueva().pasarA(EstadoOrden.PENDIENTE_PAGO, null, AHORA);

        Decision rechazo = saga.alRecibir(pendientePago, new PagoRechazado("m2", pendientePago.id(), "Fondos insuficientes"), AHORA);
        assertThat(rechazo.orden().estado()).isEqualTo(EstadoOrden.COMPENSANDO);
        assertThat(rechazo.salientes().getFirst().mensaje()).isInstanceOf(LiberarStock.class);

        Decision liberado = saga.alRecibir(rechazo.orden(), new StockLiberado("m3", pendientePago.id()), AHORA);
        assertThat(liberado.orden().estado()).isEqualTo(EstadoOrden.CANCELADA);
        assertThat(liberado.orden().motivo()).isEqualTo("Fondos insuficientes");
    }

    @Test
    @DisplayName("Idempotencia: un StockReservado duplicado no vuelve a enviar CobrarOrden")
    void duplicadoIgnorado() {
        Orden pendientePago = ordenNueva().pasarA(EstadoOrden.PENDIENTE_PAGO, null, AHORA);

        Decision d = saga.alRecibir(pendientePago, new StockReservado("m1-repetido", pendientePago.id()), AHORA);

        assertThat(d.huboCambio()).isFalse();
        assertThat(d.salientes()).isEmpty();
    }
}
