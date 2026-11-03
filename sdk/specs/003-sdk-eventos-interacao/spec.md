# Feature Specification: SDK React Native — eventos de interação

**Feature Branch**: `003-sdk-eventos-interacao`

**Created**: 2026-09-13

**Status**: Implemented

**Input**: User description: "Quero saber o que a pessoa faz dentro da pesquisa, pergunta por pergunta, e que isso seja igual não importa a UI que o app usar."

## Contexto

A coleta registra o resultado de uma exibição — o que foi respondido e como ela terminou. Ela não
diz o caminho: quanto tempo a pessoa passou em cada pergunta, se voltou, se trocou a resposta, em
que pergunta desistiu, se tentou avançar com obrigatória em branco, por onde fechou. O backend já
aceita esse caminho como **eventos de interação** num catálogo fechado e versionado
(`backend 013-interaction-events`); falta o SDK produzi-los.

Esta feature faz o SDK emitir o catálogo versão 1 e entregá-lo a dois destinos: ao servidor, em
lote e sem duplicar, e ao app hospedeiro, só para leitura, pelo `onEvent`.

Três regras desenham o escopo:

- **Quem emite é o core.** Os eventos nascem como consequência das transições da máquina de
  estado (`001-sdk-core`), nunca de componente de UI. A UI padrão, uma UI com renderizador
  substituído e uma UI inteiramente própria produzem a mesma sequência para a mesma interação.
- **O catálogo é fechado.** O app ouve, nunca cria tipo nem altera payload. Mudar o catálogo é
  mudança de contrato, com versão nova.
- **Nenhum conteúdo de texto livre.** Texto aparece só como tamanho; escolha, pelo `value` da opção
  ou pelo número, nunca pelo rótulo.

Fora do escopo: os eventos `placement_` de controle de onde e quando, que não fazem parte do
catálogo enviado e são da `004-sdk-controle-onde-quando`; a leitura de comportamento no painel.

## Clarifications

### Session 2026-09-13

- Q: Os eventos de interação saem um a um ou em lote? → A: Em lote. Enquanto a pessoa responde, os
  eventos esperam até 5 s na fila; abertura da exibição, desfecho e ida para o segundo plano mandam
  na hora. Lotes de até 100.
- Q: O que identifica um evento para o reenvio não duplicar? → A: O par (`displayId`, `seq`), com
  `seq` monotônico por exibição começando em 1.
- Q: Qual relógio mede os tempos? → A: O monotônico. `occurredAt` usa o relógio do dispositivo e é
  só informativo; `elapsedMs`, `durationMs` e `activeMs` usam o monotônico, imune a ajuste de hora.
- Q: O tempo em segundo plano conta como tempo na pergunta? → A: Conta em `durationMs` e é
  descontado em `activeMs`.
- Q: Com que frequência `text_edited` sai? → A: Uma vez por pausa de 1 s na digitação, e sempre
  antes de a pessoa sair da pergunta.
- Q: Os eventos de antes da exibição (bloqueio, adiamento) entram no catálogo? → A: Não. Têm tipos
  próprios prefixados `placement_` e vão só para o `onEvent`.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - O servidor recebe o caminho de cada exibição (Priority: P1)

Cada exibição produz uma sequência de eventos do catálogo — apresentada, pergunta vista, resposta
escolhida ou trocada, texto focado e editado, validação bloqueada, pergunta pulada ou não
aplicável, navegação, pergunta deixada, segundo plano, dispensa ou conclusão — que chega ao
servidor depois da abertura, em lote, sem duplicar.

**Why this priority**: é o dado que a leitura de comportamento do painel precisa. Sem ele, o
backend aceita eventos que ninguém manda.

**Independent Test**: com o servidor falso, apresentar, responder e concluir uma pesquisa e
conferir que o servidor recebeu, depois da abertura, exatamente os eventos que o `onEvent`
recebeu, com `seq` de 1 em diante.

**Acceptance Scenarios**:

1. **Given** uma pesquisa apresentada, **When** ela fica visível, **Then** o primeiro evento é
   `survey_presented` com `presentation`, `questionCount`, `renderableCount` e `triggerEvent`,
   seguido de `question_viewed` da primeira pergunta com `from: start`.
2. **Given** a pessoa respondendo, **When** os eventos se acumulam, **Then** saem em lote cerca de
   5 s depois, não um por um.
3. **Given** o desfecho ou a ida para o segundo plano, **When** acontece, **Then** o lote pendente
   sai na hora.
4. **Given** a abertura ainda não entregue, **When** a fila envia, **Then** nenhum evento daquela
   exibição sai antes dela.
5. **Given** um lote reenviado depois de uma falha de rede, **When** o servidor já o tinha, **Then**
   nada é duplicado.
6. **Given** mais de 100 eventos pendentes de uma exibição, **When** a fila envia, **Then** eles
   saem em lotes de até 100, em ordem de `seq`.
7. **Given** mais de 500 eventos numa exibição, **When** são enfileirados, **Then** o excedente é
   descartado.

---

### User Story 2 - O app ouve os eventos sem poder alterá-los (Priority: P1)

O app passa `onEvent` ao Provider e recebe cada evento do catálogo assim que acontece — para mandar
à própria ferramenta de analytics, por exemplo — sem poder criar tipo nem mudar o que vai ao
servidor.

**Why this priority**: o app quer correlacionar a pesquisa com o próprio produto; dar o dado pronto
evita que cada app reinvente o tracking por cima da UI.

**Independent Test**: passar um `onEvent` que guarda os eventos, percorrer a pesquisa e conferir que
a lista é igual à recebida pelo servidor; passar um `onEvent` que lança e conferir que nada quebra.

**Acceptance Scenarios**:

1. **Given** um `onEvent` no Provider, **When** a pesquisa é percorrida, **Then** ele recebe os
   mesmos eventos, na mesma ordem e com o mesmo conteúdo que vão ao servidor.
2. **Given** um `onEvent` que lança, **When** um evento é entregue, **Then** o SDK segue, avisa uma
   vez em desenvolvimento e não trata como falha própria.
3. **Given** um `onEvent` trocado depois da montagem, **When** o próximo evento acontece, **Then**
   ele vai para o ouvinte novo sem recriar o runtime.
4. **Given** o app recebe um evento, **When** o tipo é inspecionado em TypeScript, **Then** o
   envelope e o `data` são somente leitura, e o `type` estreita o `data` sem conversão.
5. **Given** a superfície pública, **When** o app procura emitir um evento, **Then** não existe API
   para isso; ele pode distinguir os tipos por `isInteractionEvent` e `isPlacementEvent`.

---

### User Story 3 - A mesma sequência com qualquer UI (Priority: P1)

Para o mesmo roteiro de interação, a UI padrão, a UI com renderizador substituído e uma UI headless
construída sobre `usePitacoSurvey()` produzem a mesma sequência de eventos — mesmo tipo, mesma
ordem, mesma pergunta e mesmo `data`, tempos à parte.

**Why this priority**: é a promessa que torna a customização segura. Se a sequência dependesse da
UI, comparar comportamento entre apps — ou entre versões de um app — não teria sentido.

**Independent Test**: rodar o roteiro completo e o de dispensa sobre as três UIs e sobre a máquina
pura, e comparar as assinaturas.

**Acceptance Scenarios**:

1. **Given** o roteiro completo (respostas, troca, desmarcar, texto, validação, voltar, condição,
   conclusão), **When** é executado na UI padrão, na substituída e na headless, **Then** as três
   assinaturas são iguais à da máquina de referência.
2. **Given** o roteiro de dispensa, **When** é executado nas três UIs, **Then** as assinaturas são
   iguais.
3. **Given** um renderizador substituído que expõe o gesto de desmarcar de outro jeito, **When** o
   roteiro desmarca, **Then** a sequência continua igual.
4. **Given** o Provider de verdade com `AppState`, **When** o roteiro inclui ir e voltar do segundo
   plano numa UI headless, **Then** a sequência é a mesma da máquina.
5. **Given** o roteiro completo e o de dispensa juntos, **When** são executados, **Then** cobrem os
   18 tipos do catálogo.

---

### User Story 4 - O catálogo é o mesmo dos dois lados (Priority: P2)

O catálogo do SDK e o do backend não podem divergir: um tipo que só um lado conhece é dado perdido
ou rejeitado em silêncio.

**Why this priority**: protege o contrato contra mudança unilateral. Vem depois porque, enquanto o
catálogo não muda, as histórias acima já entregam valor.

**Independent Test**: comparar o catálogo do SDK com o OpenAPI versionado do backend e com a
enumeração do backend no repositório.

**Acceptance Scenarios**:

1. **Given** o snapshot do OpenAPI, **When** o teste de contrato roda, **Then** os tipos são
   exatamente os mesmos dos dois lados, são 18, e a versão do catálogo é a mesma.
2. **Given** cada tipo, **When** o teste compara o `data`, **Then** os campos e os obrigatórios são
   os mesmos.
3. **Given** os eventos de pergunta, **When** o teste compara, **Then** são exatamente os que o
   contrato obriga a levar `questionKey`.
4. **Given** a união discriminada do contrato, **When** o teste compara, **Then** ela mapeia todos
   os tipos.
5. **Given** os tipos `placement_`, **When** o teste compara, **Then** nenhum está no catálogo do
   contrato.
6. **Given** a enumeração do backend no código, **When** o teste a lê, **Then** os nomes batem com o
   catálogo do SDK.

---

### User Story 5 - Tempos que dizem a verdade (Priority: P2)

Cada visita a uma pergunta tem os próprios tempos: total entre vista e deixada, e ativo descontando
o segundo plano. A pesquisa toda tem o tempo ativo na conclusão. Nada disso volta para trás quando
o relógio do aparelho muda.

**Why this priority**: tempo por pergunta é a métrica mais pedida da leitura de comportamento; mas
depende das histórias acima existirem.

**Independent Test**: com relógio falso, percorrer perguntas, ir e voltar do segundo plano no meio
de uma, voltar a uma pergunta já vista e conferir `visit`, `durationMs`, `activeMs` e `elapsedMs`.

**Acceptance Scenarios**:

1. **Given** uma pergunta vista e deixada, **When** `question_left` sai, **Then** `durationMs` é o
   tempo monotônico entre vista e deixada, e `activeMs` desconta o segundo plano.
2. **Given** a pessoa volta a uma pergunta, **When** ela é vista de novo, **Then** `question_viewed`
   traz `visit: 2` e `from: back`, e a nova visita tem os próprios tempos.
3. **Given** o app vai para o segundo plano com a pesquisa aberta, **When** volta, **Then** saem
   `survey_backgrounded` e `survey_foregrounded` com `backgroundMs`.
4. **Given** a pessoa sai da pergunta ainda em segundo plano, **When** `question_left` sai, **Then**
   o tempo corrido até ali conta como inativo.
5. **Given** qualquer sequência, **When** os eventos são emitidos, **Then** `elapsedMs` nunca
   diminui e é arredondado.
6. **Given** a conclusão, **When** `survey_completed` sai, **Then** traz `answeredCount`,
   `skippedCount`, `notApplicableCount` e `activeMs` da pesquisa inteira.

---

### Edge Cases

- **Valor repetido**: escolher de novo a mesma opção não emite nada.
- **Escolha em texto livre**: nunca vira evento de resposta; texto só gera `text_*` com `length`.
- **Sair da pergunta com o campo em foco**: o core emite `text_blurred` antes de navegar, mesmo que
  o campo desmonte sem `onBlur`.
- **`text_edited` pendente**: sai antes de qualquer saída da pergunta e antes da validação.
- **Dispensa antes de ficar visível**: nenhum evento; a pesquisa vira descartada.
- **Via de dispensa fora do catálogo**: vira `programmatic`.
- **Condição que pula várias perguntas**: um `question_not_applicable` por pergunta pulada, com
  `sourceKey`, entre `question_left` e o próximo `question_viewed`.
- **`next` na última aplicável**: conclui; a última opcional em branco gera `question_skipped` antes.
- **Exibição descartada na fila** (abertura recusada): os eventos dela saem junto.
- **Envio para o servidor falhando**: o `onEvent` já recebeu; o servidor recebe no reenvio.
- **Preview** (`005-sdk-preview-web`): a mesma máquina emite os mesmos eventos, só para o
  `onEvent`, sem fila.

## Requirements *(mandatory)*

### Functional Requirements

**Catálogo**

- **FR-001**: O catálogo MUST morar num único módulo tipado, com `CATALOG_VERSION = 1`, os 18 tipos
  e o `data` fechado por tipo, como união discriminada.
- **FR-002**: Todo evento MUST levar o envelope `catalogVersion`, `type`, `displayId`, `seq`,
  `occurredAt` (ISO 8601 do relógio do dispositivo), `elapsedMs` e `data`; os 13 eventos de
  pergunta MUST levar `questionKey`, e os demais MUST NOT.
- **FR-003**: `seq` MUST ser monotônico por exibição, começando em 1.
- **FR-004**: `elapsedMs` MUST ser medido no relógio monotônico desde `survey_presented`, arredondado
  e nunca decrescente.
- **FR-005**: `value` nos eventos de resposta MUST ser o `value` da opção ou o número escolhido,
  nunca o rótulo nem texto livre; texto MUST aparecer só como `length`.
- **FR-006**: O catálogo MUST exportar os tipos, os eventos de pergunta, os campos de `data` por
  tipo, as vias de dispensa, as apresentações e os detectores `isInteractionEvent` e
  `isQuestionEventType`.

**Emissão**

- **FR-007**: Os eventos MUST ser emitidos exclusivamente pela máquina de estado como consequência
  de transições.
- **FR-008**: A ordem dos eventos numa transição MUST ser fixa: em `next`, `text_edited?` →
  `question_skipped?` → `text_blurred?` → `navigated_next` → `question_left` →
  `question_not_applicable*` → `question_viewed`; em `back`, `text_edited?` → `text_blurred?` →
  `navigated_back` → `question_left` → `question_viewed`; na conclusão, `text_edited?` →
  `question_skipped?` → `text_blurred?` → `question_left` → `question_not_applicable*` →
  `survey_completed`; na dispensa, `text_edited?` → `text_blurred?` → `question_left` →
  `survey_dismissed`.
- **FR-009**: Tentar avançar ou concluir com obrigatória em branco MUST emitir só
  `validation_blocked` (depois de um `text_edited` pendente).
- **FR-010**: `answer_selected` MUST sair na primeira escolha (ou opção adicionada em múltipla
  escolha), `answer_changed` na troca de escolha com valor, e `answer_deselected` ao remover ou
  limpar.
- **FR-011**: `text_edited` MUST sair 1 s depois da última edição e antes de qualquer saída da
  pergunta; sair com o campo em foco MUST emitir `text_blurred`.
- **FR-012**: `question_left` MUST trazer `visit`, `to` (`next`, `back`, `dismiss`, `complete`),
  `durationMs`, `activeMs` e `answered`, com tempos próprios por visita.
- **FR-013**: `survey_backgrounded` e `survey_foregrounded` MUST acompanhar o `AppState` com a
  pesquisa aberta, e `activeMs` MUST descontar o segundo plano.
- **FR-014**: `survey_dismissed` MUST trazer a via (fora do catálogo vira `programmatic`), a
  `position` da pergunta atual e `answeredCount`.

**Entrega ao servidor**

- **FR-015**: Os eventos MUST entrar na fila persistente pelo par (`displayId`, `seq`), sem repetir.
- **FR-016**: Os eventos MUST ser enviados a `POST /collect/displays/{displayId}/events` só depois
  de a abertura daquela exibição ser entregue.
- **FR-017**: Durante a resposta, os eventos MUST esperar até 5 s para sair em lote; abertura,
  desfecho e segundo plano MUST mandar na hora.
- **FR-018**: Os lotes MUST ter até 100 eventos, em ordem de `seq`; cada exibição MUST ter até 500
  eventos na fila.
- **FR-019**: Qualquer `202` MUST remover os eventos enviados da fila, inclusive com corpo
  ilegível.

**Entrega ao app**

- **FR-020**: O `onEvent` do Provider MUST receber cada evento do catálogo, o mesmo que vai para a
  fila, e pode ser trocado sem recriar o runtime.
- **FR-021**: Erro lançado pelo `onEvent` MUST ser engolido, avisado uma vez em desenvolvimento e
  MUST NOT ser reportado como falha do SDK.
- **FR-022**: O SDK MUST NOT oferecer API pública para emitir evento.

**Contrato**

- **FR-023**: Um teste de contrato MUST comparar o catálogo com o OpenAPI versionado do backend:
  tipos, versão, campos e obrigatórios de cada `data`, eventos com `questionKey` e mapeamento da
  união discriminada, e MUST garantir que nenhum `placement_` está no contrato.
- **FR-024**: Um teste MUST comparar os nomes do catálogo com a enumeração do backend no
  repositório.
- **FR-025**: Um teste MUST provar que a UI padrão, a UI com renderizador substituído e uma UI
  headless produzem a mesma sequência que a máquina pura para os roteiros completo e de dispensa.

### Key Entities

- **Evento de interação**: um fato do catálogo numa exibição — envelope e `data` fechado por tipo.
- **Catálogo**: o conjunto versionado dos 18 tipos e da forma de cada `data`.
- **Visita**: uma passagem por uma pergunta, numerada, com tempo total e ativo.
- **Lote**: até 100 eventos de uma exibição enviados juntos.
- **Assinatura**: a sequência de (tipo, pergunta, `data`) sem os tempos, usada para comparar UIs.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Para o mesmo roteiro, 100% das UIs testadas (padrão, substituída, headless e headless
  sobre o Provider real) produzem assinatura idêntica à da máquina.
- **SC-002**: Os roteiros de referência cobrem os 18 tipos do catálogo.
- **SC-003**: Cada evento chega ao servidor no máximo uma vez, mesmo com reenvio.
- **SC-004**: Nenhum evento enviado ou entregue ao app contém texto digitado ou rótulo de opção.
- **SC-005**: Enquanto a pessoa responde, nenhuma interação gera chamada de rede própria: os
  eventos saem agrupados num envio agendado 5 s depois do primeiro pendente, além dos envios
  imediatos de abertura, desfecho e segundo plano.
- **SC-006**: Qualquer divergência de tipo, versão ou campo entre o SDK e o contrato do backend faz
  o portão de qualidade falhar.

## Assumptions

- **O backend aceita a versão 1 e descarta o desconhecido.** A ingestão, a idempotência por
  (`displayId`, `seq`), o teto de 500 e a janela de 7 dias são da `backend 013-interaction-events`.
- **A fila é a mesma das respostas.** Eventos herdam o backoff, a pausa por `429`, a idade máxima
  de 3 dias e o descarte da `001-sdk-core`.
- **O app recebe o mesmo objeto, só para leitura.** O SDK não faz cópia defensiva: a garantia de
  "só leitura" é do tipo (`readonly`), não de congelamento em runtime, e um app que mute o objeto
  dentro do `onEvent` está fora do contrato.
- **O teto de 120 requisições por minuto por origem** justifica o lote de 5 s.

## Dependencies

- `001-sdk-core`: máquina de estado, sessão, fila, runtime e `onEvent`.
- `002-sdk-ui-apresentacoes`: a UI padrão e a substituição de renderizador usadas no teste de
  mesma sequência.
- `backend 013-interaction-events`: rota `POST /collect/displays/{displayId}/events`, catálogo no
  OpenAPI (`InteractionEventType` com `x-pitaco-catalog-version`, `InteractionEvent` discriminado,
  `<Tipo>Data` fechados) e enumeração `InteractionEventType`.
- Consumidores: `004-sdk-controle-onde-quando` (mesmo `onEvent`), `005-sdk-preview-web` e
  `006-sdk-app-exemplo` (painel de depuração).
