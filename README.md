# Automated Invoice Reconciliation Agent

An enterprise AI-augmented financial audit system that ingests supplier invoices (PDF and images), extracts unstructured bilingual line items, matches vendor commodities to internal Purchase Orders, enforces 100% deterministic mathematical verification in Java, and flags discrepancies on a modern Next.js split-screen review dashboard.

The system is completely generic and industry-agnostic, handling invoices across technology, retail, manufacturing, logistics, pharmaceuticals, construction, and agriculture with zero hardcoded commodity fallbacks.

---

## 🌟 Key Capabilities & Architectural Invariants

1. **Dynamic Real-Document Ingestion (Apache PDFBox + Google Gemini Flash):**
   - Ingests native PDFs, PNGs, and JPEGs without external OCR services.
   - Uses **Apache PDFBox 3.0.4** to extract text directly from PDFs and render pages to PNG for vision processing, avoiding MIME-type incompatibilities with OpenAI-compatible endpoints.
   - Normalizes Arabic-Indic numerals (`٠-٩`) to standard decimal digits (`0-9`).
   - Extracts real printed data verbatim: descriptions, quantities/weights, unit prices, and line totals.
   - **Zero Fake Data:** Never fabricates items, prices, or weights. If an invoice cannot be extracted, a clean exception is thrown instead of returning static fallbacks.

2. **Generic Database SKU & Product Retrieval:**
   - Matches invoice line items to PO items and database catalogs by canonical `skuCode` and generic normalized token similarity (token overlap and substring containment).
   - Zero hardcoded product or commodity words; handles any product (e.g. `SKU-LAPTOP-15`, `SKU-STEEL-BEAM-12`, `SKU-TOMATO-RED`).

3. **Zero Arithmetic Delegation to LLMs:**
   - **LLM extracts only:** Never computes sums, taxes, or variances.
   - **Pure Java Math:** All variance calculations, line extension checks, and tolerance comparisons (0.00 EGP) are computed in Java using `BigDecimal`.

4. **Strict Dispute Language Isolation & Bilingual Parity:**
   - Prompt outputs are framed with machine delimiters (`<<<ARABIC_START>>>...<<<ARABIC_END>>>` and `<<<ENGLISH_START>>>...<<<ENGLISH_END>>>`).
   - The Arabic section in the "Bilingual (All)" tab is **100% identical** to the Arabic tab.
   - The English section in the "Bilingual (All)" tab is **100% identical** to the English tab.
   - Zero language mixing: Arabic tab contains pure Arabic text; English tab contains pure English text.
   - Persisted in MySQL columns `dispute_draft_arabic` and `dispute_draft_english`.

5. **Decoupled Database Transactions & LLM Network I/O:**
   - Database operations and external LLM calls are decoupled in `ReconciliationEngineService`.
   - Invoices and audits are committed immediately; LLM dispute drafts execute outside the transaction to protect database connection pools.

6. **Commercial Email Engine with Gmail SMTP & Safe Simulation:**
   - Transmits live emails via Gmail SMTP (`smtp.gmail.com:587` with STARTTLS) when configured in `.env`.
   - Graceful fallback: logs formatted HTML and ASCII summaries to the server console if credentials are unset, returning `X-Email-Simulated: true`.
   - Includes one-click payment authorization token links in manager alert emails.

7. **Next.js 15 Split-Screen Auditor Workspace:**
   - Enforced Next.js App Router Server vs. Client component boundaries (`DashboardClient.tsx` & `InvoiceWorkspaceClient.tsx`).
   - Centralized typed HTTP API in `frontend/src/lib/api.ts`.
   - Native document viewer with 100% full-width and fit-width scaling (`#view=FitH`).
   - Clean two-line bilingual audit findings: Line 1 in Modern Standard Arabic (`dir="rtl"`) and Line 2 in Business English (`dir="ltr"`), without visual clutter.

---

## 🛠 Technology Stack

| Layer | Technologies |
| :--- | :--- |
| **Backend Runtime** | Java 21 (LTS) |
| **Framework** | Spring Boot 3.5.x, Spring Data JPA / Hibernate |
| **Document Processing** | Apache PDFBox 3.0.4 (Text Extraction & Page-to-Image Rendering) |
| **AI Orchestration** | Spring AI 1.0.0-M6 (Google Gemini `gemini-3.8-flash`) |
| **Database** | MySQL 8.4 LTS (Dockerized on port `3307`) |
| **Email Dispatch** | Spring Boot Starter Mail (JavaMailSender / Gmail SMTP) |
| **Frontend Framework** | Next.js 15 (App Router, Turbopack, React 19) |
| **Styling & Icons** | Tailwind CSS v4, Lucide React |
| **Containerization** | Docker Compose |

---

## 📁 Project Directory Layout

```
Automated-Invoice-Reconciliation/
├── docker-compose.yml                      # MySQL 8.4 LTS service (port 3307)
├── pom.xml                                 # Maven dependencies (Spring Boot 3.5, Spring AI, PDFBox)
├── scripts/
│   ├── generate_valid_invoice_pdf.py       # Agricultural commodity invoice generator
│   └── generate_electronics_invoice_pdf.py # Technology & electronics invoice generator
├── src/main/
│   ├── java/com/agent/reconciliation/
│   │   ├── config/
│   │   │   └── AppProperties.java          # Type-safe @ConfigurationProperties(prefix = "app")
│   │   ├── controller/
│   │   │   └── InvoiceController.java      # REST endpoints (/api/invoices)
│   │   ├── domain/
│   │   │   ├── dto/                        # Immutable Java Records (ExtractedInvoice, Audits, Responses)
│   │   │   ├── entity/                     # JPA Entities (PurchaseOrder, Invoice, ReconciliationAudit)
│   │   │   └── enums/                      # ReconciliationStatus, IssueType, PoStatus
│   │   ├── exception/
│   │   │   └── GlobalExceptionHandler.java # RFC-7807 problem details handler
│   │   ├── repository/                     # Spring Data JPA repositories
│   │   └── service/
│   │       ├── DisputeDraftingService.java # Gemini LLM dispute generator with strict delimiter parsing
│   │       ├── EmailNotificationService.java # Pure SMTP MIME message dispatcher
│   │       ├── EmailTemplateService.java   # Template engine for external HTML/CSS
│   │       ├── FileStorageService.java     # Local disk storage & PDF streaming
│   │       ├── InvoiceExtractionService.java # PDFBox text extraction + Gemini Flash multimodal vision
│   │       └── ReconciliationEngineService.java # Deterministic Java audit engine (generic SKU matching)
│   └── resources/
│       ├── application.yml                 # DB, Mail, App, and Spring AI configuration
│       ├── data.sql                        # Multi-industry seed POs (commodities, dairy, tech, industrial)
│       ├── mail/                           # External HTML Email Templates
│       │   ├── vendor-dispute.html         # Vendor dispute notice layout
│       │   ├── manager-approval.html       # Manager sign-off receipt layout
│       │   ├── manager-alert.html          # High-priority manager alert layout
│       │   └── components/                 # 27 modular atomic HTML components
│       └── prompts/                        # External StringTemplate (.st) Prompts
│           ├── gemini-extraction.st        # Industry-agnostic multimodal extraction prompt
│           ├── gemini-dispute.st           # Dispute letter prompt template with machine delimiters
│           └── gemini-dispute-system.st    # Commercial mediator persona instructions
└── frontend/                               # Next.js 15 Auditor Workspace
    ├── next.config.ts                      # Backend proxy rewrite (/api/:path*)
    └── src/
        ├── app/
        │   ├── layout.tsx                  # Root layout & typography
        │   ├── page.tsx                    # Server component -> DashboardClient
        │   └── invoice/[id]/page.tsx       # Server component -> InvoiceWorkspaceClient
        ├── components/
        │   ├── DashboardClient.tsx         # Metrics cards, queue table, search/filters
        │   ├── DisputeActionDrawer.tsx     # Isolated dispute tabs (Arabic, English, Bilingual)
        │   ├── DocumentViewer.tsx          # Fit-width PDF preview (#view=FitH)
        │   ├── DropzoneUploader.tsx        # Drag & drop upload zone
        │   ├── InvoiceWorkspaceClient.tsx  # Workspace container
        │   └── SplitScreenViewer.tsx       # 50/50 split container & clean 2-line findings
        └── lib/
            ├── api.ts                      # Centralized typed HTTP API client
            └── types.ts                    # TypeScript interfaces mirroring Java DTOs
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
- `EXTRA_FEE`: Unapproved delivery, freight, or porterage surcharge (`مشال / توصيل`).
- `UNRECOGNIZED_ITEM`: Line item printed on invoice cannot be mapped to the PO.
- `PO_NOT_FOUND`: Purchase order reference not found in database.

### Reconciliation Status Hierarchy
- 🟠 `FLAGGED_DISCREPANCY`: Discrepancies detected; awaiting auditor review.
- 🔴 `REJECTED`: Formal dispute dispatched to vendor; payment blocked.
- 🟢 `APPROVED`: Clean match or auditor override authorized.
- 🔵 `MANUAL_REVIEW`: PO reference missing; manual investigation required.

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
| `GET` | `/api/invoices/{id}/override-approval` | One-click direct manager approval via email token. | Query: `token` |

---

## 🚀 Running Locally

### 1. Configure Environment (`.env`)
```env
# Google Gemini API
GEMINI_API_KEY=your_gemini_api_key_here
AI_BASE_URL=https://generativelanguage.googleapis.com/v1beta/openai/
AI_MODEL=gemini-3.8-flash

# MySQL Database
SPRING_DATASOURCE_URL=jdbc:mysql://localhost:3307/reconciliation_db?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&createDatabaseIfNotExist=true
SPRING_DATASOURCE_USERNAME=root
SPRING_DATASOURCE_PASSWORD=root

# Live Gmail SMTP (Optional - falls back to safe console simulation if omitted)
SPRING_MAIL_HOST=smtp.gmail.com
SPRING_MAIL_PORT=587
SPRING_MAIL_USERNAME=your_gmail@gmail.com
SPRING_MAIL_PASSWORD=your_16_digit_app_password
NOTIFICATION_MANAGER_EMAIL=manager@example.com
NOTIFICATION_VENDOR_EMAIL=vendor@example.com
```

### 2. Start Database
```bash
docker compose up -d
```
*MySQL 8.4 will initialize on port `3307` and automatically seed multi-industry POs from `data.sql`.*

### 3. Start Backend (Port 8080)
```bash
./mvnw spring-boot:run
```

### 4. Start Frontend (Port 3000)
```bash
cd frontend
npm install
npm run dev
```
Open [http://localhost:3000](http://localhost:3000) to access the auditor dashboard.

---

## 🧪 Testing & Verification

```bash
# Run backend test suite (unit and slice tests)
./mvnw test -Dtest='!AutomatedInvoiceReconciliationApplicationTests'

# Run specific reconciliation test
./mvnw test -Dtest=ReconciliationEngineServiceTest

# Run controller REST tests
./mvnw test -Dtest=InvoiceControllerTest

# Validate frontend production build
cd frontend && npm run build

# Generate synthetic multi-industry invoice PDFs
python3 scripts/generate_valid_invoice_pdf.py
python3 scripts/generate_electronics_invoice_pdf.py
```
