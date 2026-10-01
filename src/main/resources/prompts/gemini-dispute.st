Vendor Name: {vendorName}
Invoice Number: {invoiceNumber}
PO Reference: {poReference}
Invoiced Total: {invoicedTotal} EGP
PO Agreed Total: {expectedTotal} EGP

Itemized Discrepancy Findings:
{auditSummary}

Required Structure and Strict Output Invariants:
You MUST produce TWO distinct, comprehensive commercial letters wrapped in the exact machine-readable delimiter tags shown below.

1. ARABIC DISPUTE LETTER:
- Must be wrapped between `<<<ARABIC_START>>>` and `<<<ARABIC_END>>>`.
- Must be written in 100% formal Modern Standard Arabic (اللغة العربية الفصحى التجارية).
- Do NOT include any English paragraphs, English headings, or English table text inside this section.
- Cite the PO reference, Invoice number, exact figures, itemized discrepancies, and request a credit note (إشعار دائن) or revised invoice.

2. ENGLISH DISPUTE LETTER:
- Must be wrapped between `<<<ENGLISH_START>>>` and `<<<ENGLISH_END>>>`.
- Must be written in 100% formal Business English.
- Do NOT include any Arabic paragraphs, Arabic headings, or Arabic table text inside this section.
- Cite the PO reference, Invoice number, exact figures, itemized discrepancies, and state payment hold pending a credit note or amended invoice.

Output format template:

<<<ARABIC_START>>>
إشعار الاعتراض المالي والرقابي الرسمي
[Formal Arabic Letter Content Here]
<<<ARABIC_END>>>

<<<ENGLISH_START>>>
Formal Financial Dispute Notice
[Formal English Letter Content Here]
<<<ENGLISH_END>>>
