# Walkthrough: Commercial Email Notifications & UX Color Differentiation

This milestone delivers an end-to-end commercial email notification flow via Gmail SMTP (with safe console simulation fallback) and refines the status color hierarchy to strictly differentiate **`FLAGGED_DISCREPANCY`** from **`REJECTED`**.

---

## 1. Architectural Summary & Delivered Features

### A. Commercial Dispute & Approval Email Dispatch
- **Dispute Rejection Flow (`POST /api/invoices/{id}/reject?email=...`):**
  - Updates the invoice status to `REJECTED`.
  - Dispatches a formal commercial dispute email to the **Vendor** with:
    - Company & Accounts Payable Header.
    - Summary of Billed vs Expected PO Totals.
    - Itemized table of detected variances (Price hikes, Quantity mismatches, Unapproved surcharges).
    - Complete Gemini 3.8 bilingual dispute draft in formatted Arabic and English.
    - Automatic CC to the Finance Manager recording that payment is blocked.
- **Approval Sign-Off Flow (`POST /api/invoices/{id}/approve?email=...&notes=...`):**
  - Updates the invoice status to `APPROVED`.
  - Dispatches a payment release authorization notice to the **Finance Manager** with:
    - Formal payment release confirmation.
    - Auditor override and responsibility justification notes.
    - Total authorized disbursement in EGP against the purchase order.
- **Dual Mode (Live Gmail SMTP or Safe Simulation):**
  - If Gmail credentials (`SPRING_MAIL_USERNAME` and `SPRING_MAIL_PASSWORD`) are present in `.env`, transmits live emails through `smtp.gmail.com:587` with TLS.
  - If unset or running offline, safely logs the formatted HTML and ASCII notification to the server console and returns `X-Email-Simulated: true` without failing requests.

---

### B. UX Color Differentiation & Visual Hierarchy
To prevent cognitive overload and clearly communicate invoice review states, the color system was updated:

| Reconciliation Status | Visual Style | Meaning & Context |
| :--- | :--- | :--- |
| 🟠 **`FLAGGED_DISCREPANCY`** | **Warm Amber / Warning Orange**<br>`bg-amber-50 text-amber-900 border-amber-300` | **Audit Pending / Under Investigation:** Discrepancies detected between invoice and PO; awaiting auditor decision. |
| 🔴 **`REJECTED`** | **Crimson / Deep Ruby Red**<br>`bg-rose-100 text-rose-950 border-rose-400 font-semibold` | **Dispute Issued / Payment Blocked:** Formal dispute letter sent to vendor; payment held until revised invoice or credit note. |
| 🟢 **`APPROVED`** | **Emerald Green**<br>`bg-emerald-50 text-emerald-800 border-emerald-300` | **Payment Released:** Clean invoice or auditor override confirmed; ready for disbursement. |
| 🔵 **`MANUAL_REVIEW`** | **Indigo / Slate Blue**<br>`bg-indigo-50 text-indigo-800 border-indigo-200` | **PO Missing / Unmatched:** Purchase order could not be located in database; manual review required. |

---

## 2. Key Code Modifications

1. [pom.xml](file:///Users/mohamed.abdelfatah/Mohamed-Ramadan/Automated-Invoice-Reconciliation/pom.xml):
   - Added `spring-boot-starter-mail` dependency.
2. [src/main/resources/application.yml](file:///Users/mohamed.abdelfatah/Mohamed-Ramadan/Automated-Invoice-Reconciliation/src/main/resources/application.yml) & [.env.example](file:///Users/mohamed.abdelfatah/Mohamed-Ramadan/Automated-Invoice-Reconciliation/.env.example):
   - Configured `spring.mail` host (`smtp.gmail.com:587`), STARTTLS, timeout properties, and notification defaults.
3. [EmailNotificationService.java](file:///Users/mohamed.abdelfatah/Mohamed-Ramadan/Automated-Invoice-Reconciliation/src/main/java/com/agent/reconciliation/service/EmailNotificationService.java):
   - Created full HTML email generation for vendor disputes and manager sign-off receipts.
   - Implemented resilient fallback handling (`EmailDispatchResult`).
4. [InvoiceController.java](file:///Users/mohamed.abdelfatah/Mohamed-Ramadan/Automated-Invoice-Reconciliation/src/main/java/com/agent/reconciliation/controller/InvoiceController.java):
   - Injected `EmailNotificationService`.
   - Enhanced `POST /{id}/reject` and `POST /{id}/approve` to accept optional `email` and `notes` query parameters and return diagnostic response headers (`X-Email-Dispatched`, `X-Email-Recipient`, `X-Email-Simulated`).
5. [DisputeActionDrawer.tsx](file:///Users/mohamed.abdelfatah/Mohamed-Ramadan/Automated-Invoice-Reconciliation/frontend/src/components/DisputeActionDrawer.tsx):
   - Added interactive email panels allowing the user to configure vendor or manager recipients and input auditor justification notes.
   - Added live/simulated delivery confirmation banner.
6. [SplitScreenViewer.tsx](file:///Users/mohamed.abdelfatah/Mohamed-Ramadan/Automated-Invoice-Reconciliation/frontend/src/components/SplitScreenViewer.tsx):
   - Reconfigured status pill badges to use Amber for `FLAGGED_DISCREPANCY` and Crimson for `REJECTED`.
7. [frontend/src/app/page.tsx](file:///Users/mohamed.abdelfatah/Mohamed-Ramadan/Automated-Invoice-Reconciliation/frontend/src/app/page.tsx):
   - Upgraded dashboard to a 5-card metric row with distinct Amber and Crimson cards.
   - Enhanced filter tabs with active status color borders and count pills.

---

## 3. Verification & Live Testing Instructions

### Automated Backend Tests
```bash
./mvnw test
```
*Results: 18 passed, 0 failures, 0 errors.*

### Testing Endpoints via cURL
```bash
# 1. Reject Invoice and Dispatch Dispute Email to Vendor
curl -s -i -X POST "http://localhost:8080/api/invoices/4/reject?email=your_email@gmail.com"

# 2. Approve Invoice and Dispatch Sign-Off Receipt to Manager
curl -s -i -X POST "http://localhost:8080/api/invoices/4/approve?email=your_email@gmail.com&notes=Emergency%20authorization"
```

### Configuring Live Gmail Sending
To receive actual emails in your personal Gmail account:
1. Generate an App Password in your Google Account:
   - Go to **Google Account Settings** -> **Security** -> **2-Step Verification** -> **App passwords**.
   - Create a password named "Invoice Reconciliation".
2. Add the following to your [.env](file:///Users/mohamed.abdelfatah/Mohamed-Ramadan/Automated-Invoice-Reconciliation/.env) file:
   ```env
   SPRING_MAIL_HOST=smtp.gmail.com
   SPRING_MAIL_PORT=587
   SPRING_MAIL_USERNAME=your_gmail_address@gmail.com
   SPRING_MAIL_PASSWORD=your_16_digit_app_password
   NOTIFICATION_MANAGER_EMAIL=your_gmail_address@gmail.com
   NOTIFICATION_VENDOR_EMAIL=your_gmail_address@gmail.com
   ```
3. Restart the Spring Boot backend (`./mvnw spring-boot:run`).
