"use client";

import React, { useState, useRef } from "react";
import { useRouter } from "next/navigation";
import { UploadCloud, FileText, AlertCircle, Loader2, CheckCircle2 } from "lucide-react";
import type { ReconciliationSummaryResponse } from "@/lib/types";
import { uploadInvoice } from "@/lib/api";

interface DropzoneUploaderProps {
  onSuccess?: (data: ReconciliationSummaryResponse) => void;
  redirectToInvoice?: boolean;
}

export default function DropzoneUploader({
  onSuccess,
  redirectToInvoice = true,
}: DropzoneUploaderProps) {
  const router = useRouter();
  const [isDragging, setIsDragging] = useState(false);
  const [isUploading, setIsUploading] = useState(false);
  const [uploadProgress, setUploadProgress] = useState<string>("");
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [successInfo, setSuccessInfo] = useState<string | null>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);

  const handleDragOver = (e: React.DragEvent<HTMLDivElement>) => {
    e.preventDefault();
    e.stopPropagation();
    setIsDragging(true);
  };

  const handleDragLeave = (e: React.DragEvent<HTMLDivElement>) => {
    e.preventDefault();
    e.stopPropagation();
    setIsDragging(false);
  };

  const handleDrop = (e: React.DragEvent<HTMLDivElement>) => {
    e.preventDefault();
    e.stopPropagation();
    setIsDragging(false);

    if (e.dataTransfer.files && e.dataTransfer.files.length > 0) {
      const file = e.dataTransfer.files[0];
      handleFileSelected(file);
    }
  };

  const handleFileInputChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    if (e.target.files && e.target.files.length > 0) {
      handleFileSelected(e.target.files[0]);
    }
  };

  const handleFileSelected = async (file: File) => {
    const validTypes = [
      "application/pdf",
      "image/png",
      "image/jpeg",
      "image/jpg",
    ];

    if (!validTypes.includes(file.type) && !file.name.match(/\.(pdf|png|jpe?g)$/i)) {
      setErrorMessage("Unsupported file format. Please upload a PDF or image (PNG, JPG).");
      return;
    }

    // Max 25MB
    if (file.size > 25 * 1024 * 1024) {
      setErrorMessage("File is too large. Maximum size is 25MB.");
      return;
    }

    setErrorMessage(null);
    setSuccessInfo(null);
    setIsUploading(true);
    setUploadProgress("Uploading file & extracting line items via Gemini Pro...");

    try {
      setUploadProgress("Uploading file & extracting line items via Gemini Pro...");
      const summary = await uploadInvoice(file);

      setSuccessInfo(
        `Invoice ${summary.invoiceNumber} processed successfully! Status: ${summary.reconciliationStatus}`
      );

      if (onSuccess) {
        onSuccess(summary);
      }

      if (redirectToInvoice && summary.invoiceId) {
        setTimeout(() => {
          router.push(`/invoice/${summary.invoiceId}`);
        }, 800);
      }
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : "Failed to process invoice.";
      setErrorMessage(msg);
    } finally {
      setIsUploading(false);
      setUploadProgress("");
      if (fileInputRef.current) {
        fileInputRef.current.value = "";
      }
    }
  };

  return (
    <div className="w-full">
      <div
        onDragOver={handleDragOver}
        onDragLeave={handleDragLeave}
        onDrop={handleDrop}
        onClick={() => !isUploading && fileInputRef.current?.click()}
        className={`relative border-2 border-dashed rounded-2xl p-8 text-center transition-all duration-200 cursor-pointer overflow-hidden ${
          isDragging
            ? "border-indigo-500 bg-indigo-50/60 shadow-lg scale-[1.005]"
            : "border-slate-300 hover:border-indigo-400 bg-white hover:bg-slate-50/50 shadow-sm"
        }`}
      >
        <input
          ref={fileInputRef}
          type="file"
          accept=".pdf,.png,.jpg,.jpeg"
          onChange={handleFileInputChange}
          className="hidden"
          disabled={isUploading}
        />

        <div className="flex flex-col items-center justify-center space-y-3">
          <div
            className={`w-14 h-14 rounded-2xl flex items-center justify-center transition-colors ${
              isDragging
                ? "bg-indigo-600 text-white"
                : "bg-indigo-50 text-indigo-600 group-hover:bg-indigo-100"
            }`}
          >
            {isUploading ? (
              <Loader2 className="w-7 h-7 animate-spin text-indigo-600" />
            ) : (
              <UploadCloud className="w-7 h-7" />
            )}
          </div>

          <div>
            <h3 className="text-base font-semibold text-slate-800">
              {isUploading
                ? "Processing Invoice Document..."
                : isDragging
                ? "Drop the invoice here"
                : "Upload Supplier Invoice"}
            </h3>
            <p className="text-xs text-slate-500 mt-1 max-w-sm mx-auto">
              {isUploading
                ? uploadProgress
                : "Drag & drop vendor PDF, PNG, or JPG, or click to browse. Max 25MB."}
            </p>
          </div>

          <div className="flex items-center gap-2 pt-2">
            <span className="inline-flex items-center gap-1 px-2.5 py-1 rounded-md bg-slate-100 text-slate-600 text-[11px] font-medium">
              <FileText className="w-3.5 h-3.5 text-slate-500" /> PDF, PNG, JPEG
            </span>
            <span className="inline-flex items-center gap-1 px-2.5 py-1 rounded-md bg-indigo-50 text-indigo-700 text-[11px] font-medium">
              Multimodal AI Extraction
            </span>
            <span className="inline-flex items-center gap-1 px-2.5 py-1 rounded-md bg-emerald-50 text-emerald-700 text-[11px] font-medium">
              Pure Java Math Audit
            </span>
          </div>
        </div>

        {isUploading && (
          <div className="absolute inset-x-0 bottom-0 h-1 bg-slate-100 overflow-hidden">
            <div className="h-full bg-gradient-to-r from-indigo-500 to-violet-500 animate-pulse w-full" />
          </div>
        )}
      </div>

      {errorMessage && (
        <div className="mt-3 flex items-start gap-2.5 p-3 rounded-xl bg-rose-50 border border-rose-200 text-rose-800 text-xs">
          <AlertCircle className="w-4 h-4 text-rose-600 shrink-0 mt-0.5" />
          <div className="flex-1 font-medium">{errorMessage}</div>
        </div>
      )}

      {successInfo && (
        <div className="mt-3 flex items-start gap-2.5 p-3 rounded-xl bg-emerald-50 border border-emerald-200 text-emerald-800 text-xs">
          <CheckCircle2 className="w-4 h-4 text-emerald-600 shrink-0 mt-0.5" />
          <div className="flex-1 font-medium">{successInfo}</div>
        </div>
      )}
    </div>
  );
}
