-- V1: Schéma initial - restaurants, qr_codes, qr_scans
-- UUIDs sont générés côté application (Java), pas de dépendance à pgcrypto/uuid-ossp
-- afin de rester portable (Postgres en prod, H2 en tests).

CREATE TABLE restaurants (
    id          UUID PRIMARY KEY,
    name        VARCHAR(255) NOT NULL,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at  TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE qr_codes (
    id                UUID PRIMARY KEY,
    restaurant_id     UUID NOT NULL,
    name              VARCHAR(100) NOT NULL,
    destination_url   TEXT NOT NULL,
    code              VARCHAR(32) NOT NULL,
    is_active         BOOLEAN NOT NULL DEFAULT TRUE,
    created_at        TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at        TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_qr_codes_restaurant
        FOREIGN KEY (restaurant_id) REFERENCES restaurants (id) ON DELETE CASCADE,
    CONSTRAINT uq_qr_codes_code UNIQUE (code)
);

CREATE INDEX idx_qr_codes_restaurant_id ON qr_codes (restaurant_id);
CREATE INDEX idx_qr_codes_code ON qr_codes (code);

CREATE TABLE qr_scans (
    id            UUID PRIMARY KEY,
    qr_code_id    UUID NOT NULL,
    scanned_at    TIMESTAMP WITH TIME ZONE NOT NULL,
    user_agent    TEXT,
    device_type   VARCHAR(20),
    CONSTRAINT fk_qr_scans_qr_code
        FOREIGN KEY (qr_code_id) REFERENCES qr_codes (id) ON DELETE CASCADE
);

CREATE INDEX idx_qr_scans_qr_code_id ON qr_scans (qr_code_id);
CREATE INDEX idx_qr_scans_scanned_at ON qr_scans (scanned_at);
