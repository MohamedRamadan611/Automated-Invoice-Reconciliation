import type { Metadata } from "next";
import DashboardClient from "@/components/DashboardClient";

export const metadata: Metadata = {
  title: "AP Reconciliation Engine | Automated Invoice Audit Queue",
  description:
    "Autonomous accounts payable reconciliation engine combining multimodal Google Gemini extraction with deterministic Java audit logic.",
};

export default function DashboardPage() {
  return <DashboardClient />;
}
