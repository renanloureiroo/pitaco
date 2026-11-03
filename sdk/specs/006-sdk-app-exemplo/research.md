# Research: SDK React Native — app de exemplo

**Feature**: `006-sdk-app-exemplo` · **Date**: 2026-09-14 · **Spec**: [spec.md](./spec.md)

---

## D-01 — Expo SDK 57 com React Native 0.86

**Decision**: `expo@57`, `expo-router@57`, React Native 0.86.3, React 19.2.3. `react-dom@19.2.3`
explícito.

**Rationale**: é o SDK mais recente com Expo Go nas duas plataformas; o 58 (RN 0.87, o de
desenvolvimento do SDK) ainda não tinha Expo Go. A diferença prova o SDK abaixo da versão em que é
desenvolvido, como os peers prometem. Sem `react-dom` fixo, o npm puxava um `react-dom` que exige
`react@^19.3` (peer opcional do Expo Router) e travava a instalação.

**Alternatives considered**: build de desenvolvimento com a versão mais nova (exigiria build
nativo, contra o Princípio I).

---

## D-02 — Metro resolve o SDK pelo código-fonte

**Decision**: `watchFolders` inclui a raiz do SDK; `resolveRequest` mapeia os quatro pontos de
entrada públicos para `sdk/src`, recusa com erro qualquer outro subpath `@pitaco/react-native/...`,
e resolve todo import sem caminho vindo de `sdk/src` como se viesse do exemplo; `blockList` bloqueia
`sdk/node_modules/` e `sdk/lib/`. O `tsconfig.json` do exemplo tem `paths` só para os quatro
pontos, sem `react`/`react-native`.

**Rationale**: editar o SDK recarrega o exemplo; um import interno que não existiria no pacote
publicado falha no exemplo; uma única cópia de `react` e `react-native` evita o erro clássico de
hooks com duas cópias. Mapear `react` nos `paths` fazia o Metro do Expo resolver para `@types/react`.

**Alternatives considered**: `disableHierarchicalLookup` com `extraNodeModules` (quebra dependências
aninhadas); consumir `sdk/lib` (exigiria build a cada mudança).

---

## D-03 — Um Provider na raiz, sobreposto por tela

**Decision**: `ExampleProvider` guarda perfil, storage e a pilha de cenários montados; `PitacoRoot`
monta o único `<PitacoProvider>` com as props base e espalha a configuração do cenário montado por
último. `useScenario(id, config)` empilha na montagem e desempilha na desmontagem; ações e estado
ficam em contextos separados, para a tela que configura não ser re-renderizada pela própria troca.

**Rationale**: é a integração que o guia ensina; o painel lê o runtime certo sem registro de
"cenário ativo"; trocar para a aba Depuração não desfaz a configuração. Um Provider por tela
quebrava casos que atravessam telas.

**Alternatives considered**: um Provider por tela (perde estado entre telas).

---

## D-04 — Tela única de teste pelo fluxo real

**Decision**: o registro tem um cenário, `04-comparar` ("Testar pesquisas"), e a raiz redireciona
para ele. A tela escolhe pesquisa (do JSON do seed), forma e tema; `compareConfig(forma, tema)`
devolve constantes de módulo (`bottom-sheet` para o Sheet do SDK, `inline` para Gorhom e Tela, com
o tema escolhido). "Abrir pesquisa" chama `finish()`, guarda a forma que disparou,
`simulateAppReopen()` e `track(evento)`. A forma Tela empilha `04-comparar-tela` com a mesma
configuração.

**Rationale**: a primeira versão abria um `<PitacoPreview />` local, sem transporte — responder não
gravava nada. Pelo `track` real, exibição e respostas aparecem nos resultados do painel. Constantes
de módulo são necessárias porque `useScenario` compara por referência e a rota Tela não pode
recriar o runtime no meio da exibição.

**Alternatives considered**: manter as quinze telas numeradas (duplicava o que os testes do SDK já
provam e custava manutenção dos fluxos).

---

## D-05 — Painel sem API nova no SDK

**Decision**: eventos pelo `onEvent` da raiz, marcados com o cenário ativo e a origem; estado por
`usePitaco().diagnostics()` a cada 1 s; rede por um embrulho do `fetch` global instalado antes do
Provider (`installNetworkLogger`), só para URLs com `/collect/`, registrando método, URL, cabeçalhos
com `X-Pitaco-Key` truncada, corpo, status, duração e resposta (por `clone()`).

**Rationale**: o SDK lê `globalThis.fetch` a cada chamada, então o embrulho funciona; um gancho de
rede no SDK seria superfície pública só para diagnóstico.

**Alternatives considered**: um `onRequest` no Provider.

---

## D-06 — Logs fora do React

**Decision**: `createLogStore` com `useSyncExternalStore`; a notificação sai numa microtarefa.

**Rationale**: um evento a mais não re-renderiza a raiz; o `onEvent` pode chegar durante o render de
outro componente, e notificar na hora geraria aviso de atualização durante o render.

---

## D-07 — Reset de instalação

**Decision**: "Resetar Estado" troca o storage do Provider por um em memória (o runtime atual é
substituído e pausa a fila), espera 300 ms, apaga do AsyncStorage as chaves `@pitaco/`, limpa os
logs e volta a um adaptador persistente novo.

**Rationale**: trocar a instância de `storage` recria o runtime, que lê um storage vazio e gera
`deviceId` novo; o app não é remontado e a navegação fica onde estava. O servidor não reexibe
pesquisa já respondida ou dispensada ao mesmo respondente.

**Alternatives considered**: reiniciar o app (lento e fora do controle do Expo Go).

---

## D-08 — Seed com catálogo nomeado e idempotente

**Decision**: `scripts/seed.ts` usa a API administrativa local: aplicação "demo" (criada ou
reaproveitada, sem descanso), chave `sdk-example` (reaproveitada se a rastreada em
`.seed-state.json` existe e está no `.env`; senão revoga as do rótulo e emite outra), e cinco
pesquisas localizadas por nome — "Todos os tipos" (perguntas próprias: NPS, escala com rótulos,
avaliação, escolha única com condição NPS 0–6, múltipla escolha, texto livre), "NPS", "CSAT" e "CES"
por modelo, e "Próxima feature" (escolha única e um "por quê?" opcional). Cada uma recebe aviso de
texto livre, `ignoresQuietPeriod`, disparo com janela desde ontem e amostragem 1.0, e é publicada;
já publicada, só reforça `ignoresQuietPeriod: true` e `responseQuota: null`. No fim consulta a
elegibilidade com um `deviceId` descartável para gravar o schema entregue de cada pesquisa em
`src/generated/seed-survey.json`.

**Rationale**: deixa a pesquisa o mais reexibível que o servidor permite e entrega ao app o schema
exato que o SDK receberia.

**Alternatives considered**: SQL direto no banco (contornaria as regras do domínio).

---

## D-09 — Proxy em Node puro

**Decision**: `proxy/server.mjs` só com `node:http`/`node:https`: repassa `POST {prefixo}/collect/*`
para `{alvo}/collect/*`; outra rota → `404 gateway.route_not_found`, outro método → `405`;
cabeçalhos do cliente preservados menos os de salto e de origem; `X-Forwarded-For` substituído,
`X-Forwarded-Proto` e `X-Forwarded-Host` definidos, `CF-Connecting-IP` descartado; resposta intacta;
timeouts de 5 s para conectar e 10 s para responder, com `502`/`504` em JSON.

**Rationale**: segue `backend/docs/backend/proxy.md` sem instalar nada. O Pitaco lê
`CF-Connecting-IP` antes do `X-Forwarded-For` e o proxy local está em loopback (confiável);
repassá-lo deixaria o cliente escolher a própria origem no limite por IP.

**Alternatives considered**: Caddy (instalação a mais).

---

## D-10 — Smoke com identidades descartáveis

**Decision**: `scripts/smoke.ts` faz, direto e pelo proxy, elegibilidade, abertura, lote de
eventos, resposta, reenvio idêntico, elegibilidade depois de responder e chamada sem chave, cada
caminho com `deviceId` novo; a resposta nunca leva texto livre e avalia a condição como o SDK.
`--check-429` estoura o limite por origem pelo proxy.

**Rationale**: prova o ambiente sem simulador e sem gastar a pesquisa do aparelho.

---

## D-11 — Gorhom pelo modo `inline`

**Decision**: `GorhomSurveySheet` abre um `BottomSheetModal` novo por exibição (`key` pelo
`displayId`), com `<PitacoSurveyContent presentDeferred />` numa `BottomSheetScrollView`; o primeiro
`onChange` com índice ≥ 0 chama `present('inline')`; o `onDismiss` informa a via (`swipe` padrão,
`backdrop` pelo `onPress` do fundo, `hardware_back` por um `BackHandler` enquanto aberto). No iOS, o
sheet vai num `FullWindowOverlay` com `GestureHandlerRootView` próprio; o
`BottomSheetModalProvider` fica na raiz. O corpo sobe o sheet para 92% quando o core informa foco
no texto livre.

**Rationale**: o gorhom chama `onDismiss` antes de desmontar o conteúdo, então a via chega ao core
antes da dispensa por desmontagem (que não se repete). A mesma instância do modal não reabria numa
segunda apresentação; uma por exibição resolveu. A barra de abas nativa cobria o pé do sheet no
iOS. O `TextInput` do SDK não é o `BottomSheetTextInput`, então o `keyboardBehavior` do gorhom não
age sobre ele.

**Alternatives considered**: renderizador `freeText` próprio com `BottomSheetTextInput` (registrado
como alternativa para quem quiser o comportamento interativo do gorhom).

---

## D-12 — Tela pela navegação, sem voltar duas vezes

**Decision**: a rota `04-comparar-tela` renderiza `<PitacoSurveyContent />` com `onFinish` que só
chama `router.back()` se a rota ainda não estiver saindo (`useLeavingRef`, sobre `beforeRemove`).

**Rationale**: ao desmontar em andamento, o conteúdo dispensa com `navigation` e chama
`onFinish('dismissed')`; sem o guarda, a navegação voltaria duas vezes.

---

## D-13 — Fluxos Maestro portáveis entre iOS e Android (retirados com a primeira grade, T038)

**Decision**: um conjunto de arquivos com passos `when: platform`; subfluxos `_comum/` para abrir o
cenário por deep link (Metro em `127.0.0.1` no iOS e `10.0.2.2` no Android, abrindo antes o Expo Go
no Android, `retry` só na abertura), fechar o app pelo pacote certo de cada plataforma e fechar o
tutorial de caneta do teclado no Android. Scripts `e2e:ios` e `e2e:android`.

**Rationale**: o Expo Go frio às vezes engolia o deep link no Android; `stopApp` sem pacote não
fechava o Expo Go no Android (`host.exp.exponent` em minúsculas); `retry` no fluxo inteiro esconderia
instabilidade do app.

---

## D-14 — LogBox só com textos exatos

**Decision**: o exemplo silencia no LogBox apenas os avisos que provoca de propósito (storage em
memória durante o reset) e o aviso do Expo Router ao abrir por deep link; tudo continua no console.

**Rationale**: as notificações cobriam a barra de abas no Android e engoliam toques; silenciar tudo
esconderia avisos legítimos do SDK.
