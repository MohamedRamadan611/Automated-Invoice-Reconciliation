import React from "react";
import { render, screen, fireEvent } from "@testing-library/react";
import { describe, it, expect, vi, beforeEach } from "vitest";
import DisputeActionDrawer from "@/components/DisputeActionDrawer";
import type { ReconciliationSummaryResponse } from "@/lib/types";

vi.mock("@/lib/api", () => ({
  approveInvoice: vi.fn(),
  rejectInvoice: vi.fn(),
  regenerateDispute: vi.fn(),
  getDebitNoteUrl: (id: number | string) => `/api/invoices/${id}/debit-note`,
  getPortalLink: vi.fn(),
}));

describe("DisputeActionDrawer Component", () => {
  const mockInvoice: ReconciliationSummaryResponse = {
    invoiceId: 77,
    invoiceNumber: "INV-2026-077",
    poReference: "PO-2026-003",
    vendorName: "Cairo Electronics",
    reconciliationStatus: "FLAGGED_DISCREPANCY",
    invoicedTotal: 18500,
    expectedTotal: 16000,
    discrepancyCount: 1,
    audits: [
      {
        id: 1,
        issueType: "PRICE_MISMATCH",
        skuCode: "SKU-MONITOR-27",
        itemDescription: "27-inch 4K Monitor",
        expectedValue: 4000,
        actualValue: 4500,
        explanation: "Invoiced price 4,500 exceeds PO price 4,000",
      },
    ],
    disputeDraft: "Full bilingual dispute draft",
    disputeDraftArabic: "مسودة النزاع باللغة العربية",
    disputeDraftEnglish: "English dispute draft content",
    fileDownloadUri: "/api/invoices/77/file",
  };

  const mockOnUpdate = vi.fn();

  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("renders Debit Note download link with correct URL", () => {
    render(
      <DisputeActionDrawer
        invoice={mockInvoice}
        onInvoiceUpdated={mockOnUpdate}
      />
    );

    const debitNoteLink = screen.getByRole("link", { name: /Debit Note \(PDF\)/i });
    expect(debitNoteLink).toBeInTheDocument();
    expect(debitNoteLink).toHaveAttribute("href", "/api/invoices/77/debit-note");
  });

  it("renders Supplier Portal link generation button", () => {
    render(
      <DisputeActionDrawer
        invoice={mockInvoice}
        onInvoiceUpdated={mockOnUpdate}
      />
    );

    const portalBtn = screen.getByRole("button", { name: /Supplier Portal Link/i });
    expect(portalBtn).toBeInTheDocument();
  });

  it("switches language tabs cleanly between Arabic and English", () => {
    render(
      <DisputeActionDrawer
        invoice={mockInvoice}
        onInvoiceUpdated={mockOnUpdate}
      />
    );

    // Initial state (both): displays both Arabic and English text
    expect(screen.getByText(/مسودة النزاع باللغة العربية/)).toBeInTheDocument();
    expect(screen.getByText(/English dispute draft content/)).toBeInTheDocument();

    // Click Arabic tab
    const arTab = screen.getByRole("button", { name: /العربية \(Arabic\)/i });
    fireEvent.click(arTab);
    expect(screen.getByText("مسودة النزاع باللغة العربية")).toBeInTheDocument();

    // Click English tab
    const enTab = screen.getByRole("button", { name: /^English$/i });
    fireEvent.click(enTab);
    expect(screen.getByText("English dispute draft content")).toBeInTheDocument();
  });
});
