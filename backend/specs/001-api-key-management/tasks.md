---
description: "Task list para a feature 001-api-key-management"
---

# Tasks: Chaves de API — criação e exclusão

**Input**: documentos de projeto em `/specs/001-api-key-management/`

**Prerequisites**: [plan.md](./plan.md), [spec.md](./spec.md), [research.md](./research.md),
[data-model.md](./data-model.md), [contracts/](./contracts/), [quickstart.md](./quickstart.md)

**Tests**: obrigatórios, e **antes** do código que cobrem. Não é preferência desta feature — é o
Princípio III da constituição, marcado como NÃO-NEGOCIÁVEL. Cada tarefa de teste abaixo só está
concluída quando o teste **falha** pelo motivo certo; a tarefa de implementação seguinte é o que
o faz passar.

**Organização**: por user story, para que cada uma seja implementável e testável sozinha.

## Format: `[ID] [P?] [Story] Descrição`

- **[P]**: pode rodar em paralelo (arquivo diferente, sem dependência pendente)
- **[Story]**: US1 (emitir) ou US2 (excluir); fases de Setup, Foundational e Polish não têm rótulo
- Todo caminho é relativo à raiz do módulo, `backend/`

**Raiz dos pacotes**: `src/main/java/com/renanloureiroo/pitaco/` (produção) e
`src/test/java/com/renanloureiroo/pitaco/` (teste). Abaixo abreviados como `…/main/` e `…/test/`.

---

## Phase 1: Setup

**Propósito**: garantir que a linha de partida está verde antes de qualquer arquivo novo.

- [X] T001 Rodar `./mvnw verify` com Docker de pé e confirmar a suíte atual verde (110 testes) — é a base de comparação de tudo que vem depois

---

## Phase 2: Foundational (pré-requisitos bloqueantes)

**Propósito**: os identificadores, o domínio da chave, a porta, os adaptadores e o presenter —
tudo que as **duas** histórias precisam. Nenhuma história começa antes desta fase fechar.

**⚠️ CRÍTICO**: US1 e US2 dependem inteiramente desta fase.

### Identidade — formato UUID validado (teste antes, D-06)

Sem isto, o `try/catch` de D-06 nos dois casos de uso é inalcançável: hoje `Id` só rejeita nulo e
em branco, então qualquer texto vira identificador válido e "malformado" nunca se distingue de
"inexistente" na origem. Os testes de identificador malformado passariam sem exercitar nada.

- [X] T002 [P] Criar `ApplicationIdTest` em `…/test/modules/app/domain/entities/ApplicationIdTest.java` — `@DisplayName("ApplicationId")`; `of(uuid)` aceita; `@ParameterizedTest` com `"nao-e-uuid"`, `"123"`, UUID truncado e UUID com caractere fora do alfabeto hexadecimal, todos rejeitados com `type VALIDATION` e `code application.id_invalid`; nulo e em branco continuam caindo em `id.invalid`, o `code` de `Id`; `generate()` produz valor que `of(...)` aceita de volta
- [X] T003 [P] Criar `ApiKeyIdTest` em `…/test/modules/app/domain/entities/ApiKeyIdTest.java` — mesmo roteiro de T002, com `code api_key.id_invalid`
- [X] T004 Alterar `Id` em `…/main/core/identity/Id.java` — acrescentar `protected static boolean isUuid(String value)` sobre `UUID.fromString` (`try/catch IllegalArgumentException`). `Id` **não** passa a validar formato: continua opaco e sem opinião, e é a subclasse que decide se exige UUID e com qual `code`. `IdTest` deve seguir verde **sem alteração** — é a prova de que a opacidade do core não mudou
- [X] T005 Alterar `ApplicationId` em `…/main/modules/app/domain/entities/ApplicationId.java` — validar no construtor privado com `DomainException(VALIDATION, "application.id_invalid", "Identificador de aplicação inválido")`, `code` em constante `private static final`. Conferir que `CreateApplicationUseCaseTest`, `ApplicationJpaMapper` e `ApplicationFactory` seguem verdes: todos já usam UUID de verdade
- [X] T006 Criar `ApiKeyId` em `…/main/modules/app/domain/entities/ApiKeyId.java` — estende `Id`, construtor privado validando com `api_key.id_invalid`, `generate()` e `of(String)`, exatamente no molde de `ApplicationId` recém-alterado

### Domínio da chave (teste antes)

- [X] T007 [P] Criar `ApiKeyLabelTest` em `…/test/modules/app/domain/valueobjects/ApiKeyLabelTest.java` — `@DisplayName("ApiKeyLabel")`, `@ParameterizedTest` para rótulos válidos e inválidos: nulo, vazio, só espaços, 81 caracteres; espaço nas pontas removido; 80 caracteres aceito; asserção sobre `type` e `code` (`api_key.label_invalid`) via `assertThatThrownBy`, **nunca** sobre a mensagem
- [X] T008 [P] Criar `ApiKeySecretTest` em `…/test/modules/app/domain/valueobjects/ApiKeySecretTest.java` — `generate()` produz segredo em claro no formato `pit_<prefixo>_<aleatório>` começando pelo `prefix` do value object; duas chamadas produzem segredos e prefixos distintos; `hashOf(plainText)` bate com o hash guardado, tem 64 caracteres hexadecimais e é determinístico; `toString()` não contém o hash
- [X] T009 Implementar `ApiKeyLabel` em `…/main/modules/app/domain/valueobjects/ApiKeyLabel.java` — `record` no molde de `Name`: `strip()` no compact constructor, 1..80 caracteres, `DomainException(VALIDATION, "api_key.label_invalid", …)` em constante `private static final`, factory `of(String)`, `toString()` devolvendo o valor
- [X] T010 Implementar `ApiKeySecret` em `…/main/modules/app/domain/valueobjects/ApiKeySecret.java` — `record ApiKeySecret(String prefix, String hash)`, `static Generated generate()` (32 bytes de `SecureRandom`, Base64 URL-safe sem padding, prefixo `pit_<8 chars>`), `record Generated(ApiKeySecret secret, String plainText)`, `static String hashOf(String)` com SHA-256 hex minúsculo via `MessageDigest`. Apenas JDK — nenhuma dependência nova no `pom.xml` (D-03)
- [X] T011 Criar `ApiKeyTest` em `…/test/modules/app/domain/entities/ApiKeyTest.java` — `issue(...)` devolve `Issued` com `plainSecret` cujo hash bate com o da entidade, `createdAt` preenchido e `revokedAt()` vazio; `revoke()` preenche `revokedAt`; **`revoke()` duas vezes lança `DomainException` com `type CONFLICT` e `code api_key.already_revoked` sem alterar o `revokedAt` gravado**; não existe método que devolva a chave ao estado válido; `restore(...)` com `revokedAt` anterior ao `createdAt` é rejeitado com `code api_key.revoked_before_created`; `applicationId` nulo é rejeitado com `code api_key.application_required`
- [X] T012 Implementar `ApiKey` em `…/main/modules/app/domain/entities/ApiKey.java` — estende `Entity<ApiKeyId>`, campos e semântica conforme [data-model.md](./data-model.md); `static Issued issue(ApplicationId, ApiKeyLabel)`, `static ApiKey restore(...)`, `void revoke()`, `boolean isRevoked()`, `Optional<Instant> revokedAt()`, `record Issued(ApiKey apiKey, String plainSecret)`. Os quatro `code`s do agregado (`already_revoked`, `application_required`, `revoked_before_created`, e o `label_invalid` que vem do value object) em constantes `private static final`. Sem setter de `label`, `secret`, `applicationId` ou `createdAt`; o segredo em claro **não** é campo da entidade (D-04)

### Porta, fake e factory de teste

- [X] T013 Criar a porta `ApiKeyRepository` em `…/main/modules/app/application/repositories/ApiKeyRepository.java` — `create`, `findByIdAndApplicationId`, e `boolean revoke(ApiKey)` com semântica de update condicional (`false` = alguém revogou antes)
- [X] T014 [P] Criar o fake `InMemoryApiKeyRepository` em `…/test/testsupport/repositories/InMemoryApiKeyRepository.java` — mesma semântica da porta, inclusive o `false` do `revoke` sobre chave já revogada. É este fake que os testes de caso de uso usam; mock sobre porta do projeto é proibido
- [X] T015 [P] Criar `ApiKeyFactory` em `…/test/testsupport/factories/ApiKeyFactory.java` — fluente no molde de `ApplicationFactory`: `anApiKey()`, `forApplication(ApplicationId)`, `withLabel(String)`, `revoked()`, `build()`, `buildSavedIn(ApiKeyRepository)`

### Persistência

- [X] T016 [P] Criar a migration `src/main/resources/db/migration/V20260908120000__create_api_keys.sql` — tabela `api_keys` conforme [data-model.md](./data-model.md), com FK para `applications(id)`, **`unique` em `secret_hash`** (não em `prefix`), `idx_api_keys_application_id` e `idx_api_keys_prefix`. `prefix` é dica pública e pode repetir: quem identifica é o `id`, e não existe caminho de erro de colisão a tratar (D-08). Nenhuma migration existente é editada
- [X] T017 [P] Criar `ApiKeyJpaEntity` em `…/main/modules/app/infra/database/jpa/entities/ApiKeyJpaEntity.java` — molde de `ApplicationJpaEntity`; `application_id` como `String`, não `@ManyToOne`; `revoked_at` nulo permitido; sem coluna para segredo em claro
- [X] T018 Criar `ApiKeyJpaMapper` em `…/main/modules/app/infra/database/jpa/mappers/ApiKeyJpaMapper.java` — `toJpa` e `toDomain`, chamando `ApiKey.restore(...)`, nunca `issue(...)`
- [X] T019 Criar `ApiKeyJpaRepository` em `…/main/modules/app/infra/database/jpa/repositories/ApiKeyJpaRepository.java` — `JpaRepository<ApiKeyJpaEntity, String>`, `findByIdAndApplicationId(String, String)` e o update condicional `@Modifying @Query("update ApiKeyJpaEntity k set k.revokedAt = :revokedAt where k.id = :id and k.revokedAt is null")` devolvendo `int` (D-05)
- [X] T020 Criar `ApiKeyRepositoryJpa` em `…/main/modules/app/infra/database/jpa/repositories/ApiKeyRepositoryJpa.java` — `@Repository` implementando a porta sobre o Spring Data; `revoke` devolve `linhas > 0`. Sem `EntityManager`

### Presenter — dívida registrada no Princípio IV (D-07)

- [X] T021 [P] Criar a porta `Presenter<O, R>` em `…/main/core/presenter/Presenter.java` — interface funcional Java pura, `R present(O output)`, sem nenhum tipo de framework
- [X] T022 Criar `CreateApplicationPresenter` em `…/main/modules/app/infra/http/presenters/CreateApplicationPresenter.java` — `@Component` implementando `Presenter<CreateApplicationUseCase.Output, CreateApplicationResponseDTO>`; passa a ser o único a conhecer o DTO de resposta
- [X] T023 Migrar `CreateApplicationResponseDTO` (`…/main/modules/app/infra/http/dtos/`) e `ApplicationController` (`…/main/modules/app/infra/http/controllers/`) — remover o `from(...)` estático do DTO, injetar o presenter no controller. `CreateApplicationE2ETest` deve seguir verde **sem alteração** — é a prova de que o contrato não mudou

**Checkpoint**: `./mvnw verify` verde. Identificadores validados, domínio da chave, porta, fake, adaptadores e presenter prontos; as duas histórias podem começar.

---

## Phase 3: User Story 1 — Emitir uma chave para a aplicação (P1) 🎯 MVP

**Goal**: emitir uma credencial para uma aplicação ativa, devolvendo o segredo em claro uma
única vez, junto com identificador e prefixo público.

**Independent Test**: `POST /applications/{id}/api-keys` com rótulo válido devolve 201 com o
segredo; a chave existe no banco vinculada à aplicação; o segredo em claro não aparece em nenhuma
coluna, em nenhum log e em nenhuma outra resposta.

### Erros da história

- [X] T024 [P] [US1] Criar `ApplicationNotFound` em `…/main/modules/app/application/errors/ApplicationNotFound.java` — `NotFoundException`, `code` `application.not_found` em constante `private static final`, carregando o identificador tentado
- [X] T025 [P] [US1] Criar `ApplicationIsInactive` em `…/main/modules/app/application/errors/ApplicationIsInactive.java` — `ApplicationException` com `ErrorType.BUSINESS_RULE` (→ 422), `code` `application.inactive`, carregando o `ApplicationId`

### Caso de uso (teste antes)

- [X] T026 [US1] Criar `IssueApiKeyUseCaseTest` em `…/test/modules/app/application/usecases/IssueApiKeyUseCaseTest.java` — sobre `InMemoryApplicationRepository` + `InMemoryApiKeyRepository`: caminho feliz (chave persistida, `Output` com `plainSecret` cujo hash bate com o armazenado, prefixo igual ao da entidade); duas emissões seguidas geram id e segredo distintos e coexistem; aplicação inexistente → `ApplicationNotFound`; **identificador de aplicação fora do formato UUID → `ApplicationNotFound`, não `application.id_invalid`** — a `DomainException` de T005 é capturada e traduzida (D-06); aplicação inativa → `ApplicationIsInactive`; rótulo inválido → `code api_key.label_invalid`; em todo caminho de falha o repositório de chaves continua vazio (FR-017)
- [X] T027 [US1] Implementar `IssueApiKeyUseCase` em `…/main/modules/app/application/usecases/IssueApiKeyUseCase.java` — `UseCase<Input, Output>` conforme [data-model.md](./data-model.md); POJO sem anotação de framework; `try/catch (DomainException)` em volta de `ApplicationId.of(...)` traduzindo para `ApplicationNotFound` (D-06); `@Slf4j` logando id, aplicação e prefixo — **nunca** o rótulo nem o segredo. Sem `Transactor`: escrita única

### Borda HTTP (teste antes)

- [X] T028 [US1] Criar `IssueApiKeyRequestDTOTest` em `…/test/modules/app/infra/http/dtos/IssueApiKeyRequestDTOTest.java` — `Validator` standalone: `label` nulo, em branco e com 81 caracteres são recusados; 80 é aceito. É o único lugar (com o E2E) onde o teste afirma sobre **mensagem**, porque aqui a mensagem é o contrato: cada uma espelha a de `ApiKeyLabel`
- [X] T029 [US1] Criar `IssueApiKeyRequestDTO` em `…/main/modules/app/infra/http/dtos/IssueApiKeyRequestDTO.java` — `record` com `@NotBlank` + `@Size(max = 80)` cujas mensagens espelham `ApiKeyLabel`, `@Schema` em português, e `toInput(String applicationId)` devolvendo o `Input` do caso de uso. **Nenhuma constraint sobre o `applicationId`**: validar o formato na borda devolveria 400 e revelaria o formato interno do identificador (D-06)
- [X] T030 [P] [US1] Criar `IssueApiKeyResponseDTO` em `…/main/modules/app/infra/http/dtos/IssueApiKeyResponseDTO.java` — campos `id`, `applicationId`, `label`, `prefix`, `secret`, `createdAt` conforme [contracts/api-keys.openapi.yaml](./contracts/api-keys.openapi.yaml); `@Schema` do `secret` deixando explícito que é exibido uma única vez. Sem factory estática — quem monta é o presenter
- [X] T031 [US1] Criar `IssueApiKeyPresenter` em `…/main/modules/app/infra/http/presenters/IssueApiKeyPresenter.java` — `Presenter<IssueApiKeyUseCase.Output, IssueApiKeyResponseDTO>`
- [X] T032 [US1] Criar `ApiKeyControllerSwagger` em `…/main/modules/app/infra/http/controllers/ApiKeyControllerSwagger.java` — `@Tag("Chaves de API")` e a assinatura do `POST` com `@ApiResponse` para **201** (com header `Location`), **400** (`ApiValidationErrorResponse`), **404** e **422** (`ApiErrorResponse`), fielmente ao contrato
- [X] T033 [US1] Criar `ApiKeyController` em `…/main/modules/app/infra/http/controllers/ApiKeyController.java` — `@RestController`, `@RequestMapping("/applications/{applicationId}/api-keys")` (sem `/api`, que vem do context-path), implementando a interface Swagger; `POST` devolve 201 com `Location` via `ServletUriComponentsBuilder`, corpo montado pelo presenter. **Zero `try/catch`**
- [X] T034 [US1] Registrar `@Bean IssueApiKeyUseCase` em `…/main/modules/app/infra/config/UseCasesConfiguration.java`

### E2E

- [X] T035 [US1] Criar `IssueApiKeyE2ETest` em `…/test/modules/app/infra/http/controllers/IssueApiKeyE2ETest.java` — `@E2E`, `@DisplayName("POST /applications/{applicationId}/api-keys")`, `DatabaseCleaner` no `@BeforeEach`, **sem `@Transactional`**. Cobre: caminho feliz (201, corpo, `Location`, **e a linha relida do banco** com `secret_hash` batendo com o segredo devolvido e `revoked_at` nulo); duas emissões coexistindo; 400 de `label` ausente/vazio/longo; 404 de aplicação inexistente (`application.not_found`); **404 — e não 400 — de `applicationId` fora do formato UUID** (D-06); 422 de aplicação inativa (`application.inactive`); JSON malformado; e que **nenhuma linha foi criada** em cada caminho de falha
- [X] T036 [US1] Acrescentar ao `IssueApiKeyE2ETest` a asserção de SC-003: consultando a linha persistida, nenhuma coluna contém o segredo em claro devolvido na resposta

**Checkpoint**: US1 completa e demonstrável sozinha. `./mvnw verify` verde. É o MVP.

---

## Phase 4: User Story 2 — Excluir uma chave comprometida ou obsoleta (P2)

**Goal**: revogar uma chave pelo identificador, de forma imediata e irreversível, preservando a
trilha e sem afetar as demais chaves.

**Independent Test**: emitir uma chave, excluí-la, e conferir que ela deixou de ser válida (linha
com `revoked_at` preenchido), que outra chave da mesma aplicação segue intacta, e que a segunda
exclusão é recusada por conflito sem mexer no instante já gravado.

**Depende de**: Phase 2. Usa o endpoint de US1 apenas para produzir a chave nos testes — a
implementação em si não depende de US1.

### Erro da história

- [X] T037 [P] [US2] Criar `ApiKeyNotFound` em `…/main/modules/app/application/errors/ApiKeyNotFound.java` — `NotFoundException`, `code` `api_key.not_found`, carregando o identificador tentado. Mensagem sem valor de entrada do usuário

### Caso de uso (teste antes)

- [X] T038 [US2] Criar `RevokeApiKeyUseCaseTest` em `…/test/modules/app/application/usecases/RevokeApiKeyUseCaseTest.java` — sobre `InMemoryApiKeyRepository`: caminho feliz (chave passa a revogada, `revokedAt` preenchido); chave inexistente → `ApiKeyNotFound`; **identificador fora do formato UUID → `ApiKeyNotFound`, não `api_key.id_invalid`** — vale para os dois identificadores do `Input` (D-06); **chave de outra aplicação → `ApiKeyNotFound`**, não 403; chave já revogada → `code api_key.already_revoked` com o `revokedAt` original preservado; **corrida**: com o fake devolvendo `false` no `revoke` (alguém revogou antes), o caso de uso relê e lança `api_key.already_revoked` em vez de sobrescrever o instante (D-05, FR-012); outra chave da mesma aplicação permanece inalterada (FR-016)
- [X] T039 [US2] Implementar `RevokeApiKeyUseCase` em `…/main/modules/app/application/usecases/RevokeApiKeyUseCase.java` — `UseCaseWithoutOutput<Input>`, fluxo dos 5 passos de [data-model.md](./data-model.md), com o `try/catch (DomainException)` da conversão dos dois identificadores traduzindo para `ApiKeyNotFound`; `@Slf4j` logando id da chave e da aplicação. Sem `Transactor`: o `update` condicional já é atômico

### Borda HTTP

- [X] T040 [US2] Acrescentar o `DELETE` a `ApiKeyControllerSwagger` (`…/main/modules/app/infra/http/controllers/`) — `@ApiResponse` para **204**, **404** (`api_key.not_found`, cobrindo inexistente, de outra aplicação e malformado) e **409** (`api_key.already_revoked`), fielmente ao contrato
- [X] T041 [US2] Acrescentar o `DELETE /{apiKeyId}` a `ApiKeyController` (`…/main/modules/app/infra/http/controllers/`) — devolve `204 No Content`, sem corpo, sem `try/catch`
- [X] T042 [US2] Registrar `@Bean RevokeApiKeyUseCase` em `…/main/modules/app/infra/config/UseCasesConfiguration.java`

### E2E

- [X] T043 [US2] Criar `RevokeApiKeyE2ETest` em `…/test/modules/app/infra/http/controllers/RevokeApiKeyE2ETest.java` — `@E2E`, `@DisplayName("DELETE /applications/{applicationId}/api-keys/{apiKeyId}")`, `DatabaseCleaner` no `@BeforeEach`, sem `@Transactional`. Cobre: 204 no caminho feliz **com a linha relida mostrando `revoked_at` preenchido**; 409 (`api_key.already_revoked`) na segunda chamada, **com o `revoked_at` idêntico ao da primeira** (FR-012); 404 (`api_key.not_found`) para chave inexistente, identificador fora do formato UUID e chave de outra aplicação — os três com o mesmo `code`, indistinguíveis para quem chama (D-06); e que a segunda chave da mesma aplicação segue com `revoked_at` nulo
- [X] T044 [US2] Acrescentar ao `RevokeApiKeyE2ETest` a asserção de SC-007: depois da revogação, `label`, `prefix` e `created_at` continuam intactos na linha, e `secret_hash` segue sendo o único vestígio do segredo

**Checkpoint**: US1 e US2 completas e independentes. `./mvnw verify` verde.

---

## Phase 5: Polish & Cross-Cutting

- [X] T045 [P] Atualizar `README.md` — a resposta HTTP passa a ser montada por presenter; documentar os dois endpoints novos e os `code`s de erro que chegam ao cliente (`application.not_found`, `application.inactive`, `api_key.not_found`, `api_key.already_revoked`, `api_key.label_invalid`). Os `code`s de identificador (`application.id_invalid`, `api_key.id_invalid`) **não** entram: são internos, capturados pelo caso de uso e traduzidos em 404
- [X] T046 [P] Atualizar `AGENTS.md` — remover `Presenter` da lista "o que ainda não existe" e registrar `modules/app/infra/http/presenters` como o lugar dele
- [X] T047 Emenda **PATCH** da constituição (`.specify/memory/constitution.md`, 1.0.0 → 1.0.1), em commit próprio: remover do Princípio IV a frase "*este é o único ponto ainda não implementado…*", remover o TODO correspondente do Sync Impact Report, e atualizar `Last Amended`. Aproveitar para alinhar o Princípio I, que lista `Presenter` entre as portas da camada `application`, com o Princípio IV, que a declara em `core/presenter` — o código segue o IV. Sem esta tarefa a constituição fica descrevendo como pendente algo que já existe no repositório
- [X] T048 Rodar as checagens de conformidade de [quickstart.md](./quickstart.md) — o `grep` de framework em `core`/`domain`/`application` e o `grep` de `plainSecret` em `infra/database` devem sair vazios
- [X] T049 Conferir o OpenAPI gerado em `/api/swagger-ui.html` contra [contracts/api-keys.openapi.yaml](./contracts/api-keys.openapi.yaml) — todo status declarado, nenhum a mais, nenhum a menos
- [X] T050 Executar a validação manual de [quickstart.md](./quickstart.md) de ponta a ponta, incluindo a inspeção da tabela `api_keys` no psql
- [X] T051 Rodar `./mvnw verify` — portão obrigatório: verde, sem teste ignorado ou desabilitado

---

## Dependencies & Execution Order

### Entre fases

- **Phase 1 (Setup)**: sem dependência
- **Phase 2 (Foundational)**: depende de T001 — **bloqueia as duas histórias**
- **Phase 3 (US1)** e **Phase 4 (US2)**: dependem só da Phase 2; entre si, são independentes
- **Phase 5 (Polish)**: depende das histórias que se quer entregar

### Entre histórias

- **US1 (P1)**: começa assim que a Phase 2 fecha. Não depende de US2
- **US2 (P2)**: começa assim que a Phase 2 fecha. Usa o endpoint de US1 apenas como conveniência
  para produzir chaves no E2E — se as duas forem tocadas em paralelo, o teste de US2 pode semear
  a chave direto pelo `ApiKeyJpaRepository`

### Dentro da Phase 2

```
T002, T003, T007, T008, T016, T017, T021  →  paralelos
T004 ← T002, T003   T005 ← T004        T006 ← T004
T009 ← T007         T010 ← T008        T011 ← T006, T009, T010
T012 ← T011         T013 ← T012        T014, T015 ← T013 (paralelos entre si)
T018 ← T012, T017   T019 ← T017        T020 ← T018, T019
T022 ← T021         T023 ← T022
```

A trilha da identidade (T002–T006) é a primeira: `ApiKeyId` é pré-requisito de `ApiKey`, e a
alteração de `ApplicationId` toca código já existente — melhor descobrir cedo se ela quebra algo.

### Dentro de cada história

- Teste antes da implementação, e **falhando** antes dela — sempre
- Erros → caso de uso → DTO → presenter → Swagger → controller → `@Bean` → E2E

### Oportunidades de paralelismo

- **Phase 2**: T002, T003, T007, T008, T016, T017 e T021 são sete arquivos independentes
- **Phase 2**: a trilha do presenter (T021 → T022 → T023) é independente da trilha da chave e pode
  correr ao lado dela do começo ao fim
- **Phase 2**: T004 é o gargalo da identidade — T005 e T006 saem juntos depois dele
- **US1**: T024 e T025 em paralelo; T030 em paralelo com T028/T029
- **US1 × US2**: fases inteiras em paralelo, com uma ressalva — T033/T041 e T034/T042 tocam os
  mesmos dois arquivos (`ApiKeyController` e `UseCasesConfiguration`) e precisam ser serializados
- **Phase 5**: T045 e T046 em paralelo; T047 é commit próprio e não conflita com nenhum

---

## Parallel Example: Phase 2

```bash
# Os testes de identidade e de domínio e os arquivos de infra que não dependem de nada:
Task: "ApplicationIdTest em …/test/modules/app/domain/entities/ApplicationIdTest.java"      # T002
Task: "ApiKeyIdTest em …/test/modules/app/domain/entities/ApiKeyIdTest.java"                # T003
Task: "ApiKeyLabelTest em …/test/modules/app/domain/valueobjects/ApiKeyLabelTest.java"      # T007
Task: "ApiKeySecretTest em …/test/modules/app/domain/valueobjects/ApiKeySecretTest.java"    # T008
Task: "Migration V20260908120000__create_api_keys.sql"                                      # T016
Task: "ApiKeyJpaEntity em …/main/modules/app/infra/database/jpa/entities/"                  # T017
Task: "Presenter em …/main/core/presenter/Presenter.java"                                   # T021
```

---

## Implementation Strategy

### MVP primeiro (só US1)

1. Phase 1 — linha de partida verde
2. Phase 2 — fundação (**bloqueia tudo**)
3. Phase 3 — emitir chave
4. **PARE E VALIDE**: passos 1 a 4 do [quickstart.md](./quickstart.md). Uma aplicação já pode ser
   integrada. Uma chave vazada ainda não tem remédio — é exatamente o que US2 resolve
5. Entregar/demonstrar

### Entrega incremental

1. Setup + Foundational → fundação pronta (e a dívida do presenter paga)
2. US1 → validar sozinha → entregar (MVP)
3. US2 → validar sozinha → entregar
4. Polish

### Com mais de uma pessoa

Fechem a Phase 2 juntas — ela é o gargalo real, e a trilha do presenter (T021–T023) pode ser
tocada por quem não estiver no domínio da chave. Depois, uma pessoa por história, combinando
antes quem encosta em `ApiKeyController` e em `UseCasesConfiguration`.

---

## Notes

- Nenhuma dependência nova entra no `pom.xml`. Se alguma tarefa parecer pedir uma, releia D-03
- Mock sobre porta do projeto é proibido: os testes de caso de uso usam os fakes de `testsupport`
- Asserção sobre **mensagem** só em T028 e nos E2E — nos demais, `type` e `code`
- Todo `code` é constante `private static final` no tipo que o lança
- `application.id_invalid` e `api_key.id_invalid` existem para o teste de domínio e **nunca**
  chegam ao cliente: o caso de uso os captura e traduz em "não encontrado" (D-06)
- Entrada do usuário (rótulo, segredo, identificador tentado) **nunca** vai para o log
- Commit por tarefa ou por grupo lógico; parar em qualquer checkpoint é seguro
