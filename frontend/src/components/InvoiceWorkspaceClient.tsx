"use client";

import React, { useEffect, useState, useCallback } from "react";
import Link from "next/link";
import { Loader2, FileQuestion, ArrowLeft, RefreshCw } from "lucide-react";
import type { ReconciliationSummaryResponse } from "@/lib/types";
import { getInvoiceById } from "@/lib/api";
import SplitScreenViewer from "@/components/SplitScreenViewer";

interface InvoiceWorkspaceClientProps {
  id: string;
}

export default function InvoiceWorkspaceClient({ id }: InvoiceWorkspaceClientProps) {
  const [invoice, setInvoice] = useState<ReconciliationSummaryResponse | null>(null);
  const [isLoading, setIsLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);

  const fetchInvoiceData = useCallback(async () => {
    if (!id) return;
    setIsLoading(true);
    setError(null);
    try {
      const data = await getInvoiceById(id);
      setInvoice(data);
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : "Error fetching invoice details.");
    } finally {
      setIsLoading(false);
    }
  }, [id]);

  useEffect(() => {
    fetchInvoiceData();
  }, [fetchInvoiceData]);

  if (isLoading) {
    return (
      <div className="min-h-screen bg-slate-900 flex flex-col items-center justify-center space-y-4 text-white">
        <Loader2 className="w-10 h-10 animate-spin text-indigo-500" />
        <p className="text-sm font-medium text-slate-300">
          Loading Invoice Reconciliation Workspace...
        </p>
      </div>
    );
  }

  if (error || !invoice) {
    return (
      <div className="min-h-screen bg-slate-50 flex flex-col items-center justify-center p-6 text-center">
        <div className="w-14 h-14 rounded-2xl bg-rose-50 text-rose-600 flex items-center justify-center mb-4">
          <FileQuestion className="w-8 h-8" />
        </div>
        <h2 className="text-lg font-bold text-slate-800">
          Invoice Not Found or Failed to Load
        </h2>
        <p className="text-xs text-slate-500 mt-1 max-w-sm">
          {error || "Could not retrieve the requested reconciliation record."}
        </p>
        <div className="flex items-center gap-3 mt-6">
          <Link
            href="/"
            className="inline-flex items-center gap-1.5 px-4 py-2 rounded-xl text-xs font-semibold bg-slate-900 text-white hover:bg-slate-800 transition-colors shadow-xs"
          >
            <ArrowLeft className="w-4 h-4" /> Back to Queue
          </Link>
          <button
            onClick={fetchInvoiceData}
            className="inline-flex items-center gap-1.5 px-4 py-2 rounded-xl text-xs font-semibold border border-slate-200 bg-white text-slate-700 hover:bg-slate-50 transition-colors"
          >
            <RefreshCw className="w-4 h-4" /> Retry
          </button>
        </div>
      </div>
    );
  }

  return (
    <div className="h-screen w-screen bg-slate-100 flex flex-col overflow-hidden">
      <SplitScreenViewer
        invoice={invoice}
        onInvoiceUpdated={(updated) => setInvoice(updated)}
      />
    </div>
  );
}
