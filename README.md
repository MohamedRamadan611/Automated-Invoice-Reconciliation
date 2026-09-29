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
- [x] **Phase 3: Deterministic Java Reconciliation Engine & REST APIs**
- [x] **Phase 4: Next.js Split-Screen Review Dashboard**
- [ ] **Phase 5: End-to-End Integration, Docker Orchestration & Arabic Invoice Fixtures**

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

### Phase 3: Deterministic Java Reconciliation Engine & REST APIs
Connects multimodal invoice extraction to MySQL Purchase Orders, enforces 100% deterministic mathematical verification in Java, drafts bilingual dispute notices via Google Gemini (`gemini-3.8-flash`), and exposes the complete RESTful endpoint suite.

- **Deterministic Verification Engine (`ReconciliationEngineService`):**
  - **Step A (PO Lookup):** Validates PO reference against MySQL 8.4 (`findWithItemsByPoNumber`). Flags `PO_NOT_FOUND` and routes to `MANUAL_REVIEW` if missing.
  - **Step B (Line Item Verification):** Matches items by SKU and semantic keyword similarity.
    - *Price Mismatch:* `item.unitPrice().compareTo(poItem.agreedUnitPrice()) != 0` $\rightarrow$ flags `PRICE_MISMATCH`.
    - *Quantity Mismatch:* `item.quantity().compareTo(poItem.expectedQuantity()) != 0` $\rightarrow$ flags `QUANTITY_MISMATCH`.
    - *Unrecognized Items:* Invoiced items missing from PO $\rightarrow$ flags `UNRECOGNIZED_ITEM`.
  - **Step C (Extra Fees Check):** Surcharges (`extraFees > 0`) $\rightarrow$ flags `EXTRA_FEE`.
  - **Step D (Status Resolution):**
    - Zero discrepancies $\rightarrow$ `APPROVED`.
    - Discrepancies detected $\rightarrow$ `FLAGGED_DISCREPANCY` and triggers dispute draft generation.
- **Bilingual Dispute Generator (`DisputeDraftingService`):**
  - Uses Spring AI `ChatClient.Builder` with Google Gemini (`gemini-3.8-flash`) via an OpenAI-compatible endpoint.
  - Generates formal, polite commercial letters structured into:
    - *Section 1: Modern Standard Arabic* (`القسم الأول: إشعار الاعتراض المالي الرسمي`)
    - *Section 2: Business English* (`Section 2: Formal Financial Dispute Notice`)
  - Includes offline fallback generator for offline resiliency.
- **REST Endpoints (`InvoiceController` under `/api/invoices`):**
  - `POST /api/invoices/upload`: Multipart upload $\rightarrow$ storage $\rightarrow$ extraction $\rightarrow$ deterministic audit $\rightarrow$ returns `ReconciliationSummaryResponse`.
  - `GET /api/invoices`: Returns list of all processed invoices (`InvoiceListItemResponse`).
  - `GET /api/invoices/{id}`: Returns complete invoice details with audits and dispute draft.
  - `GET /api/invoices/{id}/file`: Streams binary PDF/image content with inline `Content-Disposition` for browser previews.
  - `POST /api/invoices/{id}/approve`: Overrides status to `APPROVED` for payment authorization.
- **Automated Tests:**
  - `ReconciliationEngineServiceTest`: Unit tests covering clean matches, price/qty/extra fee discrepancies, unrecognized items, and missing POs.
  - `InvoiceControllerTest`: MockMvc tests covering upload, list, detail, file stream, and approve endpoints.

---

### Phase 4: Next.js 15 Review Interface (`/frontend`)
Implements an auditor-first desktop workspace with high information density, live document previews, and bilingual dispute resolution.

- **Queue Dashboard (`/`):**
  - Financial metric cards (Total Processed, Discrepancies Flagged, Approved, Audited EGP Volume).
  - Animated `DropzoneUploader` supporting PDF, PNG, and JPG documents.
  - Search and status filter controls (`ALL`, `FLAGGED_DISCREPANCY`, `APPROVED`, `MANUAL_REVIEW`).
  - Invoice queue table with status badges and quick workspace links.
- **50/50 Split-Screen Review Workspace (`/invoice/[id]`):**
  - **Left Viewport (Document Viewer):** Native document frame (`/api/invoices/{id}/file`) with zoom in, zoom out, 100% reset, new tab, and file download actions.
  - **Right Viewport (Reconciliation Table):**
    - Metadata ribbon with invoice number, vendor name, PO reference, status pill, and billed total.
    - Financial variance card displaying net difference against agreed PO totals.
    - Itemized audit cards with color-coded badges (`PRICE_MISMATCH`, `EXTRA_FEE`, `QUANTITY_MISMATCH`, `UNRECOGNIZED_ITEM`) and human-readable explanations.
    - Soft rose variance highlighting (`bg-rose-50 text-rose-800 border-rose-200`) for discrepancy lines.
- **Gemini AI Bilingual Dispute Drawer (`DisputeActionDrawer`):**
  - Displays formal dispute notice in Arabic (`العربية`) and English with tab toggles.
  - "Copy Draft" button with instant clipboard visual confirmation.
  - "Approve & Release Payment" button issuing `POST /api/invoices/{id}/approve` with live status update.
- **Proxy Configuration:** Configured `next.config.ts` rewrite proxying `/api/:path*` to `http://localhost:8080/api/:path*`.

---

## 🚀 Running the Project Locally

### 1. Database
```bash
docker compose up -d
```

### 2. Backend (Spring Boot 3.5 on Port 8080)
Ensure `.env` contains your Gemini API key:
```bash
./mvnw spring-boot:run
```

### 3. Frontend (Next.js 15 on Port 3000)
```bash
cd frontend
npm run dev
```
Open [http://localhost:3000](http://localhost:3000) to access the auditor dashboard.

---

## 🧪 Running Tests

```bash
# Run unit tests for Phase 3 reconciliation engine
./mvnw test -Dtest=ReconciliationEngineServiceTest

# Run REST API controller tests
./mvnw test -Dtest=InvoiceControllerTest

# Run all backend test suites
./mvnw test

# Validate frontend production build
cd frontend && npm run build
```

