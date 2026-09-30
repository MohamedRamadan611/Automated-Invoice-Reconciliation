import type { Metadata } from "next";
import InvoiceWorkspaceClient from "@/components/InvoiceWorkspaceClient";

interface PageProps {
  params: Promise<{ id: string }> | { id: string };
}

export async function generateMetadata({ params }: PageProps): Promise<Metadata> {
  const resolved = await params;
  return {
    title: `Invoice Audit #${resolved.id} | AP Reconciliation Engine`,
    description: "Detailed split-screen invoice reconciliation workspace and audit findings.",
  };
}

export default async function InvoicePage({ params }: PageProps) {
  const resolved = await params;
  return <InvoiceWorkspaceClient id={resolved.id} />;
}
