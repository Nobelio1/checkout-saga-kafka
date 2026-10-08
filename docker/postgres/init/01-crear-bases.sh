#!/bin/bash
set -euo pipefail

crear_base() {
  local base="$1" usuario="$2" clave="$3"
  psql -v ON_ERROR_STOP=1 -v clave="$clave" --username "$POSTGRES_USER" --dbname postgres <<-SQL
    CREATE ROLE ${usuario} LOGIN PASSWORD :'clave';
    CREATE DATABASE ${base} OWNER ${usuario};
    REVOKE ALL ON DATABASE ${base} FROM PUBLIC;
    GRANT ALL ON DATABASE ${base} TO ${usuario};
SQL
}

crear_base ordenes    orden_svc "${ORDEN_DB_PASSWORD}"
crear_base inventario inv_svc   "${INVENTARIO_DB_PASSWORD}"
crear_base pagos      pago_svc  "${PAGOS_DB_PASSWORD}"
