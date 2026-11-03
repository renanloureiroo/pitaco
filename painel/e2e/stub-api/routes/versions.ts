import {
  conflict,
  json,
  noContent,
  notFound,
  paginate,
  readPageQuery,
  type Route,
} from "../http.ts";
import { draftVersion, publishedVersion, type StubVersion } from "../store.ts";
import { toVersion } from "./publication.ts";
import { surveyOf } from "./surveys.ts";

export const versionRoutes: Route[] = [
  {
    method: "GET",
    pattern: "/applications/:applicationId/surveys/:surveyId/versions/comparability",
    handler: ({ params }) => {
      const survey = surveyOf(params.applicationId, params.surveyId);
      if (survey === undefined) {
        return notFound("survey.not_found", "Pesquisa não encontrada.");
      }

      const groups = new Map<number, number[]>();
      for (const version of survey.versions) {
        if (version.status !== "published") {
          continue;
        }
        const versions = groups.get(version.comparabilityGroup) ?? [];
        versions.push(version.number);
        groups.set(version.comparabilityGroup, versions);
      }

      return json(200, {
        groups: [...groups.entries()]
          .sort(([a], [b]) => a - b)
          .map(([group, versions]) => ({ group, versions })),
      });
    },
  },
  {
    method: "GET",
    pattern: "/applications/:applicationId/surveys/:surveyId/versions",
    handler: ({ params, query }) => {
      const survey = surveyOf(params.applicationId, params.surveyId);
      if (survey === undefined) {
        return notFound("survey.not_found", "Pesquisa não encontrada.");
      }

      const items = [...survey.versions].sort((a, b) => b.number - a.number).map((version) => toVersion(survey.id, version));
      return json(200, paginate(items, readPageQuery(query)));
    },
  },
  {
    method: "GET",
    pattern: "/applications/:applicationId/surveys/:surveyId/versions/:number",
    handler: ({ params }) => {
      const survey = surveyOf(params.applicationId, params.surveyId);
      if (survey === undefined) {
        return notFound("survey.not_found", "Pesquisa não encontrada.");
      }

      const number = Number.parseInt(params.number, 10);
      const version = survey.versions.find((candidate) => candidate.number === number);

      if (version === undefined) {
        return notFound("survey_version.not_found", "Versão não encontrada.");
      }

      return json(200, {
        ...toVersion(survey.id, version),
        questions: [...version.questions].sort((a, b) => a.position - b.position),
        ...(version.trigger !== undefined ? { trigger: version.trigger } : {}),
      });
    },
  },
  {
    method: "POST",
    pattern: "/applications/:applicationId/surveys/:surveyId/versions",
    handler: ({ params }) => {
      const survey = surveyOf(params.applicationId, params.surveyId);
      if (survey === undefined) {
        return notFound("survey.not_found", "Pesquisa não encontrada.");
      }

      if (draftVersion(survey) !== undefined) {
        return conflict("survey_version.draft_exists", "Já existe um rascunho de versão aberto.");
      }

      const published = publishedVersion(survey);
      if (published === undefined) {
        return conflict("survey_version.no_published", "Publique a versão 1 antes de abrir outra.");
      }

      // O rascunho nasce como cópia do publicado: editar não altera o que está no ar.
      const draft: StubVersion = {
        number: published.number + 1,
        status: "draft",
        comparabilityGroup: published.comparabilityGroup,
        questions: published.questions.map((question) => ({ ...question })),
        ...(published.trigger !== undefined
          ? { trigger: { ...published.trigger, rules: [...published.trigger.rules] } }
          : {}),
      };

      survey.versions.push(draft);
      return json(201, toVersion(survey.id, draft));
    },
  },
  {
    method: "DELETE",
    pattern: "/applications/:applicationId/surveys/:surveyId/versions/draft",
    handler: ({ params }) => {
      const survey = surveyOf(params.applicationId, params.surveyId);
      if (survey === undefined) {
        return notFound("survey.not_found", "Pesquisa não encontrada.");
      }

      const draft = draftVersion(survey);
      if (draft === undefined) {
        return conflict("survey_version.no_draft", "Não há rascunho de versão aberto.");
      }

      survey.versions = survey.versions.filter((version) => version !== draft);
      return noContent();
    },
  },
];
