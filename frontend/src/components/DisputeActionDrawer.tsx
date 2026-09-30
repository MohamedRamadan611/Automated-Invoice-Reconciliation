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
  Mail,
  Building2,
  Info,
  Clock,
  Sparkles,
} from "lucide-react";
import type { ReconciliationSummaryResponse } from "@/lib/types";
import { approveInvoice, rejectInvoice, regenerateDispute } from "@/lib/api";

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
  const [isGeneratingDraft, setIsGeneratingDraft] = useState(false);
  const [actionError, setActionError] = useState<string | null>(null);
  const [successNotice, setSuccessNotice] = useState<{
    title: string;
    message: string;
    simulated: boolean;
    recipient: string;
  } | null>(null);

  // Email input and modal/panel states
  const [showRejectPanel, setShowRejectPanel] = useState(false);
  const [showApprovePanel, setShowApprovePanel] = useState(false);

  const [vendorEmail, setVendorEmail] = useState("mohamed.ramadan61197@gmail.com");
  const [managerEmail, setManagerEmail] = useState("mohamed.ramadan97116@gmail.com");
  const [emailLanguage, setEmailLanguage] = useState<"BOTH" | "AR" | "EN">("BOTH");
  const [approvalNotes, setApprovalNotes] = useState(
    "Variance acceptable under emergency supply authorization. Payment released."
  );
  const [lastRefreshedAt, setLastRefreshedAt] = useState<string | null>(null);

  const disputeText = invoice.disputeDraft || "No dispute notice generated.";

  // Pure Arabic and English sections from API or clean fallback parser
  let arabicSection = invoice.disputeDraftArabic?.trim() || "";
  let englishSection = invoice.disputeDraftEnglish?.trim() || "";

  if (!arabicSection || !englishSection) {
    if (disputeText.includes("---")) {
      const parts = disputeText.split("---");
      if (!arabicSection) arabicSection = parts[0]?.trim() || "";
      if (!englishSection) englishSection = parts.slice(1).join("---").trim() || "";
    } else if (disputeText.includes("### Section 2")) {
      const idx = disputeText.indexOf("### Section 2");
      if (!arabicSection) arabicSection = disputeText.substring(0, idx).trim();
      if (!englishSection) englishSection = disputeText.substring(idx).trim();
    } else if (disputeText.includes("Section 2:")) {
      const idx = disputeText.indexOf("Section 2:");
      if (!arabicSection) arabicSection = disputeText.substring(0, idx).trim();
      if (!englishSection) englishSection = disputeText.substring(idx).trim();
    } else {
      if (!arabicSection) arabicSection = disputeText;
      if (!englishSection) englishSection = disputeText;
    }
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

  // Regenerate dispute draft on demand with Gemini AI
  const handleGenerateDispute = async () => {
    setIsGeneratingDraft(true);
    setActionError(null);
    try {
      const updated = await regenerateDispute(invoice.invoiceId);
      onInvoiceUpdated(updated);
      const timeStr = new Date().toLocaleTimeString();
      setLastRefreshedAt(timeStr);
      setSuccessNotice({
        title: "Dispute Draft Regenerated Successfully",
        message: `Commercial dispute notice refreshed at ${timeStr} and persisted to workspace database.`,
        simulated: false,
        recipient: "",
      });
    } catch (err: unknown) {
      setActionError(err instanceof Error ? err.message : "Failed to regenerate dispute draft.");
    } finally {
      setIsGeneratingDraft(false);
    }
  };

  // Reject invoice & issue dispute email
  const handleReject = async () => {
    setIsProcessing(true);
    setActionError(null);
    setSuccessNotice(null);

    try {
      const result = await rejectInvoice(invoice.invoiceId, vendorEmail, emailLanguage);

      onInvoiceUpdated(result.invoice);
      setShowRejectPanel(false);

      setSuccessNotice({
        title: "Invoice Rejected & Dispute Notice Issued",
        message: result.isSimulated
          ? `Dispute letter (${emailLanguage}) formatted & simulated in server logs (Recipient: ${result.recipient}). Configure Gmail SMTP in .env for live dispatch.`
          : `Live commercial dispute letter (${emailLanguage}) successfully transmitted to vendor at ${result.recipient} with CC to manager!`,
        simulated: result.isSimulated,
        recipient: result.recipient,
      });
    } catch (err: unknown) {
      setActionError(err instanceof Error ? err.message : "Failed to reject invoice.");
    } finally {
      setIsProcessing(false);
    }
  };

  // Approve invoice (manager sign-off)
  const handleApprove = async () => {
    setIsProcessing(true);
    setActionError(null);
    setSuccessNotice(null);

    try {
      const result = await approveInvoice(invoice.invoiceId, managerEmail, approvalNotes);

      onInvoiceUpdated(result.invoice);
      setShowApprovePanel(false);

      setSuccessNotice({
        title: "Invoice Approved & Payment Sign-Off Dispatched",
        message: result.isSimulated
          ? `Payment authorization sign-off simulated in server logs (Recipient: ${result.recipient}). Configure Gmail SMTP in .env for live dispatch.`
          : `Live payment authorization and manager responsibility record dispatched to ${result.recipient}!`,
        simulated: result.isSimulated,
        recipient: result.recipient,
      });
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
                <span className="px-2 py-0.5 rounded-full text-[10px] font-bold bg-amber-100 text-amber-800 border border-amber-200">
                  {invoice.discrepancyCount} Discrepanc{invoice.discrepancyCount === 1 ? "y" : "ies"} (Reviewing)
                </span>
              )}
              {isRejected && (
                <span className="px-2 py-0.5 rounded-full text-[10px] font-bold bg-rose-100 text-rose-900 border border-rose-300">
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
          {/* Regenerate Draft with Gemini AI */}
          <button
            onClick={handleGenerateDispute}
            disabled={isGeneratingDraft || isProcessing}
            className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-semibold border border-indigo-200 bg-indigo-50 hover:bg-indigo-100 text-indigo-700 shadow-xs transition-colors disabled:opacity-50"
            title="Regenerate commercial dispute draft using Gemini AI"
          >
            {isGeneratingDraft ? (
              <>
                <Loader2 className="w-3.5 h-3.5 animate-spin text-indigo-600" />
                <span>Regenerating...</span>
              </>
            ) : (
              <>
                <Sparkles className="w-3.5 h-3.5 text-indigo-600" />
                <span>Regenerate Draft with AI</span>
              </>
            )}
          </button>

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

          {/* Action 1: Reject & Issue Dispute Toggle */}
          {!isRejected && (
            <button
              onClick={() => {
                setShowRejectPanel(!showRejectPanel);
                setShowApprovePanel(false);
              }}
              disabled={isProcessing}
              className={`inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-semibold transition-all shadow-xs disabled:opacity-50 ${
                showRejectPanel
                  ? "bg-rose-800 text-white ring-2 ring-rose-500/50"
                  : "bg-rose-600 hover:bg-rose-700 text-white"
              }`}
              title="Reject invoice and prepare dispute email to vendor"
            >
              <XCircle className="w-3.5 h-3.5" />
              <span>Reject & Issue Dispute</span>
            </button>
          )}

          {/* Action 2: Override & Approve Payment Toggle */}
          {!isApproved && (
            <button
              onClick={() => {
                setShowApprovePanel(!showApprovePanel);
                setShowRejectPanel(false);
              }}
              disabled={isProcessing}
              className={`inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-semibold transition-all shadow-xs disabled:opacity-50 ${
                showApprovePanel
                  ? "bg-emerald-800 text-white ring-2 ring-emerald-500/50"
                  : "bg-emerald-600 hover:bg-emerald-700 text-white"
              }`}
              title="Authorize payment override and send manager sign-off email"
            >
              <ShieldCheck className="w-3.5 h-3.5" />
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

      {/* ERROR NOTICE */}
      {actionError && (
        <div className="p-3.5 bg-rose-50 border-b border-rose-200 text-rose-800 text-xs flex items-center gap-2 font-medium">
          <AlertTriangle className="w-4 h-4 text-rose-600 shrink-0" />
          <span>{actionError}</span>
        </div>
      )}

      {/* SUCCESS & EMAIL DISPATCH CONFIRMATION BANNER */}
      {successNotice && (
        <div className="p-4 bg-emerald-50 border-b border-emerald-200 text-emerald-900 text-xs flex items-start gap-3">
          <CheckCircle className="w-5 h-5 text-emerald-600 shrink-0 mt-0.5" />
          <div className="space-y-1">
            <h5 className="font-bold text-emerald-950">{successNotice.title}</h5>
            <p className="text-emerald-800 leading-relaxed">{successNotice.message}</p>
            <div className="flex items-center gap-2 pt-1 font-mono text-[11px] text-emerald-700">
              <span className="px-2 py-0.5 bg-emerald-100 rounded-md">Recipient: {successNotice.recipient}</span>
              {successNotice.simulated ? (
                <span className="px-2 py-0.5 bg-amber-100 text-amber-800 rounded-md">Mode: Safe Console Simulation</span>
              ) : (
                <span className="px-2 py-0.5 bg-emerald-200 text-emerald-900 rounded-md font-bold">Mode: Live SMTP Delivery</span>
              )}
            </div>
          </div>
        </div>
      )}

      {/* REJECT & DISPUTE EMAIL DISPATCH PANEL */}
      {showRejectPanel && (
        <div className="p-5 bg-rose-50/60 border-b border-rose-200 space-y-4">
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-2 text-rose-900 font-bold text-xs uppercase tracking-wider">
              <Mail className="w-4 h-4 text-rose-600" />
              <span>Commercial Dispute Email Configuration</span>
            </div>
            <span className="text-[11px] text-rose-700 font-medium">Payment will remain BLOCKED</span>
          </div>

          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            <div>
              <label className="block text-xs font-semibold text-slate-700 mb-1">
                Vendor Billing Recipient Email:
              </label>
              <input
                type="email"
                value={vendorEmail}
                onChange={(e) => setVendorEmail(e.target.value)}
                placeholder="vendor-billing@supplier.com"
                className="w-full px-3 py-2 text-xs rounded-lg border border-rose-200 bg-white text-slate-900 focus:outline-none focus:ring-2 focus:ring-rose-500/20 focus:border-rose-500"
              />
              <p className="text-[10px] text-slate-500 mt-1">
                Enter your Gmail address here to test live inbox delivery!
              </p>
            </div>

            <div>
              <label className="block text-xs font-semibold text-slate-700 mb-1">
                Email Language Format / لغة الخطاب:
              </label>
              <div className="flex items-center gap-2 mt-1">
                <button
                  type="button"
                  onClick={() => setEmailLanguage("BOTH")}
                  className={`flex-1 py-1.5 px-2 rounded-lg text-xs font-semibold transition-all border ${
                    emailLanguage === "BOTH"
                      ? "bg-rose-700 text-white border-rose-800 shadow-xs"
                      : "bg-white text-slate-700 border-slate-200 hover:bg-slate-50"
                  }`}
                >
                  🌐 Bilingual (AR & EN)
                </button>
                <button
                  type="button"
                  onClick={() => setEmailLanguage("AR")}
                  className={`flex-1 py-1.5 px-2 rounded-lg text-xs font-semibold transition-all border ${
                    emailLanguage === "AR"
                      ? "bg-rose-700 text-white border-rose-800 shadow-xs"
                      : "bg-white text-slate-700 border-slate-200 hover:bg-slate-50"
                  }`}
                >
                  🇪🇬 Arabic
                </button>
                <button
                  type="button"
                  onClick={() => setEmailLanguage("EN")}
                  className={`flex-1 py-1.5 px-2 rounded-lg text-xs font-semibold transition-all border ${
                    emailLanguage === "EN"
                      ? "bg-rose-700 text-white border-rose-800 shadow-xs"
                      : "bg-white text-slate-700 border-slate-200 hover:bg-slate-50"
                  }`}
                >
                  🇬🇧 English
                </button>
              </div>
              <p className="text-[10px] text-slate-500 mt-1.5">
                Select desired language for both email subject and formal dispute body.
              </p>
            </div>
          </div>

          <div className="flex items-center justify-end gap-2 pt-2">
            <button
              onClick={() => setShowRejectPanel(false)}
              className="px-3 py-1.5 text-xs font-medium text-slate-600 hover:text-slate-900"
            >
              Cancel
            </button>
            <button
              onClick={handleReject}
              disabled={isProcessing}
              className="inline-flex items-center gap-1.5 px-4 py-2 rounded-lg text-xs font-bold bg-rose-700 hover:bg-rose-800 text-white shadow-sm transition-colors disabled:opacity-50"
            >
              {isProcessing ? (
                <Loader2 className="w-3.5 h-3.5 animate-spin" />
              ) : (
                <Send className="w-3.5 h-3.5" />
              )}
              <span>Confirm Rejection & Dispatch Dispute Email</span>
            </button>
          </div>
        </div>
      )}

      {/* APPROVE & PAYMENT SIGN-OFF PANEL */}
      {showApprovePanel && (
        <div className="p-5 bg-emerald-50/60 border-b border-emerald-200 space-y-4">
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-2 text-emerald-900 font-bold text-xs uppercase tracking-wider">
              <ShieldCheck className="w-4 h-4 text-emerald-600" />
              <span>Payment Authorization & Sign-Off Record</span>
            </div>
            <span className="text-[11px] text-emerald-700 font-medium">Payment will be RELEASED</span>
          </div>

          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            <div>
              <label className="block text-xs font-semibold text-slate-700 mb-1">
                Finance Manager Recipient Email:
              </label>
              <input
                type="email"
                value={managerEmail}
                onChange={(e) => setManagerEmail(e.target.value)}
                placeholder="finance-manager@company.com"
                className="w-full px-3 py-2 text-xs rounded-lg border border-emerald-200 bg-white text-slate-900 focus:outline-none focus:ring-2 focus:ring-emerald-500/20 focus:border-emerald-500"
              />
              <p className="text-[10px] text-slate-500 mt-1">
                Enter your Gmail address here to receive the payment sign-off receipt.
              </p>
            </div>

            <div>
              <label className="block text-xs font-semibold text-slate-700 mb-1">
                Auditor Override / Responsibility Note:
              </label>
              <textarea
                rows={2}
                value={approvalNotes}
                onChange={(e) => setApprovalNotes(e.target.value)}
                className="w-full px-3 py-1.5 text-xs rounded-lg border border-emerald-200 bg-white text-slate-900 focus:outline-none focus:ring-2 focus:ring-emerald-500/20 focus:border-emerald-500"
              />
            </div>
          </div>

          <div className="flex items-center justify-end gap-2 pt-2">
            <button
              onClick={() => setShowApprovePanel(false)}
              className="px-3 py-1.5 text-xs font-medium text-slate-600 hover:text-slate-900"
            >
              Cancel
            </button>
            <button
              onClick={handleApprove}
              disabled={isProcessing}
              className="inline-flex items-center gap-1.5 px-4 py-2 rounded-lg text-xs font-bold bg-emerald-700 hover:bg-emerald-800 text-white shadow-sm transition-colors disabled:opacity-50"
            >
              {isProcessing ? (
                <Loader2 className="w-3.5 h-3.5 animate-spin" />
              ) : (
                <Check className="w-3.5 h-3.5" />
              )}
              <span>Confirm Approval & Send Manager Sign-Off</span>
            </button>
          </div>
        </div>
      )}

      {/* DRAWER BODY: PREVIEW OF DISPUTE TEXT */}
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
              {lastRefreshedAt ? `Refreshed at ${lastRefreshedAt}` : "Commercial mediation correspondence"}
            </span>
          </div>

          {/* Text preview */}
          <div className="relative bg-slate-900 rounded-xl p-5 text-slate-100 text-xs font-mono leading-relaxed max-h-72 overflow-y-auto shadow-inner border border-slate-800">
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
