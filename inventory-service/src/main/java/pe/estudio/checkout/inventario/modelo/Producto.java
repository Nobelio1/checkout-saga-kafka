package pe.estudio.checkout.inventario.modelo;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "productos")
public class Producto {

    @Id
    private String sku;
    private String nombre;
    private int disponible;
    private int reservado;

    @Version
    private Long version;

    protected Producto() {
    }

    public Producto(String sku, String nombre, int disponible) {
        this.sku = sku;
        this.nombre = nombre;
        this.disponible = disponible;
    }

    public boolean alcanzaPara(int cantidad) {
        return disponible >= cantidad;
    }

    public void reservar(int cantidad) {
        if (!alcanzaPara(cantidad)) {
            throw new IllegalStateException("Stock insuficiente de " + sku);
        }
        disponible -= cantidad;
        reservado += cantidad;
    }

    public void liberar(int cantidad) {
        reservado -= cantidad;
        disponible += cantidad;
    }

    public String getSku() { return sku; }
    public String getNombre() { return nombre; }
    public int getDisponible() { return disponible; }
    public int getReservado() { return reservado; }
}
