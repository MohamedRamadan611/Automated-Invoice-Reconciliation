# Automated Invoice Reconciliation Agent

[![Java 21](https://img.shields.io/badge/Java-21%20LTS-ED8B00?style=flat-square&logo=openjdk&logoColor=white)](https://openjdk.org/projects/jdk/21/)
[![Spring Boot 3.5](https://img.shields.io/badge/Spring%20Boot-3.5.12-6DB33F?style=flat-square&logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot)
[![Spring AI](https://img.shields.io/badge/Spring%20AI-1.0.0--M6-6DB33F?style=flat-square)](https://spring.io/projects/spring-ai)
[![Next.js 16](https://img.shields.io/badge/Next.js-16%20Turbopack-000000?style=flat-square&logo=nextdotjs&logoColor=white)](https://nextjs.org/)
[![MySQL 8.4](https://img.shields.io/badge/MySQL-8.4%20LTS-4479A1?style=flat-square&logo=mysql&logoColor=white)](https://www.mysql.com/)
[![Backend Tests](https://img.shields.io/badge/Backend%20Tests-84%2F84%20Passed%20(100%25)-emerald?style=flat-square&logo=junit5&logoColor=white)](#-testing--quality-assurance)
[![Frontend Tests](https://img.shields.io/badge/Frontend%20Tests-19%20Vitest%20%2B%205%20Playwright-blue?style=flat-square&logo=vitest&logoColor=white)](#-testing--quality-assurance)

An enterprise AI-augmented financial audit and commercial settlement platform. It ingests supplier invoices (PDF and images), extracts unstructured bilingual line items, performs semantic discovery against Purchase Orders, enforces **100% deterministic mathematical verification in pure Java**, generates audit-certified **Debit Note (إشعار مدين) PDFs**, quantifies **capital leakage prevented for CFOs**, tracks **vendor reliability scorecards**, and provides an external **supplier self-service dispute settlement portal** secured by time-bounded HMAC-SHA256 magic tokens.

The system is completely generic and industry-agnostic, handling invoices across technology, retail, manufacturing, logistics, pharmaceuticals, construction, and agriculture with zero hardcoded commodity fallbacks.

---

## 🌟 Key Capabilities & Architectural Pillars

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

### 3. "Leakage Prevented" CFO ROI Dashboard & AI Executive Brief
- **Executive Metric Stream (`/api/analytics/roi-summary`):** Real-time aggregation of total invoices audited, clean match rates, audit discrepancy volumes, and exact **Capital Leakage Prevented (EGP)**.
- **Autonomous Gemini Executive Brief:** High-level narrative synthesis analyzing top leakage categories and financial risk exposure with dual-language formatting (English LTR on top; Arabic RTL below) in a unified executive card.

### 4. Vendor Reliability Scorecard & AI Risk Dossiers
- **Vendor Risk Indexing (`/vendors`):** Analyzes historical invoice accuracy, overcharge volume, and clean match rates to compute a 0–100% Reliability Score.
- **Automated Tier Classification:**
  - 🟢 **Tier 1 (Trusted):** Reliability $\ge 90\%$ (Eligible for straight-through processing).
  - 🟡 **Tier 2 (Moderate):** Reliability $75\% - 89\%$ (Standard reconciliation audit).
  - 🔴 **Tier 3 (High Risk):** Reliability $< 75\%$ (Mandatory manager sign-off).
- **Gemini Qualitative Risk Analysis:** Generates commercial leverage points and negotiation tactics tailored to supplier billing history.

### 5. Official Commercial Debit Note (إشعار مدين) PDF Engine
- **Audit-Certified PDF Generation (`/api/invoices/{id}/debit-note`):** High-resolution, printable bilingual debit note generated via Apache PDFBox 3.0.4.
- **Ledger Compliance:** Features official corporate header, reference stamping, itemized variance schedule (SKU, Agreed Rate, Billed Rate, Variance, Deduction), certified seal, and authorized signature lines.

### 6. Secure Supplier Dispute Settlement Portal
- **HMAC-SHA256 Magic Token Security (`/portal/dispute?token=...`):** 7-day time-bounded, tamper-proof supplier access link requiring zero supplier login or account creation.
- **Itemized Variance Table with Master-Detail UX:**
  - Locked column layout (`table-fixed` + `<colgroup>`) preventing any horizontal column jittering when expanding or collapsing rows.
  - Symmetrical edge insets (`pl-6 pr-4` on SKU column; `pl-4 pr-6 sm:pr-8` on action column) ensuring action buttons never touch table boundary lines.
  - Expandable Master-Detail sub-rows rendering side-by-side English (LTR) and Arabic (RTL) cards with fallback synthesis ensuring formal Arabic explanations are present across all screens.
  - Global `Expand All Notes / Collapse All Notes` toggle button.
  - Clean empty state for approved invoices with zero audit variances.
- **Autonomous Supplier Resolution Options (Available on Flagged & Rejected Invoices):**
  - **Option 1: Accept Deductions & Release Payment:** Instantly finalizes deduction, transitions invoice to `RESOLVED_BY_DEBIT_NOTE`, stamps Debit Note PDF, and schedules net approved balance for immediate disbursement.
  - **Option 2: Counter-Explanation with AI Negotiator:** Gemini AI evaluates supplier justifications against commercial standards in real time.
  - **Option 3: Upload Amended Invoice:** Suppliers upload corrected invoices (PDF/Image) to trigger autonomous re-reconciliation against original PO terms.

### 7. Bilingual Dispute Isolation & Commercial Email Engine
- **Strict Machine Delimiters:** Gemini outputs strictly bounded by `<<<ARABIC_START>>>...<<<ARABIC_END>>>` and `<<<ENGLISH_START>>>...<<<ENGLISH_END>>>`.
- **100% Language Isolation:** Arabic tab contains zero Latin script; English tab contains zero Arabic script.
- **Modular Email Dispatch:** In `src/main/resources/mail/` with live Gmail SMTP dispatch or safe console simulation fallback (`X-Email-Simulated: true`).
- **Manager One-Click Override:** Secure signed URL (`/api/invoices/override-approve?token=...`) allowing emergency payment authorization.

---

## 🛠 Technology Stack

| Layer | Technologies |
| :--- | :--- |
| **Backend Runtime** | Java 21 (LTS) |
| **Framework** | Spring Boot 3.5.12, Spring Data JPA / Hibernate 6.6 |
| **Document Processing** | Apache PDFBox 3.0.4 (Text Extraction, Vision Page Rendering, Debit Note PDF Generation) |
| **AI Orchestration** | Spring AI 1.0.0-M6 (Google Gemini `gemini-3.8-flash`) |
| **Security & Cryptography** | HMAC-SHA256 (`HmacTokenService`) with constant-time byte comparison |
| **Database** | MySQL 8.4 LTS (Dockerized on port `3307`) |
| **Email Dispatch** | Spring Boot Starter Mail (JavaMailSender / Gmail SMTP) |
| **Frontend Framework** | Next.js 16 (App Router, Turbopack, React 19) |
| **Styling & Components** | Vanilla CSS, Tailwind CSS v4, Lucide React |
| **Testing Frameworks** | JUnit 5, Mockito, MockMvc, Vitest, React Testing Library, Playwright E2E |
| **Containerization** | Docker & Docker Compose |

---

## 📁 Project Directory Layout

```
Automated-Invoice-Reconciliation/
├── .project-phases/
│   ├── Commercial_Features_Implementation_Plan.md # 5-sprint commercial roadmap
│   ├── qa_test_execution_report.md               # QA audit report
│   └── walkthrough.md                            # Comprehensive project walkthrough
├── docker-compose.yml                            # MySQL 8.4 LTS service (port 3307)
├── pom.xml                                       # Maven dependencies & test plugins
├── src/main/
│   ├── java/com/agent/reconciliation/
│   │   ├── config/
│   │   │   └── AppProperties.java                # Type-safe @ConfigurationProperties(prefix = "app")
│   │   ├── controller/
│   │   │   ├── InvoiceController.java            # Invoices, approvals, rejects, overrides, Debit Note PDF
│   │   │   ├── AnalyticsController.java          # CFO ROI metrics & AI Executive Brief (/api/analytics)
│   │   │   ├── VendorController.java             # Vendor reliability scorecards (/api/vendors)
│   │   │   └── SupplierPortalController.java     # Secure supplier dispute portal (/api/portal/dispute)
│   │   ├── domain/
│   │   │   ├── dto/                              # Immutable DTO records (Extraction, ROI, Portal, Scorecard)
│   │   │   ├── entity/                           # JPA Entities (PurchaseOrder, Invoice, ReconciliationAudit)
│   │   │   └── enums/                            # ReconciliationStatus, IssueType, PoStatus, VendorTier
│   │   ├── repository/                           # Spring Data JPA repositories
│   │   ├── service/
│   │   │   ├── ReconciliationEngineService.java  # Deterministic Java audit engine (FIN-001 - FIN-008)
│   │   │   ├── FinancialAnalyticsService.java    # Capital leakage & ROI aggregation
│   │   │   ├── VendorScorecardService.java       # Reliability scoring & AI risk dossier generation
│   │   │   ├── DebitNoteGeneratorService.java    # Apache PDFBox Debit Note PDF generator
│   │   │   ├── DisputeDraftingService.java       # Gemini LLM bilingual dispute drafter
│   │   │   ├── HmacTokenService.java             # HMAC-SHA256 token generator & validator
│   │   │   ├── InvoiceExtractionService.java     # PDFBox text extraction + Gemini Flash vision
│   │   │   └── EmailNotificationService.java     # SMTP MIME message dispatcher
│   │   └── util/
│   │       └── DisputeReasonHelper.java          # Canonical Arabic & English audit reason formatter
│   └── resources/
│       ├── application.yml                       # DB, Mail, App, and Spring AI configuration
│       ├── data.sql                              # Multi-industry seed POs (commodities, tech, industrial)
│       ├── mail/                                 # HTML email layouts & components
│       └── prompts/                              # External StringTemplate (.st) Prompts
│           ├── gemini-extraction.st              # Multimodal invoice extraction prompt
│           ├── gemini-dispute.st                 # Bilingual dispute letter prompt
│           ├── gemini-executive-brief.st         # Autonomous executive summary prompt
│           ├── gemini-vendor-risk.st             # Vendor risk assessment prompt
│           └── gemini-portal-negotiation.st      # Supplier dispute negotiation prompt
├── src/test/java/com/agent/reconciliation/
│   ├── controller/                               # MockMvc REST controller tests (84 total tests)
│   │   ├── InvoiceControllerTest.java
│   │   ├── AnalyticsControllerTest.java
│   │   ├── VendorControllerTest.java
│   │   └── SupplierPortalControllerTest.java
│   ├── service/                                  # Unit tests (Engine, Analytics, DebitNote, HMAC, Scorecard)
│   └── suite/                                    # Exhaustive QA test suites (FIN, EXT, SKU, DISP, MAIL, SEC)
└── frontend/                                     # Next.js 16 Auditor & Supplier Workspace
    ├── playwright.config.ts                      # Playwright E2E configuration
    ├── vitest.config.ts                          # Vitest component test configuration
    ├── e2e/                                      # Playwright browser journeys (5 spec suites)
    │   ├── roi-dashboard.spec.ts
    │   ├── vendor-scorecard.spec.ts
    │   └── supplier-portal.spec.ts
    └── src/
        ├── app/
        │   ├── page.tsx                          # CFO ROI Dashboard & Invoices Queue
        │   ├── vendors/page.tsx                  # Vendor Reliability Scorecards
        │   ├── invoice/[id]/page.tsx             # 50/50 Split-Screen Auditor Workspace
        │   └── portal/dispute/page.tsx           # Supplier Dispute Settlement Portal
        ├── components/
        │   ├── DashboardClient.tsx               # Hero KPI banner, AI Executive Brief, invoices table
        │   ├── VendorScorecardClient.tsx         # Scorecard table, risk filters, AI dossier drawer
        │   ├── SupplierPortalClient.tsx          # Master-detail variance table, Debit Note, resolution tabs
        │   ├── SplitScreenViewer.tsx             # Fit-width PDF viewer + audit findings
        │   └── DisputeActionDrawer.tsx           # Bilingual dispute letters & Debit Note download link
        ├── __tests__/                            # Vitest component tests (19 tests)
        │   ├── DashboardRoiMetrics.test.tsx
        │   ├── VendorScorecard.test.tsx
        │   ├── SupplierPortal.test.tsx
        │   └── DisputeActionDrawer.test.tsx
        └── lib/
            ├── api.ts                            # Typed HTTP client
            └── types.ts                          # TypeScript interfaces mirroring Java DTOs
```

---

## 🗄️ Database Schema & Reconciliation Statuses

```
 purchase_orders                      invoices
+--------------------+               +-----------------------------+
| id (PK)            | 1           * | id (PK)                     |
| po_number (UNIQUE) |---------------+ po_reference (FK-logic)     |
| vendor_name        |               | invoice_number              |
| status             |               | invoiced_total              |
| expected_total     |               | reconciliation_status       |
+--------------------+               | file_path                   |
          | 1                        | dispute_draft_arabic        |
          |                          | dispute_draft_english       |
          | *                        +-----------------------------+
 purchase_order_items                               | 1
+--------------------+                              |
| id (PK)            |               reconciliation_audits
| po_id (FK)         |              +-----------------------------+
| sku_code           |              | id (PK)                     |
| expected_quantity  |              | invoice_id (FK)             |
| agreed_unit_price  |              | issue_type                  |
+--------------------+              | expected_value              |
                                    | actual_value                |
                                    | explanation                 |
                                    +-----------------------------+
```

### Reconciliation Status Hierarchy
- 🟢 `APPROVED`: Clean match or auditor override authorized.
- 🔵 `RESOLVED_BY_DEBIT_NOTE`: Settled via accepted deduction & official Debit Note.
- 🔴 `FLAGGED_DISCREPANCY`: Discrepancies detected; awaiting auditor or supplier resolution.
- ⚫ `REJECTED`: Formal dispute dispatched to vendor; payment blocked.
- 🟡 `MANUAL_REVIEW`: PO reference missing; manual investigation required.

### Discrepancy Issue Types
- `PRICE_MISMATCH`: Invoiced unit price exceeds agreed PO unit price.
- `QUANTITY_MISMATCH`: Billed quantity/weight differs from ordered PO quantity.
- `EXTRA_FEE`: Unapproved freight, porterage, or surcharge (`مشال / توصيل`), or arithmetic mismatch (`HEADER_SUM_MISMATCH`).
- `UNRECOGNIZED_ITEM`: Line item on invoice cannot be matched to PO.
- `PO_NOT_FOUND`: Purchase order reference not found in database.

---

## 🌐 Complete REST API Specifications

| Method | Endpoint | Description | Query / Body Params |
| :--- | :--- | :--- | :--- |
| `POST` | `/api/invoices/upload` | Ingests invoice, extracts data via PDFBox & Gemini, executes deterministic audit. | Multipart `file` |
| `GET` | `/api/invoices` | Lists all processed invoices for the queue dashboard. | None |
| `GET` | `/api/invoices/{id}` | Fetches detailed invoice audit breakdown and dispute drafts. | Path: `id` |
| `GET` | `/api/invoices/{id}/file` | Streams raw binary document for inline browser preview. | Path: `id` |
| `GET` | `/api/invoices/{id}/debit-note` | Generates official audit-certified Debit Note PDF (Apache PDFBox). | Path: `id` |
| `POST` | `/api/invoices/{id}/generate-dispute` | Regenerates dispute letters dynamically via Gemini AI. | Path: `id` |
| `POST` | `/api/invoices/{id}/approve` | Manually approves invoice and sends manager sign-off receipt. | Query: `email`, `notes` |
| `POST` | `/api/invoices/{id}/reject` | Rejects invoice and sends formal dispute letter to vendor. | Query: `email`, `lang` |
| `GET` | `/api/invoices/override-approve` | One-click direct manager approval via signed HMAC-SHA256 token. | Query: `token`, `notes` |
| `GET` | `/api/analytics/roi-summary` | Aggregates CFO ROI metrics (leakage prevented, AI brief, category breakdown). | None |
| `GET` | `/api/vendors/scorecard` | Lists reliability metrics and risk tier classification for all vendors. | None |
| `GET` | `/api/vendors/{vendorName}/scorecard` | Fetches vendor scorecard with Gemini qualitative risk dossier. | Path: `vendorName` |
| `GET` | `/api/portal/dispute` | Validates HMAC token and returns restricted supplier dispute portal view. | Query: `token` |
| `POST` | `/api/portal/dispute/accept` | Supplier accepts deduction; transitions to `RESOLVED_BY_DEBIT_NOTE`. | Query: `token` |
| `POST` | `/api/portal/dispute/explain` | Supplier submits counter-justification; evaluated by AI Negotiator. | Query: `token`, JSON `{"explanation": "..."}` |
| `POST` | `/api/portal/dispute/upload-amended` | Supplier uploads amended invoice; triggers autonomous re-reconciliation. | Query: `token`, Multipart `file` |

---

## 🧪 Testing & Quality Assurance

The system is guarded by a 4-tier testing pyramid with **108 total automated tests** passing at 100%:

```bash
# 1. Run all 84 Backend JUnit 5 tests
./mvnw clean test

# 2. Run all 19 Frontend Vitest component tests
npm --prefix frontend test -- --run

# 3. Run all 5 Full-Stack Playwright E2E browser tests
npm --prefix frontend run test:e2e

# 4. Validate Next.js production build with Turbopack
npm --prefix frontend run build
```

### Test Suite Execution Summary

| Layer | Framework | Scope / Coverage | Tests | Status |
| :--- | :--- | :--- | :---: | :---: |
| **Backend Unit & Logic** | JUnit 5 + Mockito | Deterministic math, rounding tolerances, PDF Debit Note generation, HMAC token lifecycle, vendor scoring | 40 | **100% PASS** |
| **Backend QA Suites** | Spring Boot + MySQL | `FIN-001` - `FIN-008`, `EXT-001` - `EXT-007`, `SKU-001` - `SKU-006`, `DISP-001` - `DISP-009`, `MAIL-001` - `MAIL-006`, `SEC-001` - `SEC-007` | 44 | **100% PASS** |
| **Frontend Components** | Vitest + RTL | Hero KPI metrics, executive brief rendering, vendor scorecard filters, dispute action drawer, master-detail supplier portal | 19 | **100% PASS** |
| **End-to-End User Journeys** | Playwright (Chromium) | Live browser navigation: `/` dashboard, `/vendors` scorecard search, and `/portal/dispute?token=...` supplier settlement loop | 5 | **100% PASS** |
| **Total Automated Tests** | **Full System Under Test** | **Unit + Integration + Component + E2E** | **108** | **100% PASS** |

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
Open [http://localhost:3000](http://localhost:3000) to access the auditor workspace, [http://localhost:3000/vendors](http://localhost:3000/vendors) for vendor scorecards, or open a generated magic link for the supplier portal.

---

## 🗺️ Commercial Roadmap Progress

Tracked in [.project-phases/Commercial_Features_Implementation_Plan.md](.project-phases/Commercial_Features_Implementation_Plan.md):

- ✅ **Sprint 1 (Fast ROI):** "Leakage Prevented" CFO ROI Dashboard, Autonomous Gemini Executive Brief & Vendor Reliability Scorecard. *(COMPLETED)*
- ✅ **Sprint 2 (Resolution Loop):** Apache PDFBox Commercial Debit Note (إشعار مدين) PDF Engine & Supplier Self-Service Dispute Settlement Portal with 3 resolution options. *(COMPLETED)*
- ⏳ **Sprint 3 (System of Record):** One-Click ERP Sync (Odoo REST Integration & Universal CSV/UBL Exports).
- ⏳ **Sprint 4 (Gold Standard):** Agentic 3-Way Matching (PO + GRN Goods Receipt Note + Invoice).
- ⏳ **Sprint 5 (MENA Dominance):** WhatsApp Invoice Ingestion & Real-Time Autonomous Dispute Bot.
