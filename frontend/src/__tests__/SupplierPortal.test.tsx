import React from "react";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { describe, it, expect, vi, beforeEach } from "vitest";
import SupplierPortalClient from "@/components/SupplierPortalClient";
import * as api from "@/lib/api";
import type { SupplierDisputePortalResponse, PortalAcceptResponse } from "@/lib/types";

const mockGet = vi.fn();
vi.mock("next/navigation", () => ({
  useSearchParams: () => ({
    get: mockGet,
  }),
}));

vi.mock("@/lib/api", () => ({
  fetchPortalDispute: vi.fn(),
  acceptPortalDeduction: vi.fn(),
  submitPortalExplanation: vi.fn(),
  uploadAmendedInvoice: vi.fn(),
  getDebitNoteUrl: (id: number | string) => `/api/invoices/${id}/debit-note`,
  ApiError: class extends Error {
    constructor(public status: number, message: string) {
      super(message);
    }
  },
}));

describe("SupplierPortalClient Component", () => {
  const mockDisputeData: SupplierDisputePortalResponse = {
    invoiceId: 105,
    invoiceNumber: "INV-2026-105",
    poReference: "PO-2026-004",
    vendorName: "Steel Corp",
    invoicedTotal: 50000,
    totalDeduction: 4000,
    netPayableAmount: 46000,
    status: "FLAGGED_DISCREPANCY",
    isResolved: false,
    discrepancies: [
      {
        id: 1,
        issueType: "PRICE_MISMATCH",
        skuCode: "SKU-REBAR-16",
        itemDescription: "Steel Rebar 16mm",
        expectedValue: 1200,
        actualValue: 1400,
        deductionAmount: 4000,
        explanation: "Invoiced unit price 1,400.00 EGP exceeds agreed purchase order unit price 1,200.00 EGP",
      },
    ],
  };

  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("displays error message when token is missing", async () => {
    mockGet.mockReturnValue(null); // No token
    render(<SupplierPortalClient />);

    expect(
      screen.getByText(/No access token provided/i)
    ).toBeInTheDocument();
  });

  it("renders dispute details when valid token is supplied", async () => {
    mockGet.mockReturnValue("valid-token-105");
    vi.mocked(api.fetchPortalDispute).mockResolvedValue(mockDisputeData);

    render(<SupplierPortalClient />);

    await waitFor(() => {
      expect(screen.getByText("INV-2026-105")).toBeInTheDocument();
    });

    expect(screen.getByText("PO-2026-004")).toBeInTheDocument();
    expect(screen.getByText("Steel Corp")).toBeInTheDocument();
    expect(screen.getByText("Steel Rebar 16mm")).toBeInTheDocument();
    expect(screen.getAllByText(/1,400/).length).toBeGreaterThan(0);
  });

  it("successfully accepts adjustment and issues debit note", async () => {
    mockGet.mockReturnValue("valid-token-105");
    
    // First call: initial load. Second call: reload after accept.
    vi.mocked(api.fetchPortalDispute)
      .mockResolvedValueOnce(mockDisputeData)
      .mockResolvedValueOnce({
        ...mockDisputeData,
        status: "RESOLVED_BY_DEBIT_NOTE",
        isResolved: true,
        debitNoteDownloadUrl: "/api/invoices/105/debit-note",
      });

    const mockAcceptResponse: PortalAcceptResponse = {
      success: true,
      invoiceId: 105,
      invoiceNumber: "INV-2026-105",
      newStatus: "RESOLVED_BY_DEBIT_NOTE",
      netApprovedAmount: 46000,
      totalDeduction: 4000,
      debitNoteDownloadUrl: "/api/invoices/105/debit-note",
      message: "Deduction accepted. Debit note generated.",
    };

    vi.mocked(api.acceptPortalDeduction).mockResolvedValue(mockAcceptResponse);

    render(<SupplierPortalClient />);

    await waitFor(() => {
      expect(screen.getByText("INV-2026-105")).toBeInTheDocument();
    });

    // Find and click "Accept Deductions & Download Debit Note" button
    const acceptBtn = screen.getByRole("button", {
      name: /Accept Deductions & Download Debit Note/i,
    });
    fireEvent.click(acceptBtn);

    await waitFor(() => {
      expect(
        screen.getByText(/Dispute Finalized — Commercial Debit Note Issued/i)
      ).toBeInTheDocument();
    });

    expect(
      screen.getByText(/Download Debit Note PDF/i)
    ).toBeInTheDocument();
  });

  it("handles backend totalDeductionAmount, formats UNRECOGNIZED_ITEM, and ensures no NaN amounts", async () => {
    mockGet.mockReturnValue("valid-token-unrecognized");
    const mockUnrecognizedData = {
      invoiceId: 106,
      invoiceNumber: "INV-2026-106",
      poReference: "PO-2026-005",
      vendorName: "Delta Logistics",
      invoicedTotal: 25000,
      totalDeductionAmount: 3500, // Backend property name
      netPayableAmount: 21500,
      reconciliationStatus: "FLAGGED_DISCREPANCY",
      isResolved: false,
      discrepancies: [
        {
          id: 10,
          issueType: "UNRECOGNIZED_ITEM",
          skuCode: "SKU-UNKNOWN-01",
          itemDescription: "Emergency Pallet Fee",
          invoicedValue: 3500,
          agreedValue: 0,
          deductionAmount: 3500,
          reason: "Item not found on purchase order PO-2026-005",
        },
      ],
    };

    vi.mocked(api.fetchPortalDispute).mockResolvedValue(mockUnrecognizedData as any);

    render(<SupplierPortalClient />);

    await waitFor(() => {
      expect(screen.getByText("INV-2026-106")).toBeInTheDocument();
    });

    // 1. Ensure Commercial Deductions displays the real number and NOT NaN
    expect(screen.getAllByText(/-3,500.00/).length).toBeGreaterThan(0);
    expect(screen.queryByText(/NaN/)).not.toBeInTheDocument();

    // 2. Ensure UNRECOGNIZED_ITEM is rendered with polished label
    expect(screen.getByText("Unrecognized Item (Not on PO)")).toBeInTheDocument();

    // 3. Ensure Accept button and text display formatted net amount and NOT NaN
    expect(screen.getAllByText(/21,500/).length).toBeGreaterThan(0);
  });

  it("displays explicit Reconciliation Status label and badge in the portal", async () => {
    mockGet.mockReturnValue("valid-token-status");
    vi.mocked(api.fetchPortalDispute).mockResolvedValue(mockDisputeData);

    render(<SupplierPortalClient />);

    await waitFor(() => {
      expect(screen.getByText("INV-2026-105")).toBeInTheDocument();
    });

    // Check Reconciliation Status label
    expect(screen.getByText("Reconciliation Status")).toBeInTheDocument();
    expect(screen.getByText("Discrepancies Flagged")).toBeInTheDocument();
  });

  it("shows Download Debit Note PDF banner and link on a REJECTED invoice", async () => {
    mockGet.mockReturnValue("valid-token-rejected");
    const mockRejectedInvoice: SupplierDisputePortalResponse = {
      ...mockDisputeData,
      status: "REJECTED",
      reconciliationStatus: "REJECTED",
      isResolved: false,
      debitNoteDownloadUrl: "/api/invoices/105/debit-note",
    };

    vi.mocked(api.fetchPortalDispute).mockResolvedValue(mockRejectedInvoice);

    render(<SupplierPortalClient />);

    await waitFor(() => {
      expect(screen.getByText("INV-2026-105")).toBeInTheDocument();
    });

    // Reconciliation Status badge displays Rejected
    expect(screen.getByText("Rejected - Claimed Overcharge")).toBeInTheDocument();

    // Rejection banner is displayed with Debit Note download button
    expect(
      screen.getByText("Invoice Rejected — Commercial Debit Note Issued")
    ).toBeInTheDocument();

    const debitNoteBtn = screen.getByRole("link", { name: /Download Debit Note PDF/i });
    expect(debitNoteBtn).toBeInTheDocument();
    expect(debitNoteBtn).toHaveAttribute("href", "/api/invoices/105/debit-note");

    // Supplier MUST still have access to resolution options on rejected invoice
    expect(screen.getByText(/Option 1: Accept Deductions & Release Payment/i)).toBeInTheDocument();
    expect(screen.getByText(/Option 2: Counter-Explanation/i)).toBeInTheDocument();
    expect(screen.getByText(/Option 3: Upload Amended Invoice/i)).toBeInTheDocument();
  });

  it("renders bilingual Audit Rationale with separated English and Arabic cards and allows collapsing", async () => {
    mockGet.mockReturnValue("valid-token-bilingual");
    const mockBilingualRationaleData: SupplierDisputePortalResponse = {
      ...mockDisputeData,
      discrepancies: [
        {
          id: 20,
          issueType: "PRICE_MISMATCH",
          skuCode: "SKU-TOMATO-RED",
          itemDescription: "Fresh Tomatoes",
          expectedValue: 20,
          actualValue: 25,
          deductionAmount: 500,
          explanation: "Billed unit price exceeds purchase order terms. تم احتساب فرق سعر للوحدة بقيمة 5 جنيه.",
        },
      ],
    };

    vi.mocked(api.fetchPortalDispute).mockResolvedValue(mockBilingualRationaleData);

    render(<SupplierPortalClient />);

    await waitFor(() => {
      expect(screen.getByText("SKU-TOMATO-RED")).toBeInTheDocument();
    });

    const enText = screen.getByText(/Billed unit price exceeds purchase order terms/i);
    const arText = screen.getByText(/تم احتساب فرق سعر للوحدة بقيمة 5 جنيه/i);

    expect(enText).toBeInTheDocument();
    expect(enText.getAttribute("dir")).toBe("ltr");

    expect(arText).toBeInTheDocument();
    expect(arText.getAttribute("dir")).toBe("rtl");

    // Toggle note row
    const hideNoteBtn = screen.getByRole("button", { name: /Hide Note/i });
    fireEvent.click(hideNoteBtn);
    expect(screen.queryByText(/Billed unit price exceeds purchase order terms/i)).not.toBeInTheDocument();

    const viewNoteBtn = screen.getByRole("button", { name: /View Note/i });
    fireEvent.click(viewNoteBtn);
    expect(screen.getByText(/Billed unit price exceeds purchase order terms/i)).toBeInTheDocument();
  });

  it("ensures Arabic note is present on rejected screen even if raw explanation was English-only", async () => {
    mockGet.mockReturnValue("valid-token-rejected-en");
    const mockRejectedEnOnly: SupplierDisputePortalResponse = {
      ...mockDisputeData,
      status: "REJECTED",
      reconciliationStatus: "REJECTED",
      isResolved: false,
      discrepancies: [
        {
          id: 30,
          issueType: "PRICE_MISMATCH",
          skuCode: "SKU-BEAM-200",
          itemDescription: "Steel I-Beam 200mm",
          expectedValue: 3000,
          actualValue: 3500,
          deductionAmount: 5000,
          explanation: "Invoiced unit price 3,500.00 EGP exceeds agreed purchase order unit price 3,000.00 EGP",
        },
      ],
    };

    vi.mocked(api.fetchPortalDispute).mockResolvedValue(mockRejectedEnOnly);

    render(<SupplierPortalClient />);

    await waitFor(() => {
      expect(screen.getByText("SKU-BEAM-200")).toBeInTheDocument();
    });

    // English card
    expect(screen.getByText(/Invoiced unit price 3,500.00 EGP exceeds agreed purchase order unit price 3,000.00 EGP/i)).toBeInTheDocument();

    // Arabic card MUST be present with RTL direction
    const arabicCard = screen.getByText(/سعر الوحدة المفوتر/i);
    expect(arabicCard).toBeInTheDocument();
    expect(arabicCard.getAttribute("dir")).toBe("rtl");
  });

  it("renders zero discrepancies state cleanly on approved invoices with fixed table layout", async () => {
    mockGet.mockReturnValue("valid-token-approved-clean");
    const mockApprovedClean: SupplierDisputePortalResponse = {
      ...mockDisputeData,
      status: "APPROVED",
      reconciliationStatus: "APPROVED",
      isResolved: true,
      totalDeduction: 0,
      discrepancies: [],
    };

    vi.mocked(api.fetchPortalDispute).mockResolvedValue(mockApprovedClean);

    render(<SupplierPortalClient />);

    await waitFor(() => {
      expect(screen.getByText("Zero Audit Discrepancies")).toBeInTheDocument();
    });

    expect(screen.getByText(/All line items on this invoice perfectly matched/i)).toBeInTheDocument();
  });
});

