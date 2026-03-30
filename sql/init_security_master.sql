-- Run after trading_system exists (see init_orders_table.sql).
USE trading_system;

CREATE TABLE IF NOT EXISTS security_master (
    symbol          VARCHAR(20)  NOT NULL PRIMARY KEY,
    security_type   VARCHAR(20)  NULL,
    description     VARCHAR(100) NULL,
    underlying      VARCHAR(20)  NULL,
    lot_size        INT          NOT NULL DEFAULT 100
);

-- Reference data for validation: NewOrderSingle 55=GOOG should be accepted when this row exists.
INSERT IGNORE INTO security_master (symbol, security_type, description, underlying, lot_size)
VALUES ('GOOG', 'CS', 'Alphabet Inc Class C', NULL, 100);
