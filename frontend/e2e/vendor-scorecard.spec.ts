import { test, expect } from "@playwright/test";

test.describe("E2E: Supplier Reliability Scorecard", () => {
  test.beforeEach(async ({ page }) => {
    // Mock Vendor Scorecard API
    await page.route("**/api/vendors/scorecard", async (route) => {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify([
          {
            vendorName: "Alpha Logistics Egypt",
            totalInvoicesAudited: 12,
            cleanInvoicesCount: 12,
            discrepancyInvoicesCount: 0,
            cleanMatchRate: 100.0,
            discrepancyRate: 0.0,
            totalAuditedVolume: 240000,
            totalOverchargeAttempted: 0,
            tier: "CERTIFIED_PARTNER",
            topDiscrepancyCategories: [],
            negotiationAdvice: "Exemplary billing compliance.",
            lastAuditedAt: new Date().toISOString(),
          },
          {
            vendorName: "Cairo Building Tech",
            totalInvoicesAudited: 10,
            cleanInvoicesCount: 6,
            discrepancyInvoicesCount: 4,
            cleanMatchRate: 60.0,
            discrepancyRate: 40.0,
            totalAuditedVolume: 150000,
            totalOverchargeAttempted: 8500,
            tier: "HIGH_RISK",
            topDiscrepancyCategories: ["PRICE_MISMATCH", "EXTRA_FEE"],
            negotiationAdvice: "Frequent unauthorized rate increases.",
            lastAuditedAt: new Date().toISOString(),
          },
        ]),
      });
    });

    // Mock Invoices API
    await page.route("**/api/invoices", async (route) => {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify([]),
      });
    });
  });

  test("loads scorecard table, searches vendor, and filters by tier", async ({ page }) => {
    await page.goto("/vendors");

    // Check Header
    await expect(page.locator("h1")).toContainText("Supplier Reliability Scorecard");

    // Both suppliers visible initially
    await expect(page.getByText("Alpha Logistics Egypt")).toBeVisible();
    await expect(page.getByText("Cairo Building Tech")).toBeVisible();

    // Search for "Cairo"
    const searchInput = page.getByPlaceholder("Search supplier name...");
    await searchInput.fill("Cairo");
    await expect(page.getByText("Cairo Building Tech")).toBeVisible();
    await expect(page.getByText("Alpha Logistics Egypt")).not.toBeVisible();

    // Clear search
    await searchInput.clear();
    await expect(page.getByText("Alpha Logistics Egypt")).toBeVisible();

    // Filter by Certified tab
    const certifiedTab = page.getByRole("button", { name: /Certified/i });
    await certifiedTab.click();
    await expect(page.getByText("Alpha Logistics Egypt")).toBeVisible();
    await expect(page.getByText("Cairo Building Tech")).not.toBeVisible();
  });
});
