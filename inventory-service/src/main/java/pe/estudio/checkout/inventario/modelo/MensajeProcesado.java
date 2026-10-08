package pe.estudio.checkout.inventario.modelo;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "mensajes_procesados")
public class MensajeProcesado {

    @Id
    private String mensajeId;
    private Instant procesadoEn;

    protected MensajeProcesado() {
    }

    public MensajeProcesado(String mensajeId) {
        this.mensajeId = mensajeId;
        this.procesadoEn = Instant.now();
    }

    public String getMensajeId() { return mensajeId; }
}
