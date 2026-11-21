
-- Product variants
INSERT INTO product_variants
(product_id, sku, color, size, material, status)
VALUES
(1, 'APL-IP16-BLK-128', 'Black', '128GB', 'Aluminium', 'ACTIVE'),
(1, 'APL-IP16-BLU-256', 'Blue', '256GB', 'Aluminium', 'ACTIVE'),
(2, 'SAM-S25-BLK-256', 'Black', '256GB', 'Aluminium', 'ACTIVE'),
(2, 'SAM-S25-SLV-512', 'Silver', '512GB', 'Aluminium', 'ACTIVE'),
(3, 'SONY-XM5-BLK', 'Black', 'Standard', 'Plastic', 'ACTIVE'),
(4, 'NIK-AM01-BLK-9', 'Black', 'UK 9', 'Mesh', 'ACTIVE');

-- Product prices
INSERT INTO product_prices
(product_id, variant_id, currency, price, discount_price)
VALUES
(1, 1, 'INR', 79999.00, 74999.00),
(1, 2, 'INR', 89999.00, 84999.00),
(2, 3, 'INR', 74999.00, 69999.00),
(2, 4, 'INR', 84999.00, 79999.00),
(3, 5, 'INR', 29999.00, 24999.00),
(4, 6, 'INR', 8999.00, 7999.00);

-- Components
INSERT INTO components (name, type)
VALUES
('A18 Chip', 'PROCESSOR'),
('OLED Display', 'DISPLAY'),
('Lithium Battery', 'BATTERY'),
('Noise Cancellation Driver', 'AUDIO'),
('Air Cushion', 'FOOTWEAR');

-- Product components
INSERT INTO product_components (product_id, component_id, quantity)
VALUES
(1, 1, 1),
(1, 2, 1),
(1, 3, 1),
(2, 2, 1),
(2, 3, 1),
(3, 4, 2),
(4, 5, 1);

-- Product attributes
INSERT INTO product_attributes (product_id, attribute_name, attribute_value)
VALUES
(1, 'processor', 'A18'),
(1, 'display', 'OLED'),
(1, 'storage', '128GB'),
(2, 'processor', 'Snapdragon'),
(2, 'storage', '256GB'),
(3, 'connectivity', 'Bluetooth 5.2'),
(3, 'noise_cancellation', 'Active'),
(4, 'shoe_type', 'Running');

-- Warehouses
INSERT INTO warehouses (name, city)
VALUES
('Chennai Central Warehouse', 'Chennai'),
('Mumbai Distribution Center', 'Mumbai'),
('Bangalore Fulfillment Center', 'Bangalore');

-- Inventory
INSERT INTO inventory (variant_id, warehouse_id, available_quantity)
VALUES
(1, 1, 50),
(1, 2, 20),
(2, 1, 30),
(3, 1, 40),
(4, 2, 15),
(5, 3, 25),
(6, 3, 60);
