export type ReconciliationStatus =
  | 'APPROVED'
  | 'FLAGGED_DISCREPANCY'
  | 'MANUAL_REVIEW'
  | 'REJECTED'
  | 'RESOLVED_BY_DEBIT_NOTE';

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

export interface CategoryDiscrepancyStat {
  issueType: string;
  label: string;
  count: number;
  totalAmount: number;
}

export interface FinancialRoiSummaryResponse {
  totalInvoicedVolume: number;
  totalCashLeakageBlocked: number;
  priceMismatchLeakage: number;
  extraFeesBlocked: number;
  quantityShortageValuePrevented: number;
  totalInvoicesAudited: number;
  cleanInvoicesCount: number;
  discrepancyInvoicesCount: number;
  manualReviewCount: number;
  accuracyRate: number;
  topOffenses: CategoryDiscrepancyStat[];
  executiveBrief: string;
  currency: string;
}

export type VendorTier = 'CERTIFIED_PARTNER' | 'AUDIT_SCRUTINY' | 'HIGH_RISK';

export interface VendorScorecardResponse {
  vendorName: string;
  totalInvoices: number;
  cleanInvoices: number;
  discrepancyInvoices: number;
  cleanMatchRate: number;
  discrepancyRate: number;
  totalBilledAmount: number;
  totalOverchargeAttempted: number;
  tier: VendorTier | string;
  topOffendingCategories: CategoryDiscrepancyStat[];
  riskNarrative: string;
  lastAuditedDate: string;
}

export interface PortalDiscrepancyItem {
  id?: number;
  issueType: IssueType | string;
  skuCode?: string;
  itemDescription?: string;
  expectedValue?: number;
  agreedValue?: number;
  actualValue?: number;
  invoicedValue?: number;
  deductionAmount: number;
  explanation?: string;
  reason?: string;
}

export interface SupplierDisputePortalResponse {
  invoiceId: number;
  invoiceNumber: string;
  poReference: string;
  vendorName: string;
  invoicedTotal: number;
  poExpectedTotal?: number;
  totalDeduction?: number;
  totalDeductionAmount?: number;
  netPayableAmount: number;
  status?: ReconciliationStatus | string;
  reconciliationStatus?: string;
  discrepancies: PortalDiscrepancyItem[];
  disputeDraftArabic?: string;
  disputeDraftEnglish?: string;
  invoiceDate?: string;
  isResolved: boolean;
  debitNoteDownloadUrl?: string;
}


export interface PortalAcceptResponse {
  success: boolean;
  message: string;
  invoiceId: number;
  invoiceNumber: string;
  newStatus: string;
  debitNoteDownloadUrl: string;
  netApprovedAmount: number;
  totalDeduction: number;
}

export interface NegotiationAssessmentResponse {
  invoiceId: number;
  status: string;
  aiRecommendation: 'APPROVE_EXPLANATION' | 'PARTIAL_CONCESSION' | 'REJECT_EXPLANATION' | string;
  confidenceScore: number;
  commercialJustification: string;
  recommendedCounterOffer?: number | null;
  nextSteps: string;
}

export interface PortalLinkResponse {
  invoiceId: number;
  token: string;
  portalUrl: string;
  expiresInDays: number;
}
