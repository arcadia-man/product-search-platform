CREATE TABLE product_variants (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    product_id BIGINT NOT NULL,
    sku VARCHAR(100) NOT NULL UNIQUE,
    color VARCHAR(100),
    size VARCHAR(100),
    material VARCHAR(100),
    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT fk_variant_product
        FOREIGN KEY (product_id) REFERENCES products(id)
);

CREATE TABLE product_prices (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    product_id BIGINT NOT NULL,
    variant_id BIGINT,
    currency VARCHAR(10) NOT NULL DEFAULT 'INR',
    price DECIMAL(12,2) NOT NULL,
    discount_price DECIMAL(12,2),
    valid_from TIMESTAMP,
    valid_to TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT fk_price_product
        FOREIGN KEY (product_id) REFERENCES products(id),

    CONSTRAINT fk_price_variant
        FOREIGN KEY (variant_id) REFERENCES product_variants(id)
);

CREATE TABLE components (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(150) NOT NULL,
    type VARCHAR(100) NOT NULL
);

CREATE TABLE product_components (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    product_id BIGINT NOT NULL,
    component_id BIGINT NOT NULL,
    quantity DECIMAL(12,3) NOT NULL DEFAULT 1,

    CONSTRAINT fk_product_component_product
        FOREIGN KEY (product_id) REFERENCES products(id),

    CONSTRAINT fk_product_component_component
        FOREIGN KEY (component_id) REFERENCES components(id),

    CONSTRAINT uq_product_component
        UNIQUE (product_id, component_id)
);

CREATE TABLE product_attributes (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    product_id BIGINT NOT NULL,
    attribute_name VARCHAR(150) NOT NULL,
    attribute_value VARCHAR(500) NOT NULL,

    CONSTRAINT fk_attribute_product
        FOREIGN KEY (product_id) REFERENCES products(id)
);

CREATE TABLE warehouses (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(150) NOT NULL,
    city VARCHAR(100) NOT NULL
);

CREATE TABLE inventory (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    variant_id BIGINT NOT NULL,
    warehouse_id BIGINT NOT NULL,
    available_quantity INT NOT NULL DEFAULT 0,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT fk_inventory_variant
        FOREIGN KEY (variant_id) REFERENCES product_variants(id),

    CONSTRAINT fk_inventory_warehouse
        FOREIGN KEY (warehouse_id) REFERENCES warehouses(id),

    CONSTRAINT uq_inventory_variant_warehouse
        UNIQUE (variant_id, warehouse_id)
);