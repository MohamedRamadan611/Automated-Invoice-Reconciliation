# Automated Invoice Reconciliation Agent

[![Java 21](https://img.shields.io/badge/Java-21%20LTS-ED8B00?style=flat-square&logo=openjdk&logoColor=white)](https://openjdk.org/projects/jdk/21/)
[![Spring Boot 3.5](https://img.shields.io/badge/Spring%20Boot-3.5.12-6DB33F?style=flat-square&logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot)
[![Spring AI](https://img.shields.io/badge/Spring%20AI-1.0.0--M6-6DB33F?style=flat-square)](https://spring.io/projects/spring-ai)
[![Next.js 15](https://img.shields.io/badge/Next.js-15%20App%20Router-000000?style=flat-square&logo=nextdotjs&logoColor=white)](https://nextjs.org/)
[![MySQL 8.4](https://img.shields.io/badge/MySQL-8.4%20LTS-4479A1?style=flat-square&logo=mysql&logoColor=white)](https://www.mysql.com/)
[![Tests](https://img.shields.io/badge/Tests-63%2F63%20Passed%20(100%25)-emerald?style=flat-square&logo=junit5&logoColor=white)](#-testing--quality-assurance)

An enterprise AI-augmented financial audit system that ingests supplier invoices (PDF and images), extracts unstructured bilingual line items, matches vendor commodities to internal Purchase Orders, enforces **100% deterministic mathematical verification in pure Java**, and flags discrepancies on a high-density Next.js 15 split-screen review dashboard.

The system is completely generic and industry-agnostic, handling invoices across technology, retail, manufacturing, logistics, pharmaceuticals, construction, and agriculture with zero hardcoded commodity fallbacks.

---

## 🌟 Key Capabilities & Architectural Invariants

### 1. Invariant 1: Separation of Extraction vs. Math
- **LLM Extracts Real Printed Data Only:** Multimodal document ingestion (PDF, PNG, JPEG) of supplier invoices using Google Gemini (`gemini-3.8-flash`) via Spring AI and Apache PDFBox 3.0.4 (raw text extraction + page vision rendering). Never computes sums, taxes, or variances.
- **Pure Java Deterministic Math:** All arithmetic, variance calculations, line extension checks, and tolerance comparisons (`0.00 EGP` threshold) are computed strictly in Java business logic using `BigDecimal` (`RoundingMode.HALF_UP`).
- **Zero Dummy Data Fabrication (`EXT-006`):** Rejects empty files with `HTTP 400 Bad Request` and corrupted files with `HTTP 422 Unprocessable Entity`. Never fabricates placeholder items or quantities.

### 2. Invariant 2: Deterministic Reconciliation Rules
- **Micro Price Variance (`FIN-002`):** Flagged as `PRICE_MISMATCH` if $|\text{invoicedPrice} - \text{agreedPrice}| > 0.01\text{ EGP}$.
- **Quantity Variance (`FIN-004`, `FIN-005`):** Flagged as `QUANTITY_MISMATCH` if $\text{invoicedQty} \neq \text{expectedQty}$.
- **Extra Fees (`FIN-006`):** Any unapproved freight, porterage, or surcharge (`مشال / توصيل`) is flagged as `EXTRA_FEE`.
- **Unrecognized Items (`SKU-005`):** Line items that cannot be matched to the PO are flagged as `UNRECOGNIZED_ITEM`.
- **Header Sum Mismatch (`FIN-008`):** Compares invoiced grand total against $\sum(\text{lineItemTotal}) + \text{extraFees}$; flags discrepancy when arithmetic diverges by $> 0.01\text{ EGP}$.

### 3. Generic SKU & Product Discovery
- Matches invoice line items to PO items and database catalogs by canonical `skuCode` and generic normalized token similarity (handling diacritics, Eastern Arabic numerals `٠-٩`, and multi-industry items).
- Zero hardcoded commodities; verified on laptops, steel beams, feta cheese, and agricultural produce.

### 4. Strict Bilingual Dispute Isolation & Machine Delimiters
- Gemini prompt outputs are strictly bounded with machine-readable delimiters (`<<<ARABIC_START>>>...<<<ARABIC_END>>>` and `<<<ENGLISH_START>>>...<<<ENGLISH_END>>>`).
- **100% Language Isolation:** Arabic tab contains zero Latin script; English tab contains zero Arabic script.
- **100% Parity:** Bilingual combined tab matches isolated tabs verbatim.
- Persisted in separate database columns (`dispute_draft_arabic`, `dispute_draft_english`).

### 5. Decoupled Persistence & Network LLM Calls
- Invoices and deterministic audit results are committed to MySQL immediately.
- External Gemini AI dispute generation executes outside the database transaction, preventing connection pool starvation.

### 6. Commercial Email Engine & HMAC-SHA256 Manager Override
- Modular HTML email templates in `src/main/resources/mail/` with live Gmail SMTP dispatch or safe console simulation fallback (`X-Email-Simulated: true`).
- Secure one-click manager payment override links powered by `HmacTokenService` (`/api/invoices/override-approve?token=...`), featuring constant-time signature verification, configurable expiration (default 24h), and replay protection.

### 7. Next.js 15 Split-Screen Auditor Workspace
- Server/Client component boundary optimization (`DashboardClient.tsx` & `InvoiceWorkspaceClient.tsx`).
- 50/50 responsive split: fit-width PDF streaming (`#view=FitH`) on the left; structured audit findings with clean two-line Arabic/English explanations on the right.
- Unified status badge tokens:
  - 🟢 **`APPROVED`**: Emerald
  - 🔴 **`FLAGGED_DISCREPANCY`**: Rose
  - 🟡 **`MANUAL_REVIEW`**: Amber
  - ⚫ **`REJECTED`**: Slate

---

## 🛠 Technology Stack

| Layer | Technologies |
| :--- | :--- |
| **Backend Runtime** | Java 21 (LTS) |
| **Framework** | Spring Boot 3.5.12, Spring Data JPA / Hibernate |
| **Document Ingestion** | Apache PDFBox 3.0.4 (Text Extraction & Page-to-Image Vision Rendering) |
| **AI Orchestration** | Spring AI 1.0.0-M6 (Google Gemini `gemini-3.8-flash`) |
| **Security & Signing** | HMAC-SHA256 (`HmacTokenService`) with constant-time byte comparison |
| **Database** | MySQL 8.4 LTS (Dockerized on port `3307`) |
| **Email Dispatch** | Spring Boot Starter Mail (JavaMailSender / Gmail SMTP) |
| **Frontend Framework** | Next.js 15 / 16 (App Router, Turbopack, React 19) |
| **Styling & Icons** | Tailwind CSS v4, Lucide React |
| **Containerization** | Docker Compose |

---

## 📁 Project Directory Layout

```
Automated-Invoice-Reconciliation/
├── .project-phases/
│   ├── Commercial_Features_Implementation_Plan.md # 5-sprint commercial roadmap (ROI, Debit Notes, ERP Sync)
│   ├── qa_test_execution_report.md               # Complete QA audit report (63/63 passing tests)
│   └── walkthrough.md                            # Comprehensive project walkthrough & operational fixes
├── docker-compose.yml                            # MySQL 8.4 LTS service (port 3307)
├── pom.xml                                       # Maven dependencies & Surefire configuration
├── scripts/
│   ├── generate_valid_invoice_pdf.py             # Agricultural commodity invoice generator
│   ├── generate_electronics_invoice_pdf.py       # Technology & electronics invoice generator
│   └── repair_dispute_drafts.py                  # Database dispute draft utf8mb4 repair script
├── src/main/
│   ├── java/com/agent/reconciliation/
│   │   ├── config/
│   │   │   └── AppProperties.java                # Type-safe @ConfigurationProperties(prefix = "app")
│   │   ├── controller/
│   │   │   └── InvoiceController.java            # REST endpoints (/api/invoices, /override-approve)
│   │   ├── domain/
│   │   │   ├── dto/                              # Immutable Java Records (ExtractedInvoice, Audits)
│   │   │   ├── entity/                           # JPA Entities (PurchaseOrder, Invoice, ReconciliationAudit)
│   │   │   └── enums/                            # ReconciliationStatus, IssueType, PoStatus
│   │   ├── exception/
│   │   │   └── GlobalExceptionHandler.java       # RFC-7807 problem details & client disconnect handler
│   │   ├── repository/                           # Spring Data JPA repositories
│   │   └── service/
│   │       ├── DisputeDraftingService.java       # Gemini LLM dispute generator with strict delimiter parsing
│   │       ├── EmailNotificationService.java     # Pure SMTP MIME message dispatcher
│   │       ├── EmailTemplateService.java         # Template engine for external HTML/CSS
│   │       ├── FileStorageService.java           # Local disk storage & PDF streaming
│   │       ├── HmacTokenService.java             # HMAC-SHA256 signed approval tokens
│   │       ├── InvoiceExtractionService.java     # PDFBox text extraction + Gemini Flash vision
│   │       └── ReconciliationEngineService.java  # Deterministic Java audit engine (FIN-008 header sum check)
│   └── resources/
│       ├── application.yml                       # DB, Mail, App, and Spring AI configuration
│       ├── data.sql                              # Multi-industry seed POs (commodities, dairy, tech, industrial)
│       ├── mail/                                 # External HTML Email Templates
│       │   ├── vendor-dispute.html               # Vendor dispute notice layout
│       │   ├── manager-approval.html             # Manager sign-off receipt layout
│       │   ├── manager-alert.html                # High-priority manager alert layout
│       │   └── components/                       # 27 modular atomic HTML components
│       └── prompts/                              # External StringTemplate (.st) Prompts
│           ├── gemini-extraction.st              # Industry-agnostic multimodal extraction prompt
│           ├── gemini-dispute.st                 # Dispute letter prompt template with machine delimiters
│           └── gemini-dispute-system.st          # Commercial mediator persona instructions
├── src/test/java/com/agent/reconciliation/
│   ├── suite/                                    # Exhaustive QA test suites (44 tests)
│   │   ├── FinancialReconciliationTestSuite.java # FIN-001 through FIN-008
│   │   ├── MultimodalExtractionTestSuite.java    # EXT-001 through EXT-007
│   │   ├── SemanticSkuMappingTestSuite.java      # SKU-001 through SKU-006
│   │   ├── BilingualDisputeDraftingTestSuite.java# DISP-001 through DISP-009
│   │   ├── EmailAndPaymentOverrideTestSuite.java # MAIL-001 through MAIL-006
│   │   └── SecurityAndEdgeCasesTestSuite.java    # SEC-001 through SEC-007
│   ├── controller/InvoiceControllerTest.java     # Controller slice tests
│   └── service/                                  # Unit tests (Engine, Extraction, Storage)
└── frontend/                                     # Next.js 15 Auditor Workspace
    ├── next.config.ts                            # Backend proxy rewrite (/api/:path*)
    └── src/
        ├── app/
        │   ├── layout.tsx                        # Root layout & typography
        │   ├── page.tsx                          # Server component -> DashboardClient
        │   └── invoice/[id]/page.tsx             # Server component -> InvoiceWorkspaceClient
        ├── components/
        │   ├── DashboardClient.tsx               # Metrics cards, queue table, search/filters
        │   ├── DisputeActionDrawer.tsx           # Isolated dispute tabs (Arabic, English, Bilingual)
        │   ├── DocumentViewer.tsx                # Fit-width PDF preview (#view=FitH)
        │   ├── DropzoneUploader.tsx              # Drag & drop upload zone
        │   ├── InvoiceWorkspaceClient.tsx        # Workspace container
        │   └── SplitScreenViewer.tsx             # 50/50 split container & clean 2-line findings
        └── lib/
            ├── api.ts                            # Centralized typed HTTP API client
            └── types.ts                          # TypeScript interfaces mirroring Java DTOs
```

---

## 🗄️ Domain Entities & Database Schema

```
 purchase_orders                      invoices
+--------------------+               +-----------------------------+
| id (PK)            | 1           * | id (PK)                     |
| po_number (UNIQUE) |---------------+ po_reference (FK-logic)     |
| vendor_name        |               | invoice_number              |
| status             |               | invoiced_total              |
| expected_total     |               | reconciliation_status       |
+--------------------+               | file_path                   |
          | 1                        | dispute_draft               |
          |                          | dispute_draft_arabic        |
          | *                        | dispute_draft_english       |
 purchase_order_items                +-----------------------------+
+--------------------+                              | 1
| id (PK)            |                              |
| po_id (FK)         |               reconciliation_audits
| sku_code           |              +-----------------------------+
| expected_quantity  |              | id (PK)                     |
| agreed_unit_price  |              | invoice_id (FK)             |
+--------------------+              | issue_type                  |
                                    | expected_value              |
                                    | actual_value                |
                                    | explanation                 |
                                    +-----------------------------+
```

### Discrepancy Issue Types
- `PRICE_MISMATCH`: Invoiced unit price exceeds agreed PO unit price.
- `QUANTITY_MISMATCH`: Billed quantity/weight differs from ordered PO quantity.
- `EXTRA_FEE`: Unapproved delivery, freight, or surcharge (`مشال / توصيل`), or header grand total arithmetic mismatch (`HEADER_SUM_MISMATCH`).
- `UNRECOGNIZED_ITEM`: Line item printed on invoice cannot be mapped to the PO.
- `PO_NOT_FOUND`: Purchase order reference not found in database.

### Reconciliation Status Hierarchy
- 🔴 `FLAGGED_DISCREPANCY`: Discrepancies detected; awaiting auditor review.
- ⚫ `REJECTED`: Formal dispute dispatched to vendor; payment blocked.
- 🟢 `APPROVED`: Clean match or auditor override authorized.
- 🟡 `MANUAL_REVIEW`: PO reference missing; manual investigation required.

---

## 🌐 REST API Specifications

| Method | Endpoint | Description | Request / Query Params |
| :--- | :--- | :--- | :--- |
| `POST` | `/api/invoices/upload` | Ingests invoice, extracts data via PDFBox & Gemini, and reconciles against PO. | Multipart `file` (PDF/Image) |
| `GET` | `/api/invoices` | Lists all processed invoices for the queue dashboard. | None |
| `GET` | `/api/invoices/{id}` | Fetches detailed invoice audit breakdown and isolated dispute drafts. | Path: `id` |
| `GET` | `/api/invoices/{id}/file` | Streams raw binary document for inline browser preview. | Path: `id` |
| `POST` | `/api/invoices/{id}/generate-dispute` | Regenerates dispute letters dynamically via Gemini AI. | Path: `id` |
| `POST` | `/api/invoices/{id}/approve` | Manually approves invoice and sends manager sign-off receipt. | Query: `email`, `notes` |
| `POST` | `/api/invoices/{id}/reject` | Rejects invoice and sends formal dispute letter to vendor. | Query: `email`, `lang` |
| `GET` | `/api/invoices/override-approve` | One-click direct manager approval via signed HMAC-SHA256 token. | Query: `token`, `notes` |

---

## 🧪 Testing & Quality Assurance

The system is validated by an automated test suite comprising **63 tests** across 6 specialized QA suites and unit test layers:

```bash
# Run all 63 automated tests
./mvnw clean test

# Run specific QA suites:
./mvnw test -Dtest=FinancialReconciliationTestSuite   # FIN-001 through FIN-008
./mvnw test -Dtest=MultimodalExtractionTestSuite        # EXT-001 through EXT-007
./mvnw test -Dtest=SemanticSkuMappingTestSuite          # SKU-001 through SKU-006
./mvnw test -Dtest=BilingualDisputeDraftingTestSuite    # DISP-001 through DISP-009
./mvnw test -Dtest=EmailAndPaymentOverrideTestSuite     # MAIL-001 through MAIL-006
./mvnw test -Dtest=SecurityAndEdgeCasesTestSuite        # SEC-001 through SEC-007

# Validate Next.js frontend production build
npm --prefix frontend run build
```

| Suite | Focus Areas | Tests | Result |
| :--- | :--- | :---: | :---: |
| **Suite 1: Financial Reconciliation** | Exact match, micro-variance (>0.01 EGP), rounding, over/under billing, unauthorized fees, header-vs-line sum mismatch | 9 | **PASS** |
| **Suite 2: Multimodal Extraction** | PDFBox text stripping, raster vision rendering, Eastern Arabic numerals, empty (400) and corrupted (422) rejection | 6 | **PASS** |
| **Suite 3: Semantic SKU Mapping** | Tashkeel diacritics, dairy, electronics, industrial steel, unrecognized commodities, missing PO handling | 6 | **PASS** |
| **Suite 4: Dispute Drafting & Isolation** | 100% Arabic isolation (0% Latin), 100% English isolation (0% Arabic), machine delimiter bounding, sanitization | 9 | **PASS** |
| **Suite 5: Email & Payment Override** | Manager approvals, vendor dispute dispatch, language filtering, SMTP fallback, HMAC token signing & expiry | 7 | **PASS** |
| **Suite 6: Security & Edge Cases** | Prompt injection immunity, path traversal sanitization, XSS serialization safety, concurrent idempotency | 7 | **PASS** |
| **Unit & Context Tests** | Controller slice tests, storage service, extraction service, application context | 19 | **PASS** |
| **Total** | **End-to-End System Under Test** | **63** | **100% PASS** |

Detailed audit log: [.project-phases/qa_test_execution_report.md](.project-phases/qa_test_execution_report.md).

---

## 🚀 Running Locally

### 1. Configure Environment (`.env`)
```env
# Google Gemini API
GEMINI_API_KEY=your_gemini_api_key_here
AI_BASE_URL=https://generativelanguage.googleapis.com/v1beta/openai/
AI_MODEL=gemini-3.8-flash

# MySQL Database (Port 3307)
SPRING_DATASOURCE_URL=jdbc:mysql://localhost:3307/reconciliation_db?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&createDatabaseIfNotExist=true
SPRING_DATASOURCE_USERNAME=root
SPRING_DATASOURCE_PASSWORD=root

# Live Gmail SMTP (Optional - falls back to safe console simulation if omitted)
SPRING_MAIL_HOST=smtp.gmail.com
SPRING_MAIL_PORT=587
SPRING_MAIL_USERNAME=your_gmail@gmail.com
SPRING_MAIL_PASSWORD="your_16_digit_app_password"
NOTIFICATION_MANAGER_EMAIL=manager@example.com
NOTIFICATION_VENDOR_EMAIL=vendor@example.com
```

### 2. Start MySQL via Docker
```bash
docker compose up -d
```
*MySQL 8.4 will initialize on port `3307` and automatically seed multi-industry POs from `data.sql`.*

### 3. Start Spring Boot Backend (Port 8080)
```bash
./mvnw spring-boot:run
```

### 4. Start Next.js Frontend (Port 3000)
```bash
cd frontend
npm install
npm run dev
```
Open [http://localhost:3000](http://localhost:3000) to access the auditor dashboard.

---

## 🗺️ Commercial Roadmap

For executive features and the upcoming product rollout, review [.project-phases/Commercial_Features_Implementation_Plan.md](.project-phases/Commercial_Features_Implementation_Plan.md):
- **Sprint 1 (Fast ROI):** "Leakage Prevented" CFO ROI Dashboard & Vendor Reliability Scorecard.
- **Sprint 2 (Resolution Loop):** Auto-Generated Debit Note (إشعار مدين) PDF & Supplier Self-Service Magic Link Portal.
- **Sprint 3 (System of Record):** One-Click ERP Sync (Odoo & Universal CSV/UBL).
- **Sprint 4 (Gold Standard):** Agentic 3-Way Matching (PO + GRN Goods Receipt Note + Invoice).
- **Sprint 5 (MENA Dominance):** WhatsApp Invoice Ingestion & Real-Time AI Auto-Dispute.
