#!/usr/bin/env python3
"""
Generates a 100% valid, standards-compliant PDF 1.4 invoice.
Clean typography, beautiful borders, exact weights, prices, and totals.
Zero external dependencies (pure Python).
"""
import sys
import os

def create_invoice_pdf(output_path, invoice_number="INV-3860"):
    content_stream = f"""
% Top accent bar
0.15 0.25 0.50 rg
45 762 522 4 re f

% Company Brand / Header
BT
/F1 18 Tf
0.10 0.15 0.30 rg
45 738 Td
(AL-WADI COMMERCIAL FARMS) Tj
ET

BT
/F2 9.5 Tf
0.35 0.40 0.50 rg
45 724 Td
(Mazare Al-Wadi Agricultural Supplies & Logistics Co.) Tj
45 712 Td
(Commercial Registry: 81920 | Tax ID: 492-881-209 | Giza - Alexandria Agri-Hub) Tj
ET

% Invoice Title & Meta (Right Aligned block)
BT
/F1 15 Tf
0.10 0.15 0.35 rg
390 738 Td
(COMMERCIAL INVOICE) Tj
ET

BT
/F1 9 Tf
0.20 0.25 0.35 rg
390 722 Td
(Invoice No: {invoice_number}) Tj
ET

BT
/F2 9 Tf
0.35 0.40 0.50 rg
390 710 Td
(Date: 2026-09-28) Tj
ET

BT
/F1 9 Tf
0.10 0.30 0.65 rg
390 698 Td
(PO Reference: PO-2026-001) Tj
ET

% Horizontal separator line
0.82 0.85 0.90 RG
1 w
45 684 m 567 684 l S

% Billing Parties Panels
% Buyer Panel
BT
/F1 8.5 Tf
0.40 0.45 0.55 rg
45 668 Td
(BILLED TO (BUYER):) Tj
ET

BT
/F1 9.5 Tf
0.10 0.15 0.25 rg
45 654 Td
(National Agricultural Logistics Co.) Tj
ET

BT
/F2 8.5 Tf
0.35 0.40 0.50 rg
45 642 Td
(Procurement & Accounts Payable Auditing) Tj
45 630 Td
(Giza Commercial District, Sector 4, Egypt) Tj
ET

BT
/F1 8.5 Tf
0.10 0.30 0.65 rg
45 618 Td
(Order Ref: PO-2026-001) Tj
ET

% Vendor Panel
BT
/F1 8.5 Tf
0.40 0.45 0.55 rg
320 668 Td
(SUPPLIER / VENDOR:) Tj
ET

BT
/F1 9.5 Tf
0.10 0.15 0.25 rg
320 654 Td
(Al-Wadi Farms Ltd [Mazare Al-Wadi]) Tj
ET

BT
/F2 8.5 Tf
0.35 0.40 0.50 rg
320 642 Td
(Finance & Commercial Invoicing Department) Tj
320 630 Td
(Bank Transfer: Commercial International Bank (CIB)) Tj
320 618 Td
(Terms: Net 30 Days | dispatch@alwadifarms.com) Tj
ET

% Table Section Title
BT
/F1 10.5 Tf
0.15 0.20 0.35 rg
45 596 Td
(ALL EXTRACTED LINE ITEMS FROM DOCUMENT) Tj
ET

BT
/F2 8.5 Tf
0.45 0.50 0.60 rg
365 596 Td
(Audited Against Purchase Order PO-2026-001) Tj
ET

% Table Header Background
0.93 0.95 0.98 rg
45 566 522 22 re f

% Table Header Border
0.80 0.84 0.90 RG
0.75 w
45 566 522 22 re S

% Table Header Text
0 0 0 rg
BT
/F1 8.5 Tf
0.15 0.20 0.30 rg
54 573 Td
(#) Tj
75 573 Td
(Item & Description) Tj
255 573 Td
(SKU Code) Tj
350 573 Td
(Billed Qty / Weight) Tj
435 573 Td
(Unit Price) Tj
505 573 Td
(Line Total) Tj
ET

% Row 1: Tomatoes
BT
/F2 9 Tf
0.2 0.2 0.2 rg
54 542 Td
(1) Tj
ET

BT
/F1 9 Tf
0.1 0.15 0.25 rg
75 542 Td
(Egyptian Fresh Tomatoes [Tomatam Baladi]) Tj
ET

BT
/F2 8 Tf
0.45 0.50 0.55 rg
75 530 Td
(Fresh Grade A Harvest - Net Weight: 100.00 kg) Tj
ET

BT
/F1 8.5 Tf
0.15 0.25 0.55 rg
255 542 Td
(SKU-TOMATO-RED) Tj
ET

BT
/F2 9 Tf
0.15 0.20 0.25 rg
350 542 Td
(100.00 kg) Tj
ET

BT
/F1 9 Tf
0.75 0.15 0.15 rg
435 542 Td
(25.00 EGP) Tj
ET

BT
/F1 9 Tf
0.10 0.15 0.25 rg
505 542 Td
(2,500.00 EGP) Tj
ET

% Row 1 Separator
0.88 0.90 0.94 RG
0.5 w
45 520 m 567 520 l S

% Row 2: Onions
BT
/F2 9 Tf
0.2 0.2 0.2 rg
54 498 Td
(2) Tj
ET

BT
/F1 9 Tf
0.1 0.15 0.25 rg
75 498 Td
(Yellow Spring Onions [Basal Asfar]) Tj
ET

BT
/F2 8 Tf
0.45 0.50 0.55 rg
75 486 Td
(Standard Grade 50kg Bags - Net Weight: 50.00 kg) Tj
ET

BT
/F1 8.5 Tf
0.15 0.25 0.55 rg
255 498 Td
(SKU-ONION-YELLOW) Tj
ET

BT
/F2 9 Tf
0.15 0.20 0.25 rg
350 498 Td
(50.00 kg) Tj
ET

BT
/F2 9 Tf
0.15 0.20 0.25 rg
435 498 Td
(15.00 EGP) Tj
ET

BT
/F1 9 Tf
0.10 0.15 0.25 rg
505 498 Td
(750.00 EGP) Tj
ET

% Row 2 Separator
0.88 0.90 0.94 RG
0.5 w
45 476 m 567 476 l S

% Row 3: Freight Surcharge
BT
/F2 9 Tf
0.7 0.2 0.2 rg
54 454 Td
(3) Tj
ET

BT
/F1 9 Tf
0.70 0.15 0.15 rg
75 454 Td
(Express Freight & Logistics Surcharge [Mashal / Delivery Fee]) Tj
ET

BT
/F2 8 Tf
0.60 0.25 0.25 rg
75 442 Td
(Unapproved Delivery & Porterage Fee) Tj
ET

BT
/F1 8.5 Tf
0.70 0.15 0.15 rg
255 454 Td
(SURCHARGE) Tj
ET

BT
/F2 9 Tf
0.35 0.35 0.35 rg
350 454 Td
(1 service) Tj
ET

BT
/F1 9 Tf
0.70 0.15 0.15 rg
435 454 Td
(150.00 EGP) Tj
ET

BT
/F1 9 Tf
0.70 0.15 0.15 rg
505 454 Td
(150.00 EGP) Tj
ET

% Bottom Table Border
0.80 0.84 0.90 RG
0.75 w
45 432 m 567 432 l S

% Audit & Weight Verification Box (Left)
0.97 0.98 0.99 rg
45 320 250 96 re f
0.85 0.88 0.92 RG
0.75 w
45 320 250 96 re S

BT
/F1 8 Tf
0.20 0.25 0.35 rg
55 400 Td
(FINANCIAL AUDITING & WEIGHT RECONCILIATION) Tj
ET

BT
/F2 7.5 Tf
0.35 0.40 0.50 rg
55 384 Td
(* Billed against Purchase Order: PO-2026-001) Tj
55 372 Td
(* Net Produce Weight: 100.00 kg + 50.00 kg = 150.00 kg) Tj
55 360 Td
(* Subtotal Produce: 2,500.00 + 750.00 = 3,250.00 EGP) Tj
55 348 Td
(* Freight / Mashal Surcharge: 150.00 EGP) Tj
55 334 Td
(* Verified exact physical delivery manifest weights & rates) Tj
ET

% Totals Box (Right)
0.95 0.96 0.98 rg
310 320 257 96 re f
0.78 0.82 0.88 RG
0.75 w
310 320 257 96 re S

BT
/F1 9 Tf
0.15 0.20 0.30 rg
325 398 Td
(Total Net Produce Weight:) Tj
ET

BT
/F1 9 Tf
0.10 0.30 0.65 rg
490 398 Td
(150.00 kg) Tj
ET

BT
/F2 9 Tf
0.30 0.35 0.45 rg
325 380 Td
(Subtotal Items Amount:) Tj
ET

BT
/F2 9 Tf
0.20 0.25 0.35 rg
490 380 Td
(3,250.00 EGP) Tj
ET

BT
/F2 9 Tf
0.70 0.20 0.20 rg
325 362 Td
(Freight & Delivery [Mashal]:) Tj
ET

BT
/F1 9 Tf
0.70 0.20 0.20 rg
490 362 Td
(150.00 EGP) Tj
ET

% Divider inside Totals Box
0.80 0.84 0.90 RG
0.5 w
310 350 m 567 350 l S

BT
/F1 11 Tf
0.10 0.15 0.35 rg
325 330 Td
(Grand Total Payable:) Tj
ET

BT
/F1 12 Tf
0.10 0.15 0.35 rg
475 330 Td
(3,400.00 EGP) Tj
ET

% Footer & Notice
BT
/F2 8 Tf
0.45 0.50 0.55 rg
45 285 Td
(Warehouse Inspection Receipt: REC-9912 | Authorized for Automated Reconciliation Audit) Tj
45 273 Td
(Al-Wadi Farms - Finance & Commercial Accounts Department | Automated Invoice Reconciliation System) Tj
ET
"""
    stream_bytes = content_stream.strip().encode('latin-1')
    stream_length = len(stream_bytes)

    objects = []
    # 1: Catalog
    objects.append("1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n")
    # 2: Pages
    objects.append("2 0 obj\n<< /Type /Pages /Kids [3 0 R] /Count 1 >>\nendobj\n")
    # 3: Page
    objects.append("3 0 obj\n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Resources << /Font << /F1 4 0 R /F2 5 0 R >> >> /Contents 6 0 R >>\nendobj\n")
    # 4: Font F1 (Helvetica-Bold)
    objects.append("4 0 obj\n<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica-Bold /Encoding /WinAnsiEncoding >>\nendobj\n")
    # 5: Font F2 (Helvetica)
    objects.append("5 0 obj\n<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>\nendobj\n")
    # 6: Contents
    obj6_header = f"6 0 obj\n<< /Length {stream_length} >>\nstream\n"
    obj6_footer = "\nendstream\nendobj\n"

    pdf_header = "%PDF-1.4\n%\xe2\xe3\xcf\xd3\n"
    
    body = bytearray()
    body.extend(pdf_header.encode('latin-1'))
    
    xref_offsets = [0]
    for i, obj_str in enumerate(objects):
        xref_offsets.append(len(body))
        body.extend(obj_str.encode('latin-1'))
        
    xref_offsets.append(len(body))
    body.extend(obj6_header.encode('latin-1'))
    body.extend(stream_bytes)
    body.extend(obj6_footer.encode('latin-1'))
    
    startxref = len(body)
    xref_str = f"xref\n0 {len(xref_offsets)}\n0000000000 65535 f \n"
    for offset in xref_offsets[1:]:
        xref_str += f"{offset:010d} 00000 n \n"
        
    trailer_str = f"trailer\n<< /Size {len(xref_offsets)} /Root 1 0 R >>\nstartxref\n{startxref}\n%%EOF\n"
    body.extend(xref_str.encode('latin-1'))
    body.extend(trailer_str.encode('latin-1'))
    
    with open(output_path, "wb") as f:
        f.write(body)
        
    print(f"Generated standards-compliant PDF: {output_path} ({len(body)} bytes)")

if __name__ == "__main__":
    out = sys.argv[1] if len(sys.argv) > 1 else "sample_invoice_mismatch.pdf"
    num = sys.argv[2] if len(sys.argv) > 2 else "INV-3860"
    create_invoice_pdf(out, num)
