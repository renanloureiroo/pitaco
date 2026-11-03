import { json, nextId, noContent, notFound, validation, type Route } from "../http.ts";
import type { StubSegmentationRule, StubTrigger } from "../store.ts";
import { ensureDraft, surveyOf } from "./surveys.ts";

const EVENT_NAME_PATTERN = /^[a-z][a-z0-9_.]{1,79}$/;
const OPERATIONS = ["equals", "not_equals", "present", "absent"];
const VALUED_OPERATIONS = ["equals", "not_equals"];

export const triggerRoutes: Route[] = [
  {
    method: "PUT",
    pattern: "/applications/:applicationId/surveys/:surveyId/trigger",
    handler: ({ params, body }) => {
      const survey = surveyOf(params.applicationId, params.surveyId);
      if (survey === undefined) {
        return notFound("survey.not_found", "Pesquisa não encontrada.");
      }

      const input = (body ?? {}) as Record<string, unknown>;
      const eventName = typeof input.eventName === "string" ? input.eventName : "";
      const windowStart = typeof input.windowStart === "string" ? input.windowStart : "";

      if (!EVENT_NAME_PATTERN.test(eventName)) {
        return validation("request.invalid", "Requisição inválida.", {
          eventName: "Nome de evento fora do padrão.",
        });
      }

      if (windowStart === "") {
        return validation("request.invalid", "Requisição inválida.", {
          windowStart: "O início da janela é obrigatório.",
        });
      }

      const windowEnd = typeof input.windowEnd === "string" ? input.windowEnd : undefined;

      if (windowEnd !== undefined && windowEnd <= windowStart) {
        return validation("trigger.window_invalid", "Requisição inválida.", {
          windowEnd: "O fim da janela precisa ser posterior ao início.",
        });
      }

      const draft = ensureDraft(survey);

      // `PUT` é idempotente: redefinir substitui o disparo, preservando as regras vigentes.
      const trigger: StubTrigger = {
        eventName,
        windowStart,
        ...(windowEnd !== undefined ? { windowEnd } : {}),
        samplingRate: typeof input.samplingRate === "number" ? input.samplingRate : 0,
        rules: draft.trigger?.rules ?? [],
      };

      draft.trigger = trigger;
      return json(200, trigger);
    },
  },
  {
    method: "POST",
    pattern: "/applications/:applicationId/surveys/:surveyId/trigger/rules",
    handler: ({ params, body }) => {
      const survey = surveyOf(params.applicationId, params.surveyId);
      if (survey === undefined) {
        return notFound("survey.not_found", "Pesquisa não encontrada.");
      }

      const draft = ensureDraft(survey);
      if (draft.trigger === undefined) {
        return validation("trigger.missing", "Requisição inválida.", {
          attribute: "Defina o disparo antes de criar regras.",
        });
      }

      const input = (body ?? {}) as Record<string, unknown>;
      const attribute = typeof input.attribute === "string" ? input.attribute.trim() : "";
      const operation = typeof input.operation === "string" ? input.operation : "";
      const value = typeof input.value === "string" ? input.value : undefined;

      if (attribute === "") {
        return validation("request.invalid", "Requisição inválida.", {
          attribute: "O atributo é obrigatório.",
        });
      }

      if (!OPERATIONS.includes(operation)) {
        return validation("request.invalid", "Requisição inválida.", {
          operation: "Operação desconhecida.",
        });
      }

      const needsValue = VALUED_OPERATIONS.includes(operation);
      if (needsValue && value === undefined) {
        return validation("segmentation_rule.invalid", "Requisição inválida.", {
          value: "Esta operação exige um valor.",
        });
      }
      if (!needsValue && value !== undefined) {
        return validation("segmentation_rule.invalid", "Requisição inválida.", {
          value: "Esta operação não aceita valor.",
        });
      }

      const rule: StubSegmentationRule = {
        id: nextId("rule"),
        attribute,
        operation: operation as StubSegmentationRule["operation"],
        ...(value !== undefined ? { value } : {}),
      };

      draft.trigger.rules.push(rule);
      return json(201, rule);
    },
  },
  {
    method: "DELETE",
    pattern: "/applications/:applicationId/surveys/:surveyId/trigger/rules/:ruleId",
    handler: ({ params }) => {
      const survey = surveyOf(params.applicationId, params.surveyId);
      if (survey === undefined) {
        return notFound("survey.not_found", "Pesquisa não encontrada.");
      }

      const draft = ensureDraft(survey);
      const rules = draft.trigger?.rules ?? [];
      const index = rules.findIndex((rule) => rule.id === params.ruleId);

      if (index === -1) {
        return notFound("segmentation_rule.not_found", "Regra não encontrada.");
      }

      rules.splice(index, 1);
      return noContent();
    },
  },
];
