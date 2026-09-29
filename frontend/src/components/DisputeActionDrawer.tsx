"use client";

import React, { useState } from "react";
import {
  FileText,
  Copy,
  Check,
  CheckCircle,
  AlertTriangle,
  ChevronDown,
  ChevronUp,
  ShieldCheck,
  XCircle,
  Loader2,
  Send,
  Building2,
  FileCheck2,
} from "lucide-react";
import type { ReconciliationSummaryResponse } from "@/lib/types";

interface DisputeActionDrawerProps {
  invoice: ReconciliationSummaryResponse;
  onInvoiceUpdated: (updated: ReconciliationSummaryResponse) => void;
}

export default function DisputeActionDrawer({
  invoice,
  onInvoiceUpdated,
}: DisputeActionDrawerProps) {
  const [isOpen, setIsOpen] = useState(true);
  const [activeTab, setActiveTab] = useState<"both" | "ar" | "en">("both");
  const [copied, setCopied] = useState(false);
  const [isProcessing, setIsProcessing] = useState(false);
  const [actionError, setActionError] = useState<string | null>(null);
  const [successNotice, setSuccessNotice] = useState<string | null>(null);

  const disputeText = invoice.disputeDraft || "No dispute notice generated.";

  // Split into Arabic and English sections if separated by --- or Section 2
  let arabicSection = "";
  let englishSection = "";

  if (disputeText.includes("---")) {
    const parts = disputeText.split("---");
    arabicSection = parts[0]?.trim() || "";
    englishSection = parts.slice(1).join("---").trim() || "";
  } else if (disputeText.includes("Section 2:")) {
    const idx = disputeText.indexOf("Section 2:");
    arabicSection = disputeText.substring(0, idx).trim();
    englishSection = disputeText.substring(idx).trim();
  } else {
    arabicSection = disputeText;
    englishSection = disputeText;
  }

  const handleCopy = async (textToCopy: string) => {
    try {
      await navigator.clipboard.writeText(textToCopy);
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    } catch {
      const ta = document.createElement("textarea");
      ta.value = textToCopy;
      document.body.appendChild(ta);
      ta.select();
      document.execCommand("copy");
      document.body.removeChild(ta);
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    }
  };

  // Reject invoice & issue dispute
  const handleReject = async () => {
    if (
      !confirm(
        `Are you sure you want to REJECT invoice ${invoice.invoiceNumber} and issue the formal dispute notice to ${invoice.vendorName}? Payment will remain blocked.`
      )
    ) {
      return;
    }

    setIsProcessing(true);
    setActionError(null);
    setSuccessNotice(null);

    try {
      const res = await fetch(`/api/invoices/${invoice.invoiceId}/reject`, {
        method: "POST",
      });

      if (!res.ok) {
        const errText = await res.text();
        throw new Error(errText || "Rejection action failed.");
      }

      const updated: ReconciliationSummaryResponse = await res.json();
      onInvoiceUpdated(updated);
      setSuccessNotice("Invoice rejected. Dispute notice confirmed and registered in audit records.");
    } catch (err: unknown) {
      setActionError(err instanceof Error ? err.message : "Failed to reject invoice.");
    } finally {
      setIsProcessing(false);
    }
  };

  // Approve invoice (manager override)
  const handleApprove = async () => {
    if (
      !confirm(
        `Are you sure you want to OVERRIDE and APPROVE invoice ${invoice.invoiceNumber} for payment release?`
      )
    ) {
      return;
    }

    setIsProcessing(true);
    setActionError(null);
    setSuccessNotice(null);

    try {
      const res = await fetch(`/api/invoices/${invoice.invoiceId}/approve`, {
        method: "POST",
      });

      if (!res.ok) {
        const errText = await res.text();
        throw new Error(errText || "Approval failed.");
      }

      const updated: ReconciliationSummaryResponse = await res.json();
      onInvoiceUpdated(updated);
      setSuccessNotice("Invoice approved for payment release by financial auditor override.");
    } catch (err: unknown) {
      setActionError(err instanceof Error ? err.message : "Failed to approve payment.");
    } finally {
      setIsProcessing(false);
    }
  };

  const isApproved = invoice.reconciliationStatus === "APPROVED";
  const isRejected = invoice.reconciliationStatus === "REJECTED";

  return (
    <div className="border border-slate-200 bg-white rounded-2xl shadow-xs overflow-hidden transition-all duration-200">
      {/* Header bar */}
      <div className="p-4 bg-slate-50/80 border-b border-slate-200 flex flex-wrap items-center justify-between gap-3">
        <div className="flex items-center gap-3">
          <div className="w-9 h-9 rounded-xl bg-purple-50 text-purple-600 flex items-center justify-center shrink-0 border border-purple-100">
            <FileText className="w-4 h-4" />
          </div>
          <div>
            <h4 className="text-sm font-bold text-slate-800 flex items-center gap-2">
              Gemini AI Bilingual Dispute Draft
              {invoice.discrepancyCount > 0 && !isApproved && !isRejected && (
                <span className="px-2 py-0.5 rounded-full text-[10px] font-bold bg-rose-100 text-rose-700 border border-rose-200">
                  {invoice.discrepancyCount} Discrepanc{invoice.discrepancyCount === 1 ? "y" : "ies"}
                </span>
              )}
              {isRejected && (
                <span className="px-2 py-0.5 rounded-full text-[10px] font-bold bg-rose-100 text-rose-800 border border-rose-200">
                  Dispute Issued & Payment Blocked
                </span>
              )}
              {isApproved && (
                <span className="px-2 py-0.5 rounded-full text-[10px] font-bold bg-emerald-100 text-emerald-800 border border-emerald-200">
                  Payment Approved
                </span>
              )}
            </h4>
            <p className="text-[11px] text-slate-500">
              Commercial dispute notice in Arabic & English referencing agreed PO items and pricing terms.
            </p>
          </div>
        </div>

        {/* Action Buttons Toolbar */}
        <div className="flex flex-wrap items-center gap-2">
          {/* Copy Button */}
          <button
            onClick={() =>
              handleCopy(
                activeTab === "ar"
                  ? arabicSection
                  : activeTab === "en"
                  ? englishSection
                  : disputeText
              )
            }
            className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-semibold border border-slate-200 bg-white hover:bg-slate-50 text-slate-700 shadow-xs transition-colors"
            title="Copy dispute letter to clipboard"
          >
            {copied ? (
              <>
                <Check className="w-3.5 h-3.5 text-emerald-600" />
                <span className="text-emerald-700 font-bold">Copied!</span>
              </>
            ) : (
              <>
                <Copy className="w-3.5 h-3.5 text-slate-500" />
                <span>Copy Draft</span>
              </>
            )}
          </button>

          {/* Action 1: Reject & Issue Dispute */}
          {!isRejected && (
            <button
              onClick={handleReject}
              disabled={isProcessing}
              className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-semibold bg-rose-600 hover:bg-rose-700 text-white shadow-xs transition-colors disabled:opacity-50"
              title="Reject invoice and confirm formal dispute transmission"
            >
              {isProcessing ? (
                <Loader2 className="w-3.5 h-3.5 animate-spin" />
              ) : (
                <XCircle className="w-3.5 h-3.5" />
              )}
              <span>Reject & Issue Dispute</span>
            </button>
          )}

          {/* Action 2: Override & Approve Payment */}
          {!isApproved && (
            <button
              onClick={handleApprove}
              disabled={isProcessing}
              className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-semibold bg-emerald-600 hover:bg-emerald-700 text-white shadow-xs transition-colors disabled:opacity-50"
              title="Override discrepancies and authorize payment release"
            >
              {isProcessing ? (
                <Loader2 className="w-3.5 h-3.5 animate-spin" />
              ) : (
                <ShieldCheck className="w-3.5 h-3.5" />
              )}
              <span>{isRejected ? "Override & Approve" : "Approve Payment"}</span>
            </button>
          )}

          {/* Expand / Collapse Chevron */}
          <button
            onClick={() => setIsOpen(!isOpen)}
            className="p-1.5 text-slate-400 hover:text-slate-600 rounded-lg hover:bg-slate-100 transition-colors"
            title={isOpen ? "Collapse Draft" : "Expand Draft"}
          >
            {isOpen ? <ChevronUp className="w-4 h-4" /> : <ChevronDown className="w-4 h-4" />}
          </button>
        </div>
      </div>

      {actionError && (
        <div className="p-3 bg-rose-50 border-b border-rose-200 text-rose-800 text-xs flex items-center gap-2 font-medium">
          <AlertTriangle className="w-4 h-4 text-rose-600 shrink-0" />
          <span>{actionError}</span>
        </div>
      )}

      {successNotice && (
        <div className="p-3 bg-emerald-50 border-b border-emerald-200 text-emerald-800 text-xs flex items-center gap-2 font-medium">
          <CheckCircle className="w-4 h-4 text-emerald-600 shrink-0" />
          <span>{successNotice}</span>
        </div>
      )}

      {isOpen && (
        <div className="p-5 space-y-4">
          {/* Tabs */}
          <div className="flex items-center justify-between border-b border-slate-100 pb-3">
            <div className="flex gap-1.5 bg-slate-100 p-1 rounded-lg">
              <button
                onClick={() => setActiveTab("both")}
                className={`px-3 py-1 rounded-md text-xs font-semibold transition-colors ${
                  activeTab === "both"
                    ? "bg-white text-slate-900 shadow-xs"
                    : "text-slate-600 hover:text-slate-900"
                }`}
              >
                All (Bilingual)
              </button>
              <button
                onClick={() => setActiveTab("ar")}
                className={`px-3 py-1 rounded-md text-xs font-semibold transition-colors ${
                  activeTab === "ar"
                    ? "bg-white text-slate-900 shadow-xs"
                    : "text-slate-600 hover:text-slate-900"
                }`}
              >
                العربية (Arabic)
              </button>
              <button
                onClick={() => setActiveTab("en")}
                className={`px-3 py-1 rounded-md text-xs font-semibold transition-colors ${
                  activeTab === "en"
                    ? "bg-white text-slate-900 shadow-xs"
                    : "text-slate-600 hover:text-slate-900"
                }`}
              >
                English
              </button>
            </div>
            <span className="text-[11px] text-slate-400">
              Formally structured commercial correspondence for vendor billing
            </span>
          </div>

          {/* Text preview */}
          <div className="relative bg-slate-900 rounded-xl p-5 text-slate-100 text-xs font-mono leading-relaxed max-h-80 overflow-y-auto shadow-inner border border-slate-800">
            {activeTab === "both" && (
              <pre className="whitespace-pre-wrap font-sans text-xs leading-relaxed text-slate-200">
                {disputeText}
              </pre>
            )}
            {activeTab === "ar" && (
              <pre
                dir="rtl"
                className="whitespace-pre-wrap font-sans text-xs leading-relaxed text-slate-200 text-right"
              >
                {arabicSection}
              </pre>
            )}
            {activeTab === "en" && (
              <pre className="whitespace-pre-wrap font-sans text-xs leading-relaxed text-slate-200 text-left">
                {englishSection}
              </pre>
            )}
          </div>
        </div>
      )}
    </div>
  );
}
