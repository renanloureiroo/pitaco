import { retentionNoteText } from "../lib/results-labels";
import type { Retention } from "../schemas/results";

/** Número que inclui dado descartado, ou que o deixa de fora, precisa dizer isso junto. */
export function RetentionNote({ retention }: { retention: Retention }) {
  return (
    <p
      data-testid="retention-note"
      data-applied={retention.snapshotApplied ? "true" : "false"}
      className="rounded-md border px-3 py-2 text-sm text-muted-foreground"
    >
      {retentionNoteText(retention)}
    </p>
  );
}
