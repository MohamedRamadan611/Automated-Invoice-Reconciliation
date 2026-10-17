import React from "react";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { describe, it, expect, vi, beforeEach } from "vitest";
import VendorScorecardClient from "@/components/VendorScorecardClient";
import * as api from "@/lib/api";
import type { VendorScorecardResponse } from "@/lib/types";

vi.mock("@/lib/api", () => ({
  fetchVendorScorecards: vi.fn(),
  fetchInvoices: vi.fn(),
}));

describe("VendorScorecardClient Component", () => {
  const mockScorecards: VendorScorecardResponse[] = [
    {
      vendorName: "Delta Produce Co",
      totalInvoices: 10,
      cleanInvoices: 10,
      discrepancyInvoices: 0,
      cleanMatchRate: 100.0,
      discrepancyRate: 0.0,
      totalBilledAmount: 120000,
      totalOverchargeAttempted: 0,
      tier: "CERTIFIED_PARTNER",
      topOffendingCategories: [],
      riskNarrative: "Exemplary billing compliance.",
      lastAuditedDate: "2026-10-16T14:00:00Z",
    },
    {
      vendorName: "Sinai Hardware Ltd",
      totalInvoices: 8,
      cleanInvoices: 5,
      discrepancyInvoices: 3,
      cleanMatchRate: 62.5,
      discrepancyRate: 37.5,
      totalBilledAmount: 85000,
      totalOverchargeAttempted: 4500,
      tier: "HIGH_RISK",
      topOffendingCategories: [
        { issueType: "PRICE_MISMATCH", label: "Price Mismatch", count: 2, totalAmount: 3000 },
        { issueType: "EXTRA_FEE", label: "Extra Fee", count: 1, totalAmount: 1500 },
      ],
      riskNarrative: "High frequency of price inflation. Require strict PO enforcement.",
      lastAuditedDate: "2026-10-16T15:30:00Z",
    },
  ];

  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(api.fetchVendorScorecards).mockResolvedValue(mockScorecards);
    vi.mocked(api.fetchInvoices).mockResolvedValue([]);
  });

  it("renders the vendor scorecard table with suppliers and tier badges", async () => {
    render(<VendorScorecardClient />);

    await waitFor(() => {
      expect(screen.getByText("Delta Produce Co")).toBeInTheDocument();
      expect(screen.getByText("Sinai Hardware Ltd")).toBeInTheDocument();
    });

    // Check accuracy rates
    expect(screen.getByText("100.0%")).toBeInTheDocument();
    expect(screen.getByText("62.5%")).toBeInTheDocument();

    // Check tier labels in table
    expect(screen.getAllByText(/Certified Partner/i).length).toBeGreaterThan(0);
    expect(screen.getAllByText(/High-Risk/i).length).toBeGreaterThan(0);
  });

  it("filters suppliers when typing into search input", async () => {
    render(<VendorScorecardClient />);

    await waitFor(() => {
      expect(screen.getByText("Delta Produce Co")).toBeInTheDocument();
    });

    const searchInput = screen.getByPlaceholderText("Search supplier name...");
    fireEvent.change(searchInput, { target: { value: "Sinai" } });

    expect(screen.getByText("Sinai Hardware Ltd")).toBeInTheDocument();
    expect(screen.queryByText("Delta Produce Co")).not.toBeInTheDocument();
  });

  it("filters suppliers by reliability tier button", async () => {
    render(<VendorScorecardClient />);

    await waitFor(() => {
      expect(screen.getByText("Delta Produce Co")).toBeInTheDocument();
    });

    // Click "High-Risk" tab button
    const highRiskTab = screen.getByRole("button", { name: /High-Risk/i });
    fireEvent.click(highRiskTab);

    expect(screen.getByText("Sinai Hardware Ltd")).toBeInTheDocument();
    expect(screen.queryByText("Delta Produce Co")).not.toBeInTheDocument();
  });
});
