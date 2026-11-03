# Feature Specification: SDK React Native — app de exemplo

**Feature Branch**: `006-sdk-app-exemplo`

**Created**: 2026-09-14

**Status**: Implemented

**Input**: User description: "Precisa de um app de exemplo junto do SDK pra testar de verdade contra o backend local, com painel pra ver o que o SDK está fazendo."

## Contexto

O SDK tem centenas de testes de unidade e componente, mas nenhum deles roda num aparelho, contra
o backend de verdade, ao lado das bibliotecas que um app real usa. Também falta o que o guia de
integração promete a quem nunca viu o Pitaco: um app que dá para abrir e ver a pesquisa
funcionando em minutos.

Esta feature entrega o app de exemplo em `sdk/example/`, no mesmo molde das bibliotecas React
Native que trazem o exemplo junto. Ele é ao mesmo tempo:

- **Harness de integração**: consome o código-fonte do SDK (editar o SDK recarrega o exemplo),
  roda no Expo Go em iOS e Android sem build nativo, e convive com Reanimated, Gesture Handler,
  `@gorhom/bottom-sheet`, `react-native-safe-area-context`, AsyncStorage e MMKV — que são
  dependências **do exemplo**, nunca do SDK.
- **Bancada de teste manual**: uma tela para abrir qualquer pesquisa do seed na forma escolhida
  (sheet do SDK, sheet do gorhom ou tela de navegação) e no tema escolhido, pelo fluxo real.
- **Ferramenta de diagnóstico**: um painel que mostra, ao vivo, os eventos que o SDK emitiu, as
  requisições ao Pitaco, a fila local e a identidade.
- **Ambiente reproduzível**: um seed que prepara o backend local (aplicação, chave, pesquisas,
  disparos) e escreve o `.env`, um proxy local que faz o papel do gateway do app, e uma prova
  ponta a ponta sem abrir o app.

## Clarifications

### Session 2026-09-14

- Q: O exemplo consome o pacote publicado ou o código-fonte? → A: O código-fonte, pelo Metro, com
  uma cópia só de `react` e `react-native`. Um import interno que não existiria no pacote
  publicado dá erro.
- Q: Um Provider por tela de cenário ou um só? → A: Um só, na raiz, como o guia ensina. Cada tela
  sobrepõe a configuração dele enquanto estiver montada; o painel de depuração lê o mesmo runtime.
- Q: Quantos cenários o exemplo mantém? → A: Um, "Testar pesquisas" (`04-comparar`), que cobre
  pesquisa, forma e tema pelo fluxo real. A primeira grade, de quinze telas numeradas, foi enxugada:
  tema, textos, renderizador, slots, headless, modal, bloqueio, adiamento, falhas e sem rede são
  provados pelos testes do SDK; o exemplo fica com o que só um aparelho prova.
- Q: A tela de teste usa o preview local ou o fluxo real? → A: O fluxo real: `track()` pelo
  Provider da raiz, com exibição e respostas gravadas no backend e visíveis nos resultados do
  painel. O preview não tem transporte, e responder nele não gravava nada.
- Q: Como ver de novo a mesma pesquisa no mesmo aparelho? → A: O servidor não reexibe pesquisa
  respondida ou dispensada ao mesmo respondente, e o SDK mostra uma por sessão. O exemplo oferece
  "Resetar Estado" (identidade nova) e chama `simulateAppReopen()` antes de cada abertura.
- Q: O seed cria uma pesquisa ou um catálogo? → A: Um catálogo de cinco: "Todos os tipos", "NPS",
  "CSAT", "CES" e "Próxima feature", cada uma com o próprio evento de disparo.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Do zero à primeira pesquisa no aparelho (Priority: P1)

Quem clona o repositório sobe o backend local, roda o seed, abre o exemplo no Expo Go e vê a
pesquisa aparecer — sem configurar chave à mão e sem build nativo.

**Why this priority**: é o que o guia promete e o que prova que o SDK funciona fora dos testes.

**Independent Test**: com o backend em `localhost:8080`, rodar `npm run seed`, `npx expo start`,
abrir no simulador iOS, tocar em "Abrir pesquisa" com "Todos os tipos" e "Sheet do SDK" e ver
"Pergunta 1 de 6" no bottom sheet.

**Acceptance Scenarios**:

1. **Given** o backend local sem dados do exemplo, **When** o seed roda, **Then** cria a aplicação
   "demo", emite a chave rotulada `sdk-example`, cria e publica as cinco pesquisas com disparo
   (amostragem de 100%, janela aberta desde ontem, sem descanso, sem cota, aviso de texto livre
   ligado), escreve o `.env` e `src/generated/seed-survey.json`.
2. **Given** o seed já aplicado, **When** ele roda de novo, **Then** reaproveita aplicação, chave e
   pesquisas, sem duplicar nada.
3. **Given** a chave rastreada revogada ou fora do `.env`, **When** o seed roda, **Then** revoga as
   chaves ativas com o mesmo rótulo e emite outra.
4. **Given** o alvo de execução, **When** o seed recebe `--target ios-sim`, `android-emu` ou
   `device --lan-ip <ip>`, **Then** o `.env` recebe `localhost`, `10.0.2.2` ou o IP informado nos
   dois perfis (direto e proxy).
5. **Given** o `.env` preenchido, **When** o exemplo abre, **Then** a tela "Testar pesquisas"
   aparece direto e lista as pesquisas do seed.
6. **Given** o `.env` vazio, **When** o exemplo abre, **Then** o Provider fica desligado sem
   derrubar o app; e, sem pesquisas em `seed-survey.json`, a tela orienta a rodar o seed.

---

### User Story 2 - Testar qualquer pesquisa, em qualquer forma e tema (Priority: P1)

Na tela "Testar pesquisas", a pessoa escolhe a pesquisa do seed, a forma — **Sheet do SDK**
(`bottom-sheet`), **Gorhom** (`inline` dentro de um `BottomSheetModal`) ou **Tela** (`inline` numa
rota empilhada) — e o tema claro ou escuro, e abre pelo fluxo real.

**Why this priority**: é a bancada de comparação das apresentações e a prova de que o SDK convive
com o contêiner que o app já usa.

**Independent Test**: abrir a mesma pesquisa nas três formas, percorrer igual e conferir no painel
de depuração que as sequências de eventos são iguais.

**Acceptance Scenarios**:

1. **Given** a forma "Sheet do SDK", **When** a pessoa toca em "Abrir pesquisa", **Then** o SDK
   consulta a elegibilidade com o evento da pesquisa escolhida e abre o próprio bottom sheet.
2. **Given** a forma "Gorhom", **When** a pesquisa chega, **Then** o app abre um `BottomSheetModal`
   do gorhom com `<PitacoSurveyContent presentDeferred />` dentro, e só chama `present()` quando o
   gorhom termina de abrir.
3. **Given** o sheet do gorhom aberto, **When** a pessoa arrasta para fechar, toca no fundo ou usa
   o voltar do Android, **Then** a dispensa chega ao core com via `swipe`, `backdrop` ou
   `hardware_back`, uma vez.
4. **Given** a forma "Tela", **When** a pesquisa chega, **Then** o app empilha a rota da pesquisa
   com `<PitacoSurveyContent />` em tela cheia e cabeçalho da navegação.
5. **Given** a pesquisa como tela, **When** a pessoa sai pela seta do cabeçalho ou pelo voltar do
   sistema com ela em andamento, **Then** a dispensa sai com via `navigation`, uma só; ao concluir,
   a rota volta sozinha, sem dispensa.
6. **Given** o tema escuro escolhido, **When** a pesquisa abre, **Then** a pesquisa e a moldura do
   contêiner do app usam o esquema escuro.
7. **Given** uma pesquisa já exibida nesta sessão, **When** a pessoa abre de novo, **Then** o
   exemplo zera o limite de sessão antes do `track`, e a consulta sai.
8. **Given** a forma trocada, **When** a pesquisa já foi aberta, **Then** a chegada é interpretada
   pela forma que a disparou, e a rota "Tela" reutiliza a mesma configuração do Provider para não
   recriar o runtime.

---

### User Story 3 - Ver o que o SDK está fazendo (Priority: P1)

Uma aba fixa de depuração mostra, ao vivo, os eventos que o `onEvent` recebeu (com o payload
completo), as requisições ao Pitaco com as respostas, e o estado do runtime (identidade, fila,
limite de sessão, bloqueios, adiamento, chave recusada, prazo do 429).

**Why this priority**: é a ferramenta de diagnóstico de "por que a pesquisa não apareceu" e a forma
de conferir a olho que as UIs produzem a mesma sequência.

**Independent Test**: abrir uma pesquisa, ir à aba Depuração e conferir a lista de eventos, as
requisições `POST /collect/...` com status e o estado com `deviceId` e fila.

**Acceptance Scenarios**:

1. **Given** uma pesquisa percorrida, **When** a pessoa abre a seção Eventos, **Then** vê cada
   evento com tipo, via da dispensa na linha e o payload expansível, agrupáveis por ordem, por
   exibição ou por cenário e forma.
2. **Given** requisições ao Pitaco, **When** a pessoa abre a seção Rede, **Then** vê método, URL,
   status, duração, corpo e resposta, com a chave `X-Pitaco-Key` truncada.
3. **Given** o runtime ligado, **When** a pessoa abre a seção Fila e identidade, **Then** vê o
   `deviceId`, os itens da fila e os estados de controle, atualizados a cada segundo.
4. **Given** o painel, **When** a pessoa toca em "Simular app reaberto", **Then** o limite de uma
   pesquisa por sessão volta a zero.
5. **Given** o painel, **When** a pessoa toca em "Copiar log", **Then** eventos, requisições e
   diagnóstico vão para a área de transferência em JSON; "Limpar log" esvazia as listas.
6. **Given** a aba Depuração aberta, **When** a pessoa volta para Cenários, **Then** a tela e a
   pesquisa abertas continuam montadas.

---

### User Story 4 - Recomeçar como instalação nova (Priority: P2)

Um botão flutuante "Resetar Estado" apaga o storage do Pitaco e a identidade sem reinstalar o app,
para a mesma pesquisa poder aparecer de novo.

**Why this priority**: sem ele, cada teste exigiria apagar o app; mas o fluxo principal funciona
sem.

**Independent Test**: responder uma pesquisa, tocar em "Resetar Estado", abrir de novo e conferir
`deviceId` novo e a pesquisa reaparecendo.

**Acceptance Scenarios**:

1. **Given** um `deviceId` e uma fila, **When** a pessoa toca em "Resetar Estado", **Then** o
   Provider passa a um storage em memória, as chaves `@pitaco/` do AsyncStorage são apagadas depois
   de 300 ms, os logs são limpos e o Provider volta a um adaptador persistente novo.
2. **Given** o reset, **When** o runtime é recriado, **Then** ele gera um `deviceId` novo e a
   navegação continua onde estava.

---

### User Story 5 - Exercitar a topologia com gateway (Priority: P2)

Um seletor de perfil troca o `baseUrl` do app inteiro entre o acesso direto e um proxy
transparente local que faz o papel do gateway do app hospedeiro.

**Why this priority**: prova que o SDK não distingue acesso direto de gateway; mas a maioria dos
integradores começa direto.

**Independent Test**: subir `npm run proxy`, escolher o perfil Proxy, abrir uma pesquisa e conferir
no log do proxy as chamadas `/pitaco/collect/*` repassadas.

**Acceptance Scenarios**:

1. **Given** o proxy de pé, **When** o SDK chama `POST {prefixo}/collect/*`, **Then** o proxy
   repassa para `{alvo}/collect/*` com os mesmos cabeçalhos do cliente e devolve status, corpo e
   cabeçalhos intactos, inclusive `Retry-After`.
2. **Given** qualquer outra rota ou método, **When** chega ao proxy, **Then** ele responde `404` ou
   `405` em `application/problem+json`, sem tocar no Pitaco.
3. **Given** um cliente mandando `X-Forwarded-For` ou `CF-Connecting-IP`, **When** passa pelo
   proxy, **Then** o `X-Forwarded-For` é substituído pelo IP visto e o `CF-Connecting-IP` é
   descartado.
4. **Given** o Pitaco fora do ar ou lento, **When** o proxy repassa, **Then** responde `502` ou
   `504` em JSON.
5. **Given** os perfis, **When** um deles não tem `.env`, **Then** ele aparece indisponível.

---

### User Story 6 - Provar o ciclo sem abrir o app (Priority: P3)

`npm run smoke` faz, direto e pelo proxy, o que o SDK faz — elegibilidade, abertura, lote de
eventos, resposta, reenvio — e confere os status do contrato.

**Why this priority**: diagnóstico rápido do ambiente antes de abrir simulador.

**Independent Test**: com backend e proxy de pé, rodar `npm run smoke` e ver "Tudo certo.".

**Acceptance Scenarios**:

1. **Given** backend e proxy de pé, **When** o smoke roda, **Then** confere elegibilidade `200`
   com a pesquisa, abertura `201`, lote de eventos `202`, resposta `204`, reenvio idêntico `204`,
   elegibilidade depois de responder com `survey: null`, e `401` sem chave, nos dois caminhos.
2. **Given** o smoke, **When** escolhe identidade, **Then** usa um `deviceId` descartável por
   caminho, sem gastar a pesquisa do simulador.
3. **Given** `--only direct|proxy` ou `--check-429`, **When** informados, **Then** roda só um
   caminho, ou estoura o limite por origem pelo proxy para provar o `Retry-After`.

---

### Edge Cases

- **Endereço por alvo**: `localhost` no simulador iOS, `10.0.2.2` no emulador Android, IP da rede
  local em aparelho físico (com firewall liberando 8080 e 8787).
- **`react-native-mmkv` instalado sem uso**: entra só como dependência de desenvolvimento do
  exemplo, sem import em runtime; o adaptador MMKV fica provado pelos testes do SDK.
- **Sheet do gorhom sob a barra de abas no iOS**: o sheet vai para um `FullWindowOverlay` com
  `GestureHandlerRootView` próprio.
- **O mesmo `BottomSheetModal` não reabrindo**: o exemplo monta um novo por exibição (`key` pelo
  `displayId`).
- **Teclado do texto livre no gorhom**: o corpo observa o foco do texto no core e sobe o sheet para
  92%, com `adjustResize` no Android.
- **Aviso de storage em memória durante o reset**: esperado, uma vez; silenciado só no LogBox.
- **Aviso do Expo Router ao abrir por deep link**: silenciado só no LogBox, pelo texto exato.
- **Tema escuro**: a moldura de navegação acompanha o esquema do sistema.
- **Fluxos Maestro e a grade enxuta**: os fluxos foram escritos e executados para a primeira grade
  de cenários (`01-sheet`, `02-gorhom`, `03-tela`, `07-renderizador`, `09-headless`,
  `11-bloqueio`, `13-sem-rede`). Com a grade reduzida a "Testar pesquisas", eles e os componentes
  de cenário sem rota foram retirados, para o repositório não versionar teste que não roda; o que
  eles provavam no SDK continua coberto pelos testes do SDK.

## Requirements *(mandatory)*

### Functional Requirements

**Ligação com o SDK**

- **FR-001**: O exemplo MUST ser um app Expo com Expo Router e TypeScript estrito, rodável no Expo
  Go em iOS e Android sem build nativo.
- **FR-002**: O exemplo MUST consumir o código-fonte do SDK: `watchFolders` com a raiz do SDK,
  `@pitaco/react-native` e os subpaths `/preview`, `/storage/async-storage` e `/storage/mmkv`
  resolvidos para `sdk/src`, qualquer outro subpath com erro listando os válidos, e
  `sdk/node_modules` e `sdk/lib` bloqueados.
- **FR-003**: Reanimated, Gesture Handler, `@gorhom/bottom-sheet`, `react-native-safe-area-context`,
  AsyncStorage e MMKV MUST ser dependências do exemplo, nunca do SDK.
- **FR-004**: O `typecheck` e o `lint` do exemplo MUST fazer parte do `npm run verify` do SDK.

**Provider e cenários**

- **FR-005**: O exemplo MUST ter um único `<PitacoProvider>` na raiz, com `baseUrl` do perfil
  ativo, a chave do `.env`, storage AsyncStorage, `onEvent` alimentando o painel, `debug` em
  desenvolvimento e relatório de erro ligado.
- **FR-006**: Cada tela de cenário MUST poder sobrepor a configuração do Provider enquanto estiver
  montada (`useScenario`), valendo a última montada, com objetos comparados por referência.
- **FR-007**: Os cenários MUST vir de um registro com id, título e descrição, cada um numa rota
  `cenarios/<id>`, com testIDs estáveis `cenario-<id>` e `tela-<id>`; hoje o registro tem
  `04-comparar` ("Testar pesquisas"), e a raiz do app redireciona para ele.
- **FR-008**: A tela "Testar pesquisas" MUST permitir escolher a pesquisa do seed, a forma (Sheet do
  SDK, Gorhom, Tela) e o tema (claro, escuro), e abrir pelo `track` real, zerando antes o limite de
  sessão.
- **FR-009**: A forma Gorhom MUST abrir um `BottomSheetModal` com `<PitacoSurveyContent
  presentDeferred />`, chamar `present()` só quando o gorhom terminar de abrir e informar a via da
  dispensa (`swipe`, `backdrop`, `hardware_back`) antes de o conteúdo desmontar.
- **FR-010**: A forma Tela MUST empilhar uma rota com `<PitacoSurveyContent />`, voltar sozinha ao
  concluir e não voltar duas vezes quando a pessoa sai pela navegação.

**Painel de depuração**

- **FR-011**: O exemplo MUST ter uma aba de depuração com eventos (agrupáveis por ordem, exibição
  ou cenário e forma, com a via na linha), rede e estado (`diagnostics()` lido a cada segundo).
- **FR-012**: As requisições MUST ser capturadas embrulhando o `fetch` global antes do Provider, só
  para URLs do Pitaco, com `X-Pitaco-Key` truncada; o SDK MUST NOT ganhar gancho de rede para isso.
- **FR-013**: O painel MUST oferecer "Simular app reaberto", "Copiar log" e "Limpar log".
- **FR-014**: O exemplo MUST oferecer "Resetar Estado" para recomeçar como instalação nova sem
  reiniciar o app.
- **FR-015**: Os logs MUST viver fora do estado do React, para um evento a mais não re-renderizar
  a raiz.

**Backend local**

- **FR-016**: O seed MUST ser idempotente: aplicação, chave e pesquisas reaproveitadas por nome e
  rótulo; chave reemitida só quando a rastreada sumiu ou não está no `.env`.
- **FR-017**: O seed MUST criar as cinco pesquisas do catálogo — "Todos os tipos" (seis tipos, com
  rótulos de escala, condição e aviso de texto livre), "NPS", "CSAT" e "CES" por modelo, e
  "Próxima feature" — com disparo próprio, amostragem de 100%, janela aberta, sem descanso e sem
  cota, e publicar.
- **FR-018**: O seed MUST escrever o `.env` (preservando as outras linhas) com os endereços do alvo
  e a chave, e `src/generated/seed-survey.json` com título, evento, id e o schema entregue pela
  elegibilidade de cada pesquisa.
- **FR-019**: O exemplo MUST ter dois perfis de conexão, direto e proxy, escolhidos por um seletor
  na lista de cenários.
- **FR-020**: O proxy local MUST repassar só `POST {prefixo}/collect/*`, preservar cabeçalhos do
  cliente (menos os de salto e de origem), substituir `X-Forwarded-For`, descartar
  `CF-Connecting-IP`, devolver a resposta intacta e responder erros próprios em JSON, sem
  dependência além do Node.
- **FR-021**: O smoke MUST provar o ciclo do SDK direto e pelo proxy, com identidades
  descartáveis.

**Automação e registro**

- **FR-022**: O exemplo MUST NOT versionar automação de aparelho que aponte para rotas inexistentes:
  os fluxos Maestro da primeira grade foram retirados junto com ela, e uma automação nova só entra
  escrita para a tela atual.
- **FR-023**: O exemplo MUST versionar capturas da mesma pergunta nas três formas, em claro e
  escuro, iOS e Android, e um vídeo por forma e plataforma, em `docs/capturas/`.

### Key Entities

- **Perfil de conexão**: direto ou proxy, com `baseUrl` e disponibilidade.
- **Cenário**: uma tela registrada, com a configuração que dá ao Provider enquanto montada.
- **Pesquisa do seed**: título, evento de disparo, id e schema entregue.
- **Log de depuração**: eventos (com cenário, forma e origem) e requisições, fora do React.
- **Estado do seed**: ids da aplicação e da chave, guardados para a próxima execução.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Partindo de um clone com o backend local de pé, a primeira pesquisa aparece no
  simulador com três comandos (`npm run seed`, `npx expo start`, abrir no Expo Go) e um toque.
- **SC-002**: O exemplo roda no Expo Go em iOS e Android sem nenhum passo de build nativo, e o
  bundle de exportação monta nas duas plataformas.
- **SC-003**: Rodar o seed duas vezes deixa exatamente uma aplicação, uma chave ativa com o rótulo
  do exemplo e uma pesquisa por nome.
- **SC-004**: As três formas da tela de teste produzem a mesma sequência de eventos para o mesmo
  percurso, conferível no painel.
- **SC-005**: Toda requisição do SDK ao Pitaco aparece na seção Rede do painel, sem a chave inteira.
- **SC-006**: O smoke termina com "Tudo certo." nos dois caminhos quando backend e proxy estão de
  pé.

## Assumptions

- **Node 22.18 ou mais novo para os scripts.** Seed e smoke são `.ts` executados com a remoção de
  tipos nativa do Node, sem dependência nova.
- **Backend local no perfil `local`, sem autenticação administrativa.** O seed usa a API
  administrativa direto.
- **Expo SDK 57 / React Native 0.86.** É a versão com Expo Go disponível; o SDK declara
  `react-native >=0.74` e é desenvolvido na 0.87, e a diferença é proposital.
- **O exemplo não é publicado.** É `private` e serve ao repositório.
- **A grade de cenários é enxuta de propósito.** O que os testes do SDK já provam não ganha tela.

## Dependencies

- `001-sdk-core`, `002-sdk-ui-apresentacoes`, `003-sdk-eventos-interacao`,
  `004-sdk-controle-onde-quando` e `005-sdk-preview-web`: tudo o que o exemplo exercita.
- `backend 001-api-key-management`: emissão e revogação de chave pelo seed.
- `backend 003-survey-authoring`: criação, perguntas, modelos, disparo e publicação pelo seed.
- `backend 004-response-collection`: o fluxo real de coleta.
- `backend 013-interaction-events`: a rota de eventos que o smoke e o painel exercitam.
- `backend/docs/backend/proxy.md`: o contrato do gateway que o proxy local segue.
