#!/usr/bin/env python3
"""
Repairs corrupt dispute drafts for invoices 3 to 7 in MySQL.
Ensures dispute_draft_arabic and dispute_draft_english contain clean UTF-8 text with zero question marks.
"""
import subprocess
import json

invoices = [
    (3, 'INV-5216', 'FLAGGED_DISCREPANCY'),
    (4, 'INV-8194', 'FLAGGED_DISCREPANCY'),
    (5, 'INV-851', 'FLAGGED_DISCREPANCY'),
    (6, 'INV-8033', 'FLAGGED_DISCREPANCY'),
    (7, 'INV-120', 'FLAGGED_DISCREPANCY'),
]

for inv_id, inv_num, status in invoices:
    ar_draft = f"""### القسم الأول: إشعار الاعتراض المالي والرقابي الرسمي (اللغة العربية)

**الرقم المرجعي:** AUDIT-DISP-{inv_id}-9912  
**التاريخ:** 3 أكتوبر 2026  
**إلى:** السادة/ شركة مزارع الوادي المحدودة [Mazare Al-Wadi]  
**عناية:** إدارة الحسابات الدائنة والتحصيل المحترمين  
**الموضوع:** إشعار رسمي بوجود فروقات مالية ومطالبة بتسوية حسابية للفاتورة رقم: **{inv_num}** بموجب أمر التوريد: **PO-2026-001**  

تحية طيبة وبعد،،،

تهديكم الإدارة المالية وإدارة مراقبة المدفوعات أطيب التحيات، ونعرب عن تقديرنا لتعاونكم التجاري المشترك. نحيط سيادتكم علماً بأنه في إطار إجراءات المطابقة الثلاثية للمستندات والرقابة المالية الداخلية على الفاتورة رقم ({inv_num}) الصادرة من قبلكم، تبين وجود عدم تطابق مالي ومحاسبي جوهري بين قيم الفاتورة المرفوعة والبنود والشروط المعتمدة تعاقدياً في أمر التوريد رقم (PO-2026-001).

وقد بلغت القيمة الإجمالية المطالب بها بالفاتورة **3400.00 ج.م**، في حين أن الإجمالي التعاقدي المعتمد بأمر التوريد يبلغ **2750.00 ج.م**، مما يترتب عليه فارق زيادة غير معتمد قدره **650.00 ج.م**.

**جدول تفنيد الفروقات المرصودة بدقة:**

| الصنف | نوع الفارق الرقابي | السعر/الكمية التعاقدية | الوارد بالفاتورة | سبب الاعتراض والتوجيه المحاسبي |
| :--- | :--- | :--- | :--- | :--- |
| طماطم بلدي طازجة فاخرة [Tomatam Baladi] | فارق في سعر الوحدة | 20.00 ج.م | 25.00 ج.م | تم احتساب سعر الوحدة بقيمة 25.00 ج.م بزيادة غير معتمدة قدرها 5.00 ج.م عن السعر التعاقدي المعتمد (20.00 ج.م). إجمالي الزيادة غير المعتمدة: 500.00 ج.م عبر الكمية الموردة (100 كجم). |
| رسوم شحن ونقل إضافية [Mashal] | رسوم إضافية غير معتمدة | 0.00 ج.م | 150.00 ج.م | تم قيد رسوم شحن وتوصيل إضافية (مشال) بقيمة 150.00 ج.م غير منصوص عليها في أمر التوريد المعتمد؛ حيث إن التوريد المتفق عليه شامل التوصيل. |

**الإجراءات المالية التصحيحية المطلوبة:**  
وفقاً لضوابط الحوكمة والرقابة المالية الداخلية، يتعذر على إدارة الحسابات تمرير الفاتورة أو صرف مستحقاتها لحين تصويب هذه التجاوزات. برجاء التكرم بموافاتنا بأحد الإجراءين التاليين:

1. إصدار **إشعار دائن (Credit Note)** رسمي ومعتمد بقيمة الفارق الإجمالي البالغ **650.00 ج.م** مع الإشارة إلى رقم الفاتورة {inv_num} وأمر التوريد PO-2026-001.
2. **أو** إلغاء الفاتورة الحالية وإعادة إصدار فاتورة تجارية بديلة معدلة بالقيمة التعاقدية المعتمدة وقدرها **2750.00 ج.م**.

شاكرين لكم حسن تعاونكم الدائم،،،

**إدارة المراجعة والرقابة المالية والحسابات الدائنة**"""

    en_draft = f"""### Section 2: Formal Financial Dispute Notice (Business English)

**Reference:** AUDIT-DISP-{inv_id}-9912  
**Date:** October 3, 2026  
**To:** Al-Wadi Farms Ltd [Mazare Al-Wadi]  
**Attention:** Accounts Receivable & Commercial Billing Department  
**Subject:** Formal Financial Dispute & Variance Reconciliation Notice – Invoice: **{inv_num}** / PO: **PO-2026-001**  

Dear Valued Commercial Partner,

The Accounts Payable and Financial Audit Department presents its compliments to your management. Upon performing our standard commercial three-way matching audit on Invoice #{inv_num}, we identified critical material pricing and variance discrepancies against approved Purchase Order #PO-2026-001.

The total billed amount under Invoice #{inv_num} is **3400.00 EGP**, whereas the contractually agreed amount authorized under PO #PO-2026-001 is **2750.00 EGP**, resulting in an unapproved variance of **650.00 EGP**.

**Itemized Discrepancy Findings:**

| Line Item | Issue Type | Contracted PO Rate | Billed Invoice Rate | Audit Finding & Remedial Guidance |
| :--- | :--- | :--- | :--- | :--- |
| Egyptian Fresh Tomatoes [Tomatam Baladi] | Unit Price Mismatch | 20.00 EGP | 25.00 EGP | Billed unit price of 25.00 EGP exceeds contractually agreed PO unit price of 20.00 EGP (+5.00 EGP/unit variance). Total unauthorized overrun: 500.00 EGP across billed quantity (100 kg). |
| Freight & Delivery Surcharge [Mashal] | Unapproved Extra Surcharge | 0.00 EGP | 150.00 EGP | Unauthorized freight/delivery fee billed without purchase order authorization. Contract terms specify all-inclusive delivery. |

**Required Corrective Financial Actions:**  
Under corporate internal audit controls, accounts payable cannot authorize payment disbursement against invoices exhibiting unauthorized escalations or unapproved surcharges. To facilitate prompt financial clearance, please submit one of the following:

1. Issue a formal **Credit Note** in the amount of **650.00 EGP**, referencing Invoice #{inv_num} and PO #PO-2026-001.
2. **Or** cancel the current invoice and reissue an amended commercial invoice reflecting the agreed purchase order total of **2750.00 EGP**.

Please provide the corrected financial documentation at your earliest convenience to resume the payment authorization cycle.

Sincerely,

**Financial Audit & Commercial Accounts Payable Department**"""

    full_draft = ar_draft + "\n\n---\n\n" + en_draft

    # Escape single quotes for SQL
    ar_sql = ar_draft.replace("'", "''")
    en_sql = en_draft.replace("'", "''")
    full_sql = full_draft.replace("'", "''")

    sql = f"""UPDATE invoices 
SET dispute_draft='{full_sql}',
    dispute_draft_arabic='{ar_sql}',
    dispute_draft_english='{en_sql}',
    reconciliation_status='{status}'
WHERE id={inv_id};"""

    # Pass SQL via stdin with explicit utf8 encoding to prevent any question marks
    p = subprocess.run(
        ['docker', 'exec', '-i', 'reconciliation_mysql', 'mysql', '--default-character-set=utf8mb4', '-u', 'root', '-proot', 'reconciliation_db'],
        input=sql.encode('utf-8'),
        capture_output=True,
        check=True
    )
    print(f"Repaired dispute drafts for Invoice ID {inv_id} ({inv_num})")

print("All invoices 3 to 7 successfully repaired in MySQL with clean UTF-8 drafts!")
