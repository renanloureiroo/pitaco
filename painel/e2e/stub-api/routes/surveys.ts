import {
  conflict,
  json,
  nextId,
  noContent,
  notFound,
  nowIso,
  paginate,
  readPageQuery,
  validation,
  type Route,
  type StubResponse,
} from "../http.ts";
import {
  currentVersion,
  draftVersion,
  publishedVersion,
  settleWindow,
  store,
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

  const range = input.range as { min?: unknown; max?: unknown } | undefined;

  return {
    question: {
      statement,
      type: type as StubQuestion["type"],
      required: input.required === true,
      ...(options !== undefined ? { options } : {}),
      ...(range !== undefined && typeof range.min === "number" && typeof range.max === "number"
        ? { range: { min: range.min, max: range.max } }
        : {}),
    },
  };
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

      const survey: StubSurvey = {
        id: nextId("srv"),
        applicationId: params.applicationId,
        name,
        state: "draft",
        versions: [],
        transitions: [],
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

      const name = typeof (body as { name?: unknown })?.name === "string"
        ? String((body as { name: string }).name).trim()
        : "";

      if (name === "") {
        return validation("request.invalid", "Requisição inválida.", {
          name: "O nome é obrigatório.",
        });
      }

      survey.name = name;
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

      draft.questions.splice(index, 1);
      draft.questions = draft.questions.map((question, position) => ({ ...question, position }));

      return noContent();
    },
  },
];
