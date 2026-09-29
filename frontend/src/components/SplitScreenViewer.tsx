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

interface SplitScreenViewerProps {
  invoice: ReconciliationSummaryResponse;
  onInvoiceUpdated?: (updated: ReconciliationSummaryResponse) => void;
}

export default function SplitScreenViewer({
  invoice: initialInvoice,
  onInvoiceUpdated,
}: SplitScreenViewerProps) {
  const [invoice, setInvoice] = useState<ReconciliationSummaryResponse>(initialInvoice);
  const [zoomLevel, setZoomLevel] = useState<number>(100);
  const [activeRightTab, setActiveRightTab] = useState<"findings" | "dispute" | "combined">("combined");

  const handleUpdate = (updated: ReconciliationSummaryResponse) => {
    setInvoice(updated);
    if (onInvoiceUpdated) {
      onInvoiceUpdated(updated);
    }
  };

  const handleZoomIn = () => setZoomLevel((prev) => Math.min(prev + 15, 200));
  const handleZoomOut = () => setZoomLevel((prev) => Math.max(prev - 15, 60));
  const handleZoomReset = () => setZoomLevel(100);

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
          bg: "bg-emerald-50 text-emerald-700 border-emerald-200",
          icon: <CheckCircle2 className="w-4 h-4 text-emerald-600" />,
          label: "Approved for Payment",
        };
      case "REJECTED":
        return {
          bg: "bg-rose-50 text-rose-700 border-rose-200",
          icon: <XCircle className="w-4 h-4 text-rose-600" />,
          label: "Rejected - Dispute Issued",
        };
      case "FLAGGED_DISCREPANCY":
        return {
          bg: "bg-rose-50 text-rose-700 border-rose-200",
          icon: <AlertOctagon className="w-4 h-4 text-rose-600" />,
          label: "Flagged Discrepancy",
        };
      case "MANUAL_REVIEW":
      default:
        return {
          bg: "bg-amber-50 text-amber-700 border-amber-200",
          icon: <AlertTriangle className="w-4 h-4 text-amber-600" />,
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
          {/* Document Viewer Toolbar */}
          <div className="h-11 bg-slate-900 border-b border-slate-800 px-4 flex items-center justify-between text-xs text-slate-300 shrink-0">
            <div className="flex items-center gap-2">
              <FileText className="w-4 h-4 text-indigo-400" />
              <span className="font-medium text-slate-200 truncate max-w-xs">
                Original Supplier Document
              </span>
              <span className="text-[10px] text-slate-500 font-mono">({zoomLevel}%)</span>
            </div>

            <div className="flex items-center gap-1">
              <button
                onClick={handleZoomOut}
                className="p-1.5 hover:bg-slate-800 rounded-md text-slate-400 hover:text-white transition-colors"
                title="Zoom Out"
              >
                <ZoomOut className="w-3.5 h-3.5" />
              </button>
              <button
                onClick={handleZoomReset}
                className="px-2 py-1 hover:bg-slate-800 rounded-md text-[11px] font-mono text-slate-400 hover:text-white transition-colors"
                title="Reset Zoom"
              >
                100%
              </button>
              <button
                onClick={handleZoomIn}
                className="p-1.5 hover:bg-slate-800 rounded-md text-slate-400 hover:text-white transition-colors"
                title="Zoom In"
              >
                <ZoomIn className="w-3.5 h-3.5" />
              </button>

              <div className="h-3 w-px bg-slate-700 mx-1" />

              <a
                href={fileUri}
                target="_blank"
                rel="noreferrer"
                className="p-1.5 hover:bg-slate-800 rounded-md text-slate-400 hover:text-white transition-colors flex items-center gap-1"
                title="Open in new browser tab"
              >
                <ExternalLink className="w-3.5 h-3.5" />
                <span className="text-[11px]">New Tab</span>
              </a>

              <a
                href={fileUri}
                download
                className="p-1.5 hover:bg-slate-800 rounded-md text-slate-400 hover:text-white transition-colors flex items-center gap-1"
                title="Download original file"
              >
                <Download className="w-3.5 h-3.5" />
              </a>
            </div>
          </div>

          {/* Native PDF / Image Frame with Zoom */}
          <div className="flex-1 min-h-0 w-full h-full overflow-hidden bg-slate-950 flex items-center justify-center p-3 relative">
            <div
              style={{
                transform: `scale(${zoomLevel / 100})`,
                transformOrigin: "top center",
                transition: "transform 0.15s ease-out",
                width: "100%",
                height: "100%",
              }}
              className="w-full h-full flex items-center justify-center"
            >
              {isImageFile ? (
                <img
                  src={fileUri}
                  alt="Invoice Document"
                  className="max-w-full max-h-full object-contain rounded-md shadow-2xl border border-slate-800 bg-white"
                />
              ) : (
                <object
                  data={`${fileUri}#toolbar=0&navpanes=0`}
                  type="application/pdf"
                  className="w-full h-full rounded-md shadow-2xl border border-slate-800 bg-white"
                >
                  <iframe
                    src={fileUri}
                    title="Invoice Document Preview"
                    className="w-full h-full rounded-md shadow-2xl border border-slate-800 bg-white"
                  >
                    <div className="p-8 text-center text-slate-400 bg-slate-900 rounded-xl flex flex-col items-center justify-center space-y-3">
                      <FileText className="w-10 h-10 text-slate-500" />
                      <p className="text-sm font-medium text-slate-300">
                        Unable to render embedded PDF preview in this browser viewport.
                      </p>
                      <a
                        href={fileUri}
                        target="_blank"
                        rel="noreferrer"
                        className="inline-flex items-center gap-1.5 px-4 py-2 rounded-lg bg-indigo-600 hover:bg-indigo-700 text-white text-xs font-semibold shadow-sm transition-colors"
                      >
                        <ExternalLink className="w-3.5 h-3.5" /> Open Document in New Tab
                      </a>
                    </div>
                  </iframe>
                </object>
              )}
            </div>
          </div>
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

                          <div className="p-3 bg-rose-50/70 rounded-xl border border-rose-200/80 text-rose-900 text-xs leading-relaxed flex items-start gap-2">
                            <AlertOctagon className="w-4 h-4 text-rose-600 shrink-0 mt-0.5" />
                            <span>{audit.explanation}</span>
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
