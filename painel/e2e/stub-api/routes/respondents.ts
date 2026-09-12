import { json, notFound, paginate, readPageQuery, type Route } from "../http.ts";
import { store } from "../store.ts";
import { matchesFilter, orderDisplays, readDisplayFilter, toSummary } from "./displays.ts";

function toRespondent(respondent: {
  id: string;
  identityKind: string;
  identityValue: string;
  firstSeenAt: string;
  lastSeenAt: string;
}) {
  return {
    id: respondent.id,
    identityKind: respondent.identityKind,
    identityValue: respondent.identityValue,
    firstSeenAt: respondent.firstSeenAt,
    lastSeenAt: respondent.lastSeenAt,
  };
}

export const respondentRoutes: Route[] = [
  {
    method: "GET",
    pattern: "/applications/:applicationId/respondents",
    handler: ({ params, query }) => {
      const items = [...store.respondents.values()]
        .filter((respondent) => respondent.applicationId === params.applicationId)
        .sort((a, b) => b.lastSeenAt.localeCompare(a.lastSeenAt))
        .map(toRespondent);

      return json(200, paginate(items, readPageQuery(query)));
    },
  },
  {
    method: "GET",
    pattern: "/applications/:applicationId/respondents/:respondentId/displays",
    handler: ({ params, query }) => {
      const respondent = store.respondents.get(params.respondentId);
      if (respondent === undefined || respondent.applicationId !== params.applicationId) {
        return notFound("respondent.not_found", "Respondente não encontrado.");
      }

      const filter = readDisplayFilter(query, { acceptsVersion: false });
      if ("status" in filter) {
        return filter;
      }

      const items = orderDisplays(
        [...store.displays.values()].filter(
          (display) =>
            display.applicationId === params.applicationId &&
            display.respondentId === params.respondentId &&
            matchesFilter(display, filter),
        ),
      ).map((display) => ({ ...toSummary(display), surveyId: display.surveyId }));

      return json(200, paginate(items, readPageQuery(query)));
    },
  },
];
