# Data Model: SDK React Native — app de exemplo

**Feature**: `006-sdk-app-exemplo` · **Date**: 2026-09-14

---

## 1. Configuração — `.env`

| Variável | Para quê |
| --- | --- |
| `EXPO_PUBLIC_PITACO_BASE_URL` | perfil direto: o Pitaco com o prefixo `/api` |
| `EXPO_PUBLIC_PITACO_PROXY_BASE_URL` | perfil proxy: o proxy local com o prefixo `/pitaco` |
| `EXPO_PUBLIC_PITACO_API_KEY` | a chave da aplicação, a mesma nos dois perfis |

| Alvo | Direto | Proxy |
| --- | --- | --- |
| simulador iOS | `http://localhost:8080/api` | `http://localhost:8787/pitaco` |
| emulador Android | `http://10.0.2.2:8080/api` | `http://10.0.2.2:8787/pitaco` |
| aparelho físico | `http://<IP>:8080/api` | `http://<IP>:8787/pitaco` |

## 2. Perfil de conexão — `src/pitaco/config.ts`

```ts
interface ConnectionProfile {
  id: 'direto' | 'proxy';
  label: string;
  baseUrl: string;
  available: boolean;     // baseUrl e chave preenchidos
  description: string;
}
```

O padrão é o primeiro disponível (direto, quando configurado).

## 3. Estado do exemplo — `src/pitaco/ExampleContext.tsx`

| Estado | Inicial |
| --- | --- |
| `profileId` | `defaultProfileId()` |
| `storage` | `createAsyncStorageAdapter(AsyncStorage)` |
| pilha de cenários | `[]`; cada entrada `{ token, scenarioId, config }` |

`ScenarioProviderConfig` = props do `PitacoProviderProps` menos `children`, `onEvent` e `storage`.

Ações: montar/desmontar cenário, trocar perfil, `requestFreshInstall()`.

## 4. Cenário — `src/scenarios/registry.ts`

```ts
interface ScenarioMeta { id: ScenarioId; title: string; description: string }
// hoje: { id: '04-comparar', title: 'Testar Pesquisas', description: … }
```

Configurações da tela de teste (`src/scenarios/04-comparar/configs.ts`):

| Forma | `presentation` | Tema |
| --- | --- | --- |
| Sheet do SDK (`sheet`) | `bottom-sheet` | `COMPARE_THEMES.claro` ou `.escuro` |
| Gorhom (`gorhom`) | `inline` | idem (mesmo objeto da Tela) |
| Tela (`tela`) | `inline` | idem |

## 5. Pesquisa do seed — `src/pitaco/seed.ts`

```ts
interface SeedSurvey { title: string; triggerEvent: string; surveyId: string; schema: unknown }
```

`seedSurveys` é a lista válida de `src/generated/seed-survey.json`; `seedSurvey` é a primeira.

| Título | Disparo | Conteúdo |
| --- | --- | --- |
| Todos os tipos | `checkout.completed` | NPS (obrigatória), escala 1–5 com rótulos, avaliação 1–5 (obrigatória), escolha única com condição NPS entre 0 e 6, múltipla escolha, texto livre |
| NPS | `order.delivered` | modelo `nps` |
| CSAT | `support.ticket_closed` | modelo `csat` |
| CES | `onboarding.completed` | modelo `ces` |
| Próxima feature | `changelog.viewed` | escolha única obrigatória entre recursos + texto livre opcional |

## 6. Estado do seed — `scripts/.seed-state.json` (git-ignorado, como o `.env`)

```json
{ "applicationId": "…", "apiKeyId": "…", "apiKeyPrefix": "pit_…" }
```

## 7. Log de depuração — `src/debug/`

| Registro | Campos |
| --- | --- |
| evento (`eventLog`) | o `PitacoListenerEvent`, `scenarioId`, `form` (ou `null`), `source` (`provider` ou preview) |
| requisição (`networkLog`) | método, URL, cabeçalhos (chave truncada), corpo, status, duração, resposta ou erro |
| condições de rede (`networkConditions`) | `offline`, `latencyMs`, só em memória |

## 8. Chaves do storage do SDK apagadas no reset

Tudo que começa com `@pitaco/` (fila por par `baseUrl|apiKey` e `@pitaco/v1:device-id`).
