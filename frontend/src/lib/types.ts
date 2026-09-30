export type ReconciliationStatus = 'APPROVED' | 'FLAGGED_DISCREPANCY' | 'MANUAL_REVIEW' | 'REJECTED';

export type IssueType =
  | 'PRICE_MISMATCH'
  | 'QUANTITY_MISMATCH'
  | 'EXTRA_FEE'
  | 'UNRECOGNIZED_ITEM'
  | 'PO_NOT_FOUND'
  | 'LINE_TOTAL_MISMATCH';

export interface AuditDetailResponse {
  id: number;
  issueType: IssueType | string;
  skuCode?: string | null;
  itemDescription?: string | null;
  expectedValue?: number | null;
  actualValue?: number | null;
  explanation: string;
  explanationArabic?: string | null;
  explanationEnglish?: string | null;
}

export interface BilledLineItemResponse {
  description: string;
  skuCode?: string | null;
  quantity?: number | null;
  unitPrice?: number | null;
  lineTotal?: number | null;
  matchStatus: string;
  statusLabel: string;
  auditExplanation?: string | null;
}

export interface ReconciliationSummaryResponse {
  invoiceId: number;
  invoiceNumber: string;
  poReference: string;
  vendorName: string;
  reconciliationStatus: ReconciliationStatus | string;
  invoicedTotal: number;
  expectedTotal: number;
  discrepancyCount: number;
  audits: AuditDetailResponse[];
  billedItems?: BilledLineItemResponse[];
  disputeDraft?: string | null;
  disputeDraftArabic?: string | null;
  disputeDraftEnglish?: string | null;
  fileDownloadUri: string;
}

export interface InvoiceListItemResponse {
  id: number;
  invoiceNumber: string;
  poReference: string;
  vendorName: string;
  invoicedTotal: number;
  reconciliationStatus: ReconciliationStatus | string;
  discrepancyCount: number;
  createdAt: string;
}

export interface ErrorResponse {
  status: number;
  error: string;
  message: string;
  path: string;
  timestamp: string;
}
