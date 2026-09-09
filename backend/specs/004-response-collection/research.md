# Research — 004-response-collection

Fase 0. Cada item traz a decisão, o motivo e o que foi descartado. Onde a spec deixou algo em
aberto (marcado como **resolve NEEDS CLARIFICATION**), a decisão fecha a lacuna.

---

## D-01 — Módulo novo `collect`, e não extensão de `survey`

**Decisão**: criar `modules/collect` com três agregados próprios (respondente, sessão, resposta).
A versão publicada é lida por uma porta `PublishedSurveyCatalog` declarada em
`collect/application/gateways`, com adaptador em `collect/infra/gateways` atravessando até
`modules/survey`.

**Rationale**: autoria e coleta têm consumidores diferentes (painel × SDK), ritmos de escrita
opostos (baixa × alta) e nenhuma entidade em comum. Colocar sessão e resposta dentro de `survey`
faria o agregado da autoria carregar o volume da coleta e apagaria a fronteira que a própria
spec desenha em quatro linhas.

**Alternativas consideradas**: (a) estender `modules/survey` — rejeitada pelo acima; (b) um
módulo por agregado (`respondent`, `session`, `answer`) — rejeitada porque os três só existem
juntos: uma resposta sem sessão e uma sessão sem respondente não têm sentido isolado.

---

## D-02 — Autenticação por interceptor próprio, chave no header `X-Pitaco-Key`

**Decisão**: um `HandlerInterceptor` registrado apenas em `/collect/**` extrai o header
`X-Pitaco-Key`, calcula `ApiKeySecret.hashOf(...)`, chama `AuthenticateApiKeyUseCase` de
`modules/app` e guarda o `ApplicationId` como **atributo da requisição**. Um
`HandlerMethodArgumentResolver` injeta `AuthenticatedApplication` no controller. Sem Spring
Security, sem `ThreadLocal`.

**Rationale**: a regra é uma linha de negócio ("hash → aplicação ativa"); o resto é encanamento.
Atributo de requisição em vez de `ThreadLocal` é o que o Princípio V exige com virtual threads.
O `applicationId` nunca aparece na rota pública, o que satisfaz FR-002 por construção — não há
parâmetro para o chamador forjar.

**Alternativas consideradas**: (a) Spring Security — dependência nova, mais configuração do que
regra, `SecurityContextHolder` em `ThreadLocal`; (b) `OncePerRequestFilter` — funciona, mas o
interceptor conhece o handler e permite ligar/desligar por rota sem duplicar o mapa de paths;
(c) receber a chave como parâmetro do controller e validar no caso de uso — cada endpoint
repetiria a validação, e esquecer um é o tipo de falha que não aparece em teste feliz.

**Header**: `X-Pitaco-Key`, com o valor exatamente como emitido (`pit_<prefixo>_<segredo>`).
Descartado `Authorization: Bearer` para não sugerir OAuth nem se confundir com a autenticação de
usuário do painel, que virá depois.

---

## D-03 — A separação entre superfície pública e administrativa (FR-003, cenário US1.14)

**Decisão**: as duas superfícies se separam por prefixo de rota — `/collect/**` é pública,
todo o resto é administrativo. Um segundo interceptor, registrado nas rotas administrativas,
**recusa com 403 `api_key.forbidden_surface` qualquer requisição que apresente o header
`X-Pitaco-Key`**.

**Rationale**: as rotas administrativas ainda não têm autenticação (premissa herdada da autoria),
então "recusar a chave pública" não pode significar "não autorizar" — não há nada a autorizar.
Recusar explicitamente a *apresentação* da chave é o que torna o cenário verificável hoje e é a
conduta correta amanhã: uma chave de SDK nunca deve valer no painel, e falhar barulhento é
melhor do que ignorar em silêncio.

**Alternativas consideradas**: ignorar o header nas rotas administrativas — rejeitada porque
deixaria o cenário US1.14 sem teste possível e ensinaria o cliente errado a funcionar.

---

## D-04 — Elegibilidade em duas consultas, mais uma de histórico

**Decisão**: a ordem da FR-011 é implementada assim:

| Camada | Onde é aplicada |
| --- | --- |
| 1. aplicação ativa | no caso de uso, sobre `ApplicationScopeGateway` (o interceptor já resolveu a aplicação) |
| 2. pesquisa no ar · 3. janela · 4. evento | **na consulta de candidatos** (SQL, uma consulta indexada) |
| 5. histórico do respondente | uma consulta de histórico para **todos** os candidatos de uma vez |
| 6. regras de segmentação · 7. sorteio | em memória, Java puro, sem I/O |

Só depois de sobrar exatamente uma pesquisa é que o conteúdo é buscado — a terceira consulta.

**Rationale**: o caminho mais frequente (nenhuma pesquisa escuta o evento) termina na primeira
consulta, com zero linhas e zero escritas, o que atende SC-003 literalmente. Empurrar as camadas
2–4 para o banco evita trazer pesquisas que serão descartadas; manter 5–7 fora do banco mantém a
ordem da FR-011 explícita e testável em JUnit puro, sem Testcontainers.

**Alternativas consideradas**: (a) tudo em uma consulta SQL, sorteio incluído — a ordem das
camadas viraria detalhe de query e a FR-011 deixaria de ser testável isoladamente; (b) buscar o
conteúdo de todos os candidatos e escolher depois — carrega N versões inteiras para descartar
N−1.

---

## D-05 — Amostragem determinística sem estado (FR-017, SC-005)

**Decisão**: `SamplingDecision.accepts(surveyId, respondentIdentity, rate)` calcula
`SHA-256(surveyId.value() + ":" + identity.value())`, toma os 8 primeiros bytes como inteiro sem
sinal de 63 bits e compara `valor / 2^63 < rate`. Nada é guardado.

**Rationale**: reprodutível por definição — a mesma entrada dá a mesma saída em qualquer
instância, hoje e depois de um redeploy —, satisfaz "cinco vezes o mesmo evento, cinco vezes não
sorteado" (US1.8) sem uma tabela de sorteios, e o custo é um hash por candidato. `rate = 0`
recusa todos (`x < 0` é sempre falso) e `rate = 1` aceita todos (`x < 1` é sempre verdadeiro),
cobrindo os dois extremos sem caso especial.

**Chave do sorteio**: o par **pesquisa–respondente**, não versão–respondente. Republicar não
deve re-sortear quem já tinha sido excluído; quem reabre a elegibilidade é o grupo de
comparabilidade (D-08), não o sorteio.

**Alternativas consideradas**: (a) `Random` com semente na sessão — não sobrevive a reinício nem
a duas instâncias; (b) coluna `sampled` por par — uma escrita no caminho vazio, exatamente o que
SC-003 proíbe; (c) `hashCode()` do Java — não é estável entre versões da JVM para `String`
composta, e não tem distribuição uniforme garantida.

---

## D-06 — Desempate determinístico entre pesquisas elegíveis (FR-018)

**Decisão**: entre as sobreviventes, vence a de **`published_at` mais antigo**; empate técnico é
desfeito pelo **`surveyId` em ordem lexicográfica**. A regra é documentada no OpenAPI e no
`data-model.md`.

**Rationale**: a pesquisa que já estava no ar continua sendo entregue quando outra entra —
estabilidade para quem já está coletando, e nenhum efeito de "a nova rouba a amostra da antiga".
O `surveyId` como segundo critério fecha o caso de duas publicações no mesmo instante.

**Alternativas consideradas**: (a) a mais recente — inverte a instabilidade para o lado ruim;
(b) sorteio entre elas — quebraria "a mesma consulta repetida devolve a mesma" (US1.13) sem
guardar estado; (c) prioridade declarada — é explicitamente da feature de controle de exposição
(FR-040).

---

## D-07 — Submissão única e atômica, respostas e desfecho juntos

**Decisão**: um endpoint `POST /collect/sessions/{sessionId}/submission` recebe
`{ outcome, answers[] }`. `outcome` é obrigatório (`COMPLETED` ou `DISMISSED`). Tudo é validado
antes de qualquer gravação; a recusa lista todos os problemas; nada é gravado numa recusa.

**Rationale**: é o que reconcilia os cenários da spec. US2.3 exige que um envio sem a pergunta
obrigatória seja recusado **e nada seja gravado** — o que só é possível se a checagem de
obrigatoriedade e a gravação das respostas estiverem no mesmo ato. Também é o formato natural
para a fila local do SDK, que guarda um pacote e o reenvia inteiro.

**Alternativas consideradas**: endpoints separados de respostas e de desfecho — rejeitada porque
a checagem de obrigatoriedade (FR-032) cairia no desfecho, com as respostas já gravadas, e US2.3
deixaria de valer. Ficou registrado para a feature de resultados que respostas incrementais
(salvar pergunta a pergunta) exigiriam revisitar esta decisão.

---

## D-08 — Idempotência, e o que fazer com o reenvio (FR-023, FR-036, US2.2, US3.2, US3.3)

**Decisão**:

- O identificador de sessão gerado no dispositivo **é a chave primária** de `survey_sessions`,
  validado como UUID. Abertura repetida com o mesmo id devolve a sessão existente (200 em vez de
  201) quando pesquisa e versão coincidem, e recusa com `409 session.identifier_conflict` quando
  não coincidem.
- Na submissão, **a primeira gravação vence**. Se a sessão já está fechada:
  - mesmo desfecho e nenhuma resposta nova → 204, sem escrever (reenvio da fila local);
  - desfecho diferente, ou resposta para pergunta ainda não gravada → `409
    session.already_closed`.
- Unicidade `(session_id, question_key)` no banco garante uma resposta por pergunta por sessão.

**Rationale**: satisfaz US2.2 (mesmo pacote duas vezes → uma sessão, uma resposta por pergunta),
US3.2 (mais respostas depois da dispensa → recusado) e US3.3 (concluída não vira dispensada) sem
inventar um estado intermediário. "A primeira gravação vence" evita reescrever dado coletado com
base em uma retentativa possivelmente mais velha que a original.

**Alternativas consideradas**: (a) upsert com o último envio vencendo — uma retentativa atrasada
sobrescreveria dado bom; (b) tabela de idempotência separada com o hash do payload — mais uma
escrita no caminho quente para expressar o que a chave primária já expressa.

---

## D-09 — Identidade do respondente sem fusão de históricos (FR-005, US5.3)

**Decisão**: `RespondentIdentity` é um par (`kind`, `value`), onde `kind` é `APP_REFERENCE` ou
`DEVICE` e a referência do app prevalece quando presente. A unicidade é
`(application_id, kind, value)`. Quando um respondente identificado por dispositivo passa a
informar a referência do app, **um respondente novo passa a valer dali em diante** — os
históricos não são fundidos.

**Rationale**: fundir exigiria decidir o que acontece quando dois dispositivos apontam para a
mesma referência (e vice-versa), guardaria um vínculo entre dispositivo e referência do app — que
é exatamente o tipo de correlação que FR-008 quer evitar — e nenhum cenário da spec pede a fusão.
O texto de US5.3 diz "a referência do app prevalece **a partir dali**", que é o que esta decisão
implementa literalmente.

**Alternativas consideradas**: fusão por reescrita das sessões antigas — retenção de vínculo
pessoal, migração de linhas no caminho quente e nenhum requisito pedindo.

---

## D-10 — Quando o respondente é criado (tensão entre FR-007 e FR-020) · **resolve NEEDS CLARIFICATION**

**Decisão**: a consulta de elegibilidade **nunca escreve em tabela de coleta** — nem cria
respondente, nem atualiza `last_seen_at`. O respondente é criado (ou tem `last_seen_at`
atualizado) na **abertura da sessão**. "Visto", para efeito de FR-007, é "recebeu uma pesquisa e
ela foi exibida".

A única gravação que o caminho vazio pode disparar está antes dele, na autenticação: o
`api_keys.last_used_at` amortizado de D-17, que atualiza uma linha já existente no máximo uma vez
por minuto por chave. Nenhuma linha é criada, então SC-003 — que é verificado por **contagem de
linhas** em `respondents`, `survey_sessions` e `survey_answers` — continua valendo ao pé da letra.

**Rationale**: FR-020 e SC-003 são absolutos — "nenhum registro é criado" verificado por contagem
de linhas — e o caminho vazio é a maioria absoluta do tráfego. Criar respondente na consulta
encheria a tabela de gente que nunca viu pesquisa nenhuma e transformaria o caminho barato numa
escrita por evento do app hospedeiro.

**Consequência aceita**: um respondente que nunca foi sorteado nunca existe como linha. Isso é
correto: ele não tem histórico a guardar, e o sorteio (D-05) não precisa dele para ser estável.

---

## D-11 — Abandono derivado na leitura e limite de tentativas (FR-026, FR-027) · **resolve NEEDS CLARIFICATION**

**Decisão**: `survey_sessions.outcome` guarda apenas `STARTED`, `COMPLETED` e `DISMISSED`.
`ABANDONED` é derivado na leitura: sessão `STARTED` cuja abertura é mais antiga que o prazo
configurado. Duas propriedades novas, com padrão conservador:

```yaml
pitaco:
  collect:
    session-timeout: 30m   # prazo após o qual a sessão sem desfecho é considerada abandonada
    max-attempts: 3        # abandonos no mesmo grupo de comparabilidade antes de parar de entregar
```

**Rationale**: é o mesmo tratamento que a autoria deu a `SurveyState` (nunca persistido, sempre
derivado de `stateAt`) — uma coluna atualizada por job mentiria entre o vencimento do prazo e a
execução dele, e uma rotina agendada é infraestrutura que esta feature não precisa. Trinta
minutos cobre a pessoa que trocou de tela e voltou; três tentativas é o número conservador que a
spec pediu que existisse sem fixar.

**Alternativas consideradas**: (a) job de expiração — estado mentiroso entre execuções, mais
infraestrutura; (b) prazo fixo no código — a spec diz explicitamente que o valor é de produto e
configurável.

---

## D-12 — Histórico e grupo de comparabilidade em uma consulta (FR-027, FR-028)

**Decisão**: `survey_sessions` guarda o `comparability_group` **denormalizado** da versão exibida.
A consulta de histórico traz, para o respondente e a lista de candidatos, as linhas
`(survey_id, comparability_group, outcome, opened_at)` — poucas por natureza — e o caso de uso
decide em memória: resolvida se existe `COMPLETED` ou `DISMISSED` no grupo corrente; bloqueada
por tentativas se a contagem de `STARTED` vencidas no grupo corrente atingiu `max-attempts`.

**Rationale**: uma consulta para todos os candidatos (Princípio V, sem N+1 nem chamada em laço),
sem junção com `survey_versions` no caminho quente, e a derivação de `ABANDONED` fica onde ela
pode ser testada em JUnit puro. A denormalização é segura porque o grupo da versão exibida é
imutável depois de publicada.

**Alternativas consideradas**: junção com `survey_versions` a cada consulta — junção extra no
caminho mais executado do sistema para reler um valor que nunca muda.

---

## D-13 — Como o valor da resposta é guardado

**Decisão**: `AnswerValue` é uma `sealed interface` com três formas — `TextValue`,
`NumericValue`, `ChoiceValue(List<String>)` — e a resposta `SKIPPED` não tem valor. No banco:
`survey_answers.text_value` (texto livre), `survey_answers.numeric_value` (avaliação, escala,
NPS) e a tabela filha `survey_answer_options` (escolha única e múltipla, uma linha por opção).

**Rationale**: a feature de resultados vai somar e cruzar esses valores em SQL; opção em linha é
agrupável direto, texto e número em coluna tipada são filtráveis. `sealed` faz o `switch` do
mapper e da validação ser exaustivo em tempo de compilação.

**Alternativas consideradas**: (a) uma coluna `jsonb` — adiaria toda a agregação para
`jsonb_array_elements` e traria mapeamento JSON no Hibernate sem necessidade; (b) uma coluna
`varchar` com o valor serializado — perde tipagem e ordenação numérica.

**Limite do texto livre** (edge case "texto grande demais"): 2000 caracteres, validado no VO
`AnswerText` e refletido no `varchar(2000)` da coluna. Recusa explícita, nunca truncamento.

---

## D-14 — Recusa que lista todos os problemas (FR-035, SC-007)

**Decisão**: `SubmissionRejected` estende `ApplicationException` com `ErrorType.BUSINESS_RULE` e
`code = "submission.rejected"`, carregando `List<SubmissionProblem>`; `extensions()` devolve
`errors: [{ questionKey, code }]`. O `ApiExceptionHandler` já repassa `extensions()` sem
conhecer o módulo — nada muda nele.

O `ErrorType` é `BUSINESS_RULE` e não `VALIDATION` porque a entrada está bem-formada: o que falha
é a regra da versão exibida, e o contrato exige **422**. `ErrorTypeHttpStatus` mapeia `VALIDATION`
para 400, então usá-lo devolveria o status errado — e criar um atalho na borda para corrigir isso
é justamente o que o Princípio IV proíbe.

**Rationale**: o molde já existe e está vivo em `SurveyNotPublishable`, com a mesma motivação:
quem envia descobre tudo que está errado em uma requisição só.

**Códigos dos problemas** (cada um sempre acompanhado da `questionKey` que o causou, exceto
`answer.question_unknown` em que a chave é a informada): `answer.question_unknown`,
`answer.question_duplicated`, `answer.required_missing`, `answer.value_missing`,
`answer.value_type_mismatch`, `answer.option_unknown`, `answer.options_empty`,
`answer.options_duplicated`, `answer.value_out_of_range`, `answer.text_too_long`.

---

## D-15 — Ausência de pesquisa: 200 com envelope, não 204 (FR-020)

**Decisão**: a consulta responde `200 OK` com `{"survey": null}`.

**Rationale**: mantém uma forma só de resposta para o SDK desserializar, deixa espaço para
campos futuros no envelope (a feature de controle de exposição vai querer devolver "volte depois
de tal instante") e torna explícito que ausência é sucesso, não vazio ambíguo.

**Alternativas consideradas**: `204 No Content` — economiza um punhado de bytes e obriga o SDK a
tratar dois formatos de sucesso; a economia não paga o custo na porta mais chamada do sistema.

---

## D-16 — Vocabulário compartilhado promovido para `core/catalog`

**Decisão**: mover para `core/catalog` os tipos que autoria e coleta usam para falar da mesma
coisa: `EventName`, `QuestionKey`, `QuestionType`, `QuestionOption`, `ScaleRange`, `SamplingRate`
e `RuleOperation`; e criar ali `SegmentationCriterion(String attribute, RuleOperation operation,
Optional<String> value)`. `SurveyId` e `SurveyVersionId` sobem para `core/identity`. O
`SegmentationRule` de `survey` **permanece em `survey`** com o seu `SegmentationRuleId`, passando
a expor `criterion()`.

**Rationale**: é o precedente explícito do `ApplicationId` — nasce no módulo, sobe quando o
segundo módulo precisa. A validação da resposta é a mesma regra que a autoria usou para montar a
pergunta; duas implementações da mesma regra divergem no primeiro tipo novo. O id da regra fica
para trás porque `collect` avalia critérios, não gerencia regras.

**Alternativas consideradas**: (a) `collect` declara enums próprios e o adaptador traduz — um
`QuestionType` novo esquecido na tradução aceitaria qualquer valor em silêncio; (b) `collect`
importa `survey/domain` — viola a regra que o AGENTS.md põe em primeiro lugar.

**Custo**: alteração mecânica de `import` em `modules/survey` e nos testes correspondentes, em um
único commit, sem mudança de comportamento.

---

## D-17 — Registro do último uso da chave, amortizado (FR-004)

**Decisão**: `api_keys.last_used_at` é atualizado **no máximo uma vez por minuto por chave** —
o `AuthenticateApiKeyUseCase` só chama `ApiKeyRepository.touch(...)` quando o valor registrado é
mais antigo que um minuto, com um `update` condicional que não abre transação própria.

**Rationale**: FR-004 quer saber se a chave está viva, não contar requisições. Escrever a cada
chamada colocaria uma escrita na mesma linha em todo evento do app hospedeiro — contenção de
linha quente e amplificação de WAL para uma precisão de que ninguém precisa.

**Alternativas consideradas**: (a) escrever sempre — o acima; (b) acumular em memória e liberar
periodicamente — estado de processo perdido no restart, e infraestrutura demais para um
timestamp.

---

## D-18 — Fronteiras transacionais

**Decisão**: `@Transactional` de `core.transaction` no `execute` de `OpenSurveySessionUseCase`
(cria respondente **e** sessão) e de `SubmitSurveySessionUseCase` (fecha a sessão **e** grava as
respostas). `FindEligibleSurveyUseCase` fica **sem** transação — só lê e não escreve nada.

**Rationale**: é literalmente a regra do projeto — duas escritas, ou leitura e escrita que
precisam do mesmo instante do banco. O `touch` da chave (D-17) roda fora de qualquer uma delas,
por não pertencer à unidade de trabalho de nenhum caso de uso.

---

## D-19 — Aplicação inativa: silêncio na consulta, recusa na abertura

**Decisão**: a chave de uma aplicação inativa **autentica** (a chave é válida), mas: a consulta
de elegibilidade devolve `{"survey": null}` (camada 1 da FR-011, US1.7), e a abertura de sessão
e a submissão são recusadas com `application.inactive` (422).

**Rationale**: o cenário US1.7 diz "nada é entregue", não "erro" — o app hospedeiro não deve
quebrar porque alguém desativou a aplicação no painel. Já abrir sessão para uma aplicação
inativa seria gravar coleta que o dono desligou, e aí a recusa explícita é a conduta certa.

---

## D-20 — Índices e o custo de cada caminho

**Decisão**: os índices que esta feature cria, e a consulta que cada um serve:

| Índice | Consulta |
| --- | --- |
| `survey_versions (trigger_event_name, survey_id) where status = 'PUBLISHED'` | candidatos por evento — o índice parcial mantém fora do índice todo rascunho |
| `surveys (application_id, lifecycle)` | recorte da aplicação e descarte de pausada/encerrada na junção |
| `respondents (application_id, identity_kind, identity_value)` unique | resolução do respondente |
| `survey_sessions (respondent_id, survey_id, comparability_group)` | histórico do respondente (D-12) |
| `survey_sessions (survey_id)` | leitura da coleta pela feature de resultados |
| `survey_answers (session_id, question_key)` unique | idempotência da resposta (D-08) |

**Caminho vazio** (o mais frequente): uma consulta no índice parcial, zero linhas, zero escritas.
**Caminho com entrega**: candidatos + histórico + conteúdo = três consultas, nenhuma em laço.

---

## D-22 — `SurveySession` renomeada para `SurveyDisplay` · **revisão pós-implementação**

**Decisão**: o agregado, a rota, as tabelas e os `code` de erro passam de "session" para "display":
`SurveyDisplay`/`DisplayId`/`DisplayOutcome`, `POST /collect/displays`, `survey_displays` e
`survey_display_attributes`, `display.not_found` / `display.already_closed` /
`display.identifier_conflict`, e a propriedade `pitaco.collect.display-timeout`.

**Rationale**: "session" sugeria sessão do app hospedeiro — abriu o app, o SDK iniciou —, e o
agregado é outra coisa: o registro de que **uma pesquisa foi exibida** a um respondente sob uma
versão. Quem atravessa exibições é o `Respondent`. A confusão apareceu na primeira leitura de
fora, o que é sinal suficiente. O nome novo é o que a própria spec já usava em português
("sessão de exibição" → "exibição").

**Custo**: nenhum. Não há SDK publicado nem dado em produção, e a migration
`V20260908210000__create_collect.sql` nunca rodou fora de container efêmero de teste — por isso
foi editada no lugar, em vez de virar uma migration de rename. A regra "migration aplicada não se
edita" continua valendo: esta não estava aplicada em lugar nenhum durável.

**O texto da spec e das decisões anteriores permanece com a palavra "sessão"**, como registro do
que foi decidido na época; o vocabulário corrente é o deste registro.

---

## D-21 — O que esta feature deliberadamente não faz

Sem cache (o Redis, que atravessou três features sem uso, saiu do projeto nesta), sem rate limit (FR-041, é da feature de operação da
borda), sem leitura administrativa das respostas (FR-042), sem catálogo de eventos ou atributos
observados (FR-043), sem lógica condicional entre perguntas (FR-044), sem `AggregateRoot` nem
evento de domínio (ainda não há o segundo caso concreto que os exija).
