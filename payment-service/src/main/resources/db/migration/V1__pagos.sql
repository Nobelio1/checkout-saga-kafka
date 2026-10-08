CREATE TABLE pagos (
    id                VARCHAR(36)    PRIMARY KEY,
    orden_id          VARCHAR(64)    NOT NULL,
    estado            VARCHAR(20)    NOT NULL,
    monto             NUMERIC(12, 2) NOT NULL,
    tarjeta_ultimos4  VARCHAR(4)     NOT NULL,
    referencia        VARCHAR(100),
    motivo            VARCHAR(300),
    creado_en         TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_pagos_orden UNIQUE (orden_id)
);
