"use client";

import React, { useEffect, useState, useMemo } from "react";
import Link from "next/link";
import {
  FileText,
  AlertOctagon,
  CheckCircle2,
  AlertTriangle,
  ArrowRight,
  TrendingUp,
  RefreshCw,
  Search,
  Receipt,
  Layers,
  XCircle,
  ShieldCheck,
  Sparkles,
  ChevronDown,
  ChevronUp,
  Users,
  ShieldAlert,
  BarChart3,
} from "lucide-react";
import type {
  InvoiceListItemResponse,
  ReconciliationSummaryResponse,
  FinancialRoiSummaryResponse,
} from "@/lib/types";
import { fetchInvoices, fetchRoiSummary } from "@/lib/api";
import DropzoneUploader from "@/components/DropzoneUploader";

export default function DashboardClient() {
  const [invoices, setInvoices] = useState<InvoiceListItemResponse[]>([]);
  const [roiSummary, setRoiSummary] = useState<FinancialRoiSummaryResponse | null>(null);
  const [isLoading, setIsLoading] = useState<boolean>(true);
  const [isRoiLoading, setIsRoiLoading] = useState<boolean>(true);
  const [searchQuery, setSearchQuery] = useState<string>("");
  const [statusFilter, setStatusFilter] = useState<string>("ALL");
  const [isExecutiveBriefOpen, setIsExecutiveBriefOpen] = useState<boolean>(true);


  const loadData = async () => {
    setIsLoading(true);
    setIsRoiLoading(true);
    try {
      const [invoiceData, roiData] = await Promise.all([
        fetchInvoices(),
        fetchRoiSummary().catch((err) => {
          console.warn("Could not fetch ROI summary:", err);
          return null;
        }),
      ]);
      setInvoices(invoiceData);
      setRoiSummary(roiData);
    } catch (err) {
      console.error("Failed to load dashboard data:", err);
    } finally {
      setIsLoading(false);
      setIsRoiLoading(false);
    }
  };

  useEffect(() => {
    loadData();
  }, []);

  const handleUploadSuccess = (_summary: ReconciliationSummaryResponse) => {
    loadData();
  };

  // Metrics fallback if ROI summary is loading
  const metrics = useMemo(() => {
    const total = invoices.length;
    const discrepancies = invoices.filter(
      (i) => i.reconciliationStatus === "FLAGGED_DISCREPANCY"
    ).length;
    const approved = invoices.filter((i) => i.reconciliationStatus === "APPROVED").length;
    const rejected = invoices.filter((i) => i.reconciliationStatus === "REJECTED").length;
    const manualReview = invoices.filter(
      (i) => i.reconciliationStatus === "MANUAL_REVIEW"
    ).length;
    const totalVolume = invoices.reduce((acc, curr) => acc + (curr.invoicedTotal || 0), 0);

    return { total, discrepancies, approved, rejected, manualReview, totalVolume };
  }, [invoices]);

  // Filtered invoices
  const filteredInvoices = useMemo(() => {
    return invoices.filter((inv) => {
      const matchesSearch =
        inv.invoiceNumber.toLowerCase().includes(searchQuery.toLowerCase()) ||
        (inv.vendorName && inv.vendorName.toLowerCase().includes(searchQuery.toLowerCase())) ||
        (inv.poReference && inv.poReference.toLowerCase().includes(searchQuery.toLowerCase()));

      const matchesStatus =
        statusFilter === "ALL" || inv.reconciliationStatus === statusFilter;

      return matchesSearch && matchesStatus;
    });
  }, [invoices, searchQuery, statusFilter]);

  const formatEGP = (amount?: number) => {
    if (amount === undefined || amount === null) return "0.00 EGP";
    return new Intl.NumberFormat("en-EG", {
      style: "currency",
      currency: "EGP",
      minimumFractionDigits: 2,
    }).format(amount);
  };

  const getStatusBadge = (status: string) => {
    switch (status) {
      case "APPROVED":
        return {
          bg: "bg-emerald-50 text-emerald-800 border-emerald-300",
          icon: <CheckCircle2 className="w-3.5 h-3.5 text-emerald-600" />,
          label: "Approved",
        };
      case "FLAGGED_DISCREPANCY":
        return {
          bg: "bg-rose-50 text-rose-800 border-rose-300",
          icon: <AlertTriangle className="w-3.5 h-3.5 text-rose-600" />,
          label: "Flagged Discrepancy",
        };
      case "MANUAL_REVIEW":
        return {
          bg: "bg-amber-50 text-amber-900 border-amber-300",
          icon: <Layers className="w-3.5 h-3.5 text-amber-600" />,
          label: "PO Not Found / Manual",
        };
      case "RESOLVED_BY_DEBIT_NOTE":
        return {
          bg: "bg-teal-50 text-teal-800 border-teal-300",
          icon: <ShieldCheck className="w-3.5 h-3.5 text-teal-600" />,
          label: "Settled (Debit Note)",
        };
      case "REJECTED":
        return {
          bg: "bg-slate-100 text-slate-800 border-slate-300",
          icon: <XCircle className="w-3.5 h-3.5 text-slate-600" />,
          label: "Rejected",
        };
      default:
        return {
          bg: "bg-slate-50 text-slate-700 border-slate-200",
          icon: <AlertOctagon className="w-3.5 h-3.5 text-slate-500" />,
          label: status,
        };
    }
  };

  const leakageBlocked = roiSummary?.totalCashLeakageBlocked ?? 0;
  const invoicedVolume = roiSummary?.totalInvoicedVolume ?? metrics.totalVolume;
  const accuracyRate = roiSummary?.accuracyRate ?? (metrics.total > 0 ? (metrics.approved / metrics.total) * 100 : 100);

  return (
    <div className="min-h-screen bg-slate-50/60 pb-16 font-sans">
      {/* Top Banner & Navigation Header */}
      <header className="bg-slate-900 text-white border-b border-slate-800 shadow-sm sticky top-0 z-30">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-3.5 flex flex-col md:flex-row md:items-center justify-between gap-4">
          <div className="flex items-center space-x-3">
            <div className="w-10 h-10 rounded-xl bg-gradient-to-tr from-indigo-500 via-indigo-600 to-emerald-500 flex items-center justify-center shadow-lg shadow-indigo-500/20">
              <Layers className="w-5 h-5 text-white" />
            </div>
            <div>
              <h1 className="text-base font-bold tracking-tight text-white flex items-center gap-2">
                <span>Autonomous AP Reconciliation Agent</span>
                <span className="text-[10px] uppercase font-mono font-semibold px-2 py-0.5 rounded-full bg-emerald-500/20 text-emerald-300 border border-emerald-500/30 flex items-center gap-1">
                  <ShieldCheck className="w-3 h-3 text-emerald-400" />
                  CFO Financial Guard
                </span>
              </h1>
              <p className="text-xs text-slate-400">
                0.00 EGP Zero-Tolerance &bull; Multimodal AI Perception &bull; Pure Java Math Verification
              </p>
            </div>
          </div>

          {/* Navigation Tabs */}
          <div className="flex items-center space-x-2">
            <Link
              href="/"
              className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-semibold bg-indigo-600 text-white shadow-xs"
            >
              <BarChart3 className="w-3.5 h-3.5" />
              <span>Audited Queue</span>
            </Link>
            <Link
              href="/vendors"
              className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-semibold text-slate-300 hover:text-white bg-slate-800/80 hover:bg-slate-800 border border-slate-700 transition-colors"
            >
              <Users className="w-3.5 h-3.5 text-indigo-400" />
              <span>Vendor Scorecards</span>
              <span className="px-1.5 py-0.2 text-[9px] font-bold rounded-full bg-emerald-500/20 text-emerald-300 border border-emerald-500/30">
                Phase 1
              </span>
            </Link>
            <button
              onClick={loadData}
              disabled={isLoading || isRoiLoading}
              className="inline-flex items-center space-x-1.5 px-3 py-1.5 rounded-lg text-xs font-medium text-slate-300 hover:text-white bg-slate-800 hover:bg-slate-700 border border-slate-700 transition-colors ml-1"
            >
              <RefreshCw className={`w-3.5 h-3.5 ${isLoading || isRoiLoading ? "animate-spin" : ""}`} />
              <span>Refresh</span>
            </button>
          </div>
        </div>
      </header>

      <main className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 pt-8 space-y-8">
        {/* CFO HERO ROI METRICS (Phase 1.1) */}
        <section className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
          {/* HERO CARD: Leakage Prevented */}
          <div className="relative overflow-hidden rounded-2xl bg-gradient-to-br from-slate-900 via-indigo-950 to-slate-900 p-5 text-white shadow-xl shadow-indigo-950/20 border border-indigo-500/30 flex flex-col justify-between">
            <div className="absolute -right-6 -bottom-6 w-28 h-28 bg-emerald-500/10 rounded-full blur-2xl pointer-events-none" />
            <div>
              <div className="flex items-center justify-between mb-2">
                <span className="inline-flex items-center gap-1.5 px-2.5 py-0.5 rounded-full text-[10px] font-bold tracking-wider uppercase bg-emerald-500/20 text-emerald-300 border border-emerald-500/30">
                  <ShieldCheck className="w-3.5 h-3.5 text-emerald-400" />
                  Direct Cash Saved
                </span>
                <span className="text-[10px] font-mono text-indigo-300 font-semibold">
                  Zero-Tolerance
                </span>
              </div>
              <p className="text-xs text-slate-300 font-medium">Cash Leakage Blocked</p>
              <p className="text-2xl sm:text-3xl font-extrabold font-mono tracking-tight text-white mt-1">
                {formatEGP(leakageBlocked)}
              </p>
            </div>
            <div className="mt-4 pt-3 border-t border-slate-800/80 flex items-center justify-between text-[11px] text-slate-400">
              <span className="truncate">
                Price: <strong className="text-emerald-400 font-mono font-semibold">{formatEGP(roiSummary?.priceMismatchLeakage ?? 0)}</strong>
              </span>
              <span className="truncate">
                Fees: <strong className="text-indigo-300 font-mono font-semibold">{formatEGP(roiSummary?.extraFeesBlocked ?? 0)}</strong>
              </span>
            </div>
          </div>

          {/* CARD 2: Total Audited Volume */}
          <div className="bg-white rounded-2xl p-5 border border-slate-200/80 shadow-xs flex flex-col justify-between hover:border-slate-300 transition-colors">
            <div className="flex items-center justify-between mb-2">
              <span className="text-[11px] font-bold text-slate-500 uppercase tracking-wider">
                Audited AP Volume
              </span>
              <div className="w-8 h-8 rounded-lg bg-indigo-50 text-indigo-600 flex items-center justify-center">
                <Receipt className="w-4 h-4" />
              </div>
            </div>
            <div>
              <p className="text-2xl font-bold font-mono text-slate-900">
                {formatEGP(invoicedVolume)}
              </p>
              <p className="text-xs text-slate-500 mt-1 flex items-center gap-1.5">
                <span className="font-semibold text-slate-700">{roiSummary?.totalInvoicesAudited ?? metrics.total}</span> invoices processed
              </p>
            </div>
            <div className="mt-4 pt-3 border-t border-slate-100 text-[11px] text-slate-500 flex items-center justify-between">
              <span>Avg/Invoice:</span>
              <span className="font-mono font-medium text-slate-700">
                {formatEGP(metrics.total > 0 ? invoicedVolume / metrics.total : 0)}
              </span>
            </div>
          </div>

          {/* CARD 3: Audit Accuracy & Clean Match Rate */}
          <div className="bg-white rounded-2xl p-5 border border-slate-200/80 shadow-xs flex flex-col justify-between hover:border-slate-300 transition-colors">
            <div className="flex items-center justify-between mb-2">
              <span className="text-[11px] font-bold text-slate-500 uppercase tracking-wider">
                Clean Match Rate
              </span>
              <div className="w-8 h-8 rounded-lg bg-emerald-50 text-emerald-600 flex items-center justify-center">
                <CheckCircle2 className="w-4 h-4" />
              </div>
            </div>
            <div>
              <div className="flex items-baseline gap-2">
                <p className="text-2xl font-bold font-mono text-emerald-700">
                  {accuracyRate.toFixed(1)}%
                </p>
                <span className="text-[11px] font-medium text-slate-400">100% Fidelity</span>
              </div>
              {/* Progress Bar */}
              <div className="w-full bg-slate-100 rounded-full h-2 mt-2 overflow-hidden">
                <div
                  className="bg-emerald-500 h-2 rounded-full transition-all duration-500"
                  style={{ width: `${Math.min(100, Math.max(0, accuracyRate))}%` }}
                />
              </div>
            </div>
            <div className="mt-4 pt-3 border-t border-slate-100 text-[11px] text-slate-500 flex items-center justify-between">
              <span>Clean Invoices:</span>
              <span className="font-semibold text-emerald-700 font-mono">
                {roiSummary?.cleanInvoicesCount ?? metrics.approved} / {roiSummary?.totalInvoicesAudited ?? metrics.total}
              </span>
            </div>
          </div>

          {/* CARD 4: Discrepancy & Dispute Load */}
          <div className="bg-white rounded-2xl p-5 border border-slate-200/80 shadow-xs flex flex-col justify-between hover:border-slate-300 transition-colors">
            <div className="flex items-center justify-between mb-2">
              <span className="text-[11px] font-bold text-slate-500 uppercase tracking-wider">
                Intercepted Variances
              </span>
              <div className="w-8 h-8 rounded-lg bg-rose-50 text-rose-600 flex items-center justify-center">
                <ShieldAlert className="w-4 h-4" />
              </div>
            </div>
            <div>
              <p className="text-2xl font-bold font-mono text-rose-700">
                {roiSummary?.discrepancyInvoicesCount ?? metrics.discrepancies}
              </p>
              <p className="text-xs text-slate-500 mt-1">
                Disputed invoices held for credit note
              </p>
            </div>
            <div className="mt-4 pt-3 border-t border-slate-100 text-[11px] text-slate-500 flex items-center justify-between">
              <span>Manual Review:</span>
              <span className="font-semibold text-amber-600 font-mono">
                {roiSummary?.manualReviewCount ?? metrics.manualReview}
              </span>
            </div>
          </div>
        </section>

        {/* AI EXECUTIVE BRIEF DRAWER / BANNER */}
        {roiSummary?.executiveBrief && (() => {
          const isArabicText = (text: string) => /[\u0600-\u06FF]/.test(text);
          const briefBlocks = (roiSummary.executiveBrief || "")
            .split(/\n{2,}/)
            .map((b) => b.trim())
            .filter((b) => b.length > 0);

          return (
            <section className="bg-gradient-to-r from-indigo-50/90 via-purple-50/70 to-slate-50/90 rounded-2xl p-5 border border-indigo-200/80 shadow-xs">
              <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
                <div className="flex items-center space-x-2.5">
                  <div className="w-8 h-8 rounded-lg bg-indigo-600 text-white flex items-center justify-center shadow-xs">
                    <Sparkles className="w-4 h-4" />
                  </div>
                  <div>
                    <h3 className="text-sm font-bold text-slate-900 flex items-center gap-2">
                      <span>Autonomous AI Executive Brief</span>
                      <span className="text-[10px] font-mono px-2 py-0.5 rounded-full bg-indigo-100 text-indigo-700 border border-indigo-200 font-semibold">
                        Gemini 3.8 Flash &bull; CFO Insights
                      </span>
                    </h3>
                    <p className="text-xs text-slate-500">
                      Real-time synthesis of supply chain leakage, variance trends, and financial risk
                    </p>
                  </div>
                </div>

                <button
                  onClick={() => setIsExecutiveBriefOpen(!isExecutiveBriefOpen)}
                  className="inline-flex items-center gap-1 text-xs font-semibold text-indigo-600 hover:text-indigo-800 bg-white/80 hover:bg-white px-2.5 py-1.5 rounded-lg border border-indigo-200 transition-colors self-end sm:self-auto"
                >
                  <span>{isExecutiveBriefOpen ? "Collapse" : "Expand Brief"}</span>
                  {isExecutiveBriefOpen ? (
                    <ChevronUp className="w-3.5 h-3.5" />
                  ) : (
                    <ChevronDown className="w-3.5 h-3.5" />
                  )}
                </button>
              </div>

              {isExecutiveBriefOpen && (
                <div className="mt-4 pt-4 border-t border-indigo-100 space-y-3">
                  {/* Single unified card holding both English LTR and Arabic RTL */}
                  <div className="p-4 bg-white/95 rounded-xl border border-indigo-100 shadow-xs space-y-3 font-sans">
                    {briefBlocks.map((block, idx) => {
                      const isAr = isArabicText(block);
                      return (
                        <p
                          key={idx}
                          dir={isAr ? "rtl" : "ltr"}
                          className={`leading-relaxed whitespace-pre-line ${
                            isAr
                              ? "text-right text-slate-800 text-[13px] leading-7 pt-2.5 border-t border-slate-100"
                              : "text-left text-slate-700 text-xs"
                          }`}
                        >
                          {block}
                        </p>
                      );
                    })}
                  </div>

                  {roiSummary.topOffenses && roiSummary.topOffenses.length > 0 && (
                    <div className="flex flex-wrap items-center gap-2 pt-1 text-[11px]">
                      <span className="text-slate-500 font-medium">Top Leakage Vulnerabilities:</span>
                      {roiSummary.topOffenses.slice(0, 3).map((offense, idx) => (
                        <span
                          key={idx}
                          className="inline-flex items-center gap-1 px-2.5 py-1 rounded-lg bg-rose-50 text-rose-700 border border-rose-200 font-medium font-mono"
                        >
                          <span>{offense.label}</span>
                          <strong className="text-rose-900">({formatEGP(offense.totalAmount)})</strong>
                        </span>
                      ))}
                    </div>
                  )}
                </div>
              )}
            </section>
          );
        })()}


        {/* Upload Dropzone Section */}
        <section>
          <div className="mb-3">
            <h2 className="text-sm font-bold text-slate-800 tracking-tight flex items-center gap-2">
              <span>Upload Supplier Invoice Document</span>
              <span className="text-[11px] font-normal text-slate-500">
                (PDF, PNG, JPG - Multimodal Extraction + Deterministic Java Reconciliation)
              </span>
            </h2>
          </div>
          <DropzoneUploader
            onSuccess={handleUploadSuccess}
            redirectToInvoice={true}
          />
        </section>

        {/* Invoices Queue Table */}
        <section className="bg-white rounded-2xl border border-slate-200/80 shadow-xs overflow-hidden">
          {/* Table Controls */}
          <div className="p-4 sm:p-5 border-b border-slate-100 flex flex-col sm:flex-row items-center justify-between gap-3 bg-slate-50/40">
            <div className="relative w-full sm:w-72">
              <Search className="w-4 h-4 text-slate-400 absolute left-3 top-1/2 -translate-y-1/2" />
              <input
                type="text"
                placeholder="Search invoice, vendor, or PO..."
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
                className="w-full pl-9 pr-3 py-1.5 text-xs rounded-xl border border-slate-200 bg-white text-slate-800 placeholder-slate-400 focus:outline-hidden focus:ring-2 focus:ring-indigo-500 focus:border-transparent transition-all"
              />
            </div>

            {/* Status Tabs Filter */}
            <div className="flex items-center gap-1 overflow-x-auto w-full sm:w-auto p-1 bg-slate-200/60 rounded-xl">
              {[
                { key: "ALL", label: "All Records" },
                { key: "FLAGGED_DISCREPANCY", label: "Flagged" },
                { key: "RESOLVED_BY_DEBIT_NOTE", label: "Settled" },
                { key: "APPROVED", label: "Approved" },
                { key: "REJECTED", label: "Rejected" },
                { key: "MANUAL_REVIEW", label: "Manual" },
              ].map((tab) => (
                <button
                  key={tab.key}
                  onClick={() => setStatusFilter(tab.key)}
                  className={`px-3 py-1 text-xs font-semibold rounded-lg transition-all ${
                    statusFilter === tab.key
                      ? "bg-white text-slate-900 shadow-xs"
                      : "text-slate-600 hover:text-slate-900 hover:bg-white/50"
                  }`}
                >
                  {tab.label}
                </button>
              ))}
            </div>
          </div>

          {/* Table */}
          <div className="overflow-x-auto">
            <table className="w-full text-left text-xs">
              <thead className="bg-slate-50/80 text-slate-500 uppercase font-semibold text-[10px] tracking-wider border-b border-slate-200/60">
                <tr>
                  <th className="py-3 px-5">Invoice #</th>
                  <th className="py-3 px-5">PO Reference</th>
                  <th className="py-3 px-5">Vendor Name</th>
                  <th className="py-3 px-5 text-right">Invoiced Total</th>
                  <th className="py-3 px-5">Reconciliation Status</th>
                  <th className="py-3 px-5 text-center">Variances</th>
                  <th className="py-3 px-5">Created At</th>
                  <th className="py-3 px-5 text-right">Action</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {filteredInvoices.length > 0 ? (
                  filteredInvoices.map((inv) => {
                    const badge = getStatusBadge(inv.reconciliationStatus);
                    return (
                      <tr
                        key={inv.id}
                        className="hover:bg-slate-50/80 transition-colors group cursor-pointer"
                      >
                        <td className="py-3.5 px-5 font-mono font-bold text-slate-900">
                          <Link
                            href={`/invoice/${inv.id}`}
                            className="hover:text-indigo-600 transition-colors"
                          >
                            {inv.invoiceNumber}
                          </Link>
                        </td>
                        <td className="py-3.5 px-5 font-mono font-medium text-indigo-600">
                          {inv.poReference || "—"}
                        </td>
                        <td className="py-3.5 px-5 text-slate-700 font-medium">
                          {inv.vendorName}
                        </td>
                        <td className="py-3.5 px-5 text-right font-mono font-bold text-slate-900">
                          {formatEGP(inv.invoicedTotal)}
                        </td>
                        <td className="py-3.5 px-5">
                          <span
                            className={`inline-flex items-center gap-1.5 px-2.5 py-0.5 rounded-full text-[11px] font-semibold border ${badge.bg}`}
                          >
                            {badge.icon}
                            <span>{badge.label}</span>
                          </span>
                        </td>
                        <td className="py-3.5 px-5 text-center">
                          {inv.discrepancyCount > 0 ? (
                            <span className="inline-flex items-center justify-center px-2 py-0.5 rounded-full text-[11px] font-bold bg-rose-100 text-rose-800">
                              {inv.discrepancyCount}
                            </span>
                          ) : (
                            <span className="text-slate-400 font-medium">0</span>
                          )}
                        </td>
                        <td className="py-3.5 px-5 text-slate-500 font-mono text-[11px]">
                          {inv.createdAt
                            ? new Date(inv.createdAt).toLocaleString("en-GB", {
                                day: "2-digit",
                                month: "short",
                                hour: "2-digit",
                                minute: "2-digit",
                              })
                            : "—"}
                        </td>
                        <td className="py-3.5 px-5 text-right">
                          <Link
                            href={`/invoice/${inv.id}`}
                            className="inline-flex items-center gap-1 px-3 py-1.5 rounded-lg text-xs font-semibold text-indigo-600 hover:text-indigo-700 hover:bg-indigo-50 transition-colors"
                          >
                            <span>Open Workspace</span>
                            <ArrowRight className="w-3.5 h-3.5 transition-transform group-hover:translate-x-0.5" />
                          </Link>
                        </td>
                      </tr>
                    );
                  })
                ) : (
                  <tr>
                    <td colSpan={8} className="py-12 text-center text-slate-400">
                      {isLoading ? (
                        <div className="flex flex-col items-center justify-center space-y-2">
                          <RefreshCw className="w-6 h-6 animate-spin text-indigo-500" />
                          <span className="text-xs">Loading audited invoices...</span>
                        </div>
                      ) : (
                        <div className="flex flex-col items-center justify-center space-y-2">
                          <FileText className="w-8 h-8 text-slate-300" />
                          <p className="text-xs font-medium text-slate-600">
                            No invoices match your search or filter
                          </p>
                          <p className="text-[11px] text-slate-400">
                            Upload a vendor invoice above to begin deterministic reconciliation.
                          </p>
                        </div>
                      )}
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>
        </section>
      </main>
    </div>
  );
}
