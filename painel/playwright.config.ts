import { defineConfig, devices } from "@playwright/test";

/**
 * Dois servidores, não um (R9).
 *
 * O `fetch` do painel roda no servidor Node do Next, então `page.route` do Playwright não
 * alcança a API. Por isso a suíte sobe o simulador de API junto do app e aponta
 * `PITACO_API_URL` para ele. Com `E2E_API=real`, o simulador não sobe e a mesma suíte roda
 * contra o backend de verdade.
 */

// 3001, e não 3000: a stack local do backend publica o Grafana LGTM em 3000, e um painel
// nessa porta seria sequestrado por ele — inclusive para o `reuseExistingServer` do Playwright,
// que veria a porta respondendo e nem subiria o Next.
const baseURL = process.env.PLAYWRIGHT_BASE_URL ?? "http://localhost:3001";
const stubPort = process.env.STUB_API_PORT ?? "4010";
const stubApiUrl = `http://localhost:${stubPort}/api`;

const useRealApi = process.env.E2E_API === "real";
const apiUrl = useRealApi
  ? (process.env.PITACO_API_URL ?? "http://localhost:8080/api")
  : stubApiUrl;

const stubServer = {
  command: "npx tsx e2e/stub-api/server.ts",
  url: `${stubApiUrl}/health`,
  reuseExistingServer: !process.env.CI,
  timeout: 30 * 1000,
  env: { STUB_API_PORT: stubPort },
};

const appServer = {
  command: process.env.CI ? "npm run build && npm run start" : "npm run dev",
  url: baseURL,
  reuseExistingServer: !process.env.CI,
  timeout: 180 * 1000,
  env: { PITACO_API_URL: apiUrl },
};

export default defineConfig({
  testDir: "./e2e",
  // Cada teste cria a própria aplicação e opera só dentro dela — o isolamento é por dado,
  // não por serialização.
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 2 : 0,
  workers: process.env.CI ? 1 : undefined,
  reporter: process.env.CI ? "github" : "list",
  use: {
    baseURL,
    trace: "on-first-retry",
  },
  projects: [
    {
      name: "chromium",
      use: { ...devices["Desktop Chrome"] },
    },
  ],
  webServer: useRealApi ? [appServer] : [stubServer, appServer],
});
