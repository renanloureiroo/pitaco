import { json, notFound, paginate, readPageQuery, validation, type Route, type StubResponse } from "../http.ts";
import { store, type StubAnswer, type StubDisplay, type StubQuestion, type StubSurvey } from "../store.ts";
import { surveyOf } from "./surveys.ts";

/**
 * Resultados calculados sobre o store, com as mesmas regras do backend: taxa sobre todas as
 * exibições, abandono derivado de abertura vencida, pergunta sem resposta sem agregado, texto
 * vencido e vazio fora das respostas abertas, CSV RFC 4180 com BOM.
 */

const DISPLAY_TIMEOUT_MS = 30 * 60 * 1000;
const SMALL_SAMPLE_THRESHOLD = 10;
const DEFINITION =
  "Taxa de resposta = concluídas ÷ exibidas. Exibidas conta toda abertura da pesquisa, " +
  "inclusive as dispensadas, as abandonadas e as ainda em andamento.";

type Filter = {
  from?: string;
  to?: string;
  attribute?: string;
  attributeValue?: string;
  version?: number;
};

function readFilter(query: URLSearchParams): Filter | StubResponse {
  const filter: Filter = {};
  const errors: Record<string, string> = {};

  for (const key of ["from", "to"] as const) {
    const raw = query.get(key);
    if (raw === null) {
      continue;
    }
    if (Number.isNaN(Date.parse(raw))) {
      errors[key] = "Instante malformado.";
    } else {
      filter[key] = new Date(raw).toISOString();
    }
  }
  if (filter.from !== undefined && filter.to !== undefined && filter.from > filter.to) {
    errors.periodOrdered = "Início do período não pode ser posterior ao fim";
  }

  const attribute = query.get("attribute");
  const attributeValue = query.get("attributeValue");
  if (attribute !== null) {
    if (attribute.trim() === "") {
      errors.attributeNamed = "Nome do atributo não pode ser vazio";
    } else {
      filter.attribute = attribute;
    }
  }
  if (attributeValue !== null) {
    if (attribute === null) {
      errors.attributeValueScoped = "Valor do atributo exige o nome do atributo";
    } else {
      filter.attributeValue = attributeValue;
    }
  }

  const version = query.get("version");
  if (version !== null) {
    const parsed = Number.parseInt(version, 10);
    if (!/^\d+$/.test(version) || parsed < 1) {
      errors.version = "Número de versão deve ser maior ou igual a 1";
    } else {
      filter.version = parsed;
    }
  }

  return Object.keys(errors).length > 0
    ? validation("request.invalid", "Requisição inválida.", errors)
    : filter;
}

function matches(display: StubDisplay, filter: Filter): boolean {
  const openedAt = new Date(display.openedAt).toISOString();
  const attributeOk =
    filter.attribute === undefined
      ? true
      : filter.attributeValue === undefined
        ? !(filter.attribute in display.attributes)
        : display.attributes[filter.attribute] === filter.attributeValue;

  return (
    (filter.version === undefined || display.versionNumber === filter.version) &&
    (filter.from === undefined || openedAt >= filter.from) &&
    (filter.to === undefined || openedAt <= filter.to) &&
    attributeOk
  );
}

function displaysOf(survey: StubSurvey, filter: Filter): StubDisplay[] {
  return [...store.displays.values()].filter(
    (display) =>
      display.applicationId === survey.applicationId &&
      display.surveyId === survey.id &&
      matches(display, filter),
  );
}

function resolution(display: StubDisplay, now: number): "COMPLETED" | "DISMISSED" | "ABANDONED" | "IN_PROGRESS" {
  if (display.outcome !== "STARTED") {
    return display.outcome;
  }
  return Date.parse(display.openedAt) < now - DISPLAY_TIMEOUT_MS ? "ABANDONED" : "IN_PROGRESS";
}

/** Consolidado: uma por chave, definição da versão publicada mais recente. */
function questionsOf(survey: StubSurvey, version?: number): StubQuestion[] {
  const chosen = new Map<string, StubQuestion>();
  [...survey.versions]
    .filter((entry) => entry.status === "published")
    .filter((entry) => version === undefined || entry.number === version)
    .sort((a, b) => b.number - a.number)
    .forEach((entry) => {
      for (const question of entry.questions) {
        if (!chosen.has(question.key)) {
          chosen.set(question.key, question);
        }
      }
    });
  return [...chosen.values()].sort((a, b) => a.position - b.position || a.key.localeCompare(b.key));
}

function answered(answer: StubAnswer): boolean {
  return answer.status === "ANSWERED";
}

function aggregateOf(question: StubQuestion, answers: StubAnswer[]) {
  const total = answers.length;
  const share = (count: number) => (total === 0 ? 0 : count / total);

  switch (question.type) {
    case "single_choice":
    case "multiple_choice": {
      const counts = new Map<string, number>();
      for (const answer of answers) {
        for (const option of answer.options) {
          counts.set(option, (counts.get(option) ?? 0) + 1);
        }
      }
      const options = (question.options ?? []).map((option) => ({
        value: option.value,
        label: option.label,
        count: counts.get(option.value) ?? 0,
        share: share(counts.get(option.value) ?? 0),
      }));
      for (const [value, count] of counts) {
        if (!(question.options ?? []).some((option) => option.value === value)) {
          options.push({ value, label: value, count, share: share(count) });
        }
      }
      return { kind: "choice", options };
    }
    case "rating":
    case "scale":
    case "nps": {
      const counts = new Map<number, number>();
      let sum = 0;
      for (const answer of answers) {
        if (answer.number !== undefined) {
          counts.set(answer.number, (counts.get(answer.number) ?? 0) + 1);
          sum += answer.number;
        }
      }
      const range = question.type === "nps" ? { min: 0, max: 10 } : question.range;
      const values = new Set<number>(counts.keys());
      if (range !== undefined) {
        for (let value = range.min; value <= range.max; value++) {
          values.add(value);
        }
      }
      const distribution = [...values]
        .sort((a, b) => a - b)
        .map((value) => ({ value, count: counts.get(value) ?? 0, share: share(counts.get(value) ?? 0) }));

      if (question.type === "nps") {
        let promoters = 0;
        let passives = 0;
        let detractors = 0;
        for (const [value, count] of counts) {
          if (value >= 9) {
            promoters += count;
          } else if (value >= 7) {
            passives += count;
          } else {
            detractors += count;
          }
        }
        const graded = promoters + passives + detractors;
        return {
          kind: "nps",
          promoters,
          passives,
          detractors,
          score: graded === 0 ? 0 : ((promoters - detractors) * 100) / graded,
          distribution,
        };
      }
      return { kind: "numeric", average: total === 0 ? 0 : sum / total, distribution };
    }
    case "free_text":
      return { kind: "text" };
  }
}

/** O que muda o sentido de uma resposta: tipo, opções pelo valor, faixa e condição. */
function shapeOf(question: StubQuestion): string {
  const options = (question.options ?? []).map((option) => option.value).sort().join(",");
  const range =
    question.type === "nps"
      ? "0-10"
      : question.range !== undefined
        ? `${question.range.min}-${question.range.max}`
        : "-";
  const condition = question.condition;
  const conditionShape =
    condition === undefined
      ? "-"
      : [
          condition.sourceKey,
          condition.operator,
          [...condition.values].sort().join(","),
          condition.min ?? "-",
          condition.max ?? "-",
        ].join("|");
  return [question.type, options, range, conditionShape].join("#");
}

/** Pergunta a pergunta, só entre as versões que receberam exibição no recorte. */
function comparabilityOf(survey: StubSurvey, key: string, displayed: Set<number>) {
  const shapes = survey.versions
    .filter((version) => version.status === "published" && displayed.has(version.number))
    .sort((a, b) => a.number - b.number)
    .flatMap((version) =>
      version.questions
        .filter((question) => question.key === key)
        .map((question) => ({ version: version.number, shape: shapeOf(question) })),
    );

  return {
    comparable: shapes.every((entry) => entry.shape === shapes[0]?.shape),
    versions: shapes.map((entry) => entry.version),
  };
}

/** NPS no topo só para pesquisa nascida do modelo, a partir da primeira pergunta de NPS. */
function npsOf(
  survey: StubSurvey,
  questions: Array<{ key: string; type: string; answered: number; aggregate?: unknown }>,
) {
  if (survey.templateKind !== "nps") {
    return {};
  }
  const question = questions.find((entry) => entry.type === "nps");
  if (question === undefined) {
    return {};
  }
  const aggregate = question.aggregate as
    | { promoters?: number; passives?: number; detractors?: number; score?: number }
    | undefined;

  return {
    nps: {
      questionKey: question.key,
      respondents: question.answered,
      promoters: aggregate?.promoters ?? 0,
      passives: aggregate?.passives ?? 0,
      detractors: aggregate?.detractors ?? 0,
      ...(aggregate?.score !== undefined ? { score: aggregate.score } : {}),
    },
  };
}

function answeredAtOf(display: StubDisplay): string {
  return display.closedAt ?? display.openedAt;
}

function csvCell(value: string): string {
  return /[",\r\n]/.test(value) ? `"${value.replace(/"/g, '""')}"` : value;
}

// Como no backend: o congelado só entra no consolidado sem período nem atributo.
function retentionOf(surveyId: string, filter: Filter) {
  const snapshots = store.retentionSnapshots.filter((snapshot) => snapshot.surveyId === surveyId);
  if (snapshots.length === 0) {
    return {};
  }

  const discardedBefore = snapshots.map((snapshot) => snapshot.discardedBefore).sort().at(-1)!;
  const applied =
    filter.from === undefined && filter.to === undefined && filter.attribute === undefined;

  return {
    retention: {
      snapshotApplied: applied,
      discardedBefore,
      note: applied
        ? "Inclui o agregado das respostas descartadas pela política de retenção."
        : "As respostas descartadas pela política de retenção não entram em recorte de período ou de atributo.",
    },
  };
}

export const resultRoutes: Route[] = [
  {
    method: "GET",
    pattern: "/applications/:applicationId/surveys/:surveyId/results",
    handler: ({ params, query }) => {
      const survey = surveyOf(params.applicationId, params.surveyId);
      if (survey === undefined) {
        return notFound("survey.not_found", "Pesquisa não encontrada.");
      }
      const filter = readFilter(query);
      if ("status" in filter) {
        return filter;
      }

      const everPublished = survey.versions.some((version) => version.status === "published");
      const filterEcho = {
        ...(filter.from !== undefined ? { from: filter.from } : {}),
        ...(filter.to !== undefined ? { to: filter.to } : {}),
        ...(filter.attribute !== undefined ? { attribute: filter.attribute } : {}),
        ...(filter.attributeValue !== undefined ? { attributeValue: filter.attributeValue } : {}),
        attributeAbsent: filter.attribute !== undefined && filter.attributeValue === undefined,
        ...(filter.version !== undefined ? { version: filter.version } : {}),
      };

      if (!everPublished) {
        return json(200, {
          everPublished: false,
          responseRate: {
            displayed: 0,
            completed: 0,
            dismissed: 0,
            abandoned: 0,
            inProgress: 0,
            definition: DEFINITION,
            timeline: [],
          },
          questions: [],
          sampleSize: 0,
          smallSample: true,
          filter: filterEcho,
          attributes: [],
        });
      }

      const now = Date.now();
      const displays = displaysOf(survey, filter);
      const displayedVersions = new Set(displays.map((display) => display.versionNumber));
      const resolutions = displays.map((display) => resolution(display, now));
      const displayed = displays.length;
      const completed = resolutions.filter((entry) => entry === "COMPLETED").length;

      const timeline = new Map<string, { displayed: number; completed: number }>();
      for (const display of displays) {
        const day = new Date(display.openedAt).toISOString().slice(0, 10);
        const point = timeline.get(day) ?? { displayed: 0, completed: 0 };
        point.displayed += 1;
        if (display.outcome === "COMPLETED") {
          point.completed += 1;
        }
        timeline.set(day, point);
      }

      const questions = questionsOf(survey, filter.version).map((question) => {
        const answers = displays.flatMap((display) =>
          display.answers.filter((answer) => answer.questionKey === question.key),
        );
        const given = answers.filter(answered);
        return {
          key: question.key,
          statement: question.statement,
          type: question.type,
          position: question.position,
          answered: given.length,
          skipped: answers.filter((answer) => answer.status === "SKIPPED").length,
          notApplicable: answers.filter((answer) => answer.status === "NOT_APPLICABLE").length,
          ...(given.length > 0 ? { aggregate: aggregateOf(question, given) } : {}),
          ...(filter.version === undefined
            ? { comparability: comparabilityOf(survey, question.key, displayedVersions) }
            : {}),
        };
      });

      const catalog = new Map<string, Map<string, number>>();
      for (const display of store.displays.values()) {
        if (display.applicationId !== survey.applicationId || display.surveyId !== survey.id) {
          continue;
        }
        for (const [name, value] of Object.entries(display.attributes)) {
          const values = catalog.get(name) ?? new Map<string, number>();
          values.set(value, (values.get(value) ?? 0) + 1);
          catalog.set(name, values);
        }
      }

      const sampleSize = displays.filter((display) => display.answers.some(answered)).length;

      return json(200, {
        everPublished: true,
        responseRate: {
          displayed,
          completed,
          dismissed: resolutions.filter((entry) => entry === "DISMISSED").length,
          abandoned: resolutions.filter((entry) => entry === "ABANDONED").length,
          inProgress: resolutions.filter((entry) => entry === "IN_PROGRESS").length,
          ...(displayed > 0 ? { rate: completed / displayed } : {}),
          definition: DEFINITION,
          timeline: [...timeline.entries()]
            .sort(([a], [b]) => a.localeCompare(b))
            .map(([day, point]) => ({ day, ...point })),
        },
        questions,
        sampleSize,
        smallSample: sampleSize < SMALL_SAMPLE_THRESHOLD,
        filter: filterEcho,
        attributes: [...catalog.entries()]
          .sort(([a], [b]) => a.localeCompare(b))
          .map(([name, values]) => ({
            name,
            values: [...values.entries()]
              .sort(([a], [b]) => a.localeCompare(b))
              .map(([value, count]) => ({ value, count })),
          })),
        ...npsOf(survey, questions),
        ...retentionOf(survey.id, filter),
      });
    },
  },
  {
    method: "GET",
    pattern: "/applications/:applicationId/surveys/:surveyId/results/open-answers",
    handler: ({ params, query }) => {
      const survey = surveyOf(params.applicationId, params.surveyId);
      if (survey === undefined) {
        return notFound("survey.not_found", "Pesquisa não encontrada.");
      }
      const filter = readFilter(query);
      if ("status" in filter) {
        return filter;
      }
      const term = query.get("q")?.trim().toLowerCase();
      if (term !== undefined && term.length > 200) {
        return validation("request.invalid", "Requisição inválida.", { q: "Termo longo demais." });
      }

      const definitions = new Map(questionsOf(survey).map((question) => [question.key, question]));
      const labelOf = (question: StubQuestion | undefined, value: string) =>
        question?.options?.find((option) => option.value === value)?.label ?? value;

      const items = displaysOf(survey, filter)
        .flatMap((display) =>
          display.answers
            .filter(answered)
            .filter((answer) => answer.text !== undefined && answer.text.trim() !== "")
            .filter((answer) => term === undefined || term === "" || answer.text!.toLowerCase().includes(term))
            .map((answer) => ({
              displayId: display.id,
              questionKey: answer.questionKey,
              statement: definitions.get(answer.questionKey)?.statement ?? answer.questionKey,
              text: answer.text!,
              answeredAt: answeredAtOf(display),
              context: display.answers
                .filter(answered)
                .filter((other) => other.questionKey !== answer.questionKey)
                .map((other) => {
                  const question = definitions.get(other.questionKey);
                  const value =
                    other.text ??
                    (other.number !== undefined
                      ? String(other.number)
                      : other.options.map((option) => labelOf(question, option)).join(", "));
                  return {
                    questionKey: other.questionKey,
                    statement: question?.statement ?? other.questionKey,
                    value,
                  };
                })
                .filter((entry) => entry.value !== ""),
            })),
        )
        .sort((a, b) => b.answeredAt.localeCompare(a.answeredAt) || b.displayId.localeCompare(a.displayId));

      return json(200, paginate(items, readPageQuery(query)));
    },
  },
  {
    method: "GET",
    pattern: "/applications/:applicationId/surveys/:surveyId/results/export",
    handler: ({ params, query }) => {
      const survey = surveyOf(params.applicationId, params.surveyId);
      if (survey === undefined) {
        return notFound("survey.not_found", "Pesquisa não encontrada.");
      }
      const filter = readFilter(query);
      if ("status" in filter) {
        return filter;
      }

      const now = Date.now();
      const displays = displaysOf(survey, filter).sort(
        (a, b) => a.openedAt.localeCompare(b.openedAt) || a.id.localeCompare(b.id),
      );
      const attributeNames = [...new Set(displays.flatMap((display) => Object.keys(display.attributes)))].sort();
      const questions = questionsOf(survey, filter.version);

      const header = [
        "displayId",
        "respondentReference",
        "versionNumber",
        "outcome",
        "openedAt",
        "closedAt",
        "sdkVersion",
        ...attributeNames.map((name) => `attribute:${name}`),
        ...questions.map((question) => `${question.statement} [${question.key}]`),
      ];

      const rows = displays.map((display) => [
        display.id,
        store.respondents.get(display.respondentId)?.identityValue ?? "",
        String(display.versionNumber),
        resolution(display, now),
        display.openedAt,
        display.closedAt ?? "",
        display.sdkVersion ?? "",
        ...attributeNames.map((name) => display.attributes[name] ?? ""),
        ...questions.map((question) => {
          const answer = display.answers.find((entry) => entry.questionKey === question.key);
          if (answer?.status === "NOT_APPLICABLE") {
            return "n/a";
          }
          if (answer === undefined || !answered(answer)) {
            return "";
          }
          return answer.text ?? (answer.number !== undefined ? String(answer.number) : answer.options.join(";"));
        }),
      ]);

      const text = "﻿" + [header, ...rows].map((row) => row.map(csvCell).join(",")).join("\r\n") + "\r\n";

      return {
        status: 200,
        raw: {
          contentType: "text/csv;charset=UTF-8",
          headers: {
            "Content-Disposition": `attachment; filename="resultados-${survey.id}.csv"`,
          },
          text,
        },
      };
    },
  },
];
