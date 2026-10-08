package pe.estudio.checkout.inventario.modelo;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReservaRepository extends JpaRepository<Reserva, Long> {

    List<Reserva> findByOrdenIdAndEstado(String ordenId, Reserva.Estado estado);
}
