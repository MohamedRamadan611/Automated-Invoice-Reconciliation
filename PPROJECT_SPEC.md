cat << 'EOF' > PROJECT_SPEC.md
# PROJECT_SPEC: Automated Invoice Reconciliation Agent

## 1. Project Overview & Objective
An AI-augmented financial audit system that ingests supplier invoices (PDF/images), extracts unstructured line items, maps vendor product names to internal SKUs, verifies them against internal Purchase Orders in MySQL, and surfaces discrepancies (price hikes, quantity mismatches, unexpected fees) on a Next.js split-screen review dashboard.

---

## 2. Technology Stack
- **Runtime:** Java 21 (LTS)
- **Backend Framework:** Spring Boot 3.5.x
- **AI Orchestration:** Spring AI 1.0.x (OpenAI / Anthropic Starter, ChatClient, BeanOutputConverter)
- **Database:** MySQL 8.4 LTS
- **ORM / Migration:** Spring Data JPA + Hibernate
- **Frontend:** Next.js 15 (App Router, Tailwind CSS, Lucide React, TypeScript)
- **Containerization:** Docker Compose (MySQL + Spring Boot + Next.js)

---

## 3. Database Schema (MySQL 8.4)

### Tables & Relationships

#### 1. `purchase_orders`
- `id`: BIGINT AUTO_INCREMENT PRIMARY KEY
- `po_number`: VARCHAR(50) NOT NULL UNIQUE (e.g., 'PO-2026-001')
- `vendor_name`: VARCHAR(150) NOT NULL
- `currency`: VARCHAR(10) DEFAULT 'EGP'
- `status`: ENUM('OPEN', 'PARTIALLY_RECONCILED', 'COMPLETED') DEFAULT 'OPEN'
- `total_expected_amount`: DECIMAL(12, 2) NOT NULL
- `created_at`: TIMESTAMP DEFAULT CURRENT_TIMESTAMP

#### 2. `purchase_order_items`
- `id`: BIGINT AUTO_INCREMENT PRIMARY KEY
- `po_id`: BIGINT NOT NULL (FK -> purchase_orders.id)
- `sku_code`: VARCHAR(50) NOT NULL (e.g., 'SKU-TOMATO-RED')
- `description`: VARCHAR(255) NOT NULL (e.g., 'Egyptian Fresh Tomatoes')
- `expected_quantity`: DECIMAL(12, 2) NOT NULL
- `agreed_unit_price`: DECIMAL(12, 2) NOT NULL
- `expected_line_total`: DECIMAL(12, 2) NOT NULL

#### 3. `invoices`
- `id`: BIGINT AUTO_INCREMENT PRIMARY KEY
- `po_reference`: VARCHAR(50) NULL
- `invoice_number`: VARCHAR(100) NOT NULL
- `vendor_name`: VARCHAR(150) NOT NULL
- `file_path`: VARCHAR(500) NOT NULL
- `invoiced_total`: DECIMAL(12, 2) NOT NULL
- `reconciliation_status`: ENUM('APPROVED', 'FLAGGED_DISCREPANCY', 'MANUAL_REVIEW') DEFAULT 'MANUAL_REVIEW'
- `dispute_draft`: TEXT NULL
- `raw_json_payload`: LONGTEXT NULL
- `created_at`: TIMESTAMP DEFAULT CURRENT_TIMESTAMP

#### 4. `reconciliation_audits`
- `id`: BIGINT AUTO_INCREMENT PRIMARY KEY
- `invoice_id`: BIGINT NOT NULL (FK -> invoices.id)
- `issue_type`: ENUM('PRICE_MISMATCH', 'QUANTITY_MISMATCH', 'UNRECOGNIZED_ITEM', 'EXTRA_FEE', 'PO_NOT_FOUND') NOT NULL
- `sku_code`: VARCHAR(50) NULL
- `item_description`: VARCHAR(255) NOT NULL
- `expected_value`: DECIMAL(12, 2) NULL
- `actual_value`: DECIMAL(12, 2) NOT NULL
- `explanation`: VARCHAR(500) NOT NULL

---

## 4. Core Architecture Rules
1. **Separation of Extraction vs. Math:**
    - The LLM's sole responsibility is **multimodal data extraction** and **fuzzy semantic matching** (mapping messy Arabic/English vendor line items to the closest `sku_code`).
    - The LLM must **never** do math. All reconciliation, variance calculations, and tolerance checks (> 0.00 EGP) must be executed in pure Java business logic using `BigDecimal`.
2. **Deterministic Reconciliation Rules:**
    - Price Variance: If `invoicedUnitPrice.compareTo(agreedUnitPrice) != 0` -> Flag `PRICE_MISMATCH`.
    - Quantity Variance: If `invoicedQty.compareTo(expectedQty) != 0` -> Flag `QUANTITY_MISMATCH`.
    - Extra Fees: Any freight/delivery charge not in original PO -> Flag `EXTRA_FEE`.
3. **Structured Output:** Spring AI extraction must map strictly to immutable Java Records.

---

## 5. API Endpoints

- `POST /api/invoices/upload`
    - Consumes: `multipart/form-data` (file: PDF/PNG/JPG)
    - Processes: Runs Spring AI extraction + deterministic verification against DB.
    - Returns: `InvoiceReconciliationResponse` (Full invoice state + list of flagged issues).
- `GET /api/invoices`
    - Returns: List of all processed invoices with status badges.
- `GET /api/invoices/{id}`
    - Returns: Detailed breakdown of an invoice, matching PO details, and audit records.
- `GET /api/invoices/{id}/file`
    - Returns: Raw binary file for frontend PDF/image preview.
- `POST /api/invoices/{id}/approve`
    - Marks an invoice as `APPROVED` for payment.

---

## 6. Implementation Phase Prompts

### Phase 1: Docker & MySQL Persistence