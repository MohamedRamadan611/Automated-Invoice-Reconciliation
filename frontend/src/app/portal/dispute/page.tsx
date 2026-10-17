import { Suspense } from "react";
import SupplierPortalClient from "@/components/SupplierPortalClient";
import { Loader2 } from "lucide-react";

export const metadata = {
  title: "Supplier Dispute Settlement Portal | Autonomous AP",
  description: "Secure self-service commercial dispute settlement and payment release portal for suppliers.",
};

export default function SupplierDisputePage() {
  return (
    <main className="min-h-screen bg-slate-950 text-slate-100 py-10 px-4 sm:px-6 lg:px-8">
      <Suspense
        fallback={
          <div className="flex flex-col items-center justify-center min-h-[60vh] space-y-4">
            <Loader2 className="w-10 h-10 animate-spin text-emerald-400" />
            <p className="text-sm font-medium text-slate-400">Loading dispute portal session...</p>
          </div>
        }
      >
        <SupplierPortalClient />
      </Suspense>
    </main>
  );
}
