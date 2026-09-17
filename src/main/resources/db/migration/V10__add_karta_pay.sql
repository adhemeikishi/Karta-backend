-- V10: Karta Pay MVP — commande et paiement sur place, sans fournisseur réel.
--
-- kartaPayEnabled est un interrupteur commercial (comme l'offre) : ADMIN uniquement,
-- voir RestaurantAdminController et RestaurateurScopeFilter. order_sequence porte le
-- compteur de numérotation des commandes, propre à chaque restaurant, jamais remis à
-- zéro (voir Restaurant.nextOrderNumber()).
-- Syntaxe volontairement portable (PostgreSQL en prod, H2 en tests).

ALTER TABLE restaurants ADD COLUMN karta_pay_enabled BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE restaurants ADD COLUMN order_sequence INT NOT NULL DEFAULT 0;

-- 1. Groupes d'options d'un produit (ex : "Cuisson", "Suppléments").
CREATE TABLE modifier_groups (
    id             UUID PRIMARY KEY,
    item_id        UUID NOT NULL REFERENCES menu_items (id) ON DELETE CASCADE,
    name           VARCHAR(120) NOT NULL,
    selection_type VARCHAR(16) NOT NULL CHECK (selection_type IN ('SINGLE', 'MULTIPLE')),
    min_select     INT NOT NULL DEFAULT 0,
    max_select     INT,
    sort_order     INT NOT NULL DEFAULT 0,
    created_at     TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at     TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX idx_modifier_groups_item_id ON modifier_groups (item_id);

-- 2. Options d'un groupe (ex : "Bien cuit", "+ Bacon").
CREATE TABLE modifier_options (
    id                UUID PRIMARY KEY,
    group_id          UUID NOT NULL REFERENCES modifier_groups (id) ON DELETE CASCADE,
    name              VARCHAR(120) NOT NULL,
    price_delta_cents INT NOT NULL DEFAULT 0,
    available         BOOLEAN NOT NULL DEFAULT TRUE,
    sort_order        INT NOT NULL DEFAULT 0,
    created_at        TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at        TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX idx_modifier_options_group_id ON modifier_options (group_id);

-- 3. Commandes. Prix en CENTIMES ENTIERS, comme menu_items.price_cents.
CREATE TABLE orders (
    id                UUID PRIMARY KEY,
    restaurant_id     UUID NOT NULL REFERENCES restaurants (id) ON DELETE CASCADE,
    order_number      VARCHAR(16) NOT NULL,
    status            VARCHAR(20) NOT NULL,
    fulfillment_type  VARCHAR(16) NOT NULL CHECK (fulfillment_type IN ('DINE_IN', 'TAKEAWAY')),
    table_number      VARCHAR(20),
    customer_name     VARCHAR(120) NOT NULL,
    subtotal_cents    INT NOT NULL,
    total_cents       INT NOT NULL,
    currency          VARCHAR(3) NOT NULL,
    created_at        TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at        TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_orders_restaurant_number UNIQUE (restaurant_id, order_number)
);
CREATE INDEX idx_orders_restaurant_status ON orders (restaurant_id, status);

-- 4. Lignes de commande. Snapshot complet : le prix historique ne bouge jamais, même
--    si le produit source change de prix ou disparaît ensuite (item_id -> SET NULL).
CREATE TABLE order_lines (
    id                          UUID PRIMARY KEY,
    order_id                    UUID NOT NULL REFERENCES orders (id) ON DELETE CASCADE,
    item_id                     UUID REFERENCES menu_items (id) ON DELETE SET NULL,
    item_name_snapshot          VARCHAR(160) NOT NULL,
    unit_price_cents_snapshot   INT NOT NULL,
    quantity                    INT NOT NULL,
    line_total_cents            INT NOT NULL,
    modifiers_snapshot          TEXT,
    sort_order                  INT NOT NULL DEFAULT 0
);
CREATE INDEX idx_order_lines_order_id ON order_lines (order_id);

-- 5. Paiement. provider = NONE tant que Stripe n'existe pas : jamais marqué PAID
--    automatiquement (voir NoPaymentProvider).
CREATE TABLE payments (
    id                   UUID PRIMARY KEY,
    order_id             UUID NOT NULL UNIQUE REFERENCES orders (id) ON DELETE CASCADE,
    status               VARCHAR(16) NOT NULL,
    amount_cents         INT NOT NULL,
    currency             VARCHAR(3) NOT NULL,
    provider             VARCHAR(16) NOT NULL DEFAULT 'NONE',
    provider_payment_id  VARCHAR(255),
    paid_at              TIMESTAMP WITH TIME ZONE,
    created_at           TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at           TIMESTAMP WITH TIME ZONE NOT NULL
);
