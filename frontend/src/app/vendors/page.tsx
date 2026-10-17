import type { Metadata } from "next";
import VendorScorecardClient from "@/components/VendorScorecardClient";

export const metadata: Metadata = {
  title: "Supplier Reliability Scorecard | Autonomous AP Reconciliation",
  description:
    "Monitor supplier compliance, chronic overcharge patterns, automated risk tiers (Certified, Audit Scrutiny, High-Risk), and AI procurement negotiation advice.",
};

export default function VendorsPage() {
  return <VendorScorecardClient />;
}
