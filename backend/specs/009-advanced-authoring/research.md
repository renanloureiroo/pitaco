# Research: Autoria avançada — modelos, condição, duplicação e publicação consciente

**Feature**: `009-advanced-authoring` · **Date**: 2026-09-12 · **Spec**: [spec.md](./spec.md)

---

## D-01 — O modelo é o formato como definido, não uma aproximação (FR-001 a FR-003)

**Decision**: `TemplateQuestions.draftsFor(template)` devolve uma pergunta obrigatória: NPS
(Reichheld, 2003) 0–10 "a um amigo ou colega"; CSAT avaliação 1–5; CES 2.0 (Gartner/CEB) afirmação
de facilidade com concordância 1–7. "Este app" no lugar do nome do produto. A pesquisa guarda o
modelo em `surveys.template_kind` (`check in ('NPS','CSAT','CES')`); o disparo fica vazio.

**Rationale**: a comparação com referências de mercado só vale com a pergunta e a escala oficiais.
O modelo é ponto de partida; registrá-lo deixa os resultados destacarem o NPS sem adivinhar.

**Alternatives considered**: modelo como pesquisa-semente no banco — dado de produto misturado a
dado de cliente; travar a pergunta de modelo — o autor não poderia traduzir nem ajustar o texto.

---

## D-02 — Forma da condição separada da coerência com a versão (FR-005 a FR-008, FR-010)

**Decision**: `DisplayCondition` valida o que não depende das outras perguntas (cardinalidade por
operador, valores 1..120 caracteres sem repetição, até 50 no `in`, faixa ordenada).
`QuestionConditions.check` valida contra a versão: origem existe, vem antes, não é texto livre;
em escolha, valores ⊂ opções e sem `between`; em numérica, inteiros dentro da escala e faixa contida
nela. Toda recusa é `ConditionRejected` (BUSINESS_RULE, 422) com códigos
`question.condition_source_invalid`, `question.condition_operator_invalid`,
`question.condition_value_invalid`, e extensão `field` (`condition.sourceKey`,
`condition.operator`, `condition.values`).

**Rationale**: só a versão conhece as outras perguntas. Recusar na escrita é o que torna impossível
uma condição que nunca poderia ser satisfeita.

**Alternatives considered**: aceitar e listar como impedimento de publicação — o autor descobriria
o erro longe de onde o cometeu.

---

## D-03 — Mudança na origem não quebra dependente em silêncio (FR-009)

**Decision**: `requireDependentsStillValid` (alteração da origem), `requireNoDependents`
(remoção) e `requireSourcesFirst` (reordenação) lançam `question.condition_source_in_use` ou
`question.condition_order_invalid` com `questionKey` da dependente nas extensões.

**Rationale**: o autor decide se ajusta a condição ou desiste da mudança. A seta sempre para trás
torna ciclo impossível por construção.

**Alternatives considered**: remover a condição da dependente automaticamente — mudaria quem vê a
pergunta sem o autor saber.

---

## D-04 — A condição aponta pela chave estável (FR-013)

**Decision**: `questions.condition_source_key` (com `condition_operator`, `condition_min`,
`condition_max`) e `question_condition_values (question_id, position, value)`. FK composta
`fk_questions_condition_source (version_id, condition_source_key) → questions (version_id,
question_key)` `deferrable initially deferred`.

**Rationale**: a chave atravessa versões e é única dentro de uma, então a condição copiada para a
versão seguinte continua valendo sem reescrita. Valores em tabela filha, como as opções: forma
fixa, ordem preservada.

**Alternatives considered**: apontar pelo id da linha — toda versão nova reescreveria as condições;
`jsonb` — perde a FK e a forma fixa.

---

## D-05 — Duplicar é copiar o que está valendo, sem histórico (FR-014 a FR-018)

**Decision**: `DuplicateSurveyUseCase` (`@Transactional`) resolve a aplicação de destino ativa
(`SurveyScope.activeApplicationIdOf`), pega a versão publicada ou o rascunho de quem nunca
publicou, e grava `Survey.duplicateInto(destino, nome)` (rascunho, versão 1, mesma exposição,
modelo e aviso) e `SurveyVersion.duplicateFor(novaPesquisa, agora)` (chaves e identidades novas,
condição reapontada para a chave nova, regras com identidade nova, janela encerrada vira aberta a
partir de agora). Nome padrão por `SurveyName.copy()`, cortado em 120.

**Rationale**: a cópia não é comparável com a original — chaves novas evitam que alguém some
respostas das duas como se fossem a mesma pergunta. Janela encerrada copiada como está nasceria
inútil.

**Alternatives considered**: copiar o rascunho aberto — levaria para outra aplicação algo que
ninguém validou; manter as chaves — misturaria linhagens.

---

## D-06 — Aviso de texto livre é da pesquisa (FR-021 a FR-023)

**Decision**: `FreeTextNotice(enabled, Optional<String> customText)`, padrão ligado com
`DEFAULT_TEXT`; texto próprio 1..200 após `strip` (`survey.free_text_notice_invalid`). Colunas
`surveys.free_text_notice_enabled` (default `true`) e `free_text_notice_text`. O `PATCH` da
pesquisa trata `freeTextNoticeText: null` como "volta ao padrão" (`Patch.clear`) e
`freeTextNoticeEnabled: null` como corpo malformado. A entrega ao SDK leva `freeTextNotice
{enabled, text}`.

**Rationale**: mudar o aviso não muda o que se pergunta; aviso curto e humano é lido, aviso
jurídico assusta.

**Alternatives considered**: aviso por pergunta — granularidade sem caso de uso; desligado por
padrão — a proteção dependeria de alguém lembrar de ligá-la.

---

## D-07 — Classificação enxerga o que dá para enxergar (FR-024 a FR-028)

**Decision**: `ChangeClassification.between(anterior, atual)` por chave estável:
`QUESTION_ADDED`, `QUESTION_REMOVED`, `TYPE_CHANGED`, `RANGE_CHANGED` (só se o tipo não mudou),
`OPTIONS_CHANGED` (conjunto de valores, não lista), `CONDITION_CHANGED` (impressão digital que
ignora a ordem dos valores). Enunciado, rótulo, obrigatoriedade e ordem não entram.
`PublishSurveyUseCase` recusa rascunho idêntico (`survey_version.no_changes`) antes de classificar
e, na declaração cosmética com diferenças, lança `CosmeticDeclarationRefused`
(`survey_version.cosmetic_refused`) com `differences[{questionKey, difference}]`. Declaração
ausente da v2 em diante é `survey_version.change_kind_required`.

**Rationale**: o sistema não julga se um texto mudou de sentido; verifica o que é estrutural. A
condição muda quem vê a pergunta, e somar públicos diferentes engana tanto quanto somar opções
diferentes.

**Alternatives considered**: comparar enunciados por similaridade — falso positivo e falso negativo
num lugar onde o erro custa a comparabilidade.

---

## D-08 — Avisos sem poder de veto (FR-030, FR-031)

**Decision**: `GET …/publication-warnings` lê o rascunho (ou a publicada) e reúne:
`segmentation.no_known_match` (regra sobre atributo nunca visto, ou igualdade a valor nunca visto
em catálogo não saturado; `ABSENT` nunca é avisada), `segmentation.contradictory` (dois valores
exigidos, exigir e proibir o mesmo, presença e ausência), `trigger.competing_surveys` (pesquisas
no ar ou agendadas no mesmo evento, com nome e prioridade, excluindo a própria) e
`compatibility.unsupported_by_majority` (versões que suportam a versão mínima não são maioria
estrita do tráfego recente; sem tráfego, nada). A versão mínima vem de
`SurveyVersion.requiredSdkVersion(freeTextNoticeEnabled)` sobre `SdkCapabilities`.

**Rationale**: transformar uma escolha invisível em consciente sem impedir o autor que sabe o que
está fazendo.

**Alternatives considered**: transformar concorrência em impedimento — duas pesquisas no mesmo
evento são legítimas, a prioridade decide.
