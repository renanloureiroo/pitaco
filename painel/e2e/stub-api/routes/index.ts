import type { Route } from "../http.ts";

import { apiKeyRoutes } from "./api-keys.ts";
import { displayRoutes } from "./displays.ts";
import { respondentRoutes } from "./respondents.ts";
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
 * ser avaliado antes de `/questions/:questionId`, e `/versions/comparability` antes de
 * `/versions/:number`. As rotas de coleta não concorrem com nenhuma existente: `displays` e
 * `respondents` são segmentos fixos próprios.
 *
 * `seedRoutes` vive sob `/stub` e **não** faz parte do contrato: existe só para o E2E de
 * coleta semear exibições, que nenhuma tela do painel sabe criar.
 */
export const routes: Route[] = [...applicationRoutes, ...apiKeyRoutes, ...surveyRoutes, ...triggerRoutes, ...publicationRoutes, ...versionRoutes, ...lifecycleRoutes, ...displayRoutes, ...respondentRoutes, ...seedRoutes];
