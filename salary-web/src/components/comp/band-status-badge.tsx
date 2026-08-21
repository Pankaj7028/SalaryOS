import { Badge } from "@/components/ui/badge";
import { cn } from "@/lib/utils";
import { BAND_STATUS_LABEL, type BandStatus } from "@/lib/money";

/**
 * Band status (§7.4). The TEXT is always present — colour alone never carries the
 * meaning, which is both an accessibility rule and a print/screenshot one.
 */
const TONE: Record<BandStatus, string> = {
  IN_BAND: "border-border text-foreground",
  BELOW_MIN: "border-attention/40 bg-attention-subtle text-attention",
  ABOVE_MAX: "border-critical/40 bg-critical-subtle text-critical",
  NO_BAND: "border-dashed border-border text-muted-foreground",
};

export function BandStatusBadge({ status, className }: { status: BandStatus; className?: string }) {
  return (
    <Badge variant="outline" className={cn("type-label", TONE[status], className)}>
      {BAND_STATUS_LABEL[status]}
    </Badge>
  );
}
