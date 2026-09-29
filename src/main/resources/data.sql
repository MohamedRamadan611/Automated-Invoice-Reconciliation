-- Seed Purchase Orders (idempotent)
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
