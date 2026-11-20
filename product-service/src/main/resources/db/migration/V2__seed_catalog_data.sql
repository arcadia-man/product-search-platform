INSERT INTO brands (name, slug) VALUES
('Apple', 'apple'),
('Samsung', 'samsung'),
('Sony', 'sony'),
('Nike', 'nike');

INSERT INTO categories (name, slug) VALUES
('Electronics', 'electronics'),
('Mobiles', 'mobiles'),
('Audio', 'audio'),
('Fashion', 'fashion');

UPDATE categories SET parent_id = 1 WHERE slug IN ('mobiles', 'audio');

INSERT INTO products (name, slug, description, brand_id, sku, price, status) VALUES
('iPhone 16', 'iphone-16', 'Apple smartphone with A18 chip', 1, 'APL-IP16', 79999.00, 'ACTIVE'),
('Samsung Galaxy S25', 'samsung-galaxy-s25', 'Samsung flagship smartphone', 2, 'SAM-S25', 74999.00, 'ACTIVE'),
('Sony WH-1000XM5', 'sony-wh1000xm5', 'Wireless noise cancelling headphones', 3, 'SONY-XM5', 29999.00, 'ACTIVE'),
('Nike Air Max', 'nike-air-max', 'Comfortable running shoes', 4, 'NIK-AM01', 8999.00, 'ACTIVE');