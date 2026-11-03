# Feature Specification: SDK React Native — UI padrão e apresentações

**Feature Branch**: `002-sdk-ui-apresentacoes`

**Created**: 2026-09-13

**Status**: Implemented

**Input**: User description: "Agora precisa da cara: os seis tipos de pergunta desenhados, bottom sheet por padrão, e dar pro app trocar tema, textos e até o componente de uma pergunta sem quebrar nada."

## Contexto

O core headless (`001-sdk-core`) já consulta, oferece, percorre e entrega a pesquisa, mas não
desenha nada: o app precisaria montar toda a interface sozinho. Esta feature entrega a interface
padrão do SDK e as formas de apresentá-la, para que a integração mínima — Provider e `track` —
mostre uma pesquisa completa, acessível e com a cara do app.

Duas regras desenham a feature inteira:

- **A UI padrão não tem acesso privilegiado.** Ela é construída só sobre `usePitacoSurvey()`,
  `usePitacoTheme()` e `usePitacoStrings()`, a mesma API que um app usaria para montar a própria.
  Por isso qualquer nível de customização — tema, textos, slots, renderizador por tipo ou UI
  inteira própria — continua passando pelas mesmas ações do core.
- **Nenhum módulo nativo.** O bottom sheet é feito com `Modal`, `Animated`, `PanResponder`,
  `KeyboardAvoidingView` e `BackHandler` do próprio React Native. Quem já usa `@gorhom/bottom-sheet`
  ou React Navigation encaixa o conteúdo no próprio contêiner.

Fora do escopo: o catálogo de eventos e a prova de "mesma sequência" (`003-sdk-eventos-interacao`),
o controle de onde e quando (`004-sdk-controle-onde-quando`) e o preview sem transporte
(`005-sdk-preview-web`).

## Clarifications

### Session 2026-09-13

- Q: Qual a apresentação padrão? → A: Bottom sheet. Pesquisa de uma a cinco perguntas disparada
  por evento é interrupção curta; o sheet mantém o contexto do app visível e oferece o gesto de
  dispensar mais natural. Tela cheia (`modal`) e `inline` ficam como opção.
- Q: O SDK usa `@gorhom/bottom-sheet`? → A: Não. Ele exige Reanimated e Gesture Handler, que são
  módulos nativos. O sheet é próprio; quem já usa gorhom coloca `<PitacoSurveyContent />` dentro
  do sheet dele.
- Q: Como resolver a safe area sem `react-native-safe-area-context`? → A: O app passa `insets`
  fixos ou uma função `getInsets` (por exemplo o próprio `useSafeAreaInsets`); sem nenhum dos dois,
  vale um padrão conservador por plataforma. `getInsets` tem prioridade.
- Q: O botão "Próxima" fica desabilitado enquanto a obrigatória está em branco? → A: Não. Quem
  valida e bloqueia é o core; o botão desabilitado impediria a tentativa e esconderia o bloqueio
  da medição. A mensagem de obrigatória aparece abaixo do título.
- Q: Quando a exibição é aberta numa apresentação animada? → A: Só quando a animação de entrada
  termina. O contêiner adia o `present()` até lá.
- Q: Um renderizador substituído que lança erro derruba a pesquisa? → A: Não. Cada substituição
  roda isolada; se falhar, cai no padrão daquele tipo e o SDK reporta a falha pelo canal próprio.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - A pesquisa aparece completa no bottom sheet do SDK (Priority: P1)

Com o Provider na raiz e nenhuma configuração de aparência, a pesquisa que chega abre num bottom
sheet na parte de baixo da tela, com progresso, enunciado, a pergunta desenhada conforme o tipo,
botões de voltar e avançar, botão de fechar em toda pergunta e um agradecimento curto que fecha
sozinho.

**Why this priority**: é o que transforma o core em produto para quem integra em poucas linhas.
Sem UI padrão, todo app teria de desenhar seis tipos de pergunta antes de coletar a primeira
resposta.

**Independent Test**: montar o Provider com o servidor falso, chamar `track`, conferir que a folha
abre com "Pergunta 1 de N", percorrer os seis tipos pelos controles padrão até "Enviar", ver
"Obrigado!" e a folha fechar.

**Acceptance Scenarios**:

1. **Given** uma pesquisa disponível com `presentation` padrão, **When** ela chega, **Then** o
   bottom sheet abre ancorado embaixo da tela, e a exibição só é aberta depois que a animação de
   entrada termina.
2. **Given** uma pergunta de escolha única, **When** a pessoa toca numa opção, **Then** a opção
   fica marcada (papel `radio`) e o core recebe o `value` da opção, nunca o rótulo.
3. **Given** uma pergunta de múltipla escolha, **When** a pessoa toca numa opção marcada, **Then**
   ela é desmarcada (papel `checkbox`).
4. **Given** uma avaliação, **When** a pessoa toca na quarta estrela de cinco, **Then** a estrela
   tem rótulo "4 de 5" e o core recebe o número da faixa.
5. **Given** uma escala ou um NPS, **When** a pergunta aparece, **Then** cada número é um ponto
   tocável e os rótulos das pontas aparecem; no NPS sem rótulo, valem "Nada provável" e "Muito
   provável".
6. **Given** uma pergunta de texto livre com aviso ligado na versão publicada, **When** ela
   aparece, **Then** o aviso fica junto do campo, é anunciado ao leitor de tela, e o contador
   mostra o tamanho contra o teto de 2000.
7. **Given** a última pergunta aplicável, **When** a pessoa toca em "Enviar", **Then** aparece
   "Obrigado!" por 2,5 s e a folha fecha.
8. **Given** uma obrigatória em branco, **When** a pessoa toca em "Próxima", **Then** a pergunta
   não muda, "Essa pergunta é obrigatória." aparece e a área da pergunta ganha borda de erro.

---

### User Story 2 - O app escolhe como a pesquisa é apresentada (Priority: P1)

O app escolhe entre bottom sheet (padrão), modal em tela cheia e `inline`. No bottom sheet, a
pessoa pode fechar pelo botão, arrastando a alça para baixo, tocando no fundo ou pelo voltar do
Android. No `inline`, o SDK não abre contêiner nenhum: o app recebe a pesquisa pelo hook e coloca
`<PitacoSurveyContent />` onde quiser — dentro de um sheet do gorhom, numa rota de navegação ou no
meio de uma tela com rolagem.

**Why this priority**: cada app tem um padrão de navegação; sem escolha de apresentação, a pesquisa
destoa do produto ou briga com o contêiner que o app já usa.

**Independent Test**: para cada apresentação, montar o host com uma pesquisa disponível e conferir
o contêiner desenhado e a via de cada forma de fechar.

**Acceptance Scenarios**:

1. **Given** o bottom sheet aberto, **When** a pessoa toca no fundo, **Then** a pesquisa é
   dispensada com via `backdrop` e a folha some depois da animação de saída.
2. **Given** o bottom sheet aberto, **When** a pessoa arrasta a alça além do limiar de distância ou
   de velocidade, **Then** a pesquisa é dispensada com via `swipe`; abaixo do limiar, a folha volta.
3. **Given** o bottom sheet ou o modal aberto no Android, **When** a pessoa usa o voltar do sistema,
   **Then** a pesquisa é dispensada com via `hardware_back` e a tela do app não muda.
4. **Given** qualquer apresentação, **When** a pessoa toca no botão de fechar, **Then** a pesquisa é
   dispensada com via `close_button`.
5. **Given** `presentation="modal"`, **When** a pesquisa chega, **Then** abre em tela cheia com os
   quatro insets aplicados, sem gesto de arrastar.
6. **Given** `presentation="inline"`, **When** a pesquisa chega, **Then** o SDK não desenha
   contêiner, e `<PitacoSurveyContent />` montado pelo app chama `present()` ao montar.
7. **Given** `<PitacoSurveyContent />` numa rota, **When** a pessoa sai pela navegação com a
   pesquisa em andamento, **Then** o conteúdo dispensa com a via `navigation` (ou a informada em
   `dismissVia`) e chama `onFinish('dismissed')` uma vez só.
8. **Given** um contêiner do app que já dispensou com a via dele (o gesto do gorhom), **When** o
   conteúdo desmonta, **Then** não há segunda dispensa.
9. **Given** uma dispensa vinda de fora (`usePitacoSurvey().dismiss()`), **When** o host está
   aberto, **Then** a via é `programmatic` e o contêiner fecha.
10. **Given** o texto livre focado, **When** o teclado sobe, **Then** a folha (ou o modal) sobe
    junto e o campo, o contador e "Enviar" ficam à vista, em iOS e Android.
11. **Given** movimento reduzido ligado no sistema, **When** a folha abre ou fecha, **Then** não há
    animação perceptível e a exibição é aberta sem esperar.
12. **Given** a apresentação trocada de `inline` para um contêiner depois de uma pesquisa, **When**
    o host volta a desenhar, **Then** nenhum contêiner vazio fica preso por cima do app.

---

### User Story 3 - A pesquisa com a cara do app (Priority: P2)

O app ajusta a aparência num ponto só: tema (cores, tipografia, raio e espaçamento, claro e escuro
acompanhando o sistema ou forçados), textos de interface (em pt-BR por padrão, substituíveis por
outro idioma) e slots de moldura (cabeçalho, barra de progresso, rodapé, botão de fechar e
agradecimento).

**Why this priority**: a pesquisa precisa parecer parte do produto; mas a UI padrão já entrega
valor sem nenhuma customização.

**Independent Test**: montar o conteúdo com tema escuro forçado, um rótulo substituído e cada slot
substituído, e conferir a cor do título, o rótulo novo e o slot próprio desenhado.

**Acceptance Scenarios**:

1. **Given** `theme.colorScheme` ausente ou `'system'`, **When** o sistema está em modo escuro,
   **Then** os tokens escuros valem; com `'light'` ou `'dark'`, o sistema é ignorado.
2. **Given** um tema com um token inválido entre válidos, **When** é resolvido, **Then** só aquele
   token cai no padrão, com aviso em desenvolvimento, e a UI continua desenhando.
3. **Given** um tema cujo texto principal sobre o fundo fica abaixo de 4,5:1, **When** é resolvido
   em desenvolvimento, **Then** sai um aviso de contraste com o par e a razão.
4. **Given** `strings={{ next: 'Next' }}`, **When** a pergunta aparece, **Then** o botão diz "Next"
   e os demais rótulos continuam em português.
5. **Given** um slot substituído (`Header`, `Progress`, `Footer`, `CloseButton` ou `ThankYou`),
   **When** a pesquisa aparece, **Then** o slot próprio é desenhado no lugar do padrão, recebendo
   tema, textos e as ações já ligadas ao core.
6. **Given** `renderers`/`slots` no Provider e outros locais em `<PitacoSurveyContent />`, **When**
   o conteúdo é desenhado, **Then** o local sobrepõe o do Provider, tipo a tipo e slot a slot.

---

### User Story 4 - Trocar o componente de um tipo de pergunta (Priority: P2)

O app substitui o desenho de um tipo — por exemplo `renderers={{ nps: MeuNps }}` — e o SDK continua
cuidando de validação, navegação e envio. O componente recebe a pergunta, o valor atual, as ações
do core já ligadas à pergunta, o erro de validação, o tema e os textos. Se ele lançar, cai no
padrão daquele tipo.

**Why this priority**: é o nível de customização que mais pede quem tem design system próprio, e o
que mais arrisca a estabilidade; precisa existir com rede de segurança.

**Independent Test**: montar o conteúdo com um NPS próprio e um renderizador de avaliação que lança
sempre, e conferir que o NPS próprio aparece, as estrelas padrão aparecem no lugar do quebrado, e a
falha foi reportada.

**Acceptance Scenarios**:

1. **Given** um renderizador substituído para um tipo, **When** uma pergunta desse tipo aparece,
   **Then** o componente próprio é desenhado e os outros tipos continuam padrão.
2. **Given** um renderizador substituído que lança, **When** a pergunta aparece, **Then** o
   renderizador padrão daquele tipo é desenhado, sai um aviso único em desenvolvimento e um
   relatório `render_error` com o tipo.
3. **Given** um slot substituído que lança, **When** a pesquisa aparece, **Then** o slot padrão é
   desenhado e a falha é reportada.
4. **Given** a próxima pergunta do mesmo tipo, **When** ela aparece, **Then** o componente
   substituído é tentado de novo, do zero.
5. **Given** um renderizador substituído, **When** ele chama `select`, `deselect`, `setText`,
   `focusText` ou `blurText`, **Then** a ação vai para a pergunta atual sem precisar de
   `questionKey`.

---

### User Story 5 - Acessível sem configuração (Priority: P2)

Quem usa leitor de tela, fonte ampliada ou movimento reduzido consegue responder a pesquisa padrão
sem que o app faça nada.

**Why this priority**: é requisito de qualquer interface que vai para app de terceiros, e excluir
quem usa tecnologia assistiva enviesa a amostra.

**Independent Test**: conferir papéis e rótulos de cada controle, alvo mínimo de 44 pt, o título
como alvo de foco a cada navegação, anúncios de erro e agradecimento, e o teto de escala de fonte.

**Acceptance Scenarios**:

1. **Given** qualquer pergunta, **When** ela fica atual, **Then** o foco de acessibilidade vai para
   o título, cujo rótulo diz "Pergunta X de Y", se é obrigatória, e o enunciado.
2. **Given** qualquer controle tocável da UI padrão, inclusive a alça do sheet, **When** é medido,
   **Then** tem ao menos 44 pt de alvo.
3. **Given** um erro de validação, **When** ele aparece, **Then** é anunciado ao leitor de tela.
4. **Given** o agradecimento, **When** ele aparece, **Then** título e corpo são anunciados.
5. **Given** fonte ampliada no sistema, **When** a pesquisa é desenhada, **Then** todo texto
   (inclusive título e campo de texto livre) escala até o mesmo teto.
6. **Given** a folha aberta, **When** o leitor de tela percorre a tela, **Then** o que está atrás
   da folha fica fora da árvore de acessibilidade, e a alça oferece a ação de fechar.

---

### Edge Cases

- **Pergunta atual sem componente**: o título e a moldura vêm de `<PitacoSurveyContent />`; o
  renderizador só desenha a área interativa, então trocar o renderizador nunca perde o alvo de
  foco.
- **`available` cai antes do agradecimento**: o contêiner continua aberto até o conteúdo avisar o
  desfecho, para o agradecimento aparecer e a saída animar.
- **`getInsets` que lança**: vale `insets`, ou o padrão da plataforma.
- **Escala com faixa grande (até 100 pontos)**: os pontos quebram em várias linhas.
- **Tema com cor em `hsl` ou nome CSS**: aceito quando a validação reconhece o formato; a checagem
  de contraste é pulada para ele.
- **Texto desabilitado**: fora da checagem de contraste, como o WCAG permite.
- **Teclado no Android ponta a ponta**: a janela do `Modal` não encolhe com `adjustResize`; o
  contêiner usa `behavior="height"` no Android e `padding` no iOS.
- **Rotação de tela com a folha aberta**: a altura escondida é a do primeiro desenho.
- **`useColorScheme()` devolvendo `'unspecified'`**: vale como ausência de esquema, cai no claro.
- **React Native Web**: o foco vai para o elemento do DOM do título, sem `findNodeHandle`.

## Requirements *(mandatory)*

### Functional Requirements

**Tipos de pergunta**

- **FR-001**: A UI padrão MUST desenhar os seis tipos: escolha única (`radio`), múltipla escolha
  (`checkbox`, tocar de novo desmarca), avaliação (estrelas com rótulo "X de N"), escala e NPS
  (pontos numéricos com rótulos das pontas) e texto livre (campo multilinha).
- **FR-002**: Toda escolha MUST chegar ao core pelo `value` da opção ou pelo número, nunca pelo
  rótulo.
- **FR-003**: O texto livre MUST limitar a digitação ao teto do core e mostrar o contador
  "{tamanho}/2000"; com o aviso de texto livre ligado na versão, MUST mostrá-lo junto do campo e
  anunciá-lo uma vez.
- **FR-004**: O NPS sem rótulos de ponta MUST usar os rótulos padrão dos textos.
- **FR-005**: Erro de validação MUST destacar a área da pergunta e ser anunciado, sem repetir a
  mensagem já mostrada abaixo do título.

**Conteúdo e moldura**

- **FR-006**: `<PitacoSurveyContent />` MUST desenhar a pesquisa ativa sem contêiner: cabeçalho com
  progresso, barra de progresso, título com a marca de obrigatória, botão de fechar, mensagem de
  validação, pergunta pelo registro de renderizadores, rodapé com Voltar e Próxima/Enviar, e o
  agradecimento.
- **FR-007**: O botão de avançar MUST sempre chamar `next()` e MUST NOT ser desabilitado por
  obrigatória em branco; o rótulo MUST virar "Enviar" na última pergunta aplicável.
- **FR-008**: O agradecimento MUST aparecer ao concluir e chamar `onFinish('completed')` depois de
  `thankYouDurationMs` (padrão 2500 ms).
- **FR-009**: `<PitacoSurveyContent />` MUST chamar `present()` ao montar, salvo com
  `presentDeferred`, quando o contêiner chama depois da animação.
- **FR-010**: Ao desmontar com a pesquisa pronta ou apresentada e sem desfecho já visto,
  `<PitacoSurveyContent />` MUST dispensar com `dismissVia` (padrão `navigation`) e chamar
  `onFinish('dismissed')`; com desfecho já visto, MUST NOT dispensar de novo.

**Apresentações**

- **FR-011**: O Provider MUST desenhar a apresentação escolhida em `presentation`:
  `'bottom-sheet'` (padrão), `'modal'` (tela cheia) ou `'inline'` (nada), dentro de um error
  boundary.
- **FR-012**: O bottom sheet MUST usar `Modal` transparente, animação com driver nativo, alça
  arrastável, toque no fundo, voltar do Android, `KeyboardAvoidingView` e altura máxima de 90% da
  tela, ancorado embaixo.
- **FR-013**: O modal MUST ocupar a tela cheia com os quatro insets, sem gesto de arrastar, com
  voltar do Android e `KeyboardAvoidingView`.
- **FR-014**: A apresentação MUST chamar `present()` só quando a animação de entrada terminar.
- **FR-015**: A apresentação MUST permanecer aberta até o conteúdo avisar o desfecho, e só então
  animar a saída.
- **FR-016**: Cada forma de fechar MUST informar a via certa: `close_button`, `swipe`, `backdrop`,
  `hardware_back`, `navigation` ou `programmatic`.
- **FR-017**: O arrasto MUST fechar além de 120 pt ou acima de 0,8 de velocidade, com resistência
  para cima, e voltar abaixo disso.
- **FR-018**: Com movimento reduzido, as animações MUST ter duração de 1 ms.
- **FR-019**: Os insets MUST vir de `getInsets` (prioridade), de `insets` ou do padrão da
  plataforma (iOS 47/34; Android a altura da barra de status ou 24).
- **FR-020**: Em `inline`, o host MUST NOT manter estado de contêiner aberto entre trocas de
  apresentação.

**Customização**

- **FR-021**: O tema MUST aceitar `colorScheme` (`'system'`, `'light'`, `'dark'`) e sobreposições
  parciais `light` e `dark` de cores, tipografia, raio e espaçamento, validadas token a token.
- **FR-022**: Em desenvolvimento, o tema MUST verificar o contraste dos pares texto principal e
  secundário sobre fundo, texto principal sobre superfície, texto de botão sobre a cor primária e
  texto de perigo sobre a cor de perigo, avisando abaixo de 4,5:1.
- **FR-023**: Todos os rótulos de interface MUST estar em pt-BR por padrão e ser substituíveis
  individualmente por `strings`, inclusive progresso e rótulo de acessibilidade por função.
- **FR-024**: Os cinco slots MUST ser substituíveis por componente, recebendo tema, textos e ações
  ligadas.
- **FR-025**: Os seis tipos MUST ser substituíveis por `renderers` com as chaves `singleChoice`,
  `multipleChoice`, `rating`, `scale`, `nps` e `freeText`.
- **FR-026**: Cada renderizador e slot substituído MUST rodar isolado num error boundary, cair no
  padrão se lançar, avisar uma vez em desenvolvimento e reportar `render_error`.
- **FR-027**: `usePitacoTheme()` e `usePitacoStrings()` MUST funcionar dentro e fora do Provider,
  caindo nos padrões fora dele.

**Acessibilidade**

- **FR-028**: Todo controle MUST ter papel e rótulo de acessibilidade e alvo de toque de ao menos
  44 pt.
- **FR-029**: O foco de acessibilidade MUST ir para o título da pergunta a cada navegação, sem
  deixar falha da API escapar.
- **FR-030**: Todo texto da UI padrão MUST respeitar a escala de fonte do sistema com teto de 1,6×.
- **FR-031**: A folha MUST marcar o conteúdo como modal para acessibilidade, e a alça MUST oferecer
  as ações `escape` e `magicTap` para fechar.

### Key Entities

- **Tema resolvido**: esquema efetivo (claro ou escuro) e o conjunto completo de tokens depois da
  validação.
- **Textos**: o conjunto completo de rótulos de interface, com funções para os que interpolam.
- **Renderizador de pergunta**: componente por tipo que recebe pergunta, valor, erro, ações, tema,
  textos e aviso de texto livre.
- **Slot de moldura**: componente substituível para cabeçalho, progresso, rodapé, botão de fechar
  ou agradecimento.
- **Apresentação**: o contêiner que mostra o conteúdo — folha, tela cheia ou nenhum.
- **Insets**: margens de área segura `top`, `right`, `bottom`, `left`.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Com apenas Provider e `track`, uma pesquisa com os seis tipos é exibida e respondida
  até o agradecimento sem nenhum código de interface do app.
- **SC-002**: As seis vias de dispensa chegam ao core com o valor correto em 100% das formas de
  fechar cobertas.
- **SC-003**: Um renderizador ou slot que lança nunca derruba a pesquisa: em 100% dos casos o padrão
  aparece e a falha é reportada uma vez.
- **SC-004**: Todo controle tocável da UI padrão mede ao menos 44 pt de alvo.
- **SC-005**: Nenhuma exibição é aberta antes de o contêiner estar visível; com movimento reduzido,
  a abertura não espera animação.
- **SC-006**: O pacote continua sem nenhuma importação de módulo nativo depois desta feature.

## Assumptions

- **A abertura da exibição é do core.** A UI só informa "ficou visível" (`present()`); quem grava
  e emite é a `001` e a `003`.
- **Quem usa gorhom ou React Navigation usa `inline`.** O SDK não detecta o contêiner do app; o app
  escolhe `inline` e informa a via pelo `dismiss(via)` ou pelo desmonte do conteúdo.
- **Bottom sheet e modal são detalhe de implementação.** Não são exportados; a escolha é por
  `presentation`.
- **Slider por arrasto fica de fora.** Escala com faixa grande usa pontos em várias linhas; um
  slider sem Gesture Handler exigiria gesto próprio sem caso real.
- **Mínimo e máximo de seleções na múltipla escolha não existem no contrato.** A UI não valida
  quantidade além do que o core já garante.

## Dependencies

- `001-sdk-core`: `usePitacoSurvey`, `SurveyController`, máquina de estado, relatório de erro e
  `PitacoErrorBoundary`.
- `backend 003-survey-authoring`: os seis tipos, faixas, rótulos de ponta, opções e aviso de texto
  livre que a UI desenha.
- Consumidores: `003-sdk-eventos-interacao` (teste de mesma sequência sobre esta UI),
  `005-sdk-preview-web` e `006-sdk-app-exemplo`.
