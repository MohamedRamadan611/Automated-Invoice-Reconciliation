---
name: financial-audit
description: Domain business logic for invoice reconciliation, fuzzy SKU mapping, deterministic tolerance checks, and financial audit logging.
triggers: ["*reconcil*", "*audit*", "*invoice*", "*po*", "*dispute*"]
---

# Financial Audit & Invoice Reconciliation Skill

## 1. Core Rule: Separation of Extraction vs. Math
- **Multimodal AI Role:**
  - Ingest raw invoice PDF/image content.
  - Extract header metadata: Invoice Number, Vendor Name, PO Reference Number, Invoiced Total, Currency.
  - Extract line items: Raw item description, billed quantity, unit price, line total.
  - Perform semantic fuzzy matching between raw supplier item descriptions (in Arabic or English) and canonical internal `sku_code` records.
  - **NEVER compute or verify math via the LLM.**
- **Deterministic Java Role:**
  - Execute 100% of mathematical checks, variance calculations, tolerance evaluations, and total aggregations in pure Java logic.

## 2. Deterministic Audit & Verification Rules
1. **Price Variance:**
   - Condition: `Math.abs(invoicedUnitPrice.subtract(agreedUnitPrice).doubleValue()) > 0.01`
   - Action: Flag discrepancy as `PRICE_MISMATCH`.
   - Record `expected_value = agreedUnitPrice`, `actual_value = invoicedUnitPrice`.
2. **Quantity Variance:**
   - Condition: `invoicedQuantity.compareTo(expectedQuantity) != 0`
   - Action: Flag discrepancy as `QUANTITY_MISMATCH`.
   - Record `expected_value = expectedQuantity`, `actual_value = invoicedQuantity`.
3. **Extra or Hidden Fees:**
   - Condition: Invoiced line item for freight, delivery, handling, or surcharge not explicitly defined in the approved Purchase Order.
   - Action: Flag as `EXTRA_FEE`.
4. **Unrecognized Items:**
   - Condition: Invoiced item cannot be matched with confidence to any line item in the corresponding PO.
   - Action: Flag as `UNRECOGNIZED_ITEM`.

## 3. Reconciliation Status State Machine
- `APPROVED`: Zero discrepancies flagged, all lines match PO within the <= 0.01 tolerance, invoiced total matches computed line sum.
- `FLAGGED_DISCREPANCY`: One or more `PRICE_MISMATCH`, `QUANTITY_MISMATCH`, or `EXTRA_FEE` issues detected. Requires procurement dispute.
- `MANUAL_REVIEW`: Unrecognized items, low fuzzy match confidence, or missing PO reference. Requires human auditor intervention.

## 4. Audit Trail & Dispute Draft Generation
- For every discrepancy detected, persist a record in `reconciliation_audits` with precise explanation.
- If discrepancies exist, compile an automated structured `dispute_draft` detailing:
  - PO Reference & Invoice Number.
  - Line-by-line mismatch table showing Agreed vs Invoiced figures and delta.
  - Formal vendor dispute letter ready for copy-paste or email dispatch.
