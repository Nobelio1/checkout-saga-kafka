package pe.estudio.checkout.pagos;

import org.junit.jupiter.api.Test;
import pe.estudio.checkout.pagos.pasarela.PasarelaPagos;
import pe.estudio.checkout.pagos.pasarela.PasarelaSimulada;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PasarelaSimuladaTest {

    private final PasarelaSimulada pasarela = new PasarelaSimulada();

    @Test
    void apruebaMontosNormales() {
        assertThat(pasarela.cobrar("o1", new BigDecimal("100"), "4242")).isInstanceOf(PasarelaPagos.Aprobado.class);
    }

    @Test
    void rechazaMontosAltos() {
        assertThat(pasarela.cobrar("o2", new BigDecimal("9000"), "4242")).isInstanceOf(PasarelaPagos.Rechazado.class);
    }

    @Test
    void tarjeta0000SimulaPasarelaCaida() {
        assertThatThrownBy(() -> pasarela.cobrar("o3", BigDecimal.TEN, "0000"))
                .isInstanceOf(PasarelaPagos.PasarelaNoDisponibleException.class);
    }

    @Test
    void idempotente_mismaClaveMismoResultado() {
        var primero = pasarela.cobrar("o4", new BigDecimal("50"), "4242");
        var segundo = pasarela.cobrar("o4", new BigDecimal("50"), "4242");
        assertThat(segundo).isSameAs(primero);
    }
}
