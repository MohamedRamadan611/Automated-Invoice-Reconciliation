"use client";

import React, { useState, useEffect, useTransition } from "react";
import { useSearchParams } from "next/navigation";
import {
  fetchPortalDispute,
  acceptPortalDeduction,
  submitPortalExplanation,
  uploadAmendedInvoice,
  ApiError,
} from "@/lib/api";
import type {
  SupplierDisputePortalResponse,
  PortalAcceptResponse,
  NegotiationAssessmentResponse,
  ReconciliationSummaryResponse,
} from "@/lib/types";
import {
  FileText,
  AlertTriangle,
  AlertCircle,
  CheckCircle2,
  XCircle,
  Download,
  Send,
  Upload,
  ArrowRight,
  ShieldCheck,
  Building2,
  Clock,
  Sparkles,
  HelpCircle,
  DollarSign,
  Receipt,
  FileCheck,
  Loader2,
  ChevronRight,
  ChevronDown,
  ChevronUp,
  Info,
} from "lucide-react";

function getReconciliationStatusBadge(status: string | undefined) {
  const norm = (status || "").toUpperCase();
  switch (norm) {
    case "APPROVED":
      return (
        <span className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full text-xs font-bold bg-emerald-500/15 text-emerald-400 border border-emerald-500/30 shadow-xs whitespace-nowrap">
          <CheckCircle2 className="w-3.5 h-3.5 text-emerald-400 shrink-0" />
          Approved for Payment
        </span>
      );
    case "REJECTED":
      return (
        <span className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full text-xs font-bold bg-rose-500/15 text-rose-400 border border-rose-500/30 shadow-xs whitespace-nowrap">
          <XCircle className="w-3.5 h-3.5 text-rose-400 shrink-0" />
          Rejected - Claimed Overcharge
        </span>
      );
    case "RESOLVED_BY_DEBIT_NOTE":
      return (
        <span className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full text-xs font-bold bg-teal-500/15 text-teal-400 border border-teal-500/30 shadow-xs whitespace-nowrap">
          <ShieldCheck className="w-3.5 h-3.5 text-teal-400 shrink-0" />
          Settled via Debit Note
        </span>
      );
    case "FLAGGED_DISCREPANCY":
      return (
        <span className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full text-xs font-bold bg-amber-500/15 text-amber-400 border border-amber-500/30 shadow-xs whitespace-nowrap">
          <AlertTriangle className="w-3.5 h-3.5 text-amber-400 shrink-0" />
          Discrepancies Flagged
        </span>
      );
    case "MANUAL_REVIEW":
      return (
        <span className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full text-xs font-bold bg-orange-500/15 text-orange-400 border border-orange-500/30 shadow-xs whitespace-nowrap">
          <Clock className="w-3.5 h-3.5 text-orange-400 shrink-0" />
          Manual Review Required
        </span>
      );
    default:
      return (
        <span className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full text-xs font-bold bg-slate-800 text-slate-300 border border-slate-700 whitespace-nowrap">
          {status ? status.replace(/_/g, " ") : "Pending Audit"}
        </span>
      );
  }
}

function parseBilingualRationale(text: string): { english: string; arabic: string } {
  if (!text) return { english: "", arabic: "" };

  const hasArabic = /[\u0600-\u06FF]/.test(text);
  const hasEnglish = /[a-zA-Z]/.test(text);

  if (!hasArabic) return { english: text.trim(), arabic: "" };
  if (!hasEnglish) return { english: "", arabic: text.trim() };

  // 1. Check common delimiters: " — ", " – ", "\n", " | ", " - "
  const separators = [" — ", " – ", "\n", " | ", " - "];
  for (const sep of separators) {
    if (text.includes(sep)) {
      const parts = text.split(sep).map((p) => p.trim()).filter(Boolean);
      const arPart = parts.find((p) => /[\u0600-\u06FF]/.test(p));
      const enPart =
        parts.find((p) => /[a-zA-Z]/.test(p) && !/[\u0600-\u06FF]/.test(p)) ||
        parts.find((p) => /[a-zA-Z]/.test(p));
      if (arPart && enPart && arPart !== enPart) {
        return { english: enPart, arabic: arPart };
      }
    }
  }

  // 2. Try sentence splitting: ". ", "! ", "? "
  const sentences = text.split(/(?<=[.!?])\s+/);
  if (sentences.length > 1) {
    const enParts: string[] = [];
    const arParts: string[] = [];
    for (const s of sentences) {
      if (/[\u0600-\u06FF]/.test(s)) {
        arParts.push(s.trim());
      } else if (/[a-zA-Z]/.test(s)) {
        enParts.push(s.trim());
      }
    }
    if (enParts.length > 0 && arParts.length > 0) {
      return { english: enParts.join(" "), arabic: arParts.join(" ") };
    }
  }

  // 3. If English comes first, find first Arabic char
  const firstArabicIdx = text.search(/[\u0600-\u06FF]/);
  if (firstArabicIdx > 0) {
    const en = text.substring(0, firstArabicIdx).trim().replace(/[—–-]\s*$/, "").trim();
    const ar = text.substring(firstArabicIdx).trim();
    if (en && ar) {
      return { english: en, arabic: ar };
    }
  }

  // 4. If Arabic comes first, find where Arabic ends and English begins
  let lastArabicIdx = -1;
  for (let i = text.length - 1; i >= 0; i--) {
    if (/[\u0600-\u06FF]/.test(text[i])) {
      lastArabicIdx = i;
      break;
    }
  }
  if (lastArabicIdx > 0 && lastArabicIdx < text.length - 3) {
    const ar = text.substring(0, lastArabicIdx + 1).trim();
    const en = text.substring(lastArabicIdx + 1).trim().replace(/^[—–-]\s*/, "").trim();
    if (ar && en && /[a-zA-Z]/.test(en)) {
      return { english: en, arabic: ar };
    }
  }

  return { english: text.trim(), arabic: "" };
}

function getFallbackArabicRationale(
  issueType: string | undefined,
  itemDescription?: string,
  expected?: number | string | null,
  actual?: number | string | null,
  deduction?: number | string | null
): string {
  const norm = (issueType || "").toUpperCase();
  const desc = itemDescription || "الصنف المحدد";
  const ded = deduction != null && Number(deduction) > 0 ? Number(deduction).toLocaleString() : "";
  const exp = expected != null ? Number(expected).toLocaleString() : "0.00";
  const act = actual != null ? Number(actual).toLocaleString() : "0.00";

  switch (norm) {
    case "PRICE_MISMATCH":
      return `سعر الوحدة المفوتر (${act} ج.م) لصنف (${desc}) يتجاوز السعر المتفق عليه بأمر التوريد (${exp} ج.م)${ded ? `؛ تم احتساب خصم فرق سعر بقيمة ${ded} ج.م.` : "."}`;
    case "QUANTITY_MISMATCH":
      return `الكمية الموردة بالفاتورة (${act}) لصنف (${desc}) تتجاوز الكمية المعتمدة بأمر التوريد (${exp})${ded ? `؛ تم استبعاد الزيادة بقيمة ${ded} ج.م.` : "."}`;
    case "EXTRA_FEE":
      return `تمت إضافة مصاريف شحن أو رسوم إضافية غير معتمدة بأمر التوريد${ded ? ` بقيمة ${ded} ج.م؛ تم خصمها بالكامل لمطابقة الشروط.` : "."}`;
    case "UNRECOGNIZED_ITEM":
      return `الصنف (${desc}) غير مدرج ضمن بنود أمر التوريد المعتمد${ded ? `؛ تم استبعاد كامل القيمة (${ded} ج.م) من مخصصات الصرف.` : "."}`;
    case "PO_NOT_FOUND":
      return `رقم أمر التوريد المرفق بالفاتورة غير مسجل بقاعدة البيانات المعتمدة؛ يلزم مراجعة شروط التعاقد.`;
    default:
      return `تم رصد فارق تجاري غير مطابق لبنود وشروط أمر التوريد المعتمد${ded ? ` بقيمة خصم ${ded} ج.م.` : "."}`;
  }
}

function getFallbackEnglishRationale(
  issueType: string | undefined,
  itemDescription?: string,
  expected?: number | string | null,
  actual?: number | string | null,
  deduction?: number | string | null
): string {
  const norm = (issueType || "").toUpperCase();
  const desc = itemDescription || "Specified Line Item";
  const ded = deduction != null && Number(deduction) > 0 ? Number(deduction).toLocaleString() : "";
  const exp = expected != null ? Number(expected).toLocaleString() : "0.00";
  const act = actual != null ? Number(actual).toLocaleString() : "0.00";

  switch (norm) {
    case "PRICE_MISMATCH":
      return `Invoiced unit price (${act} EGP) for '${desc}' exceeds agreed PO rate (${exp} EGP)${ded ? `. Commercial variance deduction of ${ded} EGP applied.` : "."}`;
    case "QUANTITY_MISMATCH":
      return `Invoiced quantity (${act}) for '${desc}' exceeds approved PO quantity (${exp})${ded ? `. Quantity variance deduction of ${ded} EGP applied.` : "."}`;
    case "EXTRA_FEE":
      return `Unauthorized logistics or surcharge fee billed on invoice without PO authorization${ded ? ` (${ded} EGP deducted).` : "."}`;
    case "UNRECOGNIZED_ITEM":
      return `Line item '${desc}' does not match any approved product line on Purchase Order${ded ? `. Entire amount of ${ded} EGP deducted.` : "."}`;
    case "PO_NOT_FOUND":
      return `Referenced Purchase Order was missing or not found in procurement database.`;
    default:
      return `Commercial variance flagged against Purchase Order terms${ded ? ` with deduction of ${ded} EGP.` : "."}`;
  }
}

function renderAuditRationale(
  explanation: string | undefined,
  issueType?: string,
  itemDescription?: string,
  expected?: number | string | null,
  actual?: number | string | null,
  deduction?: number | string | null
) {
  let { english, arabic } = parseBilingualRationale(explanation || "");

  // If arabic is missing, synthesize it so the Arabic card is NEVER absent on any screen
  if (!arabic) {
    arabic = getFallbackArabicRationale(issueType, itemDescription, expected, actual, deduction);
  }

  // If english is missing, synthesize it so the English card is NEVER absent
  if (!english) {
    english = getFallbackEnglishRationale(issueType, itemDescription, expected, actual, deduction);
  }

  return (
    <div className="grid grid-cols-1 md:grid-cols-2 gap-3 py-1">
      <div className="p-3.5 rounded-xl bg-slate-900/90 border border-slate-800 space-y-1">
        <div className="flex items-center gap-1.5 text-[10px] font-bold uppercase tracking-wider text-indigo-400">
          <Sparkles className="w-3.5 h-3.5 shrink-0" />
          <span>Audit Findings (English)</span>
        </div>
        <p dir="ltr" className="text-left text-slate-200 text-xs leading-relaxed font-sans">
          {english}
        </p>
      </div>

      <div className="p-3.5 rounded-xl bg-emerald-950/20 border border-emerald-500/25 space-y-1">
        <div className="flex items-center justify-end gap-1.5 text-[10px] font-bold uppercase tracking-wider text-emerald-400" dir="rtl">
          <ShieldCheck className="w-3.5 h-3.5 shrink-0" />
          <span>تقرير المبرر المالي والمطابقة (عربي)</span>
        </div>
        <p dir="rtl" className="text-right text-emerald-300 text-xs leading-relaxed font-sans">
          {arabic}
        </p>
      </div>
    </div>
  );
}

function getVarianceTypeBadge(type: string | undefined) {
  const normalized = (type || "").toUpperCase();
  switch (normalized) {
    case "PRICE_MISMATCH":
      return (
        <span className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-md text-[11px] font-semibold bg-rose-500/10 text-rose-300 border border-rose-500/30">
          <span className="w-1.5 h-1.5 rounded-full bg-rose-400" />
          Price Variance
        </span>
      );
    case "QUANTITY_MISMATCH":
      return (
        <span className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-md text-[11px] font-semibold bg-amber-500/10 text-amber-300 border border-amber-500/30">
          <span className="w-1.5 h-1.5 rounded-full bg-amber-400" />
          Quantity Variance
        </span>
      );
    case "EXTRA_FEE":
      return (
        <span className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-md text-[11px] font-semibold bg-purple-500/10 text-purple-300 border border-purple-500/30">
          <span className="w-1.5 h-1.5 rounded-full bg-purple-400" />
          Unapproved Surcharge
        </span>
      );
    case "UNRECOGNIZED_ITEM":
      return (
        <span className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-md text-[11px] font-semibold bg-rose-500/15 text-rose-200 border border-rose-500/30">
          <AlertCircle className="w-3.5 h-3.5 text-rose-400 shrink-0" />
          Unrecognized Item (Not on PO)
        </span>
      );
    case "PO_NOT_FOUND":
      return (
        <span className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-md text-[11px] font-semibold bg-rose-500/15 text-rose-300 border border-rose-500/30">
          <AlertTriangle className="w-3.5 h-3.5 text-rose-400 shrink-0" />
          Missing PO Reference
        </span>
      );
    default:
      return (
        <span className="inline-flex items-center gap-1 px-2.5 py-1 rounded-md text-[11px] font-medium bg-slate-800 text-slate-300 border border-slate-700">
          {type ? type.replace(/_/g, " ") : "Discrepancy"}
        </span>
      );
  }
}

export default function SupplierPortalClient() {
  const searchParams = useSearchParams();
  const token = searchParams.get("token") || "";

  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [data, setData] = useState<SupplierDisputePortalResponse | null>(null);

  // Tabs: 'accept' | 'explain' | 'amended'
  const [activeTab, setActiveTab] = useState<"accept" | "explain" | "amended">("accept");

  // Accept action state
  const [accepting, setAccepting] = useState(false);
  const [acceptSuccess, setAcceptSuccess] = useState<PortalAcceptResponse | null>(null);

  // Explanation state
  const [explanationText, setExplanationText] = useState("");
  const [evaluating, setEvaluating] = useState(false);
  const [assessment, setAssessment] = useState<NegotiationAssessmentResponse | null>(null);

  // Amended invoice state
  const [amendedFile, setAmendedFile] = useState<File | null>(null);
  const [uploading, setUploading] = useState(false);
  const [amendedResult, setAmendedResult] = useState<ReconciliationSummaryResponse | null>(null);

  // Master-detail expanded state for discrepancies audit rationale
  const [expandedRows, setExpandedRows] = useState<Record<string | number, boolean>>({});

  const isRowExpanded = (id: string | number) => {
    return expandedRows[id] !== false; // Default expanded for rich visibility
  };

  const toggleRow = (id: string | number) => {
    setExpandedRows((prev) => ({
      ...prev,
      [id]: !isRowExpanded(id),
    }));
  };

  const allExpanded = data?.discrepancies?.every((item, idx) => isRowExpanded(item.id ?? idx)) ?? true;

  const toggleAllRows = () => {
    if (!data?.discrepancies) return;
    const nextState = !allExpanded;
    const updated: Record<string | number, boolean> = {};
    data.discrepancies.forEach((item, idx) => {
      updated[item.id ?? idx] = nextState;
    });
    setExpandedRows(updated);
  };

  useEffect(() => {
    if (!token) {
      setError("No access token provided. Please open the link sent to your registered supplier email.");
      setLoading(false);
      return;
    }

    async function loadDispute() {
      try {
        setLoading(true);
        setError(null);
        const dispute = await fetchPortalDispute(token);
        setData(dispute);
      } catch (err) {
        if (err instanceof ApiError) {
          setError(err.message || "Failed to load supplier dispute portal.");
        } else {
          setError("Network or server connection error. Please try again shortly.");
        }
      } finally {
        setLoading(false);
      }
    }

    loadDispute();
  }, [token]);

  // Handler: Accept Deduction
  const handleAcceptDeduction = async () => {
    if (!token) return;
    try {
      setAccepting(true);
      setError(null);
      const res = await acceptPortalDeduction(token);
      setAcceptSuccess(res);
      // Reload dispute details
      const refreshed = await fetchPortalDispute(token);
      setData(refreshed);
    } catch (err) {
      if (err instanceof ApiError) {
        setError(err.message);
      } else {
        setError("Failed to accept deduction. Please try again.");
      }
    } finally {
      setAccepting(false);
    }
  };

  // Handler: Submit Counter-Explanation
  const handleSubmitExplanation = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!token || !explanationText.trim()) return;
    try {
      setEvaluating(true);
      setError(null);
      const res = await submitPortalExplanation(token, explanationText.trim());
      setAssessment(res);
    } catch (err) {
      if (err instanceof ApiError) {
        setError(err.message);
      } else {
        setError("Failed to evaluate explanation. Please try again.");
      }
    } finally {
      setEvaluating(false);
    }
  };

  // Handler: Upload Amended Invoice
  const handleUploadAmended = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!token || !amendedFile) return;
    try {
      setUploading(true);
      setError(null);
      const res = await uploadAmendedInvoice(token, amendedFile);
      setAmendedResult(res);
      // Refresh dispute state
      const refreshed = await fetchPortalDispute(token);
      setData(refreshed);
    } catch (err) {
      if (err instanceof ApiError) {
        setError(err.message);
      } else {
        setError("Failed to upload amended invoice. Please ensure valid PDF or image file.");
      }
    } finally {
      setUploading(false);
    }
  };

  if (loading) {
    return (
      <div className="max-w-4xl mx-auto py-20 flex flex-col items-center justify-center space-y-4">
        <Loader2 className="w-10 h-10 animate-spin text-emerald-400" />
        <h3 className="text-lg font-semibold text-slate-200">Verifying Security Credentials...</h3>
        <p className="text-sm text-slate-400">Authenticating HMAC-SHA256 magic token for supplier session.</p>
      </div>
    );
  }

  if (error && !data) {
    return (
      <div className="max-w-xl mx-auto py-16 px-6 bg-slate-900/90 border border-rose-500/30 rounded-2xl shadow-2xl backdrop-blur-md text-center space-y-6">
        <div className="w-14 h-14 mx-auto rounded-full bg-rose-500/10 border border-rose-500/30 flex items-center justify-center text-rose-400">
          <AlertTriangle className="w-7 h-7" />
        </div>
        <div>
          <h2 className="text-xl font-bold text-slate-100">Portal Access Denied</h2>
          <p className="text-sm text-rose-300 mt-2">{error}</p>
        </div>
        <div className="p-4 bg-slate-950/60 rounded-xl border border-slate-800 text-xs text-slate-400 text-left space-y-2">
          <p className="font-semibold text-slate-300">Security Troubleshooting:</p>
          <ul className="list-disc list-inside space-y-1">
            <li>Tokens remain valid for 7 days from dispute issuance.</li>
            <li>Ensure the full URL from the notification email was copied.</li>
            <li>Contact Accounts Payable if a new dispute settlement link is required.</li>
          </ul>
        </div>
      </div>
    );
  }

  if (!data) return null;

  const currentStatus = data.reconciliationStatus || data.status;

  const isResolved =
    data.isResolved ||
    currentStatus === "RESOLVED_BY_DEBIT_NOTE" ||
    currentStatus === "APPROVED";

  const isRejected = currentStatus === "REJECTED";

  const totalDeduction = Number(
    data.totalDeductionAmount ??
    data.totalDeduction ??
    (data.discrepancies?.reduce((acc, curr) => acc + (Number(curr.deductionAmount) || 0), 0) ?? 0)
  );

  const invoicedTotal = Number(data.invoicedTotal ?? 0);

  const netPayable = Number(
    data.netPayableAmount ?? Math.max(0, invoicedTotal - totalDeduction)
  );

  return (
    <div className="max-w-5xl mx-auto space-y-8 pb-16">
      {/* Top Header Bar */}
      <div className="flex flex-col md:flex-row md:items-center md:justify-between gap-5 border-b border-slate-800/80 pb-6">
        <div>
          <div className="flex items-center gap-2.5">
            <span className="px-2.5 py-0.5 rounded-full text-[11px] font-semibold tracking-wider uppercase bg-emerald-500/10 text-emerald-400 border border-emerald-500/20 flex items-center gap-1.5">
              <ShieldCheck className="w-3.5 h-3.5" />
              Secure Supplier Portal
            </span>
            <span className="text-xs text-slate-500">HMAC-SHA256 Encrypted</span>
          </div>
          <h1 className="text-2xl sm:text-3xl font-extrabold text-slate-100 mt-2">
            Supplier Dispute Settlement Portal
          </h1>
          <div className="mt-1 flex items-center justify-start">
            <p className="text-xs sm:text-sm text-slate-400 font-sans" dir="rtl">
              بوابة تسوية ومطابقة منازعات الفواتير وإشعار الخصم التجاري
            </p>
          </div>
        </div>

        {/* Vendor & Invoice Badge with Explicit Reconciliation Status */}
        <div className="flex flex-col sm:flex-row sm:items-center gap-4 bg-slate-900/90 border border-slate-800 rounded-2xl px-5 py-3.5 shadow-md shrink-0">
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-xl bg-indigo-500/10 border border-indigo-500/20 flex items-center justify-center text-indigo-400 shrink-0">
              <Building2 className="w-5 h-5" />
            </div>
            <div>
              <div className="text-xs font-bold text-slate-100">{data.vendorName}</div>
              <div className="text-[11px] text-slate-400 flex items-center gap-1.5 mt-0.5">
                <span>Inv: <span className="font-mono text-slate-300 font-medium">{data.invoiceNumber}</span></span>
                <span className="text-slate-600">•</span>
                <span>PO: <span className="font-mono text-slate-300 font-medium">{data.poReference}</span></span>
              </div>
            </div>
          </div>

          <div className="sm:border-l sm:border-slate-800 sm:pl-4 flex flex-col items-start justify-center">
            <span className="text-[10px] text-slate-400 font-semibold uppercase tracking-wider">
              Reconciliation Status
            </span>
            <div className="mt-1">
              {getReconciliationStatusBadge(currentStatus)}
            </div>
          </div>
        </div>
      </div>

      {/* Global Alert if Error */}
      {error && (
        <div className="p-4 rounded-xl bg-rose-500/10 border border-rose-500/30 flex items-start gap-3 text-rose-300 text-sm">
          <AlertTriangle className="w-5 h-5 text-rose-400 shrink-0 mt-0.5" />
          <div>
            <p className="font-semibold text-rose-200">Action Notice</p>
            <p className="text-xs mt-0.5">{error}</p>
          </div>
        </div>
      )}

      {/* Financial Settlement Overview Cards */}
      <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
        {/* Invoiced Total */}
        <div className="p-5 rounded-2xl bg-slate-900/60 border border-slate-800/80 relative overflow-hidden">
          <div className="flex items-center justify-between text-slate-400 text-xs font-medium">
            <span>Original Invoiced Total</span>
            <Receipt className="w-4 h-4 text-slate-500" />
          </div>
          <div className="mt-3 text-2xl font-bold font-mono text-slate-100">
            {invoicedTotal.toLocaleString(undefined, {
              minimumFractionDigits: 2,
              maximumFractionDigits: 2,
            })}{" "}
            <span className="text-xs font-normal text-slate-400">EGP</span>
          </div>
          <div className="mt-1 text-[11px] text-slate-500">Gross claimed amount on submitted invoice</div>
        </div>

        {/* Audit Deductions */}
        <div className="p-5 rounded-2xl bg-rose-950/20 border border-rose-500/30 relative overflow-hidden">
          <div className="flex items-center justify-between text-rose-300 text-xs font-medium">
            <span>Commercial Deductions</span>
            <AlertTriangle className="w-4 h-4 text-rose-400" />
          </div>
          <div className="mt-3 text-2xl font-bold font-mono text-rose-400">
            -
            {totalDeduction.toLocaleString(undefined, {
              minimumFractionDigits: 2,
              maximumFractionDigits: 2,
            })}{" "}
            <span className="text-xs font-normal text-rose-300/80">EGP</span>
          </div>
          <div className="mt-1 text-[11px] text-rose-300/70">
            {data.discrepancies.length} discrepancy variance(s) flagged by audit
          </div>
        </div>

        {/* Net Approved Balance */}
        <div className="p-5 rounded-2xl bg-emerald-950/25 border border-emerald-500/40 relative overflow-hidden">
          <div className="flex items-center justify-between text-emerald-300 text-xs font-medium">
            <span>Net Approved for Disbursement</span>
            <CheckCircle2 className="w-4 h-4 text-emerald-400" />
          </div>
          <div className="mt-3 text-2xl sm:text-3xl font-extrabold font-mono text-emerald-400">
            {netPayable.toLocaleString(undefined, {
              minimumFractionDigits: 2,
              maximumFractionDigits: 2,
            })}{" "}
            <span className="text-xs font-normal text-emerald-300/80">EGP</span>
          </div>
          <div className="mt-1 text-[11px] text-emerald-400/80 flex items-center gap-1">
            <span className="w-1.5 h-1.5 rounded-full bg-emerald-400 animate-pulse" />
            Approved PO funds released immediately upon settlement
          </div>
        </div>
      </div>

      {/* If Rejected: Show Rejection Debit Note Banner */}
      {isRejected && (
        <div className="p-6 rounded-2xl bg-gradient-to-r from-rose-950/40 via-slate-900 to-rose-950/40 border border-rose-500/40 shadow-xl space-y-4">
          <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
            <div className="flex items-start gap-3.5">
              <div className="w-10 h-10 rounded-xl bg-rose-500/10 border border-rose-500/30 flex items-center justify-center text-rose-400 shrink-0">
                <XCircle className="w-6 h-6" />
              </div>
              <div>
                <div className="flex items-center gap-2">
                  <h3 className="text-base font-bold text-slate-100">
                    Invoice Rejected — Commercial Debit Note Issued
                  </h3>
                  <span className="px-2 py-0.5 text-[10px] font-bold rounded-full bg-rose-500/20 text-rose-300 border border-rose-500/30">
                    REJECTED
                  </span>
                </div>
                <p className="text-xs text-slate-300 mt-1">
                  This invoice was rejected due to audit discrepancies against Purchase Order terms. An official commercial Debit Note of{" "}
                  <span className="font-mono font-bold text-rose-400">
                    {totalDeduction.toLocaleString()} EGP
                  </span>{" "}
                  has been generated to balance the ledger.
                </p>
              </div>
            </div>

            <a
              href={data.debitNoteDownloadUrl || `/api/invoices/${data.invoiceId}/debit-note`}
              target="_blank"
              rel="noreferrer"
              className="inline-flex items-center gap-2 px-5 py-2.5 rounded-xl bg-rose-600 hover:bg-rose-500 text-white font-bold text-xs tracking-wide shadow-lg shadow-rose-600/20 transition-all shrink-0 cursor-pointer"
            >
              <Download className="w-4 h-4" />
              Download Debit Note PDF
            </a>
          </div>
        </div>
      )}

      {/* If Already Resolved: Show Debit Note Banner */}
      {isResolved && !isRejected && (
        <div className="p-6 rounded-2xl bg-gradient-to-r from-emerald-950/40 via-slate-900 to-emerald-950/40 border border-emerald-500/40 shadow-xl space-y-4">
          <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
            <div className="flex items-start gap-3.5">
              <div className="w-10 h-10 rounded-xl bg-emerald-500/10 border border-emerald-500/30 flex items-center justify-center text-emerald-400 shrink-0">
                <FileCheck className="w-6 h-6" />
              </div>
              <div>
                <h3 className="text-base font-bold text-slate-100">
                  Dispute Finalized — Commercial Debit Note Issued
                </h3>
                <p className="text-xs text-slate-300 mt-0.5">
                  The net payable balance of{" "}
                  <span className="font-mono font-bold text-emerald-400">
                    {netPayable.toLocaleString()} EGP
                  </span>{" "}
                  has been certified for release in accordance with the official Debit Note deduction.
                </p>
              </div>
            </div>

            <a
              href={data.debitNoteDownloadUrl || `/api/invoices/${data.invoiceId}/debit-note`}
              target="_blank"
              rel="noreferrer"
              className="inline-flex items-center gap-2 px-5 py-2.5 rounded-xl bg-emerald-500 hover:bg-emerald-400 text-slate-950 font-bold text-xs tracking-wide shadow-lg shadow-emerald-500/20 transition-all shrink-0 cursor-pointer"
            >
              <Download className="w-4 h-4" />
              Download Debit Note PDF
            </a>
          </div>
        </div>
      )}

      {/* Itemized Discrepancies Audit Table */}
      <div className="bg-slate-900/60 border border-slate-800/80 rounded-2xl overflow-hidden shadow-lg">
        <div className="px-6 py-4 border-b border-slate-800/80 flex flex-col sm:flex-row sm:items-center justify-between gap-3">
          <div className="flex items-center gap-2">
            <AlertTriangle className="w-4 h-4 text-amber-400" />
            <h2 className="text-sm font-bold text-slate-200">
              Audit Variance Itemization ({data.discrepancies.length} Line Items)
            </h2>
          </div>
          <div className="flex items-center gap-3">
            {data.discrepancies.length > 0 && (
              <button
                type="button"
                onClick={toggleAllRows}
                className="text-[11px] font-semibold text-slate-400 hover:text-slate-200 transition-colors cursor-pointer"
              >
                {allExpanded ? "Collapse All Notes" : "Expand All Notes"}
              </button>
            )}
            <span className="text-slate-700 hidden sm:inline">|</span>
            <span className="text-xs text-slate-500 font-mono">PO Agreed Terms Baseline</span>
          </div>
        </div>

        <div className="overflow-x-auto">
          <table className="w-full text-left text-xs table-fixed">
            <colgroup>
              <col className="w-[28%]" />
              <col className="w-[18%]" />
              <col className="w-[13%]" />
              <col className="w-[13%]" />
              <col className="w-[14%]" />
              <col className="w-[14%]" />
            </colgroup>
            <thead className="bg-slate-950/60 text-slate-400 border-b border-slate-800 uppercase tracking-wider font-semibold">
              <tr>
                <th className="py-3 pl-6 pr-4 w-[28%]">SKU / Item</th>
                <th className="py-3 px-4 w-[18%]">Variance Type</th>
                <th className="py-3 px-4 text-right w-[13%]">PO Agreed</th>
                <th className="py-3 px-4 text-right w-[13%]">Billed Invoiced</th>
                <th className="py-3 px-4 text-right w-[14%]">Deduction (EGP)</th>
                <th className="py-3 pl-4 pr-6 sm:pr-8 text-right w-[14%]">Audit Rationale</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-800/60">
              {data.discrepancies.length === 0 ? (
                <tr>
                  <td colSpan={6} className="py-12 text-center text-slate-400">
                    <CheckCircle2 className="w-8 h-8 text-emerald-400 mx-auto mb-2" />
                    <p className="font-semibold text-slate-200 text-sm">Zero Audit Discrepancies</p>
                    <p className="text-xs text-slate-400 mt-1">
                      All line items on this invoice perfectly matched Purchase Order terms and agreed rates.
                    </p>
                  </td>
                </tr>
              ) : (
                data.discrepancies.map((item, idx) => {
                  const itemKey = item.id ?? idx;
                  const isExpanded = isRowExpanded(itemKey);
                  const expected = item.expectedValue ?? item.agreedValue;
                  const actual = item.actualValue ?? item.invoicedValue;
                  const explanation = item.explanation ?? item.reason;
                  const deduction = Number(item.deductionAmount || 0);

                  return (
                    <React.Fragment key={`fragment-${itemKey}`}>
                      <tr className="hover:bg-slate-800/30 transition-colors">
                        <td className="py-3.5 pl-6 pr-4 overflow-hidden">
                          <div className="font-semibold text-slate-200 truncate">{item.itemDescription || "Line Item"}</div>
                          <div className="font-mono text-[11px] text-slate-500 truncate">{item.skuCode || "N/A"}</div>
                        </td>
                        <td className="py-3.5 px-4">
                          {getVarianceTypeBadge(item.issueType)}
                        </td>
                        <td className="py-3.5 px-4 text-right font-mono text-slate-300">
                          {expected != null ? Number(expected).toLocaleString() : "-"}
                        </td>
                        <td className="py-3.5 px-4 text-right font-mono text-rose-300 font-medium">
                          {actual != null ? Number(actual).toLocaleString() : "-"}
                        </td>
                        <td className="py-3.5 px-4 text-right font-mono font-bold text-rose-400">
                          -
                          {deduction.toLocaleString(undefined, {
                            minimumFractionDigits: 2,
                            maximumFractionDigits: 2,
                          })}
                        </td>
                        <td className="py-3.5 pl-4 pr-6 sm:pr-8 text-right">
                          <button
                            type="button"
                            onClick={() => toggleRow(itemKey)}
                            className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-lg text-[11px] font-semibold bg-slate-800 hover:bg-slate-700 text-slate-300 hover:text-slate-100 border border-slate-700 transition-all cursor-pointer whitespace-nowrap"
                          >
                            <Info className="w-3.5 h-3.5 text-indigo-400 shrink-0" />
                            <span>{isExpanded ? "Hide Note" : "View Note"}</span>
                            {isExpanded ? (
                              <ChevronUp className="w-3.5 h-3.5 text-slate-400 shrink-0" />
                            ) : (
                              <ChevronDown className="w-3.5 h-3.5 text-slate-400 shrink-0" />
                            )}
                          </button>
                        </td>
                      </tr>
                      {isExpanded && (
                        <tr className="bg-slate-950/40">
                          <td colSpan={6} className="px-6 py-3 border-b border-slate-800/80">
                            {renderAuditRationale(
                              explanation,
                              item.issueType,
                              item.itemDescription,
                              expected,
                              actual,
                              deduction
                            )}
                          </td>
                        </tr>
                      )}
                    </React.Fragment>
                  );
                })
              )}
            </tbody>
          </table>
        </div>
      </div>

      {/* Resolution Actions Section (Available until dispute is resolved) */}
      {!isResolved && (
        <div className="bg-slate-900/80 border border-slate-800 rounded-2xl overflow-hidden shadow-2xl">
          {/* Action Tabs Header */}
          <div className="flex border-b border-slate-800 bg-slate-950/40 p-1.5 gap-2">
            <button
              onClick={() => setActiveTab("accept")}
              className={`flex-1 py-3 px-4 rounded-xl text-xs sm:text-sm font-semibold transition-all flex items-center justify-center gap-2 ${
                activeTab === "accept"
                  ? "bg-emerald-500/15 text-emerald-400 border border-emerald-500/30 shadow"
                  : "text-slate-400 hover:text-slate-200 hover:bg-slate-900"
              }`}
            >
              <CheckCircle2 className="w-4 h-4" />
              <span>Option 1: Accept Deductions & Release Payment</span>
            </button>

            <button
              onClick={() => setActiveTab("explain")}
              className={`flex-1 py-3 px-4 rounded-xl text-xs sm:text-sm font-semibold transition-all flex items-center justify-center gap-2 ${
                activeTab === "explain"
                  ? "bg-indigo-500/15 text-indigo-400 border border-indigo-500/30 shadow"
                  : "text-slate-400 hover:text-slate-200 hover:bg-slate-900"
              }`}
            >
              <Sparkles className="w-4 h-4" />
              <span>Option 2: Counter-Explanation (AI Negotiator)</span>
            </button>

            <button
              onClick={() => setActiveTab("amended")}
              className={`flex-1 py-3 px-4 rounded-xl text-xs sm:text-sm font-semibold transition-all flex items-center justify-center gap-2 ${
                activeTab === "amended"
                  ? "bg-amber-500/15 text-amber-400 border border-amber-500/30 shadow"
                  : "text-slate-400 hover:text-slate-200 hover:bg-slate-900"
              }`}
            >
              <Upload className="w-4 h-4" />
              <span>Option 3: Upload Amended Invoice</span>
            </button>
          </div>

          <div className="p-6">
            {/* TAB 1: Accept Deduction */}
            {activeTab === "accept" && (
              <div className="space-y-6">
                <div>
                  <h3 className="text-base font-bold text-slate-100">
                    Accept Line-Item Deductions & Release Net Approved Balance
                  </h3>
                  <p className="text-xs text-slate-400 mt-1">
                    By confirming acceptance, you agree to the deduction of{" "}
                    <span className="font-mono font-bold text-rose-400">
                      {totalDeduction.toLocaleString(undefined, {
                        minimumFractionDigits: 2,
                        maximumFractionDigits: 2,
                      })}{" "}
                      EGP
                    </span>
                    . An official commercial Debit Note will be stamped instantly, and the net payment of{" "}
                    <span className="font-mono font-bold text-emerald-400">
                      {netPayable.toLocaleString(undefined, {
                        minimumFractionDigits: 2,
                        maximumFractionDigits: 2,
                      })}{" "}
                      EGP
                    </span>{" "}
                    will be released.
                  </p>
                </div>

                <div className="p-4 rounded-xl bg-slate-950/60 border border-slate-800 text-xs text-slate-300 space-y-2">
                  <div className="font-semibold text-slate-200 flex items-center gap-1.5">
                    <ShieldCheck className="w-4 h-4 text-emerald-400" />
                    Commercial Settlement Guarantee:
                  </div>
                  <ul className="list-disc list-inside space-y-1 text-slate-400">
                    <li>Deductions apply exclusively to the specific line variances audited above.</li>
                    <li>Official Debit Note PDF is generated immediately for supplier accounting credit.</li>
                    <li>Payment release notification is routed directly to Accounts Payable for disbursement.</li>
                  </ul>
                </div>

                <div className="pt-2">
                  <button
                    onClick={handleAcceptDeduction}
                    disabled={accepting}
                    className="w-full sm:w-auto px-8 py-3.5 rounded-xl bg-emerald-500 hover:bg-emerald-400 disabled:opacity-50 text-slate-950 font-bold text-sm tracking-wide shadow-lg shadow-emerald-500/20 flex items-center justify-center gap-2 transition-all cursor-pointer"
                  >
                    {accepting ? (
                      <>
                        <Loader2 className="w-4 h-4 animate-spin" />
                        Generating Debit Note & Finalizing Settlement...
                      </>
                    ) : (
                      <>
                        <CheckCircle2 className="w-4 h-4" />
                        Accept Deductions & Download Debit Note ({netPayable.toLocaleString()} EGP Net)
                      </>
                    )}
                  </button>
                </div>
              </div>
            )}

            {/* TAB 2: Counter-Explanation with Gemini AI */}
            {activeTab === "explain" && (
              <div className="space-y-6">
                <div>
                  <h3 className="text-base font-bold text-slate-100 flex items-center gap-2">
                    <Sparkles className="w-5 h-5 text-indigo-400" />
                    Submit Commercial Counter-Explanation for AI Assessment
                  </h3>
                  <p className="text-xs text-slate-400 mt-1">
                    If this variance was pre-authorized, caused by market force majeure, or verbally agreed with
                    procurement, describe the justification below. Gemini 3.8 Flash will evaluate your submission
                    against commercial fairness standards.
                  </p>
                </div>

                <form onSubmit={handleSubmitExplanation} className="space-y-4">
                  <div>
                    <label className="block text-xs font-semibold text-slate-300 mb-1.5">
                      Commercial Justification & Supporting Context:
                    </label>
                    <textarea
                      rows={4}
                      value={explanationText}
                      onChange={(e) => setExplanationText(e.target.value)}
                      placeholder="e.g., The freight rate was increased by 200 EGP due to emergency port demurrage pre-cleared by the site supervisor..."
                      className="w-full px-4 py-3 rounded-xl bg-slate-950 border border-slate-800 text-sm text-slate-100 placeholder-slate-500 focus:outline-none focus:border-indigo-500 focus:ring-1 focus:ring-indigo-500 transition-all resize-none"
                    />
                  </div>

                  <button
                    type="submit"
                    disabled={evaluating || !explanationText.trim()}
                    className="px-6 py-3 rounded-xl bg-indigo-600 hover:bg-indigo-500 disabled:opacity-50 text-white font-bold text-xs tracking-wide shadow-lg shadow-indigo-600/20 flex items-center gap-2 transition-all cursor-pointer"
                  >
                    {evaluating ? (
                      <>
                        <Loader2 className="w-4 h-4 animate-spin" />
                        Gemini 3.8 Flash Evaluating Justification...
                      </>
                    ) : (
                      <>
                        <Send className="w-4 h-4" />
                        Submit for Autonomous Evaluation
                      </>
                    )}
                  </button>
                </form>

                {/* AI Assessment Display Card */}
                {assessment && (
                  <div className="mt-6 p-5 rounded-2xl bg-slate-950/80 border border-indigo-500/30 space-y-4">
                    <div className="flex flex-wrap items-center justify-between gap-2 border-b border-slate-800/80 pb-3">
                      <div className="flex items-center gap-2">
                        <Sparkles className="w-4 h-4 text-indigo-400" />
                        <span className="text-xs font-bold text-slate-200">
                          Gemini 3.8 Flash Negotiation Assessment
                        </span>
                      </div>

                      {/* Recommendation Badge */}
                      {assessment.aiRecommendation === "APPROVE_EXPLANATION" && (
                        <span className="px-3 py-1 rounded-full text-xs font-bold uppercase bg-emerald-500/10 text-emerald-400 border border-emerald-500/30 flex items-center gap-1.5">
                          <CheckCircle2 className="w-3.5 h-3.5" />
                          Recommended: Approve Explanation
                        </span>
                      )}
                      {assessment.aiRecommendation === "PARTIAL_CONCESSION" && (
                        <span className="px-3 py-1 rounded-full text-xs font-bold uppercase bg-amber-500/10 text-amber-400 border border-amber-500/30 flex items-center gap-1.5">
                          <AlertTriangle className="w-3.5 h-3.5" />
                          Recommended: 50/50 Compromise
                        </span>
                      )}
                      {assessment.aiRecommendation === "REJECT_EXPLANATION" && (
                        <span className="px-3 py-1 rounded-full text-xs font-bold uppercase bg-rose-500/10 text-rose-400 border border-rose-500/30 flex items-center gap-1.5">
                          <AlertTriangle className="w-3.5 h-3.5" />
                          Recommended: Maintain PO Terms
                        </span>
                      )}
                    </div>

                    <div className="text-xs text-slate-300 leading-relaxed">
                      <p className="font-semibold text-slate-200 mb-1">Commercial Analysis:</p>
                      <p>{assessment.commercialJustification}</p>
                    </div>

                    {assessment.recommendedCounterOffer != null && (
                      <div className="p-3 rounded-xl bg-amber-500/10 border border-amber-500/20 text-xs text-amber-300 flex items-center justify-between">
                        <span>Proposed Compromise Balance:</span>
                        <span className="font-mono font-bold text-sm">
                          {Number(assessment.recommendedCounterOffer).toLocaleString()} EGP
                        </span>
                      </div>
                    )}

                    <div className="pt-2 text-[11px] text-slate-400 flex items-center gap-2">
                      <span className="font-semibold text-slate-300">Next Action:</span>
                      <span>{assessment.nextSteps}</span>
                    </div>
                  </div>
                )}
              </div>
            )}

            {/* TAB 3: Upload Amended Invoice */}
            {activeTab === "amended" && (
              <div className="space-y-6">
                <div>
                  <h3 className="text-base font-bold text-slate-100 flex items-center gap-2">
                    <Upload className="w-5 h-5 text-amber-400" />
                    Upload Corrected / Amended Commercial Invoice
                  </h3>
                  <p className="text-xs text-slate-400 mt-1">
                    Upload an updated version of invoice {data.invoiceNumber} with corrected pricing and quantities
                    matching Purchase Order {data.poReference}.
                  </p>
                </div>

                <form onSubmit={handleUploadAmended} className="space-y-4">
                  <div className="p-6 border-2 border-dashed border-slate-800 hover:border-slate-700 rounded-2xl bg-slate-950/40 text-center space-y-2">
                    <input
                      type="file"
                      id="amendedFileInput"
                      accept=".pdf,.png,.jpg,.jpeg"
                      onChange={(e) => {
                        if (e.target.files && e.target.files[0]) {
                          setAmendedFile(e.target.files[0]);
                        }
                      }}
                      className="hidden"
                    />
                    <label
                      htmlFor="amendedFileInput"
                      className="cursor-pointer inline-flex flex-col items-center justify-center space-y-2"
                    >
                      <div className="w-12 h-12 rounded-full bg-slate-800 flex items-center justify-center text-slate-400">
                        <FileText className="w-6 h-6" />
                      </div>
                      <span className="text-xs font-semibold text-slate-200">
                        {amendedFile ? amendedFile.name : "Click to select amended invoice (PDF or Image)"}
                      </span>
                      <span className="text-[11px] text-slate-500">
                        {amendedFile ? `${(amendedFile.size / 1024).toFixed(1)} KB` : "Max file size: 10 MB"}
                      </span>
                    </label>
                  </div>

                  <button
                    type="submit"
                    disabled={uploading || !amendedFile}
                    className="px-6 py-3 rounded-xl bg-amber-500 hover:bg-amber-400 disabled:opacity-50 text-slate-950 font-bold text-xs tracking-wide shadow-lg shadow-amber-500/20 flex items-center gap-2 transition-all cursor-pointer"
                  >
                    {uploading ? (
                      <>
                        <Loader2 className="w-4 h-4 animate-spin" />
                        Uploading & Re-Reconciling Document...
                      </>
                    ) : (
                      <>
                        <Upload className="w-4 h-4" />
                        Upload & Trigger Re-Reconciliation
                      </>
                    )}
                  </button>
                </form>

                {amendedResult && (
                  <div className="p-4 rounded-xl bg-emerald-500/10 border border-emerald-500/30 text-xs text-emerald-300 flex items-start gap-3">
                    <CheckCircle2 className="w-5 h-5 text-emerald-400 shrink-0 mt-0.5" />
                    <div>
                      <p className="font-semibold text-emerald-200">Amended Document Successfully Processed</p>
                      <p className="mt-0.5">
                        New reconciliation status:{" "}
                        <span className="font-bold uppercase">{amendedResult.reconciliationStatus}</span>. Discrepancies count:{" "}
                        {amendedResult.discrepancyCount}.
                      </p>
                    </div>
                  </div>
                )}
              </div>
            )}
          </div>
        </div>
      )}
    </div>
  );
}
