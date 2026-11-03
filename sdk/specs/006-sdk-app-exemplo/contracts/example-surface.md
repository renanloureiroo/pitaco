# Contrato — superfície do app de exemplo

**Feature**: `006-sdk-app-exemplo`

O que quem usa o exemplo (pessoa ou automação) pode contar como estável.

## Scripts (`example/package.json`)

| Script | Comando | O que faz |
| --- | --- | --- |
| `start` / `ios` / `android` / `web` | `expo start [--ios\|--android\|--web]` | Metro |
| `typecheck` | `tsc --noEmit` | tipos do exemplo |
| `lint` | `eslint .` | lint do exemplo |
| `seed` | `node scripts/seed.ts` | prepara o backend local e escreve `.env` e `seed-survey.json` |
| `proxy` | `node proxy/server.mjs` | proxy transparente local |
| `smoke` | `node scripts/smoke.ts` | ciclo do SDK direto e pelo proxy |

No SDK: `npm run example:typecheck` e `npm run example:lint`, chamados pelo `verify`.

### Flags do seed

| Flag | Padrão | Efeito |
| --- | --- | --- |
| `--base-url <url>` | `http://localhost:8080/api` | endereço administrativo (fora de localhost, vai também ao perfil direto) |
| `--target ios-sim\|android-emu\|device` | `ios-sim` | host escrito no `.env` |
| `--lan-ip <ip>` | — | obrigatório com `--target device` |
| `--proxy-port <porta>` | `8787` | porta do perfil proxy |

### Flags do smoke

`--direct-url`, `--proxy-url`, `--only direct|proxy`, `--check-429`. Chave de
`EXPO_PUBLIC_PITACO_API_KEY` no `.env` ou `PITACO_API_KEY`.

## Rotas (Expo Router)

| Rota | Tela |
| --- | --- |
| `/` | redireciona para `/cenarios/04-comparar` |
| `/cenarios` | lista de cenários + seletor de perfil |
| `/cenarios/04-comparar` | "Testar pesquisas" |
| `/cenarios/04-comparar-tela?tema=claro\|escuro` | a pesquisa como tela |
| `/depuracao` | painel de depuração (aba) |

Deep link no Expo Go: `exp://<host>:8081/--/cenarios/<id>`.

## testIDs

| testID | Elemento |
| --- | --- |
| `tela-inicio` | lista de cenários |
| `cenario-04-comparar` | item da lista |
| `tela-04-comparar` | tela de teste |
| `cenario-04-forma-sheet` / `-gorhom` / `-tela` | seletor de forma |
| `cenario-04-tema-claro` / `-escuro` | seletor de tema |
| `cenario-04-abrir` | "Abrir pesquisa" |
| `tela-04-comparar-tela` | a pesquisa como tela |
| `tab-cenarios`, `tab-depuracao` | abas |
| `tela-depuracao` | painel |
| `secao-eventos`, `secao-rede`, `secao-estado` | seções do painel |
| `agrupar-tempo`, `agrupar-exibicao`, `agrupar-cenario` | agrupamento dos eventos |
| `debug-simular-reabertura`, `debug-copiar-log`, `debug-limpar-log` | ações do painel |
| `estado-sessao` | "Pesquisa já exibida nesta sessão" |

Do SDK, dentro das formas: `pitaco-bottom-sheet`, `pitaco-bottom-sheet-backdrop`,
`pitaco-bottom-sheet-handle` e os rótulos "Pergunta X de Y", "Próxima", "Voltar", "Enviar",
"Obrigado!" e "Fechar pesquisa".

## Arquivos gerados pelo seed

| Arquivo | Versionado | Conteúdo |
| --- | --- | --- |
| `.env` | não (git-ignorado; tem a chave) | endereços dos perfis e a chave; as outras linhas são preservadas |
| `scripts/.seed-state.json` | não (git-ignorado) | ids da aplicação e da chave |
| `src/generated/seed-survey.json` | sim | lista de `{ title, triggerEvent, surveyId, schema }`; sem chave nem dado pessoal |
