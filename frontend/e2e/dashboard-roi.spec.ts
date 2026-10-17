import { test, expect } from "@playwright/test";

test.describe("E2E: ROI Dashboard & Executive Brief", () => {
  test.beforeEach(async ({ page }) => {
    // Mock ROI Summary API
    await page.route("**/api/analytics/roi-summary", async (route) => {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({
          totalInvoicedVolume: 75000,
          totalCashLeakageBlocked: 6200,
          priceMismatchLeakage: 4200,
          extraFeesBlocked: 2000,
          quantityShortageValueBlocked: 0,
          totalInvoicesAudited: 5,
          cleanInvoicesCount: 3,
          discrepancyInvoicesCount: 2,
          manualReviewCount: 0,
          accuracyRate: 60.0,
          categoryBreakdown: [
            {
              issueType: "PRICE_MISMATCH",
              count: 2,
              totalLeakageAmount: 4200,
              percentageOfTotalLeakage: 67.7,
            },
            {
              issueType: "EXTRA_FEE",
              count: 1,
              totalLeakageAmount: 2000,
              percentageOfTotalLeakage: 32.3,
            },
          ],
          executiveBrief: "AI audit blocked 6,200.00 EGP in unauthorized variances across 5 invoices.",
          currency: "EGP",
        }),
      });
    });

    // Mock Invoices list API
    await page.route("**/api/invoices", async (route) => {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify([
          {
            id: 1,
            invoiceNumber: "INV-E2E-001",
            poReference: "PO-2026-001",
            vendorName: "Delta Produce Co",
            invoicedTotal: 30000,
            reconciliationStatus: "APPROVED",
            discrepancyCount: 0,
            createdAt: new Date().toISOString(),
          },
          {
            id: 2,
            invoiceNumber: "INV-E2E-002",
            poReference: "PO-2026-002",
            vendorName: "Sinai Minerals",
            invoicedTotal: 45000,
            reconciliationStatus: "FLAGGED_DISCREPANCY",
            discrepancyCount: 2,
            createdAt: new Date().toISOString(),
          },
        ]),
      });
    });
  });

  test("displays hero ROI metrics and toggles AI Executive Brief", async ({ page }) => {
    await page.goto("/");

    // Verify header and brand
    await expect(page.locator("h1")).toContainText("Autonomous AP Reconciliation Agent");

    // Verify Hero ROI cards
    await expect(page.getByText("Cash Leakage Blocked")).toBeVisible();
    await expect(page.getByText("Audited AP Volume")).toBeVisible();
    await expect(page.getByText("Clean Match Rate")).toBeVisible();

    // Verify Executive Brief content
    await expect(page.getByText(/AI audit blocked 6,200.00 EGP/)).toBeVisible();

    // Test collapse / expand toggle
    const collapseBtn = page.getByRole("button", { name: /Collapse/i });
    await collapseBtn.click();
    await expect(page.getByText(/AI audit blocked 6,200.00 EGP/)).not.toBeVisible();

    const expandBtn = page.getByRole("button", { name: /Expand Brief/i });
    await expandBtn.click();
    await expect(page.getByText(/AI audit blocked 6,200.00 EGP/)).toBeVisible();

    // Verify invoice table
    await expect(page.getByText("INV-E2E-001")).toBeVisible();
    await expect(page.getByText("INV-E2E-002")).toBeVisible();
  });
});
