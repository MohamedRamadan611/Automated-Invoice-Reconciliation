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
  Filter,
  Receipt,
  Building2,
  Calendar,
  Layers,
  XCircle,
} from "lucide-react";
import type { InvoiceListItemResponse, ReconciliationSummaryResponse } from "@/lib/types";
import { fetchInvoices } from "@/lib/api";
import DropzoneUploader from "@/components/DropzoneUploader";

export default function DashboardClient() {
  const [invoices, setInvoices] = useState<InvoiceListItemResponse[]>([]);
  const [isLoading, setIsLoading] = useState<boolean>(true);
  const [searchQuery, setSearchQuery] = useState<string>("");
  const [statusFilter, setStatusFilter] = useState<string>("ALL");

  const loadInvoices = async () => {
    setIsLoading(true);
    try {
      const data = await fetchInvoices();
      setInvoices(data);
    } catch (err) {
      console.error("Failed to fetch invoices:", err);
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    loadInvoices();
  }, []);

  const handleUploadSuccess = (_summary: ReconciliationSummaryResponse) => {
    loadInvoices();
  };

  // Metrics
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
    if (amount === undefined || amount === null) return "—";
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
          bg: "bg-amber-50 text-amber-900 border-amber-300",
          icon: <AlertTriangle className="w-3.5 h-3.5 text-amber-600" />,
          label: "Flagged Discrepancy",
        };
      case "MANUAL_REVIEW":
        return {
          bg: "bg-slate-100 text-slate-700 border-slate-300",
          icon: <FileText className="w-3.5 h-3.5 text-slate-500" />,
          label: "PO Not Found / Manual",
        };
      case "REJECTED":
        return {
          bg: "bg-rose-50 text-rose-800 border-rose-300",
          icon: <XCircle className="w-3.5 h-3.5 text-rose-600" />,
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

  return (
    <div className="min-h-screen bg-slate-50/60 pb-16 font-sans">
      {/* Top Banner */}
      <header className="bg-slate-900 text-white border-b border-slate-800 shadow-sm sticky top-0 z-30">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-3.5 flex items-center justify-between">
          <div className="flex items-center space-x-3">
            <div className="w-9 h-9 rounded-xl bg-gradient-to-tr from-indigo-500 to-indigo-700 flex items-center justify-center shadow-inner">
              <Layers className="w-5 h-5 text-white" />
            </div>
            <div>
              <h1 className="text-base font-bold tracking-tight text-white flex items-center gap-2">
                <span>Autonomous AP Reconciliation Engine</span>
                <span className="text-[10px] uppercase font-mono font-semibold px-2 py-0.5 rounded-full bg-indigo-500/20 text-indigo-300 border border-indigo-500/30">
                  Gemini Flash + Java 21
                </span>
              </h1>
              <p className="text-xs text-slate-400">
                Deterministic 3-Way Matching &bull; 0.00 EGP Zero-Tolerance &bull; Egyptian Supply Chain
              </p>
            </div>
          </div>
          <div className="flex items-center space-x-3">
            <button
              onClick={loadInvoices}
              disabled={isLoading}
              className="inline-flex items-center space-x-1.5 px-3 py-1.5 rounded-lg text-xs font-medium text-slate-300 hover:text-white bg-slate-800 hover:bg-slate-700 border border-slate-700 transition-colors"
            >
              <RefreshCw className={`w-3.5 h-3.5 ${isLoading ? "animate-spin" : ""}`} />
              <span>Refresh Queue</span>
            </button>
          </div>
        </div>
      </header>

      <main className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 pt-8 space-y-8">
        {/* Metric Overview Strip */}
        <section className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-5 gap-4">
          {/* Total Processed */}
          <div className="bg-white rounded-2xl p-4 border border-slate-200/80 shadow-xs flex items-center space-x-4">
            <div className="w-11 h-11 rounded-xl bg-indigo-50 text-indigo-600 flex items-center justify-center shrink-0">
              <Receipt className="w-5 h-5" />
            </div>
            <div>
              <p className="text-[11px] font-semibold text-slate-500 uppercase tracking-wider">
                Audited Queue
              </p>
              <div className="flex items-baseline space-x-2">
                <span className="text-2xl font-bold font-mono text-slate-900">{metrics.total}</span>
                <span className="text-xs text-slate-400">Invoices</span>
              </div>
            </div>
          </div>

          {/* Discrepancies */}
          <div className="bg-white rounded-2xl p-4 border border-slate-200/80 shadow-xs flex items-center space-x-4">
            <div className="w-11 h-11 rounded-xl bg-amber-50 text-amber-700 flex items-center justify-center shrink-0">
              <AlertTriangle className="w-5 h-5" />
            </div>
            <div>
              <p className="text-[11px] font-semibold text-slate-500 uppercase tracking-wider">
                Discrepancies
              </p>
              <div className="flex items-baseline space-x-2">
                <span className="text-2xl font-bold font-mono text-amber-800">
                  {metrics.discrepancies}
                </span>
                <span className="text-xs text-amber-700/80 font-medium">Flagged</span>
              </div>
            </div>
          </div>

          {/* Approved */}
          <div className="bg-white rounded-2xl p-4 border border-slate-200/80 shadow-xs flex items-center space-x-4">
            <div className="w-11 h-11 rounded-xl bg-emerald-50 text-emerald-600 flex items-center justify-center shrink-0">
              <CheckCircle2 className="w-5 h-5" />
            </div>
            <div>
              <p className="text-[11px] font-semibold text-slate-500 uppercase tracking-wider">
                Approved
              </p>
              <div className="flex items-baseline space-x-2">
                <span className="text-2xl font-bold font-mono text-emerald-800">
                  {metrics.approved}
                </span>
                <span className="text-xs text-emerald-700/80 font-medium">Auto Cleared</span>
              </div>
            </div>
          </div>

          {/* Rejected */}
          <div className="bg-white rounded-2xl p-4 border border-slate-200/80 shadow-xs flex items-center space-x-4">
            <div className="w-11 h-11 rounded-xl bg-rose-50 text-rose-600 flex items-center justify-center shrink-0">
              <XCircle className="w-5 h-5" />
            </div>
            <div>
              <p className="text-[11px] font-semibold text-slate-500 uppercase tracking-wider">
                Rejected
              </p>
              <div className="flex items-baseline space-x-2">
                <span className="text-2xl font-bold font-mono text-rose-800">
                  {metrics.rejected}
                </span>
                <span className="text-xs text-rose-700/80 font-medium">Disputed</span>
              </div>
            </div>
          </div>

          {/* Total Billed Volume */}
          <div className="bg-white rounded-2xl p-4 border border-slate-200/80 shadow-xs flex items-center space-x-4 sm:col-span-2 lg:col-span-1">
            <div className="w-11 h-11 rounded-xl bg-purple-50 text-purple-600 flex items-center justify-center shrink-0">
              <TrendingUp className="w-5 h-5" />
            </div>
            <div>
              <p className="text-[11px] font-semibold text-slate-500 uppercase tracking-wider">
                Audited Volume
              </p>
              <div className="flex items-baseline space-x-2">
                <span className="text-base font-bold font-mono text-slate-900 truncate">
                  {formatEGP(metrics.totalVolume)}
                </span>
              </div>
            </div>
          </div>
        </section>

        {/* Upload Dropzone Section */}
        <section>
          <div className="mb-3">
            <h2 className="text-sm font-bold text-slate-800 tracking-tight flex items-center gap-2">
              <span>Upload New Invoice Document</span>
              <span className="text-[11px] font-normal text-slate-500">
                (PDF, PNG, JPG - Multimodal OCR & Line Item Extraction)
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
