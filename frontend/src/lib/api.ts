import type {
  InvoiceListItemResponse,
  ReconciliationSummaryResponse,
} from "./types";

const BASE_URL = process.env.NEXT_PUBLIC_API_URL || "";

/**
 * Custom application API error with status code and payload details.
 */
export class ApiError extends Error {
  constructor(
    public status: number,
    message: string,
    public details?: unknown
  ) {
    super(message);
    this.name = "ApiError";
  }
}

export interface EmailActionResult {
  invoice: ReconciliationSummaryResponse;
  isDispatched: boolean;
  recipient: string;
  isSimulated: boolean;
}

async function handleResponse<T>(res: Response): Promise<T> {
  if (!res.ok) {
    let errorMsg = `HTTP Error ${res.status}: ${res.statusText}`;
    let details: unknown = null;
    try {
      details = await res.json();
      if (details && typeof details === "object" && "message" in details) {
        errorMsg = (details as { message: string }).message;
      }
    } catch {
      // Body is not JSON
    }
    throw new ApiError(res.status, errorMsg, details);
  }
  return res.json() as Promise<T>;
}

/**
 * Fetches all invoices from the queue with audit and discrepancy counts.
 */
export async function fetchInvoices(): Promise<InvoiceListItemResponse[]> {
  const res = await fetch(`${BASE_URL}/api/invoices`, {
    cache: "no-store",
  });
  return handleResponse<InvoiceListItemResponse[]>(res);
}

/**
 * Fetches a single invoice reconciliation breakdown with line items and dispute drafts.
 */
export async function getInvoiceById(
  id: string | number
): Promise<ReconciliationSummaryResponse> {
  const res = await fetch(`${BASE_URL}/api/invoices/${id}`, {
    cache: "no-store",
  });
  return handleResponse<ReconciliationSummaryResponse>(res);
}

/**
 * Uploads an invoice file (PDF/Image) for multimodal extraction and deterministic reconciliation.
 */
export async function uploadInvoice(
  file: File
): Promise<ReconciliationSummaryResponse> {
  const formData = new FormData();
  formData.append("file", file);

  const res = await fetch(`${BASE_URL}/api/invoices/upload`, {
    method: "POST",
    body: formData,
  });
  return handleResponse<ReconciliationSummaryResponse>(res);
}

/**
 * Approves an invoice for payment release with manager override justification notes.
 */
export async function approveInvoice(
  id: string | number,
  email?: string,
  notes?: string
): Promise<EmailActionResult> {
  const params = new URLSearchParams();
  if (email && email.trim()) params.append("email", email.trim());
  if (notes && notes.trim()) params.append("notes", notes.trim());

  const url = `${BASE_URL}/api/invoices/${id}/approve${
    params.toString() ? `?${params.toString()}` : ""
  }`;

  const res = await fetch(url, {
    method: "POST",
  });

  const isDispatched = res.headers.get("X-Email-Dispatched") === "true";
  const recipient = res.headers.get("X-Email-Recipient") || (email ? email.trim() : "");
  const isSimulated = res.headers.get("X-Email-Simulated") === "true";

  const invoice = await handleResponse<ReconciliationSummaryResponse>(res);

  return {
    invoice,
    isDispatched,
    recipient,
    isSimulated,
  };
}

/**
 * Rejects an invoice and triggers a formal commercial dispute notice to the vendor.
 */
export async function rejectInvoice(
  id: string | number,
  email?: string,
  lang: string = "BOTH"
): Promise<EmailActionResult> {
  const params = new URLSearchParams();
  if (email && email.trim()) params.append("email", email.trim());
  if (lang && lang.trim()) params.append("lang", lang.trim().toUpperCase());

  const url = `${BASE_URL}/api/invoices/${id}/reject${
    params.toString() ? `?${params.toString()}` : ""
  }`;

  const res = await fetch(url, {
    method: "POST",
  });

  const isDispatched = res.headers.get("X-Email-Dispatched") === "true";
  const recipient = res.headers.get("X-Email-Recipient") || (email ? email.trim() : "");
  const isSimulated = res.headers.get("X-Email-Simulated") === "true";

  const invoice = await handleResponse<ReconciliationSummaryResponse>(res);

  return {
    invoice,
    isDispatched,
    recipient,
    isSimulated,
  };
}

/**
 * Regenerates the Gemini AI dispute draft on demand.
 */
export async function regenerateDispute(
  id: string | number
): Promise<ReconciliationSummaryResponse> {
  const res = await fetch(`${BASE_URL}/api/invoices/${id}/generate-dispute`, {
    method: "POST",
  });
  return handleResponse<ReconciliationSummaryResponse>(res);
}

/**
 * Returns the streaming URL for split-screen document preview.
 */
export function getInvoiceFileUrl(id: string | number): string {
  return `${BASE_URL}/api/invoices/${id}/file`;
}
