import type { Route } from "../http.ts";

import { apiKeyRoutes } from "./api-keys.ts";
import { authoringRoutes } from "./authoring.ts";
import { displayRoutes } from "./displays.ts";
import { exposureRoutes } from "./exposure.ts";
import { healthRoutes } from "./health.ts";
import { privacyRoutes } from "./privacy.ts";
import { respondentRoutes } from "./respondents.ts";
import { resultRoutes } from "./results.ts";
import { seedRoutes } from "./seed.ts";
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
export const routes: Route[] = [...applicationRoutes, ...apiKeyRoutes, ...authoringRoutes, ...surveyRoutes, ...triggerRoutes, ...publicationRoutes, ...versionRoutes, ...lifecycleRoutes, ...displayRoutes, ...exposureRoutes, ...healthRoutes, ...resultRoutes, ...privacyRoutes, ...respondentRoutes, ...seedRoutes];
