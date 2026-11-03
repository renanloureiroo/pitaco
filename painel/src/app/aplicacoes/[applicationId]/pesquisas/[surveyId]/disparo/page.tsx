import { notFound } from "next/navigation";

import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import {
  getApplication,
  listObservedAttributes,
  listObservedEvents,
} from "@/features/applications";
import {
  ExposurePanel,
  FreeTextNoticePanel,
  TriggerPanel,
  WarningsList,
  getPublicationWarnings,
  getQuotaProgress,
  getSurvey,
  isAssemblyReadOnly,
} from "@/features/surveys";
import { ApiUnavailableError, MAX_SIZE } from "@/shared/api";

export const metadata = { title: "Disparo" };

export default async function TriggerPage({
  params,
}: PageProps<"/aplicacoes/[applicationId]/pesquisas/[surveyId]/disparo">) {
  const { applicationId, surveyId } = await params;

  // Os catálogos são sugestão para os formulários: a página maior basta, porque os eventos e
  // atributos de um app são poucos por natureza.
  const [result, eventsResult, attributesResult, applicationResult, quotaResult, warningsResult] =
    await Promise.all([
      getSurvey(applicationId, surveyId),
      listObservedEvents(applicationId, { page: 0, size: MAX_SIZE }),
      listObservedAttributes(applicationId, { page: 0, size: MAX_SIZE }),
      getApplication(applicationId),
      getQuotaProgress(applicationId, surveyId),
      getPublicationWarnings(applicationId, surveyId),
    ]);

  if (!result.ok) {
    if (result.kind === "not_found") {
      notFound();
    }
    throw new ApiUnavailableError(result);
  }

  for (const secondary of [eventsResult, attributesResult, applicationResult, quotaResult, warningsResult]) {
    if (!secondary.ok) {
      throw new ApiUnavailableError(secondary);
    }
  }

  const survey = result.data;
  const observedEvents = eventsResult.ok ? eventsResult.data.items.map((event) => event.name) : [];
  const observedAttributes = attributesResult.ok
    ? attributesResult.data.items.map((attribute) => ({
        name: attribute.name,
        values: attribute.values.map((value) => value.value),
      }))
    : [];
  const warnings = warningsResult.ok ? warningsResult.data : [];

  return (
    <section className="flex flex-col gap-4">
      <h2 className="font-heading text-lg font-medium">Disparo e segmentação</h2>
      <TriggerPanel
        applicationId={applicationId}
        surveyId={surveyId}
        trigger={survey.content?.trigger}
        observedEvents={observedEvents}
        observedAttributes={observedAttributes}
        readOnly={isAssemblyReadOnly(survey)}
      />

      {warnings.length > 0 ? (
        <Card>
          <CardHeader>
            <CardTitle>Avisos</CardTitle>
          </CardHeader>
          <CardContent>
            <WarningsList applicationId={applicationId} warnings={warnings} />
          </CardContent>
        </Card>
      ) : null}

      <ExposurePanel
        applicationId={applicationId}
        surveyId={surveyId}
        priority={survey.priority}
        responseQuota={survey.responseQuota}
        ignoresQuietPeriod={survey.ignoresQuietPeriod}
        quietPeriodDays={applicationResult.ok ? applicationResult.data.quietPeriodDays : undefined}
        quotaProgress={quotaResult.ok ? quotaResult.data : undefined}
        readOnly={survey.state === "ended"}
      />

      <FreeTextNoticePanel
        applicationId={applicationId}
        surveyId={surveyId}
        notice={survey.freeTextNotice}
        hasFreeText={survey.content?.questions.some((question) => question.type === "free_text") ?? false}
        readOnly={survey.state === "ended"}
      />
    </section>
  );
}
