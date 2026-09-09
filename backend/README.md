# Pitaco — Backend

API do Pitaco, escrita em Java 21 com Spring Boot 4.

O projeto está na fase de fundação: a base arquitetural (núcleo de domínio, contratos de caso de uso, tratamento de erros, observabilidade e infraestrutura local) já está de pé; os primeiros agregados e endpoints de negócio entram por cima dela.

---

## Stack

| Camada | Tecnologia |
| --- | --- |
| Linguagem | Java 21 (virtual threads habilitadas) |
| Framework | Spring Boot 4.1 (Web MVC, Data JPA, Validation, Actuator) |
| Banco | PostgreSQL + Flyway |
| Observabilidade | OpenTelemetry + stack Grafana LGTM |
| Documentação | springdoc-openapi (Swagger UI) |
| Testes | JUnit 5 + Testcontainers |
| Build | Maven (wrapper incluso) |

---

## Arquitetura

> Este é o resumo. O detalhamento completo, com o manual de criação de módulos,
> casos de uso e endpoints, está em
> [`docs/backend/arquitetura.md`](docs/backend/arquitetura.md).

O código é dividido em dois mundos, e a dependência só aponta em uma direção: `infra` conhece `core`, `core` não conhece ninguém.

```
com.renanloureiroo.pitaco
├── core                     # regras e contratos, sem Spring, sem HTTP, sem JPA
│   ├── entity               # Entity — base de identidade das entidades
│   ├── error                # ApplicationException, ErrorType e famílias de erro
│   ├── identity             # Id — base tipada de todo identificador de agregado
│   └── usecase              # contratos UseCase (com/sem input, com/sem output)
└── infra                    # tudo que é detalhe: framework, protocolo, persistência
    └── http
        ├── config           # metadados OpenAPI
        └── error            # tradução de erro do core para resposta HTTP
```

Quatro decisões que valem ser conhecidas antes de escrever código aqui:

**Erro não sabe o que é HTTP.** O core lança `ApplicationException` carregando um `ErrorType` (`NOT_FOUND`, `CONFLICT`, `VALIDATION`, `UNAUTHORIZED`, `FORBIDDEN`, `BUSINESS_RULE`) e um `code` textual estável. Quem transforma isso em status code é o adapter — hoje `ErrorTypeHttpStatus`, na borda HTTP. Trocar ou somar um protocolo não mexe no domínio.

**Identificador é opaco e tipado.** `Id` é a base de todo identificador de agregado. Cada agregado declara o seu (`UserId`, `AnswerId`, …), e a igualdade compara o tipo concreto além do valor — um `UserId` nunca é igual a um `AnswerId`, nem em compilação nem em runtime. Como o valor é gerado (hoje UUID) é detalhe interno, não contrato.

**Entidade é identidade; value object é conteúdo.** `Entity<ID extends Id>` iguala pelo identificador e pelo tipo concreto: dois `Pitaco` com o mesmo `PitacoId` são o mesmo pitaco, ainda que um esteja editado e o outro seja uma leitura mais antiga. Para o que se define pelo conteúdo, use `record` — a igualdade estrutural já vem pronta e não há base a herdar. Não existe `AggregateRoot`: sem eventos de domínio ele não teria nada além do que `Entity` já dá, e ele entra no dia em que houver evento para acumular.

**Resposta HTTP é montada por presenter.** Cada presenter é uma `final class` em `modules/<módulo>/infra/http/presenters`, com construtor privado e um `public static R present(O output)` que traduz a saída do caso de uso no DTO de resposta. Sem interface e sem bean: é tradução pura, no mesmo molde do mapper JPA. O DTO não conhece o caso de uso e não tem factory estática; o controller chama `XPresenter.present(output)` e devolve. É o que mantém o formato da resposta como decisão da borda, e não do núcleo.

**Caso de uso é uma interface funcional.** Quatro variações conforme a assinatura: `UseCase<I, O>`, `UseCaseWithoutInput<O>`, `UseCaseWithoutOutput<I>` e `UseCaseWithoutInputAndOutput`. Um caso de uso, um `execute`.

### Prefixo das rotas

A aplicação inteira vive sob `/api`, via `server.servlet.context-path`. O prefixo é do contêiner, então vale para tudo — API, Swagger UI e Actuator — e nenhum controller o repete: `@GetMapping("/pitacos")` responde em `/api/pitacos`.

### Formato de erro

Toda resposta de erro sai em RFC 9457 (`application/problem+json`), com duas propriedades extras:

```json
{
  "type": "about:blank",
  "title": "Not Found",
  "status": 404,
  "detail": "Palpite não encontrado",
  "instance": "/api/pitacos/42",
  "code": "pitaco.not_found",
  "traceId": "8f3a1c2b9d4e5f60"
}
```

- `code` — identificador estável do erro. É por ele que o client e o suporte se orientam; a mensagem pode mudar a qualquer momento.
- `traceId` — liga a resposta que o usuário viu ao trace correspondente no Grafana. Sai apenas quando o tracing está ligado.

Erros de Bean Validation acrescentam ainda `errors`, um mapa de campo → mensagem. Erros inesperados nunca vazam mensagem interna: viram um `internal.unexpected` genérico, com o stack trace apenas no log.

### Endpoints

| Método | Rota | O que faz |
| --- | --- | --- |
| `POST` | `/applications` | Cria uma aplicação |
| `POST` | `/applications/{applicationId}/api-keys` | Emite uma chave de API — o segredo em claro sai **uma única vez**, nesta resposta |
| `DELETE` | `/applications/{applicationId}/api-keys/{apiKeyId}` | Revoga a chave: imediata, irreversível, e o registro permanece para a trilha |

E a autoria de pesquisa, toda aninhada na aplicação dona — o escopo é parte da rota, não um
parâmetro que se pode esquecer de aplicar:

| Método | Rota (sob `/applications/{applicationId}/surveys`) | O que faz |
| --- | --- | --- |
| `POST` `GET` | `` | Cria a pesquisa em rascunho, junto da versão 1; lista as da aplicação, paginado |
| `GET` `PATCH` `DELETE` | `/{surveyId}` | Consulta com o conteúdo montado; renomeia; descarta a nunca publicada |
| `POST` | `/{surveyId}/questions` | Acrescenta pergunta, na última posição e com chave estável nova |
| `PUT` `DELETE` | `/{surveyId}/questions/{questionId}` | Reescreve preservando a chave; remove recompactando as posições |
| `PUT` | `/{surveyId}/questions/order` | Redefine a ordem, exigindo permutação exata |
| `PUT` | `/{surveyId}/trigger` | Define ou substitui o disparo: evento, janela e proporção |
| `POST` `DELETE` | `/{surveyId}/trigger/rules[/{ruleId}]` | Pendura e remove regras de segmentação |
| `GET` | `/{surveyId}/publication-impediments` | O que falta para publicar — a mesma lista que a publicação usaria |
| `POST` | `/{surveyId}/publication` | Publica, congelando o conteúdo na versão |
| `POST` | `/{surveyId}/pause` · `/resume` · `/end` | Controla o que está no ar |
| `GET` | `/{surveyId}/transitions` | Histórico: as transições comandadas mais as derivadas da janela |
| `GET` `POST` | `/{surveyId}/versions` | Lista as publicadas; abre um rascunho de versão nova |
| `DELETE` | `/{surveyId}/versions/draft` | Descarta o rascunho de versão |
| `GET` | `/{surveyId}/versions/{number}` | O conteúdo congelado naquela versão |
| `GET` | `/{surveyId}/versions/comparability` | Quais versões têm respostas somáveis entre si |

E a superfície pública, consumida pelo SDK. A aplicação vem **da chave**, no header
`X-Pitaco-Key`: nenhuma operação daqui aceita `applicationId` na rota ou no corpo. As rotas
administrativas fazem o inverso — apresentar a chave do SDK em `/applications/**` falha com
`403 api_key.forbidden_surface`, em vez de ser ignorado em silêncio.

| Método | Rota (sob `/collect`) | O que faz |
| --- | --- | --- |
| `POST` | `/eligibility` | Há pesquisa para este respondente agora? Devolve no máximo uma, com a versão publicada inteira, ou `{"survey": null}` — sem gravar nada |
| `POST` | `/displays` | Abre a exibição de exibição. O identificador nasce no dispositivo e é a chave de idempotência: a mesma abertura de novo devolve 200 em vez de 201 |
| `POST` | `/displays/{displayId}/submission` | Respostas e desfecho em um ato atômico, validado inteiro antes de qualquer gravação |

Três coisas que essa superfície assume: ausência de pesquisa **não é erro** — aplicação inativa,
pesquisa pausada, fora da janela, evento sem pesquisa, regra não satisfeita, não sorteado e já
resolvida devolvem todos `{"survey": null}`, e nenhuma linha é criada; o **sorteio** é uma função
determinística do par pesquisa–respondente, então a mesma consulta repetida dá sempre a mesma
resposta; e a submissão continua sendo aceita se a pesquisa foi pausada, encerrada ou republicada
depois da abertura, porque a validação usa a **versão exibida**, congelada na exibição.

Duas coisas que essas rotas assumem e que valem registrar: o **estado** de uma pesquisa
(`draft`, `scheduled`, `active`, `paused`, `ended`) é derivado na leitura, do ciclo de vida
somado à janela — não há coluna nem job para ele; e nada fora do escopo da aplicação dona
responde `403`, sempre `404`, para que a API não vire um oráculo de existência.

Os `code` de erro que chegam ao cliente:

| `code` | Status | Quando |
| --- | --- | --- |
| `request.invalid` | 400 | Bean Validation no corpo da requisição |
| `application.already_exists_with_same_slug` | 409 | Slug já usado por outra aplicação |
| `application.not_found` | 404 | Aplicação inexistente — **ou** identificador em formato inválido, indistinguíveis de propósito |
| `application.inactive` | 422 | Aplicação inativa não emite chave |
| `application.name_invalid` · `application.slug_invalid` · `application.quiet_period_invalid` · `application.retention_invalid` | 400 | Invariante da aplicação |
| `application.open_text_retention_invalid` | 422 | Retenção de texto livre maior que a geral |
| `api_key.label_invalid` | 400 | Rótulo vazio ou acima de 80 caracteres |
| `survey.not_found` · `survey_version.not_found` · `question.not_found` · `segmentation_rule.not_found` | 404 | Inexistente, de outra aplicação, ou identificador malformado — indistinguíveis de propósito |
| `survey.name_invalid` · `question.statement_invalid` · `question.options_not_allowed` · `question.options_duplicated` · `question.scale_range_invalid` · `question.order_invalid` | 400 | Invariante do conteúdo da pesquisa |
| `trigger.event_name_invalid` · `trigger.window_invalid` · `trigger.sampling_rate_invalid` · `segmentation_rule.invalid` | 400 | Invariante do disparo e das regras |
| `survey.content_frozen` | 422 | Não há rascunho aberto para receber a escrita |
| `survey.not_publishable` | 422 | Rascunho com pendências — a lista completa vem na extensão `impediments` |
| `survey.already_published` · `survey_version.draft_already_open` | 409 | Já publicada sem rascunho; já há rascunho de versão aberto |
| `survey.published_cannot_be_discarded` · `survey.not_published` · `survey.transition_not_allowed` | 422 | Publicada não se apaga, se encerra; e cada transição tem seu estado de partida |
| `survey_version.cosmetic_refused` | 422 | Declaração de cosmética contradita — as diferenças vêm na extensão `differences` |
| `survey_version.no_changes` | 422 | Rascunho de versão idêntico à publicada |
| `trigger.not_defined` | 422 | Regra de segmentação sem disparo onde se pendurar |
| `api_key.not_found` | 404 | Chave inexistente, de outra aplicação, ou identificador malformado — os três com o mesmo `code` |
| `api_key.already_revoked` | 409 | Chave já revogada; o instante da primeira revogação não muda |
| `api_key.missing` | 401 | Header `X-Pitaco-Key` ausente na superfície pública |
| `api_key.invalid` | 401 | Chave desconhecida **ou** revogada — a mesma resposta para as duas, sem revelar qual |
| `api_key.forbidden_surface` | 403 | Chave do SDK apresentada em rota administrativa |
| `display.not_found` | 404 | Exibição inexistente ou de outra aplicação, indistinguíveis |
| `display.identifier_conflict` | 409 | Identificador de exibição já usado para outra pesquisa ou versão |
| `display.already_closed` | 409 | Exibição fechada, e o envio traz desfecho diferente ou resposta nova |
| `submission.rejected` | 422 | Conteúdo que a versão exibida não aceita — **todos** os problemas vêm de uma vez na extensão `errors`, cada um com `questionKey` e um `code` próprio |

Os `code` dentro de `errors[]` de `submission.rejected`: `answer.question_unknown`,
`answer.question_duplicated`, `answer.required_missing`, `answer.value_missing`,
`answer.value_type_mismatch`, `answer.option_unknown`, `answer.options_empty`,
`answer.options_duplicated`, `answer.value_out_of_range` e `answer.text_too_long`. Nunca há
recusa genérica do tipo "respostas inválidas".

`application.id_invalid` e `api_key.id_invalid` não entram na tabela: são internos ao domínio, capturados pelo caso de uso e traduzidos em 404, para que "malformado" e "inexistente" não sejam distinguíveis por quem chama.

---

## Rodando localmente

**Pré-requisitos:** JDK 21 e Docker.

```bash
# 1. crie sua config local a partir do exemplo
cp src/main/resources/application-local.yml.example src/main/resources/application-local.yml

# 2. suba a aplicação (Postgres e Grafana LGTM sobem junto)
./mvnw spring-boot:run
```

O `spring-boot-docker-compose` levanta o `compose.yaml` automaticamente e injeta as conexões na aplicação — não é preciso rodar `docker compose up` na mão. O perfil `local` já vem ativado pelo plugin Maven, e `application-local.yml` está no `.gitignore` (só o `.example` é versionado).

Alternativa, sem Docker Compose: `./mvnw spring-boot:test-run` sobe a aplicação com os serviços via Testcontainers (`TestPitacoApplication`).

### Endereços

| O quê | Onde |
| --- | --- |
| API | http://localhost:8080/api |
| Swagger UI | http://localhost:8080/api/swagger-ui.html |
| OpenAPI JSON | http://localhost:8080/api/v3/api-docs |
| Actuator | http://localhost:8080/api/actuator |
| Grafana | http://localhost:3000 |

---

## Testes

> A receita de cada tipo de teste — value object, entidade, caso de uso, DTO,
> componente de borda e E2E — está em
> [`docs/backend/testes.md`](docs/backend/testes.md).

```bash
./mvnw test           # suíte completa
./mvnw verify         # testes + empacotamento
```

Os testes de integração usam Testcontainers (`TestcontainersConfiguration`) para Postgres e a stack LGTM — Docker precisa estar rodando, e nenhum serviço externo é necessário.

Mock sobre porta do projeto é proibido: cada porta tem um fake em `testsupport/` (`InMemoryApplicationRepository`, `InMemoryApiKeyRepository`, `DirectTransactor`).

Transação é decisão do caso de uso, declarada com `@Transactional` de `core.transaction` no próprio `execute` — a anotação é do projeto, não do Spring, e o caso de uso segue sem importar framework. Quem a implementa é o `TransactionalAdvisor`, na borda, sobre a porta `Transactor`; em teste de unidade o caso de uso roda sem proxy e sem transação, como qualquer bean anotado. `RevokeApiKeyUseCase` a usa porque a releitura de quem perde a corrida precisa enxergar o que o vencedor gravou.

---

## Build

```bash
./mvnw clean package
java -jar target/pitaco-0.0.1-SNAPSHOT.jar
```

---

## Convenções

- **Migrations** ficam em `src/main/resources/db/migration`, no padrão Flyway `V<yyyyMMddHHmmss>__descricao.sql` — o timestamp (em UTC) evita que duas branches disputem o mesmo número de versão. `ddl-auto` é `validate`: o schema nasce da migration, nunca do Hibernate.
- **Sem Javadoc e sem comentário explicativo.** O código se explica sozinho. A exceção é a decisão com alternativa descartada — uma linha, não um parágrafo. Regra não óbvia vira teste.
- **Rotas** não repetem o prefixo: ele vem do `context-path`. Escreva `@RequestMapping("/pitacos")`, não `@RequestMapping("/api/pitacos")`.
- **Nada de Spring no `core`.** Anotação de framework, `jakarta.persistence`, `HttpStatus` — tudo isso vive em `infra`.

---

## Documentação

| Documento | O que traz |
| --- | --- |
| [Arquitetura](docs/backend/arquitetura.md) | as camadas, as peças do núcleo, o fluxo de uma requisição e o manual de criação |
| [Testes](docs/backend/testes.md) | os seis tipos de teste, o ferramental de `testsupport` e a receita de cada um |
| [Constituição](.specify/memory/constitution.md) | os princípios obrigatórios e os portões de qualidade |
| [ADRs](docs/adrs) | as decisões de arquitetura e o que se aceitou pagar por elas |
