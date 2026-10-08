package pe.estudio.checkout.orden.dominio;

public enum EstadoOrden {
    PENDIENTE_STOCK,
    PENDIENTE_PAGO,
    COMPENSANDO,
    CONFIRMADA,
    CANCELADA;

    public boolean esFinal() {
        return this == CONFIRMADA || this == CANCELADA;
    }
}
