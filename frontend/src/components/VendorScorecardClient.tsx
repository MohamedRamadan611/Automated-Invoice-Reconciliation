"use client";

import React, { useEffect, useState, useMemo } from "react";
import Link from "next/link";
import {
  Building2,
  ShieldCheck,
  AlertTriangle,
  ShieldAlert,
  Search,
  RefreshCw,
  ArrowRight,
  TrendingUp,
  Sparkles,
  BarChart3,
  X,
  FileText,
  DollarSign,
  ChevronRight,
  Layers,
  Award,
} from "lucide-react";
import type { VendorScorecardResponse, VendorTier } from "@/lib/types";
import { fetchVendorScorecards, fetchInvoices } from "@/lib/api";
import type { InvoiceListItemResponse } from "@/lib/types";

export default function VendorScorecardClient() {
  const [scorecards, setScorecards] = useState<VendorScorecardResponse[]>([]);
  const [invoices, setInvoices] = useState<InvoiceListItemResponse[]>([]);
  const [isLoading, setIsLoading] = useState<boolean>(true);
  const [searchQuery, setSearchQuery] = useState<string>("");
  const [tierFilter, setTierFilter] = useState<string>("ALL");
  const [selectedVendor, setSelectedVendor] = useState<VendorScorecardResponse | null>(null);

  const loadData = async () => {
    setIsLoading(true);
    try {
      const [scorecardData, invoiceData] = await Promise.all([
        fetchVendorScorecards(),
        fetchInvoices().catch(() => []),
      ]);
      setScorecards(scorecardData);
      setInvoices(invoiceData);
    } catch (err) {
      console.error("Failed to load vendor scorecards:", err);
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    loadData();
  }, []);

  // Summary Metrics
  const summary = useMemo(() => {
    const totalVendors = scorecards.length;
    const certified = scorecards.filter((s) => s.tier === "CERTIFIED_PARTNER").length;
    const scrutiny = scorecards.filter((s) => s.tier === "AUDIT_SCRUTINY").length;
    const highRisk = scorecards.filter((s) => s.tier === "HIGH_RISK").length;
    const totalOvercharge = scorecards.reduce(
      (acc, curr) => acc + (curr.totalOverchargeAttempted || 0),
      0
    );

    return { totalVendors, certified, scrutiny, highRisk, totalOvercharge };
  }, [scorecards]);

  // Filtered scorecards
  const filteredScorecards = useMemo(() => {
    return scorecards.filter((s) => {
      const matchesSearch = s.vendorName.toLowerCase().includes(searchQuery.toLowerCase());
      const matchesTier = tierFilter === "ALL" || s.tier === tierFilter;
      return matchesSearch && matchesTier;
    });
  }, [scorecards, searchQuery, tierFilter]);

  const formatEGP = (amount?: number) => {
    if (amount === undefined || amount === null) return "0.00 EGP";
    return new Intl.NumberFormat("en-EG", {
      style: "currency",
      currency: "EGP",
      minimumFractionDigits: 2,
    }).format(amount);
  };

  const getTierBadge = (tier: string) => {
    switch (tier) {
      case "CERTIFIED_PARTNER":
        return {
          bg: "bg-emerald-50 text-emerald-800 border-emerald-300",
          icon: <ShieldCheck className="w-3.5 h-3.5 text-emerald-600" />,
          label: "Certified Partner (<3%)",
        };
      case "AUDIT_SCRUTINY":
        return {
          bg: "bg-amber-50 text-amber-900 border-amber-300",
          icon: <AlertTriangle className="w-3.5 h-3.5 text-amber-600" />,
          label: "Audit Scrutiny (3-15%)",
        };
      case "HIGH_RISK":
        return {
          bg: "bg-rose-50 text-rose-800 border-rose-300",
          icon: <ShieldAlert className="w-3.5 h-3.5 text-rose-600" />,
          label: "High-Risk Supplier (>15%)",
        };
      default:
        return {
          bg: "bg-slate-50 text-slate-700 border-slate-200",
          icon: <Building2 className="w-3.5 h-3.5 text-slate-500" />,
          label: tier,
        };
    }
  };

  const vendorInvoices = useMemo(() => {
    if (!selectedVendor) return [];
    return invoices.filter(
      (inv) => inv.vendorName && inv.vendorName.toLowerCase() === selectedVendor.vendorName.toLowerCase()
    );
  }, [selectedVendor, invoices]);

  return (
    <div className="min-h-screen bg-slate-50/60 pb-16 font-sans">
      {/* Top Banner & Navigation Header */}
      <header className="bg-slate-900 text-white border-b border-slate-800 shadow-sm sticky top-0 z-30">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-3.5 flex flex-col md:flex-row md:items-center justify-between gap-4">
          <div className="flex items-center space-x-3">
            <div className="w-10 h-10 rounded-xl bg-gradient-to-tr from-indigo-500 via-indigo-600 to-emerald-500 flex items-center justify-center shadow-lg shadow-indigo-500/20">
              <Building2 className="w-5 h-5 text-white" />
            </div>
            <div>
              <h1 className="text-base font-bold tracking-tight text-white flex items-center gap-2">
                <span>Supplier Reliability Scorecard</span>
                <span className="text-[10px] uppercase font-mono font-semibold px-2 py-0.5 rounded-full bg-indigo-500/20 text-indigo-300 border border-indigo-500/30">
                  Compliance &bull; Risk Tiers
                </span>
              </h1>
              <p className="text-xs text-slate-400">
                Continuous compliance auditing &bull; Chronic overcharge monitoring &bull; AI contract negotiation insights
              </p>
            </div>
          </div>

          {/* Navigation Tabs */}
          <div className="flex items-center space-x-2">
            <Link
              href="/"
              className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-semibold text-slate-300 hover:text-white bg-slate-800/80 hover:bg-slate-800 border border-slate-700 transition-colors"
            >
              <BarChart3 className="w-3.5 h-3.5" />
              <span>Audited Queue</span>
            </Link>
            <Link
              href="/vendors"
              className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-semibold bg-indigo-600 text-white shadow-xs"
            >
              <Building2 className="w-3.5 h-3.5" />
              <span>Vendor Scorecards</span>
            </Link>
            <button
              onClick={loadData}
              disabled={isLoading}
              className="inline-flex items-center space-x-1.5 px-3 py-1.5 rounded-lg text-xs font-medium text-slate-300 hover:text-white bg-slate-800 hover:bg-slate-700 border border-slate-700 transition-colors ml-1"
            >
              <RefreshCw className={`w-3.5 h-3.5 ${isLoading ? "animate-spin" : ""}`} />
              <span>Refresh</span>
            </button>
          </div>
        </div>
      </header>

      <main className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 pt-8 space-y-8">
        {/* KPI Strip */}
        <section className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-5 gap-4">
          {/* Total Suppliers */}
          <div className="bg-white rounded-2xl p-4 border border-slate-200/80 shadow-xs flex items-center space-x-4">
            <div className="w-11 h-11 rounded-xl bg-indigo-50 text-indigo-600 flex items-center justify-center shrink-0">
              <Building2 className="w-5 h-5" />
            </div>
            <div>
              <p className="text-[11px] font-semibold text-slate-500 uppercase tracking-wider">
                Active Suppliers
              </p>
              <p className="text-2xl font-bold font-mono text-slate-900">{summary.totalVendors}</p>
            </div>
          </div>

          {/* Certified Partners */}
          <div className="bg-white rounded-2xl p-4 border border-slate-200/80 shadow-xs flex items-center space-x-4">
            <div className="w-11 h-11 rounded-xl bg-emerald-50 text-emerald-600 flex items-center justify-center shrink-0">
              <ShieldCheck className="w-5 h-5" />
            </div>
            <div>
              <p className="text-[11px] font-semibold text-slate-500 uppercase tracking-wider">
                Certified Partners
              </p>
              <div className="flex items-baseline space-x-1.5">
                <span className="text-2xl font-bold font-mono text-emerald-700">{summary.certified}</span>
                <span className="text-[10px] text-emerald-600 font-medium">(&lt;3% Var)</span>
              </div>
            </div>
          </div>

          {/* Audit Scrutiny */}
          <div className="bg-white rounded-2xl p-4 border border-slate-200/80 shadow-xs flex items-center space-x-4">
            <div className="w-11 h-11 rounded-xl bg-amber-50 text-amber-600 flex items-center justify-center shrink-0">
              <AlertTriangle className="w-5 h-5" />
            </div>
            <div>
              <p className="text-[11px] font-semibold text-slate-500 uppercase tracking-wider">
                Audit Scrutiny
              </p>
              <div className="flex items-baseline space-x-1.5">
                <span className="text-2xl font-bold font-mono text-amber-700">{summary.scrutiny}</span>
                <span className="text-[10px] text-amber-600 font-medium">(3-15% Var)</span>
              </div>
            </div>
          </div>

          {/* High Risk */}
          <div className="bg-white rounded-2xl p-4 border border-slate-200/80 shadow-xs flex items-center space-x-4">
            <div className="w-11 h-11 rounded-xl bg-rose-50 text-rose-600 flex items-center justify-center shrink-0">
              <ShieldAlert className="w-5 h-5" />
            </div>
            <div>
              <p className="text-[11px] font-semibold text-slate-500 uppercase tracking-wider">
                High-Risk Suppliers
              </p>
              <div className="flex items-baseline space-x-1.5">
                <span className="text-2xl font-bold font-mono text-rose-700">{summary.highRisk}</span>
                <span className="text-[10px] text-rose-600 font-medium">(&gt;15% Var)</span>
              </div>
            </div>
          </div>

          {/* Overcharge Attempted */}
          <div className="bg-white rounded-2xl p-4 border border-slate-200/80 shadow-xs flex items-center space-x-4 sm:col-span-2 lg:col-span-1">
            <div className="w-11 h-11 rounded-xl bg-purple-50 text-purple-600 flex items-center justify-center shrink-0">
              <DollarSign className="w-5 h-5" />
            </div>
            <div>
              <p className="text-[11px] font-semibold text-slate-500 uppercase tracking-wider">
                Blocked Overcharges
              </p>
              <p className="text-base font-bold font-mono text-purple-700 truncate">
                {formatEGP(summary.totalOvercharge)}
              </p>
            </div>
          </div>
        </section>

        {/* Scorecard Table Card */}
        <section className="bg-white rounded-2xl border border-slate-200/80 shadow-xs overflow-hidden">
          {/* Controls */}
          <div className="p-4 sm:p-5 border-b border-slate-100 flex flex-col sm:flex-row items-center justify-between gap-3 bg-slate-50/40">
            <div className="relative w-full sm:w-72">
              <Search className="w-4 h-4 text-slate-400 absolute left-3 top-1/2 -translate-y-1/2" />
              <input
                type="text"
                placeholder="Search supplier name..."
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
                className="w-full pl-9 pr-3 py-1.5 text-xs rounded-xl border border-slate-200 bg-white text-slate-800 placeholder-slate-400 focus:outline-hidden focus:ring-2 focus:ring-indigo-500 focus:border-transparent transition-all"
              />
            </div>

            {/* Filter Tabs */}
            <div className="flex items-center gap-1 overflow-x-auto w-full sm:w-auto p-1 bg-slate-200/60 rounded-xl">
              {[
                { key: "ALL", label: "All Suppliers" },
                { key: "CERTIFIED_PARTNER", label: "🟢 Certified (<3%)" },
                { key: "AUDIT_SCRUTINY", label: "🟡 Scrutiny (3-15%)" },
                { key: "HIGH_RISK", label: "🔴 High-Risk (>15%)" },
              ].map((tab) => (
                <button
                  key={tab.key}
                  onClick={() => setTierFilter(tab.key)}
                  className={`px-3 py-1 text-xs font-semibold rounded-lg transition-all ${
                    tierFilter === tab.key
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
                  <th className="py-3 px-5">Supplier Name</th>
                  <th className="py-3 px-5">Reliability Tier</th>
                  <th className="py-3 px-5">Clean Match Rate</th>
                  <th className="py-3 px-5 text-right">Attempted Overcharges</th>
                  <th className="py-3 px-5 text-right">Audited Volume</th>
                  <th className="py-3 px-5 text-center">Invoices</th>
                  <th className="py-3 px-5">Top Offenses</th>
                  <th className="py-3 px-5 text-right">Action</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {filteredScorecards.length > 0 ? (
                  filteredScorecards.map((scorecard) => {
                    const badge = getTierBadge(scorecard.tier);
                    return (
                      <tr
                        key={scorecard.vendorName}
                        onClick={() => setSelectedVendor(scorecard)}
                        className="hover:bg-slate-50/80 transition-colors group cursor-pointer"
                      >
                        <td className="py-3.5 px-5 font-semibold text-slate-900">
                          <div className="flex items-center gap-2">
                            <div className="w-7 h-7 rounded-lg bg-indigo-50 text-indigo-600 flex items-center justify-center shrink-0">
                              <Building2 className="w-3.5 h-3.5" />
                            </div>
                            <span className="font-bold">{scorecard.vendorName}</span>
                          </div>
                        </td>
                        <td className="py-3.5 px-5">
                          <span
                            className={`inline-flex items-center gap-1.5 px-2.5 py-0.5 rounded-full text-[11px] font-semibold border ${badge.bg}`}
                          >
                            {badge.icon}
                            <span>{badge.label}</span>
                          </span>
                        </td>
                        <td className="py-3.5 px-5">
                          <div className="w-28 space-y-1">
                            <div className="flex items-center justify-between text-[11px]">
                              <span className="font-mono font-bold text-slate-800">
                                {scorecard.cleanMatchRate.toFixed(1)}%
                              </span>
                              <span className="text-[10px] text-slate-400">clean</span>
                            </div>
                            <div className="w-full bg-slate-100 rounded-full h-1.5 overflow-hidden">
                              <div
                                className={`h-1.5 rounded-full ${
                                  scorecard.cleanMatchRate >= 97
                                    ? "bg-emerald-500"
                                    : scorecard.cleanMatchRate >= 85
                                    ? "bg-amber-500"
                                    : "bg-rose-500"
                                }`}
                                style={{ width: `${scorecard.cleanMatchRate}%` }}
                              />
                            </div>
                          </div>
                        </td>
                        <td className="py-3.5 px-5 text-right font-mono font-bold text-rose-700">
                          {scorecard.totalOverchargeAttempted > 0
                            ? formatEGP(scorecard.totalOverchargeAttempted)
                            : "0.00 EGP"}
                        </td>
                        <td className="py-3.5 px-5 text-right font-mono text-slate-700 font-medium">
                          {formatEGP(scorecard.totalBilledAmount)}
                        </td>
                        <td className="py-3.5 px-5 text-center font-mono">
                          <span className="text-emerald-700 font-bold">{scorecard.cleanInvoices}</span>
                          <span className="text-slate-400"> / </span>
                          <span className="text-slate-800 font-bold">{scorecard.totalInvoices}</span>
                        </td>
                        <td className="py-3.5 px-5">
                          <div className="flex flex-wrap gap-1 max-w-xs">
                            {scorecard.topOffendingCategories && scorecard.topOffendingCategories.length > 0 ? (
                              scorecard.topOffendingCategories.slice(0, 2).map((offense, idx) => (
                                <span
                                  key={idx}
                                  className="text-[10px] px-2 py-0.5 rounded bg-slate-100 text-slate-700 border border-slate-200 font-medium"
                                >
                                  {offense.label.split(" ")[0]} ({offense.count}x)
                                </span>
                              ))
                            ) : (
                              <span className="text-emerald-700 text-[11px] font-medium">None (Clean)</span>
                            )}
                          </div>
                        </td>
                        <td className="py-3.5 px-5 text-right">
                          <button
                            onClick={(e) => {
                              e.stopPropagation();
                              setSelectedVendor(scorecard);
                            }}
                            className="inline-flex items-center gap-1 px-3 py-1.5 rounded-lg text-xs font-semibold text-indigo-600 hover:text-indigo-700 hover:bg-indigo-50 transition-colors"
                          >
                            <span>Risk Analysis</span>
                            <ChevronRight className="w-3.5 h-3.5" />
                          </button>
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
                          <span className="text-xs">Loading supplier scorecards...</span>
                        </div>
                      ) : (
                        <div className="flex flex-col items-center justify-center space-y-2">
                          <Building2 className="w-8 h-8 text-slate-300" />
                          <p className="text-xs font-medium text-slate-600">
                            No suppliers match your filter
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

      {/* VENDOR DETAIL & AI RISK NARRATIVE DRAWER */}
      {selectedVendor && (
        <div className="fixed inset-0 z-50 bg-slate-900/40 backdrop-blur-xs flex justify-end">
          <div className="w-full max-w-xl bg-white h-full shadow-2xl flex flex-col overflow-hidden animate-in slide-in-from-right duration-200">
            {/* Header */}
            <div className="p-5 border-b border-slate-200 flex items-center justify-between bg-slate-50/80">
              <div className="flex items-center space-x-3">
                <div className="w-10 h-10 rounded-xl bg-indigo-600 text-white flex items-center justify-center">
                  <Building2 className="w-5 h-5" />
                </div>
                <div>
                  <h2 className="text-base font-bold text-slate-900">{selectedVendor.vendorName}</h2>
                  <div className="flex items-center gap-2 mt-0.5">
                    <span
                      className={`inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-[10px] font-semibold border ${
                        getTierBadge(selectedVendor.tier).bg
                      }`}
                    >
                      {getTierBadge(selectedVendor.tier).icon}
                      <span>{getTierBadge(selectedVendor.tier).label}</span>
                    </span>
                    <span className="text-[11px] text-slate-400">
                      Clean Rate: <strong>{selectedVendor.cleanMatchRate.toFixed(1)}%</strong>
                    </span>
                  </div>
                </div>
              </div>
              <button
                onClick={() => setSelectedVendor(null)}
                className="p-1.5 rounded-lg text-slate-400 hover:text-slate-700 hover:bg-slate-200 transition-colors"
              >
                <X className="w-5 h-5" />
              </button>
            </div>

            {/* Content */}
            <div className="p-6 overflow-y-auto space-y-6 flex-1">
              {/* AI Risk Assessment */}
              <div className="bg-gradient-to-br from-indigo-50/90 to-purple-50/70 rounded-2xl p-4 border border-indigo-200/80">
                <div className="flex items-center space-x-2 mb-2">
                  <div className="w-7 h-7 rounded-lg bg-indigo-600 text-white flex items-center justify-center">
                    <Sparkles className="w-4 h-4" />
                  </div>
                  <div>
                    <h3 className="text-xs font-bold text-slate-900">
                      Autonomous AI Risk Assessment & Guidance
                    </h3>
                    <p className="text-[10px] text-indigo-700 font-mono">Gemini 3.8 Flash &bull; Procurement Intelligence</p>
                  </div>
                </div>
                <div className="mt-2 p-3 bg-white/95 rounded-xl border border-indigo-100 text-xs text-slate-700 leading-relaxed font-sans shadow-xs whitespace-pre-line">
                  {selectedVendor.riskNarrative}
                </div>
              </div>

              {/* Numerical Metrics */}
              <div className="grid grid-cols-2 gap-3">
                <div className="p-3.5 bg-slate-50 rounded-xl border border-slate-200">
                  <p className="text-[10px] font-semibold text-slate-500 uppercase">Attempted Overcharges</p>
                  <p className="text-lg font-bold font-mono text-rose-700 mt-1">
                    {formatEGP(selectedVendor.totalOverchargeAttempted)}
                  </p>
                </div>
                <div className="p-3.5 bg-slate-50 rounded-xl border border-slate-200">
                  <p className="text-[10px] font-semibold text-slate-500 uppercase">Total Billed Volume</p>
                  <p className="text-lg font-bold font-mono text-slate-800 mt-1">
                    {formatEGP(selectedVendor.totalBilledAmount)}
                  </p>
                </div>
              </div>

              {/* Breakdown by Category */}
              <div>
                <h4 className="text-xs font-bold text-slate-800 mb-2.5 uppercase tracking-wider">
                  Discrepancy Breakdown by Category
                </h4>
                {selectedVendor.topOffendingCategories && selectedVendor.topOffendingCategories.length > 0 ? (
                  <div className="space-y-2">
                    {selectedVendor.topOffendingCategories.map((offense, idx) => (
                      <div
                        key={idx}
                        className="flex items-center justify-between p-2.5 rounded-xl bg-slate-50 border border-slate-200 text-xs"
                      >
                        <div className="flex items-center gap-2">
                          <span className="w-2 h-2 rounded-full bg-rose-500" />
                          <span className="font-semibold text-slate-700">{offense.label}</span>
                          <span className="text-[10px] text-slate-400">({offense.count} incidents)</span>
                        </div>
                        <span className="font-mono font-bold text-slate-900">
                          {formatEGP(offense.totalAmount)}
                        </span>
                      </div>
                    ))}
                  </div>
                ) : (
                  <p className="text-xs text-slate-400 italic">No discrepancies recorded for this supplier.</p>
                )}
              </div>

              {/* Vendor's Invoices */}
              <div>
                <h4 className="text-xs font-bold text-slate-800 mb-2.5 uppercase tracking-wider">
                  Audited Invoices ({vendorInvoices.length})
                </h4>
                {vendorInvoices.length > 0 ? (
                  <div className="space-y-2 max-h-56 overflow-y-auto">
                    {vendorInvoices.map((inv) => (
                      <Link
                        key={inv.id}
                        href={`/invoice/${inv.id}`}
                        className="flex items-center justify-between p-3 rounded-xl bg-slate-50 hover:bg-indigo-50/70 border border-slate-200 transition-colors group"
                      >
                        <div>
                          <p className="text-xs font-mono font-bold text-slate-800 group-hover:text-indigo-600 transition-colors">
                            {inv.invoiceNumber}
                          </p>
                          <p className="text-[11px] text-slate-400">
                            PO: {inv.poReference || "N/A"} &bull; {inv.createdAt ? new Date(inv.createdAt).toLocaleDateString() : ""}
                          </p>
                        </div>
                        <div className="text-right">
                          <p className="text-xs font-mono font-bold text-slate-900">
                            {formatEGP(inv.invoicedTotal)}
                          </p>
                          <span className="text-[10px] font-semibold text-slate-500">
                            {inv.reconciliationStatus}
                          </span>
                        </div>
                      </Link>
                    ))}
                  </div>
                ) : (
                  <p className="text-xs text-slate-400 italic">No matching invoices loaded in queue.</p>
                )}
              </div>
            </div>

            {/* Footer */}
            <div className="p-4 border-t border-slate-200 bg-slate-50/80 flex items-center justify-end">
              <button
                onClick={() => setSelectedVendor(null)}
                className="px-4 py-2 text-xs font-semibold rounded-lg bg-slate-800 text-white hover:bg-slate-700 transition-colors"
              >
                Close Risk Profile
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
