import type { Route } from "../http.ts";

import { apiKeyRoutes } from "./api-keys.ts";
import { applicationRoutes } from "./applications.ts";
import { surveyRoutes } from "./surveys.ts";
import { lifecycleRoutes } from "./lifecycle.ts";
import { publicationRoutes } from "./publication.ts";
import { triggerRoutes } from "./trigger.ts";
import { versionRoutes } from "./versions.ts";

/**
 * Tabela de rotas do simulador. Cada recurso do contrato entra como um arquivo próprio, o que
 * mantém os arquivos independentes entre si depois que o núcleo existe.
 *
 * A ordem importa onde um segmento fixo concorre com um dinâmico: `/questions/order` precisa
 * ser avaliado antes de `/questions/:questionId`.
 */
export const routes: Route[] = [...applicationRoutes, ...apiKeyRoutes, ...surveyRoutes, ...triggerRoutes, ...publicationRoutes, ...versionRoutes, ...lifecycleRoutes];
