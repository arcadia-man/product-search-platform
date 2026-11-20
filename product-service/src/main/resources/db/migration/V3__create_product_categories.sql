CREATE TABLE product_categories (
    product_id BIGINT NOT NULL,
    category_id BIGINT NOT NULL,

    PRIMARY KEY (product_id, category_id),

    CONSTRAINT fk_pc_product
        FOREIGN KEY (product_id) REFERENCES products(id),

    CONSTRAINT fk_pc_category
        FOREIGN KEY (category_id) REFERENCES categories(id)
);

INSERT INTO product_categories (product_id, category_id) VALUES
(1, 2),
(2, 2),
(3, 3),
(4, 4);