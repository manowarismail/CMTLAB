-- Run after trading_system.orders exists (see init_orders_table.sql).
USE trading_system;

CREATE TABLE IF NOT EXISTS executions (
    execution_id   VARCHAR(64)  NOT NULL PRIMARY KEY,
    order_id       VARCHAR(64)  NOT NULL,
    exec_id        VARCHAR(64)  NOT NULL,
    cl_ord_id      VARCHAR(64)  NOT NULL,
    symbol         VARCHAR(32)  NOT NULL,
    side           VARCHAR(8)   NOT NULL,
    last_qty       DOUBLE       NOT NULL,
    last_px        DOUBLE       NOT NULL,
    cum_qty        DOUBLE       NULL,
    leaves_qty     DOUBLE       NULL,
    avg_px         DOUBLE       NULL,
    ord_status     VARCHAR(32)  NULL,
    created_at     TIMESTAMP    NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_executions_order
        FOREIGN KEY (order_id) REFERENCES orders (order_id)
);
