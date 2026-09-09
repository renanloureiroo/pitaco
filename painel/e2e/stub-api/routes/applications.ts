import {
  conflict,
  json,
  nextId,
  notFound,
  nowIso,
  paginate,
  readPageQuery,
  validation,
  type Route,
} from "../http.ts";
import { store, type StubApplication } from "../store.ts";

const SLUG_PATTERN = /^[a-z0-9]+(-[a-z0-9]+)*$/;

function toSummary(application: StubApplication) {
  const { id, slug, name, status, createdAt } = application;
  return { id, slug, name, status, createdAt };
}

/** Campo ausente permanece ausente na resposta — nunca vira `null` nem `0`. */
function toDetail(application: StubApplication) {
  return { ...application };
}

function slugify(name: string): string {
  const base = name
    .normalize("NFD")
    .replace(/[̀-ͯ]/g, "")
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/^-+|-+$/g, "");

  return base === "" ? `app-${nextId("s").slice(2)}` : base;
}

function readOptionalDays(value: unknown): number | undefined {
  return typeof value === "number" ? value : undefined;
}

export const applicationRoutes: Route[] = [
  {
    method: "GET",
    pattern: "/applications",
    handler: ({ query }) => {
      const status = query.get("status");

      const items = [...store.applications.values()]
        .filter((application) => status === null || application.status === status)
        // Ordenação do contrato: createdAt desc, desempate por id desc.
        .sort((a, b) =>
          a.createdAt === b.createdAt
            ? b.id.localeCompare(a.id)
            : b.createdAt.localeCompare(a.createdAt),
        )
        .map(toSummary);

      return json(200, paginate(items, readPageQuery(query)));
    },
  },
  {
    method: "GET",
    pattern: "/applications/:applicationId",
    handler: ({ params }) => {
      const application = store.applications.get(params.applicationId);

      return application === undefined
        ? notFound("application.not_found", "Aplicação não encontrada.")
        : json(200, toDetail(application));
    },
  },
  {
    method: "POST",
    pattern: "/applications",
    handler: ({ body }) => {
      const input = (body ?? {}) as Record<string, unknown>;
      const name = typeof input.name === "string" ? input.name.trim() : "";

      if (name === "") {
        return validation("request.invalid", "Requisição inválida.", {
          name: "O nome é obrigatório.",
        });
      }

      const slug = typeof input.slug === "string" ? input.slug : slugify(name);

      if (!SLUG_PATTERN.test(slug) || slug.length > 50) {
        return validation("request.invalid", "Requisição inválida.", {
          slug: "Use apenas letras minúsculas, números e hífens.",
        });
      }

      const retentionDays = readOptionalDays(input.retentionDays);
      const openTextRetentionDays = readOptionalDays(input.openTextRetentionDays);

      if (
        retentionDays !== undefined &&
        openTextRetentionDays !== undefined &&
        openTextRetentionDays > retentionDays
      ) {
        return validation("request.invalid", "Requisição inválida.", {
          openTextRetentionDays: "Não pode superar a retenção geral.",
        });
      }

      for (const existing of store.applications.values()) {
        if (existing.slug === slug) {
          return conflict("application.slug_already_taken", `O slug "${slug}" já está em uso.`);
        }
      }

      const timestamp = nowIso();
      const application: StubApplication = {
        id: nextId("app"),
        slug,
        name,
        status: "active",
        ...(readOptionalDays(input.quietPeriodDays) !== undefined
          ? { quietPeriodDays: readOptionalDays(input.quietPeriodDays) }
          : {}),
        ...(retentionDays !== undefined ? { retentionDays } : {}),
        ...(openTextRetentionDays !== undefined ? { openTextRetentionDays } : {}),
        createdAt: timestamp,
        updatedAt: timestamp,
      };

      store.applications.set(application.id, application);

      return json(201, { id: application.id, slug: application.slug });
    },
  },
];
