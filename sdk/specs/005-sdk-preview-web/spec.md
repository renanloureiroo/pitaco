# Feature Specification: SDK React Native — pré-visualização sem transporte

**Feature Branch**: `005-sdk-preview-web`

**Created**: 2026-09-14

**Status**: Implemented

**Input**: User description: "Quero ver a pesquisa no painel do jeito que vai aparecer no app, sem criar exibição nem resposta."

## Contexto

Quem monta uma pesquisa no painel hoje só vê um formulário: não sabe como a pergunta vai ficar no
app, nem o que vai ser medido quando alguém responder. O jeito honesto de mostrar isso é rodar o
**mesmo** código do SDK — a mesma máquina de estado, a mesma UI padrão, os mesmos eventos — sobre a
pesquisa que está sendo editada, sem passar pelo servidor.

Esta feature entrega o subpath `@pitaco/react-native/preview`: um renderizador sobre um schema em
memória, sem transporte, sem sessão de app, sem fila e sem envio. O painel o renderiza no navegador
via `react-native-web`; o app de exemplo e qualquer outro app podem usá-lo para reabrir uma
pesquisa localmente.

Duas fronteiras:

- **Nada sai do aparelho.** O preview nunca consulta elegibilidade, nunca abre exibição, nunca
  envia resposta, evento, supressão nem relatório de erro. Os eventos vão só para o `onEvent` de
  quem o usa.
- **Nada diverge do SDK de verdade.** O preview não tem UI própria: usa o mesmo
  `<PitacoSurveyContent />`, os mesmos renderizadores e a mesma máquina, pela mesma interface de
  controlador que o runtime implementa.

## Clarifications

### Session 2026-09-14

- Q: Qual a apresentação padrão do preview? → A: `inline`: só o conteúdo, sem `Modal`. É o que cabe
  dentro de uma página web e o que descreve com precisão o `survey_presented` emitido.
- Q: O preview pode abrir no bottom sheet ou no modal do SDK? → A: Pode, com
  `presentation="bottom-sheet"` ou `"modal"`, usando o mesmo contêiner do Provider — ainda sem
  transporte. Serve para comparar formas num aparelho.
- Q: O schema precisa estar publicado? → A: Não. Aceita o formato da elegibilidade
  (`DeliverableSurvey`) e preenche `surveyId`, `versionId` e `versionNumber` de um rascunho.
- Q: Como reiniciar a pré-visualização? → A: Mudando `resetKey` (ou a `key` do React). Não há
  `restart()` imperativo no componente; o controlador cru tem.
- Q: Um renderizador substituído que falha no preview vai para o relatório de erro? → A: Não há
  transporte; a falha vai para `onRenderError`, quando informado.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Ver a pesquisa como ela vai aparecer (Priority: P1)

O painel passa o schema atual da pesquisa para `<PitacoPreview schema={…} />` e a pesquisa aparece
com a UI padrão do SDK, percorrível do começo ao agradecimento, sem tocar no servidor.

**Why this priority**: é o propósito da feature; sem isso, o autor publica às cegas.

**Independent Test**: renderizar `<PitacoPreview />` com o schema de referência e um `fetch` espião,
percorrer até o agradecimento e conferir que nada foi chamado na rede e que `onFinish('completed')`
veio.

**Acceptance Scenarios**:

1. **Given** um schema no formato da elegibilidade, **When** `<PitacoPreview />` monta, **Then** a
   pesquisa aparece inline, sem `Modal`, já apresentada.
2. **Given** a pré-visualização, **When** a pessoa percorre até "Enviar", **Then** aparece o
   agradecimento e `onFinish('completed')` é chamado.
3. **Given** qualquer interação, **When** acontece, **Then** nenhuma requisição de rede sai.
4. **Given** um schema de rascunho sem `surveyId` e `versionId`, **When** é passado, **Then** o
   preview preenche os valores e funciona.
5. **Given** um schema sem nenhuma pergunta renderizável, **When** é passado, **Then** nada é
   desenhado e nada quebra.

---

### User Story 2 - Ver o que vai ser medido (Priority: P1)

Ao lado da pré-visualização, o painel lista os eventos que ela emitiu — os mesmos que o app
emitiria —, para o autor saber exatamente o que será medido.

**Why this priority**: o catálogo de eventos (`003-sdk-eventos-interacao`) só é útil se quem cria a
pesquisa entende o que ele registra.

**Independent Test**: passar um `onEvent`, percorrer a pesquisa e conferir que os eventos recebidos
são os da máquina, com `survey_presented` primeiro.

**Acceptance Scenarios**:

1. **Given** um `onEvent`, **When** a pré-visualização aparece, **Then** o primeiro evento é
   `survey_presented`, entregue mesmo que o conteúdo chame `present()` no próprio efeito de
   montagem.
2. **Given** a pessoa responde, troca, volta e pula, **When** cada ação acontece, **Then** o
   `onEvent` recebe os mesmos eventos que o runtime de verdade emitiria para o mesmo roteiro.
3. **Given** um `onEvent` que lança, **When** um evento é entregue, **Then** a pré-visualização
   continua.
4. **Given** `triggerEvent`, **When** a pré-visualização aparece, **Then** `survey_presented` o
   relata.

---

### User Story 3 - Pré-visualizar com a mesma customização do app (Priority: P2)

O preview aceita o mesmo tema, textos, renderizadores e slots do Provider. O painel acompanha o
tema claro ou escuro da própria interface; um app pré-visualiza com a própria marca.

**Why this priority**: a pré-visualização precisa parecer com o que vai para o app; mas a padrão já
entrega valor.

**Independent Test**: passar tema escuro, um rótulo trocado, um slot e um renderizador próprios e
conferir o desenho; passar um renderizador que lança e conferir o padrão e `onRenderError`.

**Acceptance Scenarios**:

1. **Given** `theme`, `strings`, `renderers` e `slots`, **When** o preview monta, **Then** a
   pré-visualização usa todos, com a mesma regra do Provider.
2. **Given** um renderizador que lança, **When** a pergunta aparece, **Then** o padrão é desenhado e
   `onRenderError` recebe o erro e o contexto técnico.
3. **Given** `resetKey` muda (por exemplo, a lista de perguntas foi editada), **When** o preview
   renderiza, **Then** a pré-visualização recomeça do zero, com `displayId` novo.

---

### User Story 4 - Comparar formas no aparelho, ainda sem servidor (Priority: P2)

Num app, `<PitacoPreview presentation="bottom-sheet" />` ou `"modal"` abre a pré-visualização no
mesmo contêiner do Provider, com animação, gestos e vias de dispensa — e ainda sem abrir exibição.

**Why this priority**: permite comparar o sheet do SDK com o contêiner do app sem gastar
elegibilidade nem sujar resultado.

**Independent Test**: montar com `bottom-sheet`, conferir que a folha abre e `survey_presented` só
sai depois da entrada, tocar no fundo e conferir `via: backdrop` e `onFinish('dismissed')` depois da
saída; tudo sem `fetch`.

**Acceptance Scenarios**:

1. **Given** `presentation="bottom-sheet"`, **When** o preview monta, **Then** abre a folha do SDK
   e `survey_presented` relata `bottom-sheet` só depois da entrada, sem rede.
2. **Given** a folha do preview, **When** a pessoa toca no fundo, **Then** a dispensa sai com via
   `backdrop` e `onFinish('dismissed')` só depois da saída.
3. **Given** a folha do preview, **When** a pessoa conclui, **Then** o agradecimento aparece e
   `onFinish('completed')` vem depois da saída.
4. **Given** `presentation="modal"`, **When** o preview monta, **Then** abre em tela cheia e relata
   `modal`, sem rede.

---

### User Story 5 - Montar UI própria sobre o schema em memória (Priority: P3)

Quem quer uma pré-visualização com UI inteiramente própria usa `createPreviewController` ou
`PitacoPreviewProvider` com `usePitacoSurvey()`, exatamente como faria com o Provider de verdade.

**Why this priority**: caso avançado; o componente pronto cobre o painel.

**Independent Test**: criar o controlador sobre o schema, apresentar, responder pelo hook dentro do
`PitacoPreviewProvider`, conferir os eventos e reiniciar com `restart()`.

**Acceptance Scenarios**:

1. **Given** `createPreviewController({ schema, onEvent })`, **When** a UI apresenta e responde,
   **Then** a máquina roda sem rede e entrega os eventos ao `onEvent`.
2. **Given** um controlador concluído, **When** `restart()` é chamado, **Then** começa outra passada
   com outro `displayId`.
3. **Given** `usePitacoSurvey()` dentro do `PitacoPreviewProvider`, **When** a UI própria o usa,
   **Then** estado e ações funcionam como no Provider de verdade.

---

### Edge Cases

- **Navegador (`react-native-web`)**: o foco de acessibilidade vai para o elemento do DOM do título;
  não há módulo nativo nem `findNodeHandle`.
- **Renderização no servidor**: o preview depende do DOM; quem usa num framework com SSR o carrega
  só no cliente.
- **Schema inválido** (não objeto, sem perguntas): nada desenhado, nada lançado.
- **`onEvent` trocado depois da montagem**: o controlador passa a entregar ao novo sem ser
  recriado.
- **Schema, `presentation` ou `triggerEvent` trocados**: o controlador é recriado, do zero.
- **Insets no preview em contêiner**: valem os padrões da plataforma.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: O SDK MUST oferecer o subpath `@pitaco/react-native/preview` com `PitacoPreview`,
  `PitacoPreviewProvider`, `createPreviewController` e `usePitacoSurvey`.
- **FR-002**: Nada importado pelo subpath MUST fazer requisição de rede nem usar storage
  persistente.
- **FR-003**: O preview MUST aceitar o schema no formato da elegibilidade e preencher `surveyId`,
  `versionId` e `versionNumber` ausentes.
- **FR-004**: O preview MUST normalizar o schema com a mesma regra do runtime (tipo desconhecido
  pulado, campo desconhecido ignorado).
- **FR-005**: Sem pergunta renderizável, o preview MUST NOT desenhar nada nem lançar.
- **FR-006**: O preview MUST rodar a mesma máquina de estado e emitir os mesmos eventos do catálogo,
  entregues só ao `onEvent`.
- **FR-007**: O `onEvent` MUST valer desde a criação do controlador, para o `survey_presented` não
  se perder quando o conteúdo apresenta no próprio efeito de montagem.
- **FR-008**: `<PitacoPreview />` MUST usar `presentation="inline"` por padrão, desenhando só
  `<PitacoSurveyContent />`, que apresenta ao montar.
- **FR-009**: Com `presentation="bottom-sheet"` ou `"modal"`, `<PitacoPreview />` MUST abrir no
  mesmo contêiner do Provider, com `survey_presented` só depois da entrada e `onFinish` só depois
  da saída.
- **FR-010**: O preview MUST aceitar `theme`, `strings`, `renderers`, `slots`, `triggerEvent`,
  `onEvent`, `onFinish`, `thankYouDurationMs` e `resetKey`.
- **FR-011**: Mudar `resetKey` MUST reiniciar a pré-visualização com `displayId` novo.
- **FR-012**: Falha de renderizador ou slot substituído MUST cair no padrão e ser entregue a
  `onRenderError` do `PitacoPreviewProvider`, sem transporte.
- **FR-013**: O controlador MUST implementar a mesma interface de controlador do runtime, para
  `usePitacoSurvey()` funcionar igual dentro do `PitacoPreviewProvider`.
- **FR-014**: O controlador MUST oferecer `restart()`, `dispose()` e `setEventListener()`.
- **FR-015**: O conteúdo MUST funcionar sob `react-native-web`, movendo o foco para o elemento do
  título no navegador.

### Key Entities

- **Schema em memória**: a pesquisa no formato da elegibilidade, publicada ou em rascunho.
- **Controlador de preview**: a máquina de estado sobre o schema, sem transporte, com reinício.
- **Pré-visualização**: o controlador mais a UI padrão (ou a do app), inline ou num contêiner.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Nenhuma interação com a pré-visualização gera requisição de rede — verificável com um
  `fetch` espião em 100% dos testes do preview.
- **SC-002**: Para o mesmo roteiro, os eventos do preview têm a mesma assinatura que os da máquina
  de referência.
- **SC-003**: O autor vê a mudança de uma pergunta refletida na pré-visualização assim que o
  painel a passa adiante, recomeçando do início.
- **SC-004**: A pré-visualização desenha no navegador sem nenhum módulo nativo.

## Assumptions

- **O painel cuida do próprio carregamento.** Montar o `react-native-web`, carregar o preview só no
  cliente e listar os eventos ao lado é do painel; o SDK entrega o componente.
- **Pré-visualizar não é coletar.** Nenhum dado do preview tem valor para resultado; por isso nada
  vai ao servidor, nem relatório de erro.
- **Sem sessão de app.** Limite por sessão, bloqueio e adiamento (`004`) não se aplicam.

## Dependencies

- `001-sdk-core`: máquina de estado, sessão, schema e interface de controlador.
- `002-sdk-ui-apresentacoes`: `<PitacoSurveyContent />`, renderizadores, tema, textos e
  `PitacoSurfaceHost`.
- `003-sdk-eventos-interacao`: catálogo emitido para o `onEvent`.
- Consumidores: o construtor de pesquisas do painel, que renderiza `<PitacoPreview />` via
  `react-native-web`, e `006-sdk-app-exemplo`.
