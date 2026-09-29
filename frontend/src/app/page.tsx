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
} from "lucide-react";
import type { InvoiceListItemResponse, ReconciliationSummaryResponse } from "@/lib/types";
import DropzoneUploader from "@/components/DropzoneUploader";

export default function DashboardPage() {
  const [invoices, setInvoices] = useState<InvoiceListItemResponse[]>([]);
  const [isLoading, setIsLoading] = useState<boolean>(true);
  const [searchQuery, setSearchQuery] = useState<string>("");
  const [statusFilter, setStatusFilter] = useState<string>("ALL");

  const fetchInvoices = async () => {
    setIsLoading(true);
    try {
      const res = await fetch("/api/invoices");
      if (res.ok) {
        const data: InvoiceListItemResponse[] = await res.json();
        setInvoices(data);
      }
    } catch (err) {
      console.error("Failed to fetch invoices:", err);
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    fetchInvoices();
  }, []);

  const handleUploadSuccess = (summary: ReconciliationSummaryResponse) => {
    fetchInvoices();
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
          bg: "bg-emerald-50 text-emerald-700 border-emerald-200",
          icon: <CheckCircle2 className="w-3.5 h-3.5 text-emerald-600" />,
          label: "Approved",
        };
      case "REJECTED":
        return {
          bg: "bg-rose-50 text-rose-700 border-rose-200",
          icon: <AlertOctagon className="w-3.5 h-3.5 text-rose-600" />,
          label: "Rejected - Disputed",
        };
      case "FLAGGED_DISCREPANCY":
        return {
          bg: "bg-rose-50 text-rose-700 border-rose-200",
          icon: <AlertOctagon className="w-3.5 h-3.5 text-rose-600" />,
          label: "Flagged Discrepancy",
        };
      case "MANUAL_REVIEW":
      default:
        return {
          bg: "bg-amber-50 text-amber-700 border-amber-200",
          icon: <AlertTriangle className="w-3.5 h-3.5 text-amber-600" />,
          label: "Manual Review",
        };
    }
  };

  return (
    <div className="min-h-screen bg-slate-50 flex flex-col">
      {/* Top Navbar */}
      <header className="bg-white border-b border-slate-200 px-8 py-4 flex items-center justify-between sticky top-0 z-20 shadow-xs">
        <div className="flex items-center gap-3">
          <div className="w-9 h-9 rounded-xl bg-gradient-to-tr from-indigo-600 to-violet-600 flex items-center justify-center text-white shadow-sm">
            <Layers className="w-5 h-5" />
          </div>
          <div>
            <h1 className="text-base font-bold text-slate-900 tracking-tight flex items-center gap-2">
              Automated Invoice Reconciliation
              <span className="px-2 py-0.5 rounded-full text-[10px] font-semibold bg-indigo-50 text-indigo-700 border border-indigo-200">
                Spring AI & Gemini 3.8
              </span>
            </h1>
            <p className="text-xs text-slate-500">
              Multimodal document extraction with deterministic pure-Java financial audit
            </p>
          </div>
        </div>

        <div className="flex items-center gap-3">
          <button
            onClick={fetchInvoices}
            disabled={isLoading}
            className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-medium border border-slate-200 bg-white hover:bg-slate-50 text-slate-700 shadow-xs transition-colors"
          >
            <RefreshCw className={`w-3.5 h-3.5 ${isLoading ? "animate-spin" : ""}`} />
            <span>Refresh Queue</span>
          </button>
        </div>
      </header>

      {/* Main Content Area */}
      <main className="flex-1 max-w-7xl w-full mx-auto p-6 md:p-8 space-y-8">
        {/* Metric Cards */}
        <section className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
          <div className="bg-white rounded-2xl border border-slate-200 p-5 shadow-xs flex items-center justify-between">
            <div>
              <span className="text-xs font-semibold uppercase tracking-wider text-slate-400 block">
                Total Processed
              </span>
              <span className="text-2xl font-bold font-mono text-slate-900 mt-1 block">
                {metrics.total}
              </span>
              <span className="text-[11px] text-slate-400 mt-1 block">
                Lifetime invoice batch
              </span>
            </div>
            <div className="w-12 h-12 rounded-xl bg-slate-100 text-slate-700 flex items-center justify-center">
              <Receipt className="w-6 h-6" />
            </div>
          </div>

          <div className="bg-white rounded-2xl border border-rose-200 p-5 shadow-xs flex items-center justify-between bg-gradient-to-br from-white to-rose-50/30">
            <div>
              <span className="text-xs font-semibold uppercase tracking-wider text-rose-600 block">
                Discrepancies Flagged
              </span>
              <span className="text-2xl font-bold font-mono text-rose-700 mt-1 block">
                {metrics.discrepancies}
              </span>
              <span className="text-[11px] text-rose-500 mt-1 block font-medium">
                Price hikes & extra fees
              </span>
            </div>
            <div className="w-12 h-12 rounded-xl bg-rose-100 text-rose-600 flex items-center justify-center">
              <AlertOctagon className="w-6 h-6" />
            </div>
          </div>

          <div className="bg-white rounded-2xl border border-emerald-200 p-5 shadow-xs flex items-center justify-between bg-gradient-to-br from-white to-emerald-50/30">
            <div>
              <span className="text-xs font-semibold uppercase tracking-wider text-emerald-600 block">
                Approved for Payment
              </span>
              <span className="text-2xl font-bold font-mono text-emerald-700 mt-1 block">
                {metrics.approved}
              </span>
              <span className="text-[11px] text-emerald-600 mt-1 block font-medium">
                Clean or manager approved
              </span>
            </div>
            <div className="w-12 h-12 rounded-xl bg-emerald-100 text-emerald-600 flex items-center justify-center">
              <CheckCircle2 className="w-6 h-6" />
            </div>
          </div>

          <div className="bg-white rounded-2xl border border-indigo-200 p-5 shadow-xs flex items-center justify-between bg-gradient-to-br from-white to-indigo-50/30">
            <div>
              <span className="text-xs font-semibold uppercase tracking-wider text-indigo-600 block">
                Total Audited Volume
              </span>
              <span className="text-xl font-bold font-mono text-indigo-900 mt-1 block">
                {formatEGP(metrics.totalVolume)}
              </span>
              <span className="text-[11px] text-indigo-500 mt-1 block font-medium">
                Reconciled in EGP
              </span>
            </div>
            <div className="w-12 h-12 rounded-xl bg-indigo-100 text-indigo-600 flex items-center justify-center">
              <TrendingUp className="w-6 h-6" />
            </div>
          </div>
        </section>

        {/* Upload Zone Section */}
        <section className="bg-white rounded-2xl border border-slate-200 p-6 shadow-xs">
          <div className="mb-4">
            <h2 className="text-sm font-bold text-slate-900">
              Upload Supplier Invoice for Audit
            </h2>
            <p className="text-xs text-slate-500">
              Supports Arabic and English invoices (PDF, PNG, JPG). Spring AI extracts line items and reconciles against purchase orders.
            </p>
          </div>
          <DropzoneUploader onSuccess={handleUploadSuccess} redirectToInvoice={true} />
        </section>

        {/* Invoices Queue Table */}
        <section className="bg-white rounded-2xl border border-slate-200 shadow-xs overflow-hidden">
          {/* Table Controls */}
          <div className="p-4 bg-slate-50/70 border-b border-slate-200 flex flex-wrap items-center justify-between gap-4">
            <div className="flex items-center gap-2">
              <h3 className="text-sm font-bold text-slate-900">
                Audited Invoices Queue
              </h3>
              <span className="px-2 py-0.5 rounded-full text-xs font-medium bg-slate-200 text-slate-700">
                {filteredInvoices.length}
              </span>
            </div>

            <div className="flex flex-wrap items-center gap-3">
              {/* Search */}
              <div className="relative">
                <Search className="w-4 h-4 text-slate-400 absolute left-3 top-1/2 -translate-y-1/2" />
                <input
                  type="text"
                  placeholder="Search invoice, vendor, PO..."
                  value={searchQuery}
                  onChange={(e) => setSearchQuery(e.target.value)}
                  className="pl-9 pr-3 py-1.5 rounded-lg text-xs bg-white border border-slate-200 text-slate-800 placeholder-slate-400 focus:outline-none focus:ring-2 focus:ring-indigo-500/20 focus:border-indigo-500 transition-all w-60"
                />
              </div>

              {/* Status Filter */}
              <div className="flex items-center gap-1 bg-slate-200/70 p-1 rounded-lg">
                {["ALL", "FLAGGED_DISCREPANCY", "APPROVED", "REJECTED", "MANUAL_REVIEW"].map((tab) => (
                  <button
                    key={tab}
                    onClick={() => setStatusFilter(tab)}
                    className={`px-2.5 py-1 rounded-md text-[11px] font-semibold transition-colors ${
                      statusFilter === tab
                        ? "bg-white text-slate-900 shadow-xs"
                        : "text-slate-600 hover:text-slate-900"
                    }`}
                  >
                    {tab === "ALL"
                      ? "All"
                      : tab === "FLAGGED_DISCREPANCY"
                      ? "Discrepancies"
                      : tab === "APPROVED"
                      ? "Approved"
                      : tab === "REJECTED"
                      ? "Rejected"
                      : "Manual Review"}
                  </button>
                ))}
              </div>
            </div>
          </div>

          {/* Table */}
          <div className="overflow-x-auto">
            <table className="w-full text-left border-collapse">
              <thead>
                <tr className="border-b border-slate-200 bg-slate-50 text-[11px] font-bold uppercase tracking-wider text-slate-500">
                  <th className="py-3 px-5">Invoice #</th>
                  <th className="py-3 px-5">PO Reference</th>
                  <th className="py-3 px-5">Vendor Name</th>
                  <th className="py-3 px-5 text-right">Invoiced Total</th>
                  <th className="py-3 px-5">Reconciliation Status</th>
                  <th className="py-3 px-5 text-center">Discrepancies</th>
                  <th className="py-3 px-5">Audited Date</th>
                  <th className="py-3 px-5 text-right">Action</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100 text-xs">
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
