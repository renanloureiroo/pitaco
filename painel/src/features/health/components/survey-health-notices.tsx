import { TriangleAlertIcon } from "lucide-react";

import type { HealthNotice } from "../lib/health-labels";

const TEST_IDS: Record<HealthNotice["kind"], string> = {
  suppression: "survey-health-suppression",
  event_missing: "survey-health-event-missing",
};

export function SurveyHealthNotices({ notices }: { notices: HealthNotice[] }) {
  if (notices.length === 0) {
    return null;
  }

  return (
    <ul data-testid="survey-health-notices" className="flex flex-col gap-2">
      {notices.map((notice) => (
        <li
          key={notice.kind}
          data-testid={TEST_IDS[notice.kind]}
          className="flex items-start gap-2 rounded-md border border-amber-500/40 bg-amber-500/5 px-3 py-2 text-sm"
        >
          <TriangleAlertIcon aria-hidden className="mt-0.5 size-4 shrink-0 text-amber-600" />
          <div className="flex flex-col gap-1">
            <span className="font-medium">{notice.title}</span>
            <span>{notice.message}</span>
          </div>
        </li>
      ))}
    </ul>
  );
}
