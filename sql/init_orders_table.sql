-- Run as MySQL root (or any user that can create DB/objects), then grant trader access.

CREATE DATABASE IF NOT EXISTS trading_system;
USE trading_system;

CREATE TABLE IF NOT EXISTS orders (
    order_id   VARCHAR(64)  NOT NULL PRIMARY KEY,
    cl_ord_id  VARCHAR(64)  NOT NULL,
    symbol     VARCHAR(32)  NOT NULL,
    side       VARCHAR(8)   NOT NULL,
    price      DOUBLE       NOT NULL,
    quantity   DOUBLE       NOT NULL,
    status     VARCHAR(32)  NOT NULL,
    created_at TIMESTAMP    NULL DEFAULT CURRENT_TIMESTAMP
);

-- Example grant (adjust host/password to match JdbcOrderStore.java):
-- CREATE USER IF NOT EXISTS 'trader'@'localhost' IDENTIFIED BY 'TraderPass123';
-- GRANT ALL ON trading_system.* TO 'trader'@'localhost';
-- FLUSH PRIVILEGES;
