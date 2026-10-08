package pe.estudio.checkout.inventario.modelo;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "reservas")
public class Reserva {

    public enum Estado { ACTIVA, LIBERADA }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String ordenId;
    private String sku;
    private int cantidad;

    @Enumerated(EnumType.STRING)
    private Estado estado = Estado.ACTIVA;

    protected Reserva() {
    }

    public Reserva(String ordenId, String sku, int cantidad) {
        this.ordenId = ordenId;
        this.sku = sku;
        this.cantidad = cantidad;
    }

    public void marcarLiberada() {
        this.estado = Estado.LIBERADA;
    }

    public String getOrdenId() { return ordenId; }
    public String getSku() { return sku; }
    public int getCantidad() { return cantidad; }
    public Estado getEstado() { return estado; }
}
