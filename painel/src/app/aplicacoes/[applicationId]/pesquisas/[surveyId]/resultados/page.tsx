import Link from "next/link";
import { notFound } from "next/navigation";

import { Button } from "@/components/ui/button";
import {
  ActiveFilters,
  ExportButton,
  NEVER_PUBLISHED_DESCRIPTION,
  NEVER_PUBLISHED_TITLE,
  NO_DISPLAYS_DESCRIPTION,
  NO_DISPLAYS_TITLE,
  NO_MATCHES_DESCRIPTION,
  NO_MATCHES_TITLE,
  NpsSummaryCard,
  OPEN_ANSWERS_NOTICE,
  OpenAnswersList,
  OpenAnswersSearch,
  QuestionResultCard,
  RefreshControl,
  ResponseRateCard,
  RetentionNote,
  ResultsFiltersForm,
  exportHref,
  getSurveyResults,
  hasAnyFilter,
  listOpenAnswers,
  parseResultsFilters,
  parseTerm,
  resultsEmptyVariant,
  toResultsQuery,
  type ResultsEmptyVariant,
} from "@/features/results";
import { SurveyHealthNotices, getSurveyHealth, surveyHealthNotices } from "@/features/health";
import { getVersionComparability } from "@/features/surveys";
import { ApiUnavailableError, parsePaginationParams } from "@/shared/api";
import { EmptyState, Pagination } from "@/shared/components";
import { TIMEZONE_NOTE } from "@/shared/lib";

export const metadata = { title: "Resultados" };

/**
 * Casca fina: lê o recorte da URL, busca resultado, respostas abertas e versões em paralelo,
 * e compõe. Um recorte só para as três leituras — nunca uma parte da tela com outro número.
 */
export default async function SurveyResultsPage({
  params,
  searchParams,
}: PageProps<"/aplicacoes/[applicationId]/pesquisas/[surveyId]/resultados">) {
  const { applicationId, surveyId } = await params;
  const query = await searchParams;
  const filters = parseResultsFilters(query);
  const term = parseTerm(query);
  const { page, size } = parsePaginationParams(query);
  const backendQuery = toResultsQuery(filters);

  const [resultsResult, answersResult, comparabilityResult, healthResult] = await Promise.all([
    getSurveyResults(applicationId, surveyId, backendQuery),
    listOpenAnswers(applicationId, surveyId, {
      page,
      size,
      ...(term !== undefined ? { q: term } : {}),
      ...backendQuery,
    }),
    getVersionComparability(applicationId, surveyId),
    getSurveyHealth(applicationId, surveyId),
  ]);

  if (!resultsResult.ok) {
    if (resultsResult.kind === "not_found") {
      notFound();
    }
    throw new ApiUnavailableError(resultsResult);
  }
  if (!answersResult.ok) {
    if (answersResult.kind === "not_found") {
      notFound();
    }
    throw new ApiUnavailableError(answersResult);
  }
  if (!comparabilityResult.ok) {
    throw new ApiUnavailableError(comparabilityResult);
  }

  const results = resultsResult.data;
  // A saúde complementa a leitura: se ela falhar, a tela de resultados continua de pé.
  const notices = healthResult.ok ? surveyHealthNotices(healthResult.data) : [];
  const answers = answersResult.data;
  const versions = comparabilityResult.data.groups
    .flatMap((group) => group.versions)
    .sort((a, b) => b - a);
  const base = `/aplicacoes/${applicationId}/pesquisas/${surveyId}`;
  const filtered = hasAnyFilter(filters);
  const variant = resultsEmptyVariant({
    everPublished: results.everPublished,
    displayed: results.responseRate.displayed,
    filtered,
  });

  return (
    <section className="flex flex-col gap-6">
      <div className="flex flex-wrap items-center justify-between gap-4">
        <h2 className="font-heading text-lg font-medium">Resultados</h2>
        <div className="flex flex-wrap items-center gap-3">
          <p data-testid="timezone-note" className="text-sm text-muted-foreground">
            {TIMEZONE_NOTE}
          </p>
          {results.everPublished ? (
            <>
              <RefreshControl />
              <ExportButton href={exportHref(applicationId, surveyId, backendQuery)} />
            </>
          ) : null}
        </div>
      </div>

      <SurveyHealthNotices notices={notices} />

      {variant === "never_published" ? null : (
        <>
          <ResultsFiltersForm filters={filters} attributes={results.attributes} versions={versions} />
          <ActiveFilters filters={filters} />
        </>
      )}

      {variant !== undefined ? (
        // Pesquisa suprimida não pode aparecer como "nenhuma exibição ainda": quem não a viu
        // não teve como responder. O evento que nunca chegou convive com o vazio, que já aponta
        // o disparo.
        variant === "no_displays" && notices.some((notice) => notice.kind === "suppression") ? null : (
          <EmptyVariant variant={variant} base={base} />
        )
      ) : (
        <>
          {results.nps !== undefined ? <NpsSummaryCard nps={results.nps} /> : null}
          <ResponseRateCard responseRate={results.responseRate} smallSample={results.smallSample} />
          {results.retention !== undefined ? <RetentionNote retention={results.retention} /> : null}

          <div data-testid="question-results" className="flex flex-col gap-4">
            {results.questions.map((question) => (
              <QuestionResultCard key={question.key} question={question} />
            ))}
          </div>

          <section data-testid="open-answers" className="flex flex-col gap-3">
            <div className="flex flex-wrap items-center justify-between gap-3">
              <h3 className="font-heading text-base font-medium">Respostas abertas</h3>
              <OpenAnswersSearch term={term} />
            </div>
            <p className="text-xs text-muted-foreground">{OPEN_ANSWERS_NOTICE}</p>

            {answers.items.length === 0 ? (
              <p data-testid="open-answers-empty" className="text-sm text-muted-foreground">
                {term === undefined
                  ? "Nenhuma resposta de texto neste recorte."
                  : `Nenhuma resposta contém "${term}".`}
              </p>
            ) : (
              <>
                <OpenAnswersList answers={answers.items} />
                <Pagination pathname={`${base}/resultados`} searchParams={query} page={answers} />
              </>
            )}
          </section>
        </>
      )}
    </section>
  );
}

function EmptyVariant({ variant, base }: { variant: ResultsEmptyVariant; base: string }) {
  if (variant === "never_published") {
    return (
      <EmptyState
        title={NEVER_PUBLISHED_TITLE}
        description={NEVER_PUBLISHED_DESCRIPTION}
        action={
          <Button asChild>
            <Link href={`${base}/publicacao`}>Ir para a publicação</Link>
          </Button>
        }
      />
    );
  }

  if (variant === "no_matches") {
    return (
      <EmptyState
        title={NO_MATCHES_TITLE}
        description={NO_MATCHES_DESCRIPTION}
        action={
          <Button asChild variant="outline">
            <Link href={`${base}/resultados`}>Limpar recorte</Link>
          </Button>
        }
      />
    );
  }

  return (
    <EmptyState
      title={NO_DISPLAYS_TITLE}
      description={NO_DISPLAYS_DESCRIPTION}
      action={
        <Button asChild variant="outline">
          <Link href={`${base}/disparo`}>Ver o disparo</Link>
        </Button>
      }
    />
  );
}
