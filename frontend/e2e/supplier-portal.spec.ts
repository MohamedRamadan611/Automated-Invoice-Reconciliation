import { test, expect } from "@playwright/test";

test.describe("E2E: Supplier Dispute Self-Service Portal", () => {
  test("shows access denied when opened without token", async ({ page }) => {
    await page.goto("/portal/dispute");
    await expect(page.getByText("Portal Access Denied")).toBeVisible();
    await expect(page.getByText(/No access token provided/i)).toBeVisible();
  });

  test("loads dispute findings and accepts deduction via magic token", async ({ page }) => {
    const mockToken = "42:PORTAL:9999999999:validSignature";

    // Mock Portal Dispute GET API
    let isResolved = false;
    await page.route("**/api/portal/dispute*", async (route) => {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({
          invoiceId: 42,
          invoiceNumber: "INV-PORTAL-042",
          poReference: "PO-2026-042",
          vendorName: "Sinai Quarry Solutions",
          invoicedTotal: 60000,
          expectedTotal: 55000,
          totalDiscrepancyAmount: 5000,
          adjustedPayableAmount: 55000,
          netPayableAmount: 55000,
          status: isResolved ? "RESOLVED_BY_DEBIT_NOTE" : "FLAGGED_DISCREPANCY",
          isResolved: isResolved,
          debitNoteDownloadUrl: isResolved ? "/api/invoices/42/debit-note" : null,
          discrepancies: [
            {
              issueType: "PRICE_MISMATCH",
              itemDescription: "Raw Dolomite Aggregates",
              expectedValue: 500,
              actualValue: 550,
              varianceAmount: 5000,
              deductionAmount: 5000,
              explanation: "Invoiced unit price 550 exceeds agreed PO price 500",
            },
          ],
          bilingualDisputeDraft: "Official dispute letter",
          expiresAt: new Date(Date.now() + 86400000).toISOString(),
        }),
      });
    });

    // Mock Portal Accept POST API
    await page.route("**/api/portal/dispute/accept*", async (route) => {
      isResolved = true;
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({
          success: true,
          invoiceId: 42,
          invoiceNumber: "INV-PORTAL-042",
          adjustedPayableAmount: 55000,
          totalDeduction: 5000,
          status: "RESOLVED_BY_DEBIT_NOTE",
          debitNoteDownloadUrl: "/api/invoices/42/debit-note",
          message: "Commercial adjustment accepted. Official Debit Note generated.",
        }),
      });
    });

    await page.goto(`/portal/dispute?token=${mockToken}`);

    // Verify headers & line items
    await expect(page.getByText("Supplier Dispute Settlement Portal")).toBeVisible();
    await expect(page.getByText("Sinai Quarry Solutions")).toBeVisible();
    await expect(page.getByText("INV-PORTAL-042")).toBeVisible();
    await expect(page.getByText("Raw Dolomite Aggregates", { exact: true })).toBeVisible();

    // Click Accept Deduction CTA
    const acceptBtn = page.getByRole("button", {
      name: /Accept Deductions & Download Debit Note/i,
    });
    await expect(acceptBtn).toBeVisible();
    await acceptBtn.click();

    // Verify confirmation and Debit Note download button
    await expect(page.getByText(/Dispute Finalized — Commercial Debit Note Issued/i)).toBeVisible();
    await expect(page.getByText("Download Debit Note PDF")).toBeVisible();
  });
});
