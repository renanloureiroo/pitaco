# Research: SDK React Native — eventos de interação

**Feature**: `003-sdk-eventos-interacao` · **Date**: 2026-09-13 · **Spec**: [spec.md](./spec.md)

---

## D-01 — Um módulo tipado só para o catálogo

**Decision**: `src/catalog/events.ts` declara `CATALOG_VERSION = 1`, `INTERACTION_EVENT_TYPES`
(18), `QUESTION_EVENT_TYPES` (13), `InteractionEventDataMap`, `INTERACTION_EVENT_DATA_FIELDS`,
`DISMISS_VIAS`, `PRESENTATIONS`, a união `InteractionEvent` (alias público `PitacoEvent`) e os
detectores. É o único lugar do SDK que define tipos de evento.

**Rationale**: é o que o app, o painel de depuração e os testes importam; mudar ali é mudança de
contrato, visível num diff só.

**Alternatives considered**: tipos espalhados pela máquina (sem fonte única); gerar o catálogo do
OpenAPI (o gerador não preserva a relação entre `type` e `data` como união discriminada utilizável
— o teste compara os dois em vez disso).

---

## D-02 — O envelope é montado num ponto só

**Decision**: `Run.emit(type, data, questionKey?)` incrementa `seq`, calcula
`elapsedMs = max(último, round(mono − presentedAt), 0)` e monta `{ catalogVersion, type, displayId,
seq, occurredAt, elapsedMs, questionKey?, data }`.

**Rationale**: `seq` monotônico é a chave de idempotência; `elapsedMs` nunca diminui, mesmo que o
relógio monotônico de alguma plataforma tropece.

**Alternatives considered**: `occurredAt` como base dos tempos (relógio de parede ajustável).

---

## D-03 — Ordem fixa por transição

**Decision**: a ordem dos eventos de `next`, `back`, conclusão e dispensa é a documentada no topo de
`machine.ts` e coberta por teste. `next` na última aplicável conclui; `complete()` antes dela é
ignorado; obrigatória em branco emite só `validation_blocked`.

**Rationale**: a leitura de comportamento depende de saber, por exemplo, que `question_skipped`
vem antes de `navigated_next`, e que `question_not_applicable` fica entre `question_left` e o
próximo `question_viewed`.

**Alternatives considered**: ordem livre (cada UI poderia produzir outra sequência).

---

## D-04 — O core fecha o foco e o texto pendente

**Decision**: sair de uma pergunta com o campo em foco emite `text_blurred` pelo core; um
`text_edited` pendente sai antes de qualquer saída da pergunta. O debounce de 1 s é agendado pela
sessão com `tick`, mas é a máquina quem decide se venceu, pelo relógio.

**Rationale**: um `TextInput` que desmonta pode nunca chamar `onBlur`; sem isso, UIs diferentes
dariam sequências diferentes.

**Alternatives considered**: depender do `onBlur` da UI (quebra a mesma sequência).

---

## D-05 — Tempos por visita no relógio monotônico

**Decision**: cada `question_viewed` abre uma visita numerada; `question_left` traz `durationMs`
(monotônico entre vista e deixada) e `activeMs` (descontando o segundo plano da visita, inclusive o
que ainda está em curso). `survey_completed.activeMs` desconta todo o segundo plano desde a
apresentação. `survey_foregrounded.backgroundMs` traz a duração do segundo plano.

**Rationale**: o painel soma as visitas; tempo ativo é o que separa "pensou na pergunta" de "deixou
o app no fundo".

**Alternatives considered**: um tempo só por pergunta (perde a volta).

---

## D-06 — Mesma origem para a fila e para o `onEvent`

**Decision**: em `onTransition`, o runtime enfileira (na cadeia serializada) a abertura, os eventos
e o envio, e entrega os mesmos eventos ao `onEvent`, na mesma ordem.

**Rationale**: o que o app vê é o que o servidor recebe; o teste "os eventos que o servidor recebe
são os mesmos que o onEvent recebe" prova.

**Alternatives considered**: o app ouvir o que voltou do servidor (perderia os eventos sem rede).

---

## D-07 — Envio em lote com espera de 5 s

**Decision**: durante a resposta, o runtime agenda um único envio 5 s depois (`scheduleFlush`);
abertura (`survey_presented`), desfecho e ida para o segundo plano cancelam a espera e mandam na
hora. A fila guarda um item `events` por exibição, deduplicado por `seq`, com teto de 500, e envia
lotes de 100 em ordem de `seq`.

**Rationale**: o limite por origem é de 120 requisições por minuto; um evento por requisição o
esgotaria numa pesquisa de cinco perguntas com texto. O teto de 500 é o do servidor.

**Alternatives considered**: um envio por evento (estoura o limite); só no desfecho (perde o
caminho de quem fecha o app no meio).

---

## D-08 — Teste de contrato contra o OpenAPI versionado

**Decision**: `src/catalog/__tests__/catalog.contract.test.ts` lê `openapi/pitaco.json` e confere:
tipos iguais dos dois lados (18), `x-pitaco-catalog-version` igual a `CATALOG_VERSION`, campos e
obrigatórios de cada `<Tipo>Data`, `questionKey` obrigatório exatamente nos 13 de pergunta,
`discriminator.mapping` da união com todos os tipos, e nenhum `placement_` no contrato. Também
falha no `typecheck` se o tipo gerado divergir (`Same<...>`).

**Rationale**: o snapshot é o contrato que o SDK compila; o teste roda sem backend no ar.

**Alternatives considered**: comparar com o backend vivo (exigiria subir o Spring no CI).

---

## D-09 — Reforço contra a enumeração do backend

**Decision**: `src/catalog/__tests__/contract.test.ts` lê
`backend/src/main/java/com/renanloureiroo/pitaco/core/catalog/InteractionEventType.java` do
monorepo, extrai os nomes da enumeração e compara com `INTERACTION_EVENT_TYPES`.

**Rationale**: pega o caso em que o backend mudou e o snapshot não foi regenerado, sem custo de
subir a API.

**Alternatives considered**: só o snapshot (passaria verde com snapshot velho).

---

## D-10 — Mesma sequência provada contra a máquina pura

**Decision**: `src/ui/content/__tests__/sameSequence.contract.test.tsx` roda `STANDARD_SCRIPT` e
`DISMISS_SCRIPT` sobre a UI padrão, uma UI com NPS e escolha única substituídos (a escolha única
própria alterna `select`/`deselect` no mesmo toque) e uma UI headless mínima, todas sobre
`PitacoPreviewProvider`, e compara a `signature()` (`seq`, tipo, pergunta e `data`, sem tempos) com a da
máquina. Passos de `AppState` ficam de fora ali e são cobertos por `src/react/__tests__/hooks.test.tsx`,
que roda os dois roteiros headless sobre o `PitacoProvider` real. O teste confere ainda que nenhum
`fetch` foi chamado.

**Rationale**: o preview é determinístico (sem animação de entrada); o Provider real cobre o que só
ele tem. Juntos, cobrem padrão, substituída, headless e headless com app de verdade.

**Alternatives considered**: o teste sobre o Provider real com sheet animado (mais lento e sem
sinal novo).
