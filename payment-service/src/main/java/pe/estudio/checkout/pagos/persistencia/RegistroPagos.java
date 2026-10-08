package pe.estudio.checkout.pagos.persistencia;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import pe.estudio.checkout.pagos.persistencia.Pago.Estado;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

@Repository
public class RegistroPagos {

    private static final Logger log = LoggerFactory.getLogger(RegistroPagos.class);

    private final JdbcClient jdbc;

    public RegistroPagos(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public boolean registrar(String ordenId, Estado estado, BigDecimal monto, String tarjetaUltimos4,
                             String referencia, String motivo) {
        try {
            jdbc.sql("""
                            INSERT INTO pagos (id, orden_id, estado, monto, tarjeta_ultimos4, referencia, motivo, creado_en)
                            VALUES (:id, :ordenId, :estado, :monto, :tarjeta, :referencia, :motivo, :creadoEn)
                            """)
                    .param("id", UUID.randomUUID().toString())
                    .param("ordenId", ordenId)
                    .param("estado", estado.name())
                    .param("monto", monto)
                    .param("tarjeta", tarjetaUltimos4)
                    .param("referencia", referencia)
                    .param("motivo", motivo)
                    .param("creadoEn", OffsetDateTime.now(ZoneOffset.UTC))
                    .update();
            return true;
        } catch (DuplicateKeyException e) {
            log.info("Pago de la orden {} ya estaba registrado (mensaje duplicado), se ignora", ordenId);
            return false;
        }
    }

    public Optional<Pago> buscarPorOrden(String ordenId) {
        return jdbc.sql("""
                        SELECT id, orden_id, estado, monto, tarjeta_ultimos4, referencia, motivo, creado_en
                        FROM pagos WHERE orden_id = :ordenId
                        """)
                .param("ordenId", ordenId)
                .query((rs, n) -> new Pago(
                        rs.getString("id"),
                        rs.getString("orden_id"),
                        Estado.valueOf(rs.getString("estado")),
                        rs.getBigDecimal("monto"),
                        rs.getString("tarjeta_ultimos4"),
                        rs.getString("referencia"),
                        rs.getString("motivo"),
                        rs.getObject("creado_en", OffsetDateTime.class)))
                .optional();
    }
}
