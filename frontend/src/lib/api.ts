import type {
  InvoiceListItemResponse,
  ReconciliationSummaryResponse,
  FinancialRoiSummaryResponse,
  VendorScorecardResponse,
  SupplierDisputePortalResponse,
  PortalAcceptResponse,
  NegotiationAssessmentResponse,
  PortalLinkResponse,
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

/**
 * Fetches portfolio ROI summary, cash leakage prevented, and AI executive brief.
 */
export async function fetchRoiSummary(): Promise<FinancialRoiSummaryResponse> {
  const res = await fetch(`${BASE_URL}/api/analytics/roi-summary`, {
    cache: "no-store",
  });
  return handleResponse<FinancialRoiSummaryResponse>(res);
}

/**
 * Fetches reliability scorecards for all suppliers.
 */
export async function fetchVendorScorecards(): Promise<VendorScorecardResponse[]> {
  const res = await fetch(`${BASE_URL}/api/vendors/scorecard`, {
    cache: "no-store",
  });
  return handleResponse<VendorScorecardResponse[]>(res);
}

/**
 * Fetches the reliability scorecard for a single supplier.
 */
export async function fetchVendorScorecard(
  vendorName: string
): Promise<VendorScorecardResponse> {
  const encoded = encodeURIComponent(vendorName.trim());
  const res = await fetch(`${BASE_URL}/api/vendors/${encoded}/scorecard`, {
    cache: "no-store",
  });
  return handleResponse<VendorScorecardResponse>(res);
}

/**
 * Returns direct URL for downloading the Debit Note PDF for an invoice.
 */
export function getDebitNoteUrl(invoiceId: string | number): string {
  return `${BASE_URL}/api/invoices/${invoiceId}/debit-note`;
}

/**
 * Generates or retrieves the 7-day HMAC-SHA256 magic link for the supplier self-service portal.
 */
export async function getPortalLink(invoiceId: string | number): Promise<PortalLinkResponse> {
  const res = await fetch(`${BASE_URL}/api/invoices/${invoiceId}/portal-link`, {
    cache: "no-store",
  });
  return handleResponse<PortalLinkResponse>(res);
}

/**
 * Fetches the supplier dispute view using the HMAC magic link token.
 */
export async function fetchPortalDispute(token: string): Promise<SupplierDisputePortalResponse> {
  const res = await fetch(`${BASE_URL}/api/portal/dispute?token=${encodeURIComponent(token)}`, {
    cache: "no-store",
  });
  return handleResponse<SupplierDisputePortalResponse>(res);
}

/**
 * Supplier accepts commercial deduction; creates enforceable Debit Note and marks invoice resolved.
 */
export async function acceptPortalDeduction(token: string): Promise<PortalAcceptResponse> {
  const res = await fetch(`${BASE_URL}/api/portal/dispute/accept?token=${encodeURIComponent(token)}`, {
    method: "POST",
  });
  return handleResponse<PortalAcceptResponse>(res);
}

/**
 * Supplier submits counter-explanation for review by Gemini 3.8 Flash AI.
 */
export async function submitPortalExplanation(
  token: string,
  explanation: string
): Promise<NegotiationAssessmentResponse> {
  const res = await fetch(`${BASE_URL}/api/portal/dispute/explain?token=${encodeURIComponent(token)}`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
    },
    body: JSON.stringify({ explanation }),
  });
  return handleResponse<NegotiationAssessmentResponse>(res);
}

/**
 * Supplier uploads a corrected / amended invoice document.
 */
export async function uploadAmendedInvoice(
  token: string,
  file: File
): Promise<ReconciliationSummaryResponse> {
  const formData = new FormData();
  formData.append("file", file);

  const res = await fetch(
    `${BASE_URL}/api/portal/dispute/upload-amended?token=${encodeURIComponent(token)}`,
    {
      method: "POST",
      body: formData,
    }
  );
  return handleResponse<ReconciliationSummaryResponse>(res);
}

