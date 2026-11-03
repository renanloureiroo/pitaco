# Research: Aplicação — cadastro, edição, ativação e eventos observados

**Feature**: `007-application-management` · **Date**: 2026-09-05 · **Spec**: [spec.md](./spec.md)

---

## D-01 — O slug sai do nome, mas é validado como se tivesse sido digitado (FR-002, FR-003)

**Decision**: `Slug.from(text)` normaliza o texto (NFD sem diacríticos, minúsculas, qualquer não
alfanumérico vira hífen, hífen nas pontas some, corte em 50) e passa o resultado pelo mesmo
construtor de `Slug.of`. Texto sem nada aproveitável é recusado com `application.slug_invalid`.

**Rationale**: um `Slug` em mãos é sempre válido, venha de onde vier. A derivação recebe `String`,
não `Name`, porque é a mesma para qualquer rótulo legível.

**Alternatives considered**: transliteração completa (ideogramas para latim) — dependência nova e
resultado imprevisível; gerar sufixo aleatório na colisão — esconderia o conflito de quem cadastra.

---

## D-02 — As invariantes dos prazos moram na entidade (FR-006, FR-013, FR-015)

**Decision**: `Application` valida cada prazo num setter privado: menor que um dia é
`ErrorType.VALIDATION` (400); texto livre acima do geral, ou geral abaixo do texto livre já
definido, é `ErrorType.BUSINESS_RULE` (422). Os códigos são `application.quiet_period_invalid`,
`application.retention_invalid` e `application.open_text_retention_invalid`.

**Rationale**: a mesma regra vale no cadastro (`Application.create`) e na edição (`define*`), e o
domínio que se valida não depende de o caso de uso lembrar de checar. A violação lança antes de
`touch()`, então edição recusada não mexe no `updatedAt`.

**Alternatives considered**: validar a relação entre os prazos no DTO — a mensagem de 400 apareceria
para uma regra que não é de formato, e o caminho de cadastro e o de edição precisariam de duas
cópias.

---

## D-03 — PATCH de três estados com `Patch<T>` e deserializer próprio (FR-009, FR-010, FR-011)

**Decision**: o `Input` de `UpdateApplicationUseCase` carrega `Optional<String> name` e
`Patch<Integer>` para cada prazo. `UpdateApplicationRequestDeserializer` lê a árvore JSON: campo
ausente vira referência nula (→ `Patch.absent()`), `null` vira `Optional.empty()` (→
`Patch.clear()`), valor vira `Optional.of` (→ `Patch.set`). `name: null` e prazo com tipo errado
são `reportInputMismatch` (400 de corpo malformado).

**Rationale**: o Jackson colapsa ausente e nulo em `Optional` vazio; ler a árvore é o jeito de
enxergar a diferença sem inventar um tipo de campo próprio. As constraints (`@NotBlank`,
`@Size`, `@Positive`) ficam nos argumentos de tipo do `Optional` e continuam sendo validadas.

**Alternatives considered**: `JsonNullable` — dependência nova; PUT completo — obrigaria o painel
a reenviar tudo e transformaria omissão em remoção acidental.

---

## D-04 — Retenção geral antes da de texto livre (FR-014)

**Decision**: o caso de uso aplica, nesta ordem, nome, descanso, retenção geral e retenção de texto
livre.

**Rationale**: o texto livre é validado contra o geral. Subir os dois juntos (30/7 → 90/60) só
funciona se o geral subir primeiro; na ordem inversa, 60 seria recusado contra 30.

**Alternatives considered**: validar a relação só no fim — exigiria um estado intermediário
inválido dentro da entidade.

---

## D-05 — Ativação idempotente, sem erro (FR-018, FR-019)

**Decision**: `DeactivateApplicationUseCase` com a aplicação já inativa (e
`ActivateApplicationUseCase` com ela já ativa) devolve `ApplicationOutput.of(application)` sem
chamar `update`. As rotas são `POST …/deactivate` e `POST …/activate`.

**Rationale**: o painel pode repetir o comando por duplo clique ou nova tentativa de rede; nenhum
dos dois é erro de quem chama. Não gravar mantém o `updatedAt` honesto.

**Alternatives considered**: `PATCH` com `status` — misturaria transição de estado com edição de
dado, e a transição tem efeito na superfície pública que merece rota própria; 409 na repetição —
erro sem ação corretiva para o cliente.

---

## D-06 — O silêncio da aplicação inativa já está em cada caso de uso da coleta (FR-021)

**Decision**: nenhum filtro HTTP novo. A chave continua autenticando (o `AuthenticatedApplication`
carrega `active`), e cada caso de uso de `collect` consulta `ApplicationScopeGateway.stateOf`:
elegibilidade devolve vazio, abertura e envio lançam `ApplicationIsInactive`
(`application.inactive`, 422), supressão/erro do SDK/eventos de interação descartam com 202.

**Rationale**: herdado do desenho da `004`: aplicação inativa é silêncio, não erro, onde o SDK não
tem nada a fazer com a recusa; e é recusa explícita onde a fila do SDK precisa parar de reenviar.

**Alternatives considered**: recusar a chave de aplicação inativa com 401 — o SDK trataria como
chave errada e o integrador investigaria o problema errado.

---

## D-07 — Catálogo de eventos, não log (FR-023, FR-024)

**Decision**: tabela `application_events` com uma linha por `(application_id, name)`,
`first_seen_at` e `last_seen_at`; entidade `ObservedEvent` em `collect/domain/entities`, que só
avança `lastSeenAt` (`seenAt` ignora instante mais antigo).

**Rationale**: a autoria precisa de "quais eventos existem e quando foram vistos", não de quantas
vezes. Um log cresceria com o tráfego do app hospedeiro sem leitor.

**Alternatives considered**: contar ocorrências — escrita por evento no caminho mais quente da API;
derivar o catálogo de `survey_displays` — só veria eventos que já dispararam pesquisa, justamente
os que menos importam.

---

## D-08 — Upsert amortizado num comando só (FR-025)

**Decision**: `ApplicationEventJpaRepository.record` executa `insert … on conflict (application_id,
name) do update set last_seen_at = excluded.last_seen_at where application_events.last_seen_at <
:threshold`, com `threshold = now - 5 minutos` (`ObservedEventRepositoryJpa.REFRESH_INTERVAL`).

**Rationale**: sem `select` antes, a chave única resolve a corrida entre duas primeiras
ocorrências; o `where` faz a linha ser tocada no máximo uma vez a cada cinco minutos por evento.
"Visto nos últimos minutos" é tudo que a autoria precisa.

**Alternatives considered**: `select` + `save` — duas idas ao banco e corrida na primeira
ocorrência; buffer em memória — perderia a primeira ocorrência num reinício, que é justamente a
que a autoria procura.

---

## D-09 — Falha no catálogo vira aviso, em transação própria (FR-026, FR-027)

**Decision**: `FindEligibleSurveyUseCase.observe` roda a gravação via
`Transactor.runInTransaction` dentro de `safely(...)`, que captura `RuntimeException` e loga
`warn` com aplicação e classe da falha. A gravação acontece depois do teste de aplicação ativa e
antes da busca de candidatas.

**Rationale**: a decisão de entrega não depende do catálogo. A transação própria impede que a falha
de um catálogo desfaça outro (o de atributos chega na `008`).

**Alternatives considered**: gravar dentro da transação da elegibilidade — a elegibilidade não tem
transação (só lê), e criar uma para ela acoplaria a entrega à saúde do catálogo.
