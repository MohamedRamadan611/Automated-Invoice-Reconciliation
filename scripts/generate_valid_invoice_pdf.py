#!/usr/bin/env python3
"""
Generates a 100% valid, standards-compliant PDF 1.4 invoice.
Requires zero external dependencies (pure Python).
"""
import sys
import os

def create_invoice_pdf(output_path):
    # Prepare text content stream
    # Note: Standard PDF Type 1 fonts (Helvetica) support ASCII / WinAnsiEncoding.
    # We display clean bilingual and transliterated labels.
    content_stream = """BT
/F1 20 Tf
50 740 Td
(AL-WADI COMMERCIAL FARMS) Tj
ET
BT
/F2 10 Tf
50 724 Td
(Agricultural Supplies & Logistics - Cairo / Alexandria Agri-Hub) Tj
ET
BT
/F1 14 Tf
420 740 Td
(COMMERCIAL INVOICE) Tj
ET
BT
/F2 9 Tf
420 724 Td
(Invoice No: INV-3860) Tj
420 710 Td
(Date: 2026-09-28) Tj
420 696 Td
(PO Reference: PO-2026-001) Tj
ET

% Horizontal separator line
0.75 0.75 0.75 RG
1 w
50 680 m 562 680 l S

% Billing Details
BT
/F1 10 Tf
50 660 Td
(Billed To:) Tj
ET
BT
/F2 9 Tf
50 646 Td
(National Agricultural Logistics Co.) Tj
50 634 Td
(Giza Commercial District, Egypt) Tj
50 622 Td
(Attn: Procurement & Financial Auditing) Tj
ET

BT
/F1 10 Tf
300 660 Td
(Vendor Information:) Tj
ET
BT
/F2 9 Tf
300 646 Td
(Al-Wadi Farms Ltd [Mazare Al-Wadi]) Tj
300 634 Td
(Tax ID: 492-881-209 | Commercial Reg: 81920) Tj
300 622 Td
(Payment Terms: Net 30 - Bank Transfer) Tj
ET

% Table Header Background
0.93 0.94 0.96 rg
50 580 512 24 re f

% Table Header Text
0 0 0 rg
BT
/F1 9 Tf
60 588 Td
(#) Tj
80 588 Td
(SKU / Description) Tj
320 588 Td
(Qty) Tj
380 588 Td
(Unit Price) Tj
480 588 Td
(Total (EGP)) Tj
ET

% Table Rows
% Row 1: Tomatoes
0.75 0.75 0.75 RG
0.5 w
50 556 m 562 556 l S
BT
/F2 9 Tf
60 564 Td
(1) Tj
80 564 Td
(Fresh Premium Local Tomatoes [Tomatam Baladi]) Tj
ET
BT
/F1 8 Tf
80 554 Td
0.3 0.3 0.3 rg
(SKU: SKU-TOMATO-RED | Premium Grade A) Tj
ET
0 0 0 rg
BT
/F2 9 Tf
320 564 Td
(100.00 kg) Tj
380 564 Td
(25.00 EGP) Tj
480 564 Td
(2,500.00 EGP) Tj
ET

% Row 2: Onions
50 526 m 562 526 l S
BT
/F2 9 Tf
60 534 Td
(2) Tj
80 534 Td
(Red Onions - First Grade [Basal Ahmar Daraga Oula]) Tj
ET
BT
/F1 8 Tf
80 524 Td
0.3 0.3 0.3 rg
(SKU: SKU-ONION-YELLOW | 50kg Bags) Tj
ET
0 0 0 rg
BT
/F2 9 Tf
320 534 Td
(50.00 kg) Tj
380 534 Td
(15.00 EGP) Tj
480 534 Td
(750.00 EGP) Tj
ET

% Row 3: Freight Surcharge
50 496 m 562 496 l S
BT
/F2 9 Tf
60 504 Td
(3) Tj
80 504 Td
(Express Freight & Logistics Surcharge [Mashal / Delivery Fee]) Tj
ET
BT
/F1 8 Tf
80 494 Td
0.8 0.2 0.2 rg
(SKU: SURCHARGE | Unapproved Porterage Fee) Tj
ET
0 0 0 rg
BT
/F2 9 Tf
320 504 Td
(1 order) Tj
380 504 Td
(150.00 EGP) Tj
480 504 Td
(150.00 EGP) Tj
ET

50 470 m 562 470 l S

% Totals Box (Enhanced with Net Produce Weight)
0.96 0.97 0.98 rg
330 365 232 95 re f
0.8 0.8 0.8 RG
330 365 232 95 re s

0 0 0 rg
BT
/F1 9 Tf
345 442 Td
(Total Net Weight:) Tj
470 442 Td
(150.00 kg) Tj
ET

BT
/F2 9 Tf
345 424 Td
(Subtotal Produce:) Tj
470 424 Td
(3,250.00 EGP) Tj
345 408 Td
(Freight Surcharge:) Tj
470 408 Td
(150.00 EGP) Tj
ET

0.8 0.8 0.8 RG
330 396 m 562 396 l S

0 0 0 rg
BT
/F1 11 Tf
345 376 Td
(Grand Total:) Tj
460 376 Td
(3,400.00 EGP) Tj
ET

% Footer & Notice
BT
/F2 8 Tf
50 320 Td
(Audit Notice: Invoice billed against Purchase Order PO-2026-001.) Tj
50 306 Td
(Goods inspected and accepted upon warehouse delivery receipt #REC-9912.) Tj
50 280 Td
(Al-Wadi Farms - Finance & Commercial Accounts Department) Tj
ET
"""
    # Clean whitespace
    stream_bytes = content_stream.strip().encode('latin-1')
    stream_length = len(stream_bytes)

    objects = []
    # Obj 1: Catalog
    objects.append("1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n")
    # Obj 2: Pages
    objects.append("2 0 obj\n<< /Type /Pages /Kids [3 0 R] /Count 1 >>\nendobj\n")
    # Obj 3: Page
    objects.append("3 0 obj\n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Resources << /Font << /F1 4 0 R /F2 5 0 R >> >> /Contents 6 0 R >>\nendobj\n")
    # Obj 4: Font F1 (Helvetica-Bold)
    objects.append("4 0 obj\n<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica-Bold /Encoding /WinAnsiEncoding >>\nendobj\n")
    # Obj 5: Font F2 (Helvetica)
    objects.append("5 0 obj\n<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>\nendobj\n")
    # Obj 6: Contents
    obj6_header = f"6 0 obj\n<< /Length {stream_length} >>\nstream\n"
    obj6_footer = "\nendstream\nendobj\n"

    # Assemble and calculate byte offsets for xref
    pdf_header = "%PDF-1.4\n%\xe2\xe3\xcf\xd3\n"
    
    body = bytearray()
    body.extend(pdf_header.encode('latin-1'))
    
    xref_offsets = [0] # obj 0 offset
    
    for i, obj_str in enumerate(objects):
        xref_offsets.append(len(body))
        body.extend(obj_str.encode('latin-1'))
        
    # Add Obj 6
    xref_offsets.append(len(body))
    body.extend(obj6_header.encode('latin-1'))
    body.extend(stream_bytes)
    body.extend(obj6_footer.encode('latin-1'))
    
    startxref = len(body)
    
    # Xref table
    xref_str = f"xref\n0 {len(xref_offsets)}\n"
    xref_str += "0000000000 65535 f \n"
    for offset in xref_offsets[1:]:
        xref_str += f"{offset:010d} 00000 n \n"
        
    trailer_str = f"trailer\n<< /Size {len(xref_offsets)} /Root 1 0 R >>\nstartxref\n{startxref}\n%%EOF\n"
    
    body.extend(xref_str.encode('latin-1'))
    body.extend(trailer_str.encode('latin-1'))
    
    with open(output_path, "wb") as f:
        f.write(body)
        
    print(f"Valid PDF written to: {output_path} ({len(body)} bytes)")

if __name__ == "__main__":
    out = sys.argv[1] if len(sys.argv) > 1 else "sample_invoice_mismatch.pdf"
    create_invoice_pdf(out)
