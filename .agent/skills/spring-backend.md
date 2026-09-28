---
name: spring-backend
description: Spring Boot 3.5, Java 21, Spring AI orchestration, MySQL 8.4 JPA, and REST API conventions for Automated Invoice Reconciliation.
triggers: ["src/main/java/**", "src/test/java/**", "pom.xml", "src/main/resources/**", "*.java"]
---

# Spring Boot & Spring AI Backend Skill

## 1. Environment & Stack
- **Java:** Version 21 (LTS). Leverage records, pattern matching, sealed classes, and modern switch expressions.
- **Framework:** Spring Boot 3.5.x.
- **AI Orchestration:** Spring AI with `ChatClient`, `BeanOutputConverter`, and multimodal prompt templates.
- **Persistence:** Spring Data JPA + Hibernate with MySQL 8.4 LTS dialect.
- **Validation & Serialization:** Jakarta Bean Validation, Jackson JSON with Java Record support.

## 2. Architecture & Design Principles
- **Immutable DTOs & AI Output:**
  - Map LLM extraction output strictly to immutable Java Records (e.g., `ExtractedInvoiceRecord`, `ExtractedLineItemRecord`).
  - Keep JPA `@Entity` domain models separate from AI extraction DTOs and API response models.
- **Service Layer Separation:**
  - `InvoiceExtractionService`: Multimodal LLM prompt execution via Spring AI `ChatClient` to extract structured line items and vendor metadata.
  - `ReconciliationEngineService`: Pure deterministic Java logic (no LLM) verifying extracted data against `purchase_orders` and `purchase_order_items`.
  - `DisputeDraftService`: Template or LLM-assisted draft generator explaining flagged audits.
- **Error Handling & Resilience:**
  - Global `@RestControllerAdvice` returning standard RFC 7807 `ProblemDetail`.
  - Safe handling of corrupted/unparseable PDF/image uploads with clear audit logs.

## 3. Database Schema & JPA Entity Alignment
Follow the exact schema specified in `PPROJECT_SPEC.md`:
- `purchase_orders`: `id`, `po_number` (UNIQUE), `vendor_name`, `currency` (default 'EGP'), `status` (`OPEN`, `PARTIALLY_RECONCILED`, `COMPLETED`), `total_expected_amount`, `created_at`.
- `purchase_order_items`: `id`, `po_id` (FK), `sku_code`, `description`, `expected_quantity`, `agreed_unit_price`, `expected_line_total`.
- `invoices`: `id`, `po_reference`, `invoice_number`, `vendor_name`, `file_path`, `invoiced_total`, `reconciliation_status` (`APPROVED`, `FLAGGED_DISCREPANCY`, `MANUAL_REVIEW`), `dispute_draft`, `created_at`.
- `reconciliation_audits`: `id`, `invoice_id` (FK), `issue_type` (`PRICE_MISMATCH`, `QUANTITY_MISMATCH`, `UNRECOGNIZED_ITEM`, `EXTRA_FEE`), `sku_code`, `item_description`, `expected_value`, `actual_value`, `explanation`.

## 4. REST Endpoints Specification
- `POST /api/invoices/upload`: Multipart file upload (PDF/PNG/JPEG), triggers extraction + deterministic audit against DB, returns `InvoiceReconciliationResponse`.
- `GET /api/invoices`: List all processed invoices with status badges and summary counts.
- `GET /api/invoices/{id}`: Full breakdown with matching PO details and audit records.
- `POST /api/invoices/{id}/approve`: Overrides or marks an invoice as `APPROVED` for payment.
