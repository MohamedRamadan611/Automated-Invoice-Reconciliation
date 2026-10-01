-- Seed Purchase Orders across diverse industries (idempotent)
INSERT INTO purchase_orders (id, po_number, vendor_name, currency, status, total_expected_amount, created_at)
VALUES 
(1, 'PO-2026-001', 'Al-Wadi Farms', 'EGP', 'OPEN', 2750.00, NOW())
ON DUPLICATE KEY UPDATE 
    vendor_name = VALUES(vendor_name),
    total_expected_amount = VALUES(total_expected_amount);

INSERT INTO purchase_order_items (id, po_id, sku_code, description, expected_quantity, agreed_unit_price, expected_line_total)
VALUES 
(1, 1, 'SKU-TOMATO-RED', 'Egyptian Fresh Tomatoes (kg)', 100.00, 20.00, 2000.00),
(2, 1, 'SKU-ONION-YELLOW', 'Yellow Spring Onions (kg)', 50.00, 15.00, 750.00)
ON DUPLICATE KEY UPDATE 
    expected_quantity = VALUES(expected_quantity),
    agreed_unit_price = VALUES(agreed_unit_price),
    expected_line_total = VALUES(expected_line_total);

INSERT INTO purchase_orders (id, po_number, vendor_name, currency, status, total_expected_amount, created_at)
VALUES 
(2, 'PO-2026-002', 'Delta Dairy Co', 'EGP', 'OPEN', 9800.00, NOW())
ON DUPLICATE KEY UPDATE 
    vendor_name = VALUES(vendor_name),
    total_expected_amount = VALUES(total_expected_amount);

INSERT INTO purchase_order_items (id, po_id, sku_code, description, expected_quantity, agreed_unit_price, expected_line_total)
VALUES 
(3, 2, 'SKU-CHEESE-WHITE', 'White Feta Cheese (boxes)', 40.00, 120.00, 4800.00),
(4, 2, 'SKU-BUTTER-TUB', 'Pure Farm Butter (tubs)', 20.00, 250.00, 5000.00)
ON DUPLICATE KEY UPDATE 
    expected_quantity = VALUES(expected_quantity),
    agreed_unit_price = VALUES(agreed_unit_price),
    expected_line_total = VALUES(expected_line_total);

-- Electronics & Office Equipment Industry
INSERT INTO purchase_orders (id, po_number, vendor_name, currency, status, total_expected_amount, created_at)
VALUES 
(3, 'PO-2026-003', 'Apex Technology Solutions', 'EGP', 'OPEN', 85000.00, NOW())
ON DUPLICATE KEY UPDATE 
    vendor_name = VALUES(vendor_name),
    total_expected_amount = VALUES(total_expected_amount);

INSERT INTO purchase_order_items (id, po_id, sku_code, description, expected_quantity, agreed_unit_price, expected_line_total)
VALUES 
(5, 3, 'SKU-LAPTOP-15', 'Commercial Business Laptop 15-inch 16GB', 3.00, 22000.00, 66000.00),
(6, 3, 'SKU-MONITOR-27', 'Ultra-HD 27-inch IPS Monitor', 2.00, 9500.00, 19000.00)
ON DUPLICATE KEY UPDATE 
    expected_quantity = VALUES(expected_quantity),
    agreed_unit_price = VALUES(agreed_unit_price),
    expected_line_total = VALUES(expected_line_total);

-- Industrial Hardware & Construction Industry
INSERT INTO purchase_orders (id, po_number, vendor_name, currency, status, total_expected_amount, created_at)
VALUES 
(4, 'PO-2026-004', 'Nile Metal & Industrial Supply', 'EGP', 'OPEN', 45000.00, NOW())
ON DUPLICATE KEY UPDATE 
    vendor_name = VALUES(vendor_name),
    total_expected_amount = VALUES(total_expected_amount);

INSERT INTO purchase_order_items (id, po_id, sku_code, description, expected_quantity, agreed_unit_price, expected_line_total)
VALUES 
(7, 4, 'SKU-STEEL-BEAM-12', 'Heavy Duty Steel Beams 12mm Grade B', 50.00, 600.00, 30000.00),
(8, 4, 'SKU-FASTENER-HEX', 'Galvanized Industrial Hex Fasteners (pack 100)', 30.00, 500.00, 15000.00)
ON DUPLICATE KEY UPDATE 
    expected_quantity = VALUES(expected_quantity),
    agreed_unit_price = VALUES(agreed_unit_price),
    expected_line_total = VALUES(expected_line_total);
