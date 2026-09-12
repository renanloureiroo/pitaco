import {
  conflict,
  json,
  nextId,
  noContent,
  notFound,
  nowIso,
  paginate,
  problem,
  readPageQuery,
  validation,
  type Route,
  type StubResponse,
} from "../http.ts";
import {
  DEFAULT_FREE_TEXT_NOTICE,
  currentVersion,
  draftVersion,
  publishedVersion,
  settleWindow,
  store,
  type StubCondition,
  type StubQuestion,
  type StubSurvey,
  type StubVersion,
} from "../store.ts";

const CHOICE_TYPES = ["single_choice", "multiple_choice"];
const QUESTION_TYPES = [
  "single_choice",
  "multiple_choice",
  "rating",
  "scale",
  "nps",
  "free_text",
];

/** Toda leitura de pesquisa passa por aqui, e por isso resolve a janela antes de responder. */
export function surveyOf(applicationId: string, surveyId: string): StubSurvey | undefined {
  const survey = store.surveys.get(surveyId);
  return survey !== undefined && survey.applicationId === applicationId
    ? settleWindow(survey)
    : undefined;
}

export function toSurvey(survey: StubSurvey) {
  const published = publishedVersion(survey);
  const draft = draftVersion(survey);

  return {
    id: survey.id,
    applicationId: survey.applicationId,
    name: survey.name,
    state: survey.state,
    // Ausente enquanto nunca publicada — nunca `0`.
    ...(published !== undefined ? { publishedVersionNumber: published.number } : {}),
    ...(draft !== undefined ? { draftVersionNumber: draft.number } : {}),
    priority: survey.priority ?? 0,
    ...(survey.responseQuota !== undefined ? { responseQuota: survey.responseQuota } : {}),
    ignoresQuietPeriod: survey.ignoresQuietPeriod ?? false,
    ...(survey.templateKind !== undefined ? { templateKind: survey.templateKind } : {}),
    freeTextNotice: {
      enabled: survey.freeTextNoticeEnabled ?? true,
      ...(survey.freeTextNoticeText !== undefined ? { customText: survey.freeTextNoticeText } : {}),
      text: survey.freeTextNoticeText ?? DEFAULT_FREE_TEXT_NOTICE,
      defaultText: DEFAULT_FREE_TEXT_NOTICE,
    },
    createdAt: survey.createdAt,
  };
}

function toDetail(survey: StubSurvey) {
  const version = currentVersion(survey);

  return {
    ...toSurvey(survey),
    ...(version !== undefined
      ? {
          content: {
            source: version.status,
            versionNumber: version.number,
            questions: [...version.questions].sort((a, b) => a.position - b.position),
            ...(version.trigger !== undefined ? { trigger: version.trigger } : {}),
          },
        }
      : {}),
  };
}

/** A montagem sempre acontece sobre um rascunho: se não há, o primeiro nasce aqui. */
export function ensureDraft(survey: StubSurvey): StubVersion {
  const existing = draftVersion(survey);
  if (existing !== undefined) {
    return existing;
  }

  const draft: StubVersion = {
    number: survey.versions.length + 1,
    status: "draft",
    comparabilityGroup: 1,
    questions: [],
  };
  survey.versions.push(draft);
  return draft;
}

function readQuestion(body: unknown): { question: Omit<StubQuestion, "id" | "key" | "position"> } | StubResponse {
  const input = (body ?? {}) as Record<string, unknown>;
  const statement = typeof input.statement === "string" ? input.statement.trim() : "";
  const type = typeof input.type === "string" ? input.type : "";

  if (statement === "") {
    return validation("request.invalid", "Requisição inválida.", {
      statement: "O enunciado é obrigatório.",
    });
  }

  if (!QUESTION_TYPES.includes(type)) {
    return validation("request.invalid", "Requisição inválida.", {
      type: "Tipo de pergunta desconhecido.",
    });
  }

  const options = Array.isArray(input.options)
    ? (input.options as Array<{ label?: unknown; value?: unknown }>).map((option) => ({
        label: String(option.label ?? ""),
        value: String(option.value ?? ""),
      }))
    : undefined;

  if (CHOICE_TYPES.includes(type) && (options === undefined || options.length === 0)) {
    return validation("request.invalid", "Requisição inválida.", {
      options: "Perguntas de escolha precisam de pelo menos uma opção.",
    });
  }

  if (options !== undefined && new Set(options.map((o) => o.value)).size !== options.length) {
    return validation("request.invalid", "Requisição inválida.", {
      options: "Os valores das opções precisam ser distintos.",
    });
  }

  const range = input.range as
    | { min?: unknown; max?: unknown; minLabel?: unknown; maxLabel?: unknown }
    | undefined;
  const condition = readCondition(input.condition);

  return {
    question: {
      statement,
      type: type as StubQuestion["type"],
      required: input.required === true,
      ...(options !== undefined ? { options } : {}),
      ...(range !== undefined && typeof range.min === "number" && typeof range.max === "number"
        ? {
            range: {
              min: range.min,
              max: range.max,
              ...(typeof range.minLabel === "string" ? { minLabel: range.minLabel } : {}),
              ...(typeof range.maxLabel === "string" ? { maxLabel: range.maxLabel } : {}),
            },
          }
        : {}),
      ...(condition !== undefined ? { condition } : {}),
    },
  };
}

const CONDITION_OPERATORS = ["equals", "not_equals", "in", "between"];
const NUMERIC_TYPES = ["rating", "scale", "nps"];

function readCondition(raw: unknown): StubCondition | undefined {
  if (raw === null || typeof raw !== "object") {
    return undefined;
  }
  const input = raw as Record<string, unknown>;
  if (typeof input.sourceKey !== "string" || typeof input.operator !== "string") {
    return undefined;
  }
  if (!CONDITION_OPERATORS.includes(input.operator)) {
    return undefined;
  }

  return {
    sourceKey: input.sourceKey,
    operator: input.operator as StubCondition["operator"],
    values: Array.isArray(input.values) ? input.values.map(String) : [],
    ...(typeof input.min === "number" ? { min: input.min } : {}),
    ...(typeof input.max === "number" ? { max: input.max } : {}),
  };
}

/** Recusa de regra no formato do backend: 422 com o campo que a causou. */
function ruleViolation(code: string, detail: string, field: string, questionKey?: string): StubResponse {
  const response = problem(422, code, detail);
  return {
    ...response,
    body: {
      ...(response.body as Record<string, unknown>),
      field,
      ...(questionKey !== undefined ? { questionKey } : {}),
    },
  };
}

/** As mesmas regras do backend: origem anterior, não texto livre, valor que cabe na origem. */
function checkCondition(
  condition: StubCondition | undefined,
  questions: StubQuestion[],
  position: number,
): StubResponse | undefined {
  if (condition === undefined) {
    return undefined;
  }

  const source = questions.find((question) => question.key === condition.sourceKey);
  if (source === undefined || source.position >= position || source.type === "free_text") {
    return ruleViolation(
      "question.condition_source_invalid",
      "A condição só pode olhar para uma pergunta anterior que não seja de texto livre",
      "condition.sourceKey",
    );
  }

  const numeric = NUMERIC_TYPES.includes(source.type);
  if (!numeric && condition.operator === "between") {
    return ruleViolation(
      "question.condition_operator_invalid",
      "Faixa numérica só vale para perguntas de escala",
      "condition.operator",
    );
  }

  if (numeric) {
    const scale = source.type === "nps" ? { min: 0, max: 10 } : source.range;
    const fits = (value: number) =>
      scale === undefined || (value >= scale.min && value <= scale.max);
    const invalid =
      condition.operator === "between"
        ? condition.min === undefined ||
          condition.max === undefined ||
          condition.min > condition.max ||
          !fits(condition.min) ||
          !fits(condition.max)
        : condition.values.length === 0 ||
          condition.values.some((value) => !/^-?\d+$/.test(value) || !fits(Number(value)));
    return invalid
      ? ruleViolation(
          "question.condition_value_invalid",
          "Todo valor da condição precisa caber na escala da origem",
          "condition.values",
        )
      : undefined;
  }

  const declared = new Set((source.options ?? []).map((option) => option.value));
  return condition.values.length === 0 || condition.values.some((value) => !declared.has(value))
    ? ruleViolation(
        "question.condition_value_invalid",
        "Todo valor da condição precisa ser uma opção da pergunta de origem",
        "condition.values",
      )
    : undefined;
}

type TemplateKind = NonNullable<StubSurvey["templateKind"]>;

const TEMPLATE_QUESTIONS: Record<TemplateKind, Omit<StubQuestion, "id" | "key" | "position">> = {
  nps: {
    statement: "Em uma escala de 0 a 10, o quanto você recomendaria este app a um amigo ou colega?",
    type: "nps",
    required: true,
    range: { min: 0, max: 10, minLabel: "Nada provável", maxLabel: "Extremamente provável" },
  },
  csat: {
    statement: "O quanto você está satisfeito com este app?",
    type: "rating",
    required: true,
    range: { min: 1, max: 5, minLabel: "Muito insatisfeito", maxLabel: "Muito satisfeito" },
  },
  ces: {
    statement: "Este app facilitou resolver o que eu precisava.",
    type: "scale",
    required: true,
    range: { min: 1, max: 7, minLabel: "Discordo totalmente", maxLabel: "Concordo totalmente" },
  },
};

function isTemplate(value: unknown): value is TemplateKind {
  return typeof value === "string" && value in TEMPLATE_QUESTIONS;
}

function keyFrom(statement: string): string {
  const base = statement
    .normalize("NFD")
    .replace(/[̀-ͯ]/g, "")
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, "_")
    .replace(/^_+|_+$/g, "")
    .slice(0, 40);

  return base === "" ? nextId("q").replace(/-/g, "_") : `${base}_${nextId("k").slice(2, 6)}`;
}

export const surveyRoutes: Route[] = [
  {
    method: "POST",
    pattern: "/applications/:applicationId/surveys",
    handler: ({ params, body }) => {
      if (!store.applications.has(params.applicationId)) {
        return notFound("application.not_found", "Aplicação não encontrada.");
      }

      const name = typeof (body as { name?: unknown })?.name === "string"
        ? String((body as { name: string }).name).trim()
        : "";

      if (name === "") {
        return validation("request.invalid", "Requisição inválida.", {
          name: "O nome é obrigatório.",
        });
      }

      const template = (body as { template?: unknown } | undefined)?.template;
      if (template !== undefined && template !== null && !isTemplate(template)) {
        return validation("request.invalid", "Requisição inválida.", {
          template: "Modelo deve ser nps, csat ou ces",
        });
      }

      // Com modelo, o rascunho já nasce com a pergunta do formato; sem, nasce vazio.
      const survey: StubSurvey = {
        id: nextId("srv"),
        applicationId: params.applicationId,
        name,
        state: "draft",
        versions: isTemplate(template)
          ? [
              {
                number: 1,
                status: "draft",
                comparabilityGroup: 1,
                questions: [
                  {
                    id: nextId("q"),
                    key: keyFrom(TEMPLATE_QUESTIONS[template].statement),
                    position: 0,
                    ...TEMPLATE_QUESTIONS[template],
                  },
                ],
              },
            ]
          : [],
        transitions: [],
        ...(isTemplate(template) ? { templateKind: template } : {}),
        createdAt: nowIso(),
      };

      store.surveys.set(survey.id, survey);
      return json(201, toSurvey(survey));
    },
  },
  {
    method: "GET",
    pattern: "/applications/:applicationId/surveys",
    handler: ({ params, query }) => {
      const items = [...store.surveys.values()]
        .filter((survey) => survey.applicationId === params.applicationId)
        .map(settleWindow)
        .sort((a, b) => b.createdAt.localeCompare(a.createdAt))
        .map(toSurvey);

      return json(200, paginate(items, readPageQuery(query)));
    },
  },
  {
    method: "GET",
    pattern: "/applications/:applicationId/surveys/:surveyId",
    handler: ({ params }) => {
      const survey = surveyOf(params.applicationId, params.surveyId);
      return survey === undefined
        ? notFound("survey.not_found", "Pesquisa não encontrada.")
        : json(200, toDetail(survey));
    },
  },
  {
    method: "PATCH",
    pattern: "/applications/:applicationId/surveys/:surveyId",
    handler: ({ params, body }) => {
      const survey = surveyOf(params.applicationId, params.surveyId);
      if (survey === undefined) {
        return notFound("survey.not_found", "Pesquisa não encontrada.");
      }

      // Mesmo contrato do PATCH real: ausente não mexe; só a cota admite null, que a remove.
      const input = (body ?? {}) as Record<string, unknown>;

      if ("name" in input) {
        const name = typeof input.name === "string" ? input.name.trim() : "";
        if (name === "") {
          return validation("request.invalid", "Requisição inválida.", {
            name: "O nome é obrigatório.",
          });
        }
      }

      if (
        "priority" in input &&
        (typeof input.priority !== "number" ||
          !Number.isInteger(input.priority) ||
          input.priority < -100 ||
          input.priority > 100)
      ) {
        return validation("request.invalid", "Requisição inválida.", {
          priority: "A prioridade deve estar entre -100 e 100",
        });
      }

      if (
        "responseQuota" in input &&
        input.responseQuota !== null &&
        (typeof input.responseQuota !== "number" ||
          !Number.isInteger(input.responseQuota) ||
          input.responseQuota < 1)
      ) {
        return validation("request.invalid", "Requisição inválida.", {
          responseQuota: "A cota de respostas deve ser de ao menos uma",
        });
      }

      if ("freeTextNoticeEnabled" in input && typeof input.freeTextNoticeEnabled !== "boolean") {
        return validation("request.invalid", "Requisição inválida.", {
          freeTextNoticeEnabled: "Campo precisa ser verdadeiro ou falso",
        });
      }

      if (
        "freeTextNoticeText" in input &&
        input.freeTextNoticeText !== null &&
        (typeof input.freeTextNoticeText !== "string" ||
          input.freeTextNoticeText.trim() === "" ||
          input.freeTextNoticeText.trim().length > 200)
      ) {
        return validation("request.invalid", "Requisição inválida.", {
          freeTextNoticeText:
            typeof input.freeTextNoticeText === "string" && input.freeTextNoticeText.trim() !== ""
              ? "O texto do aviso não pode passar de 200 caracteres"
              : "O texto do aviso não pode ser vazio",
        });
      }

      if ("ignoresQuietPeriod" in input && typeof input.ignoresQuietPeriod !== "boolean") {
        return validation("request.invalid", "Requisição inválida.", {
          ignoresQuietPeriod: "Campo precisa ser verdadeiro ou falso",
        });
      }

      if (typeof input.name === "string") {
        survey.name = input.name.trim();
      }
      if (typeof input.priority === "number") {
        survey.priority = input.priority;
      }
      if (input.responseQuota === null) {
        delete survey.responseQuota;
      } else if (typeof input.responseQuota === "number") {
        survey.responseQuota = input.responseQuota;
      }
      if (typeof input.ignoresQuietPeriod === "boolean") {
        survey.ignoresQuietPeriod = input.ignoresQuietPeriod;
      }
      if (typeof input.freeTextNoticeEnabled === "boolean") {
        survey.freeTextNoticeEnabled = input.freeTextNoticeEnabled;
      }
      if (input.freeTextNoticeText === null) {
        delete survey.freeTextNoticeText;
      } else if (typeof input.freeTextNoticeText === "string") {
        survey.freeTextNoticeText = input.freeTextNoticeText.trim();
      }

      return json(200, toSurvey(survey));
    },
  },
  {
    method: "DELETE",
    pattern: "/applications/:applicationId/surveys/:surveyId",
    handler: ({ params }) => {
      const survey = surveyOf(params.applicationId, params.surveyId);
      if (survey === undefined) {
        return notFound("survey.not_found", "Pesquisa não encontrada.");
      }

      if (publishedVersion(survey) !== undefined) {
        return conflict(
          "survey.already_published",
          "Uma pesquisa já publicada não pode ser descartada.",
        );
      }

      store.surveys.delete(survey.id);
      return noContent();
    },
  },
  {
    method: "POST",
    pattern: "/applications/:applicationId/surveys/:surveyId/questions",
    handler: ({ params, body }) => {
      const survey = surveyOf(params.applicationId, params.surveyId);
      if (survey === undefined) {
        return notFound("survey.not_found", "Pesquisa não encontrada.");
      }

      const read = readQuestion(body);
      if ("status" in read) {
        return read;
      }

      const draft = ensureDraft(survey);
      const violation = checkCondition(
        read.question.condition,
        draft.questions,
        draft.questions.length,
      );
      if (violation !== undefined) {
        return violation;
      }

      const question: StubQuestion = {
        id: nextId("q"),
        key: keyFrom(read.question.statement),
        position: draft.questions.length,
        ...read.question,
      };

      draft.questions.push(question);
      return json(201, question);
    },
  },
  {
    method: "PUT",
    pattern: "/applications/:applicationId/surveys/:surveyId/questions/order",
    handler: ({ params, body }) => {
      const survey = surveyOf(params.applicationId, params.surveyId);
      if (survey === undefined) {
        return notFound("survey.not_found", "Pesquisa não encontrada.");
      }

      const draft = ensureDraft(survey);
      const ids = (body as { questionIds?: unknown })?.questionIds;

      if (!Array.isArray(ids)) {
        return validation("request.invalid", "Requisição inválida.", {
          questionIds: "Informe a ordem completa das perguntas.",
        });
      }

      const current = draft.questions.map((question) => question.id);
      const received = ids.map(String);

      // Permutação exata: nem falta, nem sobra, nem repete.
      const isPermutation =
        received.length === current.length &&
        new Set(received).size === received.length &&
        received.every((id) => current.includes(id));

      if (!isPermutation) {
        return validation("question.order_invalid", "Requisição inválida.", {
          questionIds: "A ordem precisa conter exatamente as perguntas desta versão.",
        });
      }

      draft.questions = received.map((id, position) => {
        const question = draft.questions.find((candidate) => candidate.id === id);
        return { ...(question as StubQuestion), position };
      });

      return json(200, draft.questions);
    },
  },
  {
    method: "PUT",
    pattern: "/applications/:applicationId/surveys/:surveyId/questions/:questionId",
    handler: ({ params, body }) => {
      const survey = surveyOf(params.applicationId, params.surveyId);
      if (survey === undefined) {
        return notFound("survey.not_found", "Pesquisa não encontrada.");
      }

      const draft = ensureDraft(survey);
      const index = draft.questions.findIndex((question) => question.id === params.questionId);

      if (index === -1) {
        return notFound("question.not_found", "Pergunta não encontrada.");
      }

      const read = readQuestion(body);
      if ("status" in read) {
        return read;
      }

      const existing = draft.questions[index];
      const violation = checkCondition(read.question.condition, draft.questions, existing.position);
      if (violation !== undefined) {
        return violation;
      }

      // Reescrever é a operação: a chave estável e a posição não mudam.
      const updated: StubQuestion = {
        id: existing.id,
        key: existing.key,
        position: existing.position,
        ...read.question,
      };

      draft.questions[index] = updated;
      return json(200, updated);
    },
  },
  {
    method: "DELETE",
    pattern: "/applications/:applicationId/surveys/:surveyId/questions/:questionId",
    handler: ({ params }) => {
      const survey = surveyOf(params.applicationId, params.surveyId);
      if (survey === undefined) {
        return notFound("survey.not_found", "Pesquisa não encontrada.");
      }

      const draft = ensureDraft(survey);
      const index = draft.questions.findIndex((question) => question.id === params.questionId);

      if (index === -1) {
        return notFound("question.not_found", "Pergunta não encontrada.");
      }

      const removed = draft.questions[index];
      const dependent = draft.questions.find(
        (question) => question.condition?.sourceKey === removed.key,
      );
      if (dependent !== undefined) {
        return ruleViolation(
          "question.condition_source_in_use",
          "A pergunta é origem da condição de outra pergunta, e a mudança deixaria essa condição inválida",
          "condition",
          dependent.key,
        );
      }

      draft.questions.splice(index, 1);
      draft.questions = draft.questions.map((question, position) => ({ ...question, position }));

      return noContent();
    },
  },
];
