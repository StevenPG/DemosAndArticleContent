-- Seed products
INSERT INTO products (id, name, description, price, sku) VALUES
    ('11111111-0000-0000-0000-000000000001', 'Laptop Pro 15', 'High-performance 15" developer laptop', 1499.00, 'LAPTOP-PRO-15'),
    ('11111111-0000-0000-0000-000000000002', 'Mechanical Keyboard', 'Tenkeyless, Cherry MX Brown switches', 129.00, 'KB-MX-BROWN'),
    ('11111111-0000-0000-0000-000000000003', 'USB-C Hub 7-in-1', 'HDMI, USB-A x3, SD, MicroSD, Power Delivery', 49.99, 'HUB-USBC-7'),
    ('11111111-0000-0000-0000-000000000004', 'Ergonomic Mouse', 'Vertical ergonomic wireless mouse', 59.00, 'MOUSE-ERGO-W');

-- Seed inventory
INSERT INTO inventory_items (id, product_id, quantity_on_hand, quantity_reserved) VALUES
    ('22222222-0000-0000-0000-000000000001', '11111111-0000-0000-0000-000000000001', 50,  0),
    ('22222222-0000-0000-0000-000000000002', '11111111-0000-0000-0000-000000000002', 200, 0),
    ('22222222-0000-0000-0000-000000000003', '11111111-0000-0000-0000-000000000003', 150, 0),
    ('22222222-0000-0000-0000-000000000004', '11111111-0000-0000-0000-000000000004', 75,  0);
