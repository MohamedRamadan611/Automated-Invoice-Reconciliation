import React from "react";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { describe, it, expect, vi, beforeEach } from "vitest";
import DashboardClient from "@/components/DashboardClient";
import * as api from "@/lib/api";
import type { FinancialRoiSummaryResponse, InvoiceListItemResponse } from "@/lib/types";

// Mock the API module
vi.mock("@/lib/api", () => ({
  fetchInvoices: vi.fn(),
  fetchRoiSummary: vi.fn(),
}));

// Mock DropzoneUploader to simplify dashboard testing
vi.mock("@/components/DropzoneUploader", () => ({
  default: () => <div data-testid="mock-uploader">Mock Uploader</div>,
}));

describe("DashboardClient: ROI Metrics & Executive Brief", () => {
  const mockInvoices: InvoiceListItemResponse[] = [
    {
      id: 1,
      invoiceNumber: "INV-2026-001",
      poReference: "PO-2026-001",
      vendorName: "Al-Wadi Farms",
      invoicedTotal: 15000,
      reconciliationStatus: "APPROVED",
      discrepancyCount: 0,
      createdAt: "2026-10-15T10:00:00Z",
    },
    {
      id: 2,
      invoiceNumber: "INV-2026-002",
      poReference: "PO-2026-002",
      vendorName: "Cairo Hardware",
      invoicedTotal: 25000,
      reconciliationStatus: "FLAGGED_DISCREPANCY",
      discrepancyCount: 2,
      createdAt: "2026-10-15T11:00:00Z",
    },
  ];

  const mockRoi: FinancialRoiSummaryResponse = {
    totalInvoicedVolume: 40000,
    totalCashLeakageBlocked: 3500,
    priceMismatchLeakage: 2500,
    extraFeesBlocked: 1000,
    quantityShortageValuePrevented: 0,
    totalInvoicesAudited: 2,
    cleanInvoicesCount: 1,
    discrepancyInvoicesCount: 1,
    manualReviewCount: 0,
    accuracyRate: 50.0,
    topOffenses: [
      {
        issueType: "PRICE_MISMATCH",
        label: "Price Mismatch",
        count: 1,
        totalAmount: 2500,
      },
      {
        issueType: "EXTRA_FEE",
        label: "Extra Fee",
        count: 1,
        totalAmount: 1000,
      },
    ],
    executiveBrief: "Our AI auditor prevented 3,500.00 EGP in unauthorized charges. Primary driver was unapproved delivery markup.\n\nتم فحص الفواتير واعتراض تسريب مالي غير مصرح به بقيمة 3500 جنيه مصري.",
    currency: "EGP",
  };

  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(api.fetchInvoices).mockResolvedValue(mockInvoices);
    vi.mocked(api.fetchRoiSummary).mockResolvedValue(mockRoi);
  });

  it("renders the 4 Hero ROI KPI cards with correct formatted values", async () => {
    render(<DashboardClient />);

    // Wait for data load
    await waitFor(() => {
      expect(screen.getByText("Cash Leakage Blocked")).toBeInTheDocument();
    });

    // Check Hero KPI labels matching UI
    expect(screen.getByText("Audited AP Volume")).toBeInTheDocument();
    expect(screen.getByText("Clean Match Rate")).toBeInTheDocument();
    expect(screen.getByText("Intercepted Variances")).toBeInTheDocument();

    // Check presence of ROI values using getAllByText to account for brief mentions
    expect(screen.getAllByText(/3,500/).length).toBeGreaterThan(0);
    expect(screen.getByText(/50.0%/)).toBeInTheDocument();
  });

  it("toggles the AI Executive Brief accordion on button click", async () => {
    render(<DashboardClient />);

    await waitFor(() => {
      expect(screen.getByText("Autonomous AI Executive Brief")).toBeInTheDocument();
    });

    // The brief is open by default
    await waitFor(() => {
      expect(
        screen.getByText(/Primary driver was unapproved delivery markup/i)
      ).toBeInTheDocument();
    });

    // Click "Collapse" to hide
    const collapseBtn = screen.getByRole("button", { name: /Collapse/i });
    fireEvent.click(collapseBtn);

    await waitFor(() => {
      expect(
        screen.queryByText(/Primary driver was unapproved delivery markup/i)
      ).not.toBeInTheDocument();
    });

    // Click "Expand Brief" to show again
    const expandBtn = screen.getByRole("button", { name: /Expand Brief/i });
    fireEvent.click(expandBtn);

    await waitFor(() => {
      expect(
        screen.getByText(/Primary driver was unapproved delivery markup/i)
      ).toBeInTheDocument();
    });
  });

  it("renders English text LTR and Arabic text RTL in the same unified Executive Brief card", async () => {
    render(<DashboardClient />);

    await waitFor(() => {
      expect(screen.getByText("Autonomous AI Executive Brief")).toBeInTheDocument();
    });

    // Check that Arabic block is present with dir="rtl"
    const arabicText = screen.getByText(/تم فحص الفواتير واعتراض تسريب مالي/i);
    expect(arabicText).toBeInTheDocument();
    expect(arabicText.getAttribute("dir")).toBe("rtl");

    // Check that English block is present with dir="ltr"
    const englishText = screen.getByText(/Primary driver was unapproved delivery markup/i);
    expect(englishText).toBeInTheDocument();
    expect(englishText.getAttribute("dir")).toBe("ltr");

    // Verify both paragraphs reside within the same parent card container
    expect(arabicText.parentElement).toBe(englishText.parentElement);

    // Verify the removed label is not present
    expect(screen.queryByText(/الملخص التنفيذي بالعربية \(RTL\)/i)).not.toBeInTheDocument();
  });

  it("renders invoice list with status badges", async () => {
    render(<DashboardClient />);

    await waitFor(() => {
      expect(screen.getByText("INV-2026-001")).toBeInTheDocument();
      expect(screen.getByText("INV-2026-002")).toBeInTheDocument();
    });

    expect(screen.getByText("Al-Wadi Farms")).toBeInTheDocument();
    expect(screen.getByText("Cairo Hardware")).toBeInTheDocument();
  });
});
