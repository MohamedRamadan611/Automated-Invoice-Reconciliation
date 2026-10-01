"use client";

import React, { useState } from "react";
import Link from "next/link";
import {
  FileText,
  ZoomIn,
  ZoomOut,
  Maximize2,
  ExternalLink,
  Download,
  AlertTriangle,
  CheckCircle2,
  AlertOctagon,
  TrendingUp,
  ArrowRight,
  ShieldCheck,
  RefreshCw,
  Layers,
  Scale,
  MessageSquareWarning,
  Eye,
  XCircle,
} from "lucide-react";
import type { ReconciliationSummaryResponse, AuditDetailResponse } from "@/lib/types";
import DisputeActionDrawer from "./DisputeActionDrawer";
import DocumentViewer from "./DocumentViewer";

interface SplitScreenViewerProps {
  invoice: ReconciliationSummaryResponse;
  onInvoiceUpdated?: (updated: ReconciliationSummaryResponse) => void;
}

export default function SplitScreenViewer({
  invoice: initialInvoice,
  onInvoiceUpdated,
}: SplitScreenViewerProps) {
  const [invoice, setInvoice] = useState<ReconciliationSummaryResponse>(initialInvoice);
  const [activeRightTab, setActiveRightTab] = useState<"findings" | "dispute" | "combined">("combined");

  const handleUpdate = (updated: ReconciliationSummaryResponse) => {
    setInvoice(updated);
    if (onInvoiceUpdated) {
      onInvoiceUpdated(updated);
    }
  };

  // Currency formatter
  const formatEGP = (amount?: number | null) => {
    if (amount === undefined || amount === null) return "—";
    return new Intl.NumberFormat("en-EG", {
      style: "currency",
      currency: "EGP",
      minimumFractionDigits: 2,
    }).format(amount);
  };

  const invoicedTotal = invoice.invoicedTotal ?? 0;
  const expectedTotal = invoice.expectedTotal ?? 0;
  const varianceDelta = invoicedTotal - expectedTotal;

  // Status badge config
  const getStatusBadge = (status: string) => {
    switch (status) {
      case "APPROVED":
        return {
          bg: "bg-emerald-50 text-emerald-800 border-emerald-300 ring-1 ring-emerald-500/20",
          icon: <CheckCircle2 className="w-4 h-4 text-emerald-600" />,
          label: "Approved for Payment",
        };
      case "REJECTED":
        return {
          bg: "bg-rose-100 text-rose-950 border-rose-400 ring-1 ring-rose-500/30 font-semibold shadow-xs",
          icon: <XCircle className="w-4 h-4 text-rose-700" />,
          label: "Rejected - Dispute Transmitted",
        };
      case "FLAGGED_DISCREPANCY":
        return {
          bg: "bg-amber-50 text-amber-900 border-amber-300 ring-1 ring-amber-500/20",
          icon: <AlertTriangle className="w-4 h-4 text-amber-600" />,
          label: "Flagged Discrepancy",
        };
      case "MANUAL_REVIEW":
      default:
        return {
          bg: "bg-indigo-50 text-indigo-800 border-indigo-200 ring-1 ring-indigo-500/20",
          icon: <Layers className="w-4 h-4 text-indigo-600" />,
          label: "Manual Review Required",
        };
    }
  };

  const statusConfig = getStatusBadge(invoice.reconciliationStatus);

  const getIssueBadge = (type: string) => {
    switch (type) {
      case "PRICE_MISMATCH":
        return {
          badge: "bg-rose-100 text-rose-800 border-rose-200",
          label: "Price Variance",
        };
      case "QUANTITY_MISMATCH":
        return {
          badge: "bg-orange-100 text-orange-800 border-orange-200",
          label: "Quantity Variance",
        };
      case "EXTRA_FEE":
        return {
          badge: "bg-purple-100 text-purple-800 border-purple-200",
          label: "Unapproved Surcharge",
        };
      case "UNRECOGNIZED_ITEM":
        return {
          badge: "bg-amber-100 text-amber-800 border-amber-200",
          label: "Unrecognized Item",
        };
      case "PO_NOT_FOUND":
        return {
          badge: "bg-red-100 text-red-800 border-red-200",
          label: "PO Missing",
        };
      default:
        return {
          badge: "bg-slate-100 text-slate-800 border-slate-200",
          label: type,
        };
    }
  };

  const fileUri = `/api/invoices/${invoice.invoiceId}/file`;
  const isImageFile = invoice.fileDownloadUri?.match(/\.(png|jpe?g)$/i);

  return (
    <div className="flex flex-col h-screen w-screen overflow-hidden bg-slate-100">
      {/* Top Metadata & Summary Ribbon */}
      <header className="bg-white border-b border-slate-200 px-6 py-3.5 flex flex-wrap items-center justify-between gap-4 shrink-0 shadow-xs z-10">
        <div className="flex items-center gap-4">
          <Link
            href="/"
            className="text-xs font-semibold text-slate-600 hover:text-indigo-600 transition-colors flex items-center gap-1.5 px-2.5 py-1.5 rounded-lg hover:bg-slate-100"
          >
            ← Invoices Queue
          </Link>
          <div className="h-4 w-px bg-slate-200" />
          <div className="flex items-center gap-3">
            <span className="font-mono text-sm font-bold text-slate-900 bg-slate-100 px-2.5 py-1 rounded-md border border-slate-200">
              {invoice.invoiceNumber}
            </span>
            <span className="text-xs text-slate-400">|</span>
            <span className="text-xs text-slate-600 font-medium">
              Vendor: <strong className="text-slate-800">{invoice.vendorName}</strong>
            </span>
            <span className="text-xs text-slate-400">|</span>
            <span className="text-xs text-slate-600 font-medium">
              PO Ref:{" "}
              <strong className="font-mono text-indigo-600">
                {invoice.poReference || "None Detected"}
              </strong>
            </span>
          </div>
        </div>

        <div className="flex items-center gap-4">
          <div
            className={`inline-flex items-center gap-1.5 px-3 py-1 rounded-full text-xs font-semibold border ${statusConfig.bg}`}
          >
            {statusConfig.icon}
            <span>{statusConfig.label}</span>
          </div>

          <div className="flex items-center gap-3 pl-3 border-l border-slate-200">
            <div className="text-right">
              <span className="text-[10px] text-slate-400 uppercase tracking-wider block font-medium">
                Billed Total
              </span>
              <span className="font-mono text-xs font-bold text-slate-900">
                {formatEGP(invoice.invoicedTotal)}
              </span>
            </div>
            {invoice.expectedTotal > 0 && (
              <>
                <span className="text-slate-300">/</span>
                <div className="text-right">
                  <span className="text-[10px] text-slate-400 uppercase tracking-wider block font-medium">
                    Expected PO Total
                  </span>
                  <span className="font-mono text-xs font-semibold text-emerald-700">
                    {formatEGP(invoice.expectedTotal)}
                  </span>
                </div>
              </>
            )}
          </div>
        </div>
      </header>

      {/* 50/50 Split Screen Master Viewport */}
      <div className="flex-1 min-h-0 grid grid-cols-1 lg:grid-cols-2 overflow-hidden">
        {/* LEFT COLUMN: Original Document Preview */}
        <section className="relative flex flex-col h-full min-h-0 bg-slate-900 border-r border-slate-200 overflow-hidden">
          <DocumentViewer
            fileUri={fileUri}
            fileName={invoice.invoiceNumber}
            isImage={!!isImageFile}
          />
        </section>

        {/* RIGHT COLUMN: Structured Reconciliation Workspace & Findings */}
        <section className="flex flex-col h-full min-h-0 bg-slate-50 overflow-hidden">
          {/* View Switcher Header Bar */}
          <div className="h-11 bg-white border-b border-slate-200 px-6 flex items-center justify-between shrink-0">
            <div className="flex items-center gap-1.5">
              <span className="text-xs font-bold text-slate-800 uppercase tracking-wider">
                Auditor Workspace:
              </span>
            </div>

            <div className="flex items-center gap-1 bg-slate-100 p-0.5 rounded-lg border border-slate-200">
              <button
                onClick={() => setActiveRightTab("findings")}
                className={`inline-flex items-center gap-1.5 px-3 py-1 rounded-md text-xs font-semibold transition-colors ${
                  activeRightTab === "findings"
                    ? "bg-white text-slate-900 shadow-xs"
                    : "text-slate-600 hover:text-slate-900"
                }`}
              >
                <Scale className="w-3.5 h-3.5 text-indigo-600" />
                <span>Audit Findings ({invoice.audits ? invoice.audits.length : 0})</span>
              </button>

              <button
                onClick={() => setActiveRightTab("dispute")}
                className={`inline-flex items-center gap-1.5 px-3 py-1 rounded-md text-xs font-semibold transition-colors ${
                  activeRightTab === "dispute"
                    ? "bg-white text-slate-900 shadow-xs"
                    : "text-slate-600 hover:text-slate-900"
                }`}
              >
                <MessageSquareWarning className="w-3.5 h-3.5 text-purple-600" />
                <span>Dispute Draft & Notice</span>
              </button>

              <button
                onClick={() => setActiveRightTab("combined")}
                className={`inline-flex items-center gap-1.5 px-3 py-1 rounded-md text-xs font-semibold transition-colors ${
                  activeRightTab === "combined"
                    ? "bg-white text-slate-900 shadow-xs"
                    : "text-slate-600 hover:text-slate-900"
                }`}
              >
                <Layers className="w-3.5 h-3.5 text-slate-600" />
                <span>Combined View</span>
              </button>
            </div>
          </div>

          {/* Scrollable Content Container */}
          <div className="flex-1 min-h-0 overflow-y-auto p-6 space-y-6">
            {/* 1. FINANCIAL VARIANCE OVERVIEW CARD */}
            <div className="bg-white rounded-2xl border border-slate-200 p-5 shadow-xs">
              <div className="flex items-center justify-between pb-3 border-b border-slate-100">
                <div>
                  <h3 className="text-sm font-bold text-slate-900">
                    Deterministic Audit Summary
                  </h3>
                  <p className="text-xs text-slate-500">
                    Java BigDecimal comparison against PO reference records
                  </p>
                </div>

                {invoice.discrepancyCount > 0 ? (
                  <span className="inline-flex items-center gap-1 px-2.5 py-1 rounded-full text-xs font-bold bg-rose-50 text-rose-700 border border-rose-200">
                    <AlertOctagon className="w-3.5 h-3.5" />
                    {invoice.discrepancyCount} Discrepanc{invoice.discrepancyCount === 1 ? "y" : "ies"} Found
                  </span>
                ) : (
                  <span className="inline-flex items-center gap-1 px-2.5 py-1 rounded-full text-xs font-bold bg-emerald-50 text-emerald-700 border border-emerald-200">
                    <CheckCircle2 className="w-3.5 h-3.5" /> Perfect Match
                  </span>
                )}
              </div>

              <div className="grid grid-cols-3 gap-4 pt-4">
                <div className="bg-slate-50 p-3 rounded-xl border border-slate-100">
                  <span className="text-[11px] text-slate-500 uppercase tracking-wider block font-medium">
                    Billed Amount
                  </span>
                  <span className="text-base font-mono font-bold text-slate-900 mt-0.5 block">
                    {formatEGP(invoice.invoicedTotal)}
                  </span>
                </div>

                <div className="bg-slate-50 p-3 rounded-xl border border-slate-100">
                  <span className="text-[11px] text-slate-500 uppercase tracking-wider block font-medium">
                    Agreed PO Amount
                  </span>
                  <span className="text-base font-mono font-bold text-emerald-700 mt-0.5 block">
                    {formatEGP(invoice.expectedTotal)}
                  </span>
                </div>

                <div
                  className={`p-3 rounded-xl border ${
                    Math.abs(varianceDelta) > 0.01
                      ? "bg-rose-50/80 border-rose-200"
                      : "bg-emerald-50/80 border-emerald-200"
                  }`}
                >
                  <span className="text-[11px] uppercase tracking-wider block font-medium text-slate-600">
                    Financial Variance
                  </span>
                  <span
                    className={`text-base font-mono font-bold mt-0.5 block ${
                      Math.abs(varianceDelta) > 0.01 ? "text-rose-700" : "text-emerald-700"
                    }`}
                  >
                    {varianceDelta > 0 ? `+${formatEGP(varianceDelta)}` : formatEGP(varianceDelta)}
                  </span>
                </div>
              </div>
            </div>

            {/* 1.5 ALL BILLED LINE ITEMS FROM SUPPLIER DOCUMENT */}
            {(activeRightTab === "findings" || activeRightTab === "combined") && invoice.billedItems && invoice.billedItems.length > 0 && (
              <div className="bg-white rounded-2xl border border-slate-200 shadow-xs overflow-hidden">
                <div className="px-5 py-3.5 bg-slate-50/80 border-b border-slate-200 flex items-center justify-between">
                  <div className="flex items-center gap-2">
                    <FileText className="w-4 h-4 text-indigo-600" />
                    <h4 className="text-xs font-bold uppercase tracking-wider text-slate-800">
                      All Extracted Line Items from Document ({invoice.billedItems.length})
                    </h4>
                  </div>
                  <span className="text-[11px] text-slate-500 font-medium">
                    1:1 Correlated with Purchase Order
                  </span>
                </div>

                <div className="overflow-x-auto">
                  <table className="w-full text-left border-collapse text-xs">
                    <thead>
                      <tr className="bg-slate-50/50 border-b border-slate-100 text-slate-500 font-semibold text-[11px]">
                        <th className="px-4 py-2.5">Item &amp; Description</th>
                        <th className="px-3 py-2.5 text-right">Billed Qty</th>
                        <th className="px-3 py-2.5 text-right">Unit Price</th>
                        <th className="px-3 py-2.5 text-right">Line Total</th>
                        <th className="px-4 py-2.5 text-center">Reconciliation Match Status</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-slate-100">
                      {invoice.billedItems.map((item, idx) => {
                        const isMatched = item.matchStatus === "MATCHED";
                        return (
                          <tr key={idx} className="hover:bg-slate-50/60 transition-colors">
                            <td className="px-4 py-3">
                              <div className="font-semibold text-slate-900">{item.description}</div>
                              {item.skuCode && (
                                <span className="font-mono text-[10px] text-indigo-700 bg-indigo-50 border border-indigo-100 px-1.5 py-0.5 rounded mt-0.5 inline-block">
                                  SKU: {item.skuCode}
                                </span>
                              )}
                            </td>
                            <td className="px-3 py-3 text-right font-mono text-slate-700">
                              {item.quantity ?? "—"}
                            </td>
                            <td className="px-3 py-3 text-right font-mono text-slate-700">
                              {formatEGP(item.unitPrice)}
                            </td>
                            <td className="px-3 py-3 text-right font-mono font-bold text-slate-900">
                              {formatEGP(item.lineTotal)}
                            </td>
                            <td className="px-4 py-3 text-center">
                              {isMatched ? (
                                <span className="inline-flex items-center gap-1 px-2.5 py-1 rounded-full text-[11px] font-bold bg-emerald-50 text-emerald-800 border border-emerald-200">
                                  <CheckCircle2 className="w-3.5 h-3.5 text-emerald-600" />
                                  100% Matched PO Terms
                                </span>
                              ) : (
                                <span className={`inline-flex items-center gap-1 px-2.5 py-1 rounded-full text-[11px] font-bold border ${getIssueBadge(item.matchStatus).badge}`}>
                                  <AlertTriangle className="w-3.5 h-3.5" />
                                  {item.statusLabel}
                                </span>
                              )}
                            </td>
                          </tr>
                        );
                      })}
                    </tbody>
                  </table>
                </div>
              </div>
            )}

            {/* 2. ITEMIZED AUDIT FINDINGS (Rendered if tab is "findings" or "combined") */}
            {(activeRightTab === "findings" || activeRightTab === "combined") && (
              <div className="bg-white rounded-2xl border border-slate-200 shadow-xs overflow-hidden">
                <div className="px-5 py-3.5 bg-slate-50/80 border-b border-slate-200 flex items-center justify-between">
                  <div className="flex items-center gap-2">
                    <Scale className="w-4 h-4 text-indigo-600" />
                    <h4 className="text-xs font-bold uppercase tracking-wider text-slate-800">
                      Line Item Audit Findings ({invoice.audits ? invoice.audits.length : 0})
                    </h4>
                  </div>
                  <span className="text-[11px] text-slate-400 font-mono">
                    Audited with 0.01 EGP tolerance
                  </span>
                </div>

                {invoice.audits && invoice.audits.length > 0 ? (
                  <div className="divide-y divide-slate-100">
                    {invoice.audits.map((audit) => {
                      const issue = getIssueBadge(audit.issueType);
                      const isNegativeVariance =
                        audit.actualValue && audit.expectedValue
                          ? audit.actualValue > audit.expectedValue
                          : false;

                      const varianceAmount =
                        audit.actualValue && audit.expectedValue
                          ? audit.actualValue - audit.expectedValue
                          : audit.actualValue ?? 0;

                      return (
                        <div
                          key={audit.id}
                          className="p-5 hover:bg-slate-50/60 transition-colors flex flex-col gap-3"
                        >
                          <div className="flex items-start justify-between gap-4">
                            <div className="space-y-1.5 flex-1">
                              <div className="flex flex-wrap items-center gap-2">
                                <span
                                  className={`px-2.5 py-0.5 rounded-md text-[11px] font-bold border ${issue.badge}`}
                                >
                                  {issue.label}
                                </span>
                                {audit.skuCode && (
                                  <span className="font-mono text-xs text-indigo-700 bg-indigo-50 border border-indigo-100 px-2 py-0.5 rounded font-medium">
                                    SKU: {audit.skuCode}
                                  </span>
                                )}
                              </div>
                              <p className="text-sm font-semibold text-slate-900">
                                {audit.itemDescription || "Unspecified Item"}
                              </p>
                            </div>

                            <div className="text-right shrink-0 bg-slate-50 p-2.5 rounded-xl border border-slate-200/80">
                              <div className="text-[10px] text-slate-400 uppercase font-medium">
                                PO Rate vs Billed Rate
                              </div>
                              <div className="flex items-center gap-2 justify-end mt-0.5">
                                {audit.expectedValue !== undefined && audit.expectedValue !== null && (
                                  <span className="text-xs font-mono text-slate-400 line-through">
                                    {formatEGP(audit.expectedValue)}
                                  </span>
                                )}
                                <span
                                  className={`text-sm font-mono font-bold ${
                                    isNegativeVariance ? "text-rose-700" : "text-slate-900"
                                  }`}
                                >
                                  {formatEGP(audit.actualValue)}
                                </span>
                              </div>
                              {varianceAmount !== 0 && (
                                <span className="text-[10px] font-mono font-semibold text-rose-600 block mt-0.5">
                                  {varianceAmount > 0 ? `+${formatEGP(varianceAmount)} delta` : `${formatEGP(varianceAmount)} delta`}
                                </span>
                              )}
                            </div>
                          </div>

                          {/* Dual-Language Separated Audit Findings */}
                          <div className="p-3.5 bg-rose-50/70 rounded-xl border border-rose-200/80 text-xs space-y-2.5">
                            {/* Arabic Row */}
                            <div className="text-rose-950 font-medium leading-relaxed text-right" dir="rtl">
                              {audit.explanationArabic ||
                                (audit.explanation.includes(" — ")
                                  ? audit.explanation.split(" — ")[0].trim()
                                  : audit.explanation)}
                            </div>

                            {/* Separator Line */}
                            <div className="border-t border-rose-200/60" />

                            {/* English Row */}
                            <div className="text-rose-900 font-normal leading-relaxed text-left" dir="ltr">
                              {audit.explanationEnglish ||
                                (audit.explanation.includes(" — ")
                                  ? audit.explanation.split(" — ").slice(1).join(" — ").trim()
                                  : audit.explanation)}
                            </div>
                          </div>
                        </div>
                      );
                    })}
                  </div>
                ) : (
                  <div className="p-10 text-center text-slate-400">
                    <CheckCircle2 className="w-10 h-10 text-emerald-500 mx-auto mb-2" />
                    <p className="text-sm font-medium text-slate-700">No discrepancies detected</p>
                    <p className="text-xs text-slate-400 mt-1">
                      All invoiced line items match the purchase order agreed prices and quantities.
                    </p>
                  </div>
                )}
              </div>
            )}

            {/* 3. GEMINI AI BILINGUAL DISPUTE DRAWER (Rendered if tab is "dispute" or "combined") */}
            {(activeRightTab === "dispute" || activeRightTab === "combined") && (
              <DisputeActionDrawer invoice={invoice} onInvoiceUpdated={handleUpdate} />
            )}
          </div>
        </section>
      </div>
    </div>
  );
}
