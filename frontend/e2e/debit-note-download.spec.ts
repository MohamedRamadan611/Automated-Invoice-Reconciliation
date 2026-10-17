import { test, expect } from "@playwright/test";

test.describe("E2E: Invoice Workspace & Debit Note Action", () => {
  test.beforeEach(async ({ page }) => {
    // Mock single invoice breakdown API
    await page.route("**/api/invoices/1", async (route) => {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({
          invoiceId: 1,
          invoiceNumber: "INV-2026-001",
          poReference: "PO-2026-001",
          vendorName: "Al-Wadi Farms",
          reconciliationStatus: "FLAGGED_DISCREPANCY",
          invoicedTotal: 3200,
          expectedTotal: 2750,
          discrepancyCount: 1,
          audits: [
            {
              id: 1,
              issueType: "PRICE_MISMATCH",
              skuCode: "SKU-TOMATO-RED",
              itemDescription: "Egyptian Fresh Tomatoes",
              expectedValue: 20.0,
              actualValue: 25.0,
              explanation: "Unit price 25.00 exceeds PO price 20.00",
            },
          ],
          disputeDraft: "Full bilingual dispute draft",
          disputeDraftArabic: "مسودة النزاع بالعربية",
          disputeDraftEnglish: "Dispute draft in English",
          fileUrl: "/api/invoices/1/file",
        }),
      });
    });

    // Mock document file stream
    await page.route("**/api/invoices/1/file", async (route) => {
      await route.fulfill({
        status: 200,
        contentType: "application/pdf",
        body: "%PDF-1.4 Mock Invoice File",
      });
    });

    // Mock supplier portal link generation API
    await page.route("**/api/invoices/1/portal-link", async (route) => {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({
          token: "1:PORTAL:9999999999:mockToken",
          portalUrl: "http://localhost:3000/portal/dispute?token=1:PORTAL:9999999999:mockToken",
          expiresInDays: "7",
        }),
      });
    });
  });

  test("verifies Debit Note download link and generates supplier portal magic link", async ({ page }) => {
    await page.goto("/invoice/1");

    // Verify invoice number in workspace
    await expect(page.getByText("INV-2026-001").first()).toBeVisible();

    // Check Debit Note (PDF) action link
    const debitNoteLink = page.getByRole("link", { name: /Debit Note \(PDF\)/i });
    await expect(debitNoteLink).toBeVisible();
    await expect(debitNoteLink).toHaveAttribute("href", /.*\/api\/invoices\/1\/debit-note/);

    // Check Supplier Portal Link action button
    const portalBtn = page.getByRole("button", { name: /Supplier Portal Link/i });
    await expect(portalBtn).toBeVisible();
    await portalBtn.click();

    // Check active portal modal banner
    await expect(page.getByText("Supplier Magic Link (7-Day TTL)")).toBeVisible();
    await expect(page.getByText("Open Portal")).toBeVisible();
  });
});
