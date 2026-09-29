# Automated Invoice Reconciliation Agent

An AI-augmented financial audit system that ingests supplier invoices (PDF/images), extracts unstructured line items, maps vendor product names to internal SKUs, verifies them against internal Purchase Orders in MySQL, and surfaces discrepancies (price hikes, quantity mismatches, unexpected fees) on a Next.js split-screen review dashboard.

---

## 🛠 Technology Stack

- **Runtime:** Java 21 (LTS)
- **Backend:** Spring Boot 3.5.x, Spring Data JPA / Hibernate
- **AI Orchestration:** Spring AI (OpenAI `gpt-4o`, `ChatClient`, `BeanOutputConverter`)
- **Database:** MySQL 8.4 LTS
- **Frontend:** Next.js 15 (App Router, Tailwind CSS, Lucide React, TypeScript)
- **Containerization:** Docker Compose

---

## 🗺️ Project Implementation Roadmap

- [x] **Phase 1: Docker & MySQL Persistence**
- [x] **Phase 2: Multimodal Extraction Service & Structured DTOs**
- [ ] **Phase 3: Deterministic Java Reconciliation Engine & Tolerance Verification**
- [ ] **Phase 4: REST Endpoints & File Preview Streaming**
- [ ] **Phase 5: Next.js Split-Screen Review Dashboard**
- [ ] **Phase 6: End-to-End Integration, Docker Orchestration & Arabic Invoice Fixtures**

---

## 📦 Phase Details

### Phase 1: Docker & MySQL Persistence
Establishes the containerized database layer and core domain entities for purchase order management and audit logging.

- **Containerized Database:** Configured MySQL 8.4 LTS via `docker-compose.yml` exposed on port `3307`.
- **Domain Entities & Relationships:**
  - `PurchaseOrder`: Internal PO records with po number, vendor name, status, currency (`EGP`), and expected totals.
  - `PurchaseOrderItem`: Itemized lines per PO linking SKU codes, descriptions, expected quantities, and agreed unit prices.
  - `Invoice`: Supplier invoice records holding references, file paths, reconciliation statuses, and dispute drafts.
  - `ReconciliationAudit`: Discrepancy audit findings linked to invoices.
- **Domain Enums:**
  - `PoStatus`: `OPEN`, `PARTIALLY_RECONCILED`, `COMPLETED`
  - `ReconciliationStatus`: `APPROVED`, `FLAGGED_DISCREPANCY`, `MANUAL_REVIEW`
  - `IssueType`: `PRICE_MISMATCH`, `QUANTITY_MISMATCH`, `UNRECOGNIZED_ITEM`, `EXTRA_FEE`, `PO_NOT_FOUND`
- **Repositories & Seed Data:**
  - Spring Data JPA repositories with custom lookup queries (`findByPoNumber`, `findByInvoiceId`, etc.).
  - `src/main/resources/data.sql`: Seed data for standard agricultural commodities (Tomatoes, Onions, Potatoes).

---

### Phase 2: Multimodal Extraction Service & Structured DTOs
Integrates Spring AI multimodal capabilities to ingest raw invoice documents (PDF, PNG, JPEG), extract structured data via LLM without performing arithmetic, and store files locally.

- **Immutable Structured DTOs (`com.agent.reconciliation.domain.dto`):**
  - `ExtractedLineItem`: Captures extracted vendor descriptions, `BigDecimal` quantity, unit price, line total, and suggested canonical SKU.
  - `ExtractedInvoice`: Header metadata, line items list, extra fees, and grand total.
  - `ReconciliationSummaryResponse`: Summary payload containing invoice details, reconciliation status, audit items, and dispute draft.
  - `AuditDetailResponse`: DTO representing flagged discrepancy line items.
- **Local File Storage Service (`FileStorageService`):**
  - `storeFile(MultipartFile file)`: Stores uploads in `uploads/` directory with collision-free UUID prefixes and path traversal protection.
  - `loadFileAsResource(String filePath)`: Retrieves saved invoice documents as Spring `Resource` objects for frontend PDF/image preview.
- **Multimodal LLM Extraction (`InvoiceExtractionService`):**
  - Powered by Spring AI `ChatClient.Builder` with `gpt-4o` (temperature `0.0`) and `BeanOutputConverter<ExtractedInvoice>`.
  - Converts multipart uploads into Spring AI `Media` resources (`application/pdf`, `image/png`, `image/jpeg`).
  - **System Prompt Invariants:**
    1. *Zero Math in LLM:* Strictly extracts printed numerical values as-is without recalculating totals or balances (all math is reserved for Java business logic in Phase 3).
    2. *Bilingual Egyptian Supply Chain Support:* Accurately extracts Arabic produce items (`طماطم`, `بصل`, `بطاطس`) and operational charges (`مشال`, `توصيل`, `ضريبة`, `أمر توريد`).
    3. *Numeral Normalization:* Automatically converts Arabic-Indic digits (`٠-٩`) to standard decimal digits (`0-9`).
    4. *Canonical SKU Mapping:* Maps recognized commodities to internal canonical SKU codes (e.g., `SKU-TOMATO-RED`, `SKU-ONION-YELLOW`).
    5. *Extra Surcharges:* Isolates unitemized freight and porterage charges into `extraFees`.
- **Testing & Verification:**
  - `InvoiceExtractionServiceTest`: Unit tests mocking `ChatModel` verifying Arabic receipt JSON extraction with zero null fields and markdown-fenced response handling.
  - `FileStorageServiceTest`: Unit tests verifying file persistence, resource resolution, and security checks.

---

## 🧪 Running Tests

```bash
# Run unit tests for Phase 2 extraction service
./mvnw test -Dtest=InvoiceExtractionServiceTest

# Run file storage unit tests
./mvnw test -Dtest=FileStorageServiceTest

# Run all test suites
./mvnw test
```
