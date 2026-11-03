# Phase 0 — Pesquisa e decisões: Chaves de API

**Feature**: `001-api-key-management` · **Data**: 2026-09-08

Nenhum `NEEDS CLARIFICATION` restou da Technical Context: a stack é fixa pela constituição
e a única pergunta aberta da spec (semântica da exclusão) foi resolvida em `/speckit-clarify`
como **revogação com trilha preservada**. As decisões abaixo são as escolhas de projeto que
a spec deliberadamente não fez.

---

## D-01 — Onde a chave nasce: dentro de `modules/app`

**Decisão**: `ApiKey` nasce no módulo existente `modules/app`, ao lado de `Application`, e não
em um módulo `modules/apikey` próprio. A rota é aninhada: `/applications/{applicationId}/api-keys`.

**Rationale**: nesta fatia a chave não tem vida fora da aplicação — ela é emitida no contexto
da aplicação, é gerenciável só nesse contexto (edge case da spec) e depende do estado
ativo/inativo da aplicação a cada emissão. Um módulo próprio exigiria que `apikey/application`
falasse com a porta `ApplicationRepository` de outro módulo, ou que `ApplicationId` subisse
para `core` — e a constituição é explícita: `core` é para o que **dois ou mais** módulos usam,
e abstração entra com o **segundo** caso concreto, nunca por antecipação.

**Alternativas consideradas**:
- *Módulo `modules/apikey` com `ApplicationId` em `core`*: rejeitada. Move um tipo do domínio de
  aplicações para o núcleo compartilhado com um único consumidor, e cria acoplamento
  cross-module logo no primeiro caso.
- *Módulo `modules/auth`*: rejeitada por antecipação — o contexto de autenticação só existe
  quando a verificação de chave existir, e ela está fora de escopo. Quando nascer, a extração
  de `ApiKey` para lá é um movimento de arquivos, sem mudança de regra.

---

## D-02 — Formato do segredo e do prefixo público

**Decisão**: o segredo em claro tem o formato `pit_<prefix>_<random>`, onde `prefix` são 8
caracteres e `random` é a codificação Base64 URL-safe (sem padding) de 32 bytes de
`SecureRandom`. O **prefixo público** armazenado e devolvido é `pit_<prefix>` — não sensível,
livre para log e resposta (FR-008). O trecho `random` nunca é armazenado.

**Rationale**: 256 bits de entropia tornam a adivinhação inviável (FR-005). O prefixo legível
permite a quem administra reconhecer de qual chave se fala sem expor o segredo, e dá à futura
verificação um caminho de busca indexado sem varrer a tabela. O sufixo `pit_` é o marcador que
scanners de segredo em repositórios usam para reconhecer credencial vazada.

**Alternativas consideradas**:
- *UUID como segredo*: rejeitada. Entropia menor e previsibilidade dependente da implementação.
- *Segredo sem prefixo estruturado*: rejeitada. Sem prefixo público, a única forma de falar
  sobre uma chave específica seria o identificador — que quem integra não guarda ao lado do
  segredo na prática.

---

## D-03 — Armazenamento irreversível: SHA-256, sem dependência nova

**Decisão**: guarda-se `sha256(segredo em claro)` em hexadecimal (64 caracteres), via
`java.security.MessageDigest` do JDK. Nenhuma dependência nova entra no `pom.xml`.

**Rationale**: hash de senha (bcrypt, Argon2) existe para resistir a ataque de dicionário sobre
segredo escolhido por humano, com baixa entropia. Aqui o segredo é gerado pelo sistema com 256
bits de aleatoriedade: não há dicionário que o alcance, e o custo deliberado de um KDF viraria
latência em cada requisição autenticada quando a verificação nascer. SHA-256 é o padrão de fato
para chave de API por esse motivo. A constituição exige justificativa no PR para dependência
nova — esta decisão evita a discussão inteira.

**Alternativas consideradas**:
- *`spring-security-crypto` com BCrypt*: rejeitada. Dependência nova, custo por verificação e
  nenhum ganho de segurança para segredo de alta entropia.
- *Guardar cifrado em vez de hasheado*: rejeitada. Cifra é reversível — violaria FR-006 e FR-007
  diretamente, e traria gestão de chave de cifra para dentro da fatia.

---

## D-04 — Quem gera o segredo: o próprio domínio

**Decisão**: a geração vive em `ApiKeySecret.generate()`, no domínio, sobre `SecureRandom` e
`MessageDigest` — ambos do JDK, sem framework. `ApiKey.issue(...)` devolve um
`ApiKey.Issued(ApiKey apiKey, String plainSecret)`: a entidade guarda apenas prefixo e hash, e o
segredo em claro trafega fora dela, de volta ao caso de uso e daí à resposta.

**Rationale**: manter o segredo em claro fora do estado da entidade é o que torna FR-007
estrutural em vez de disciplinar — não existe getter para vazar, nem campo para um mapper
persistir por engano. E resolve `toString()`/log de graça.

**Alternativas consideradas**:
- *Porta `SecretGenerator` na camada `application`, adaptador em `infra`*: rejeitada por
  antecipação. Há um único algoritmo e nenhum segundo caso concreto; a constituição barra a
  abstração até ele existir. Testabilidade não sofre: o teste gera e confere que o hash do
  segredo devolvido bate com o armazenado.

---

## D-05 — "Já revogada": regra no domínio, corrida resolvida no `update`

**Decisão**: `ApiKey.revoke()` é a única autoridade sobre a transição — lança
`DomainException(CONFLICT, "api_key.already_revoked", ...)` quando já há `revokedAt`. A
persistência usa **update condicional** (`... set revoked_at = :now where id = :id and revoked_at
is null`) via `@Modifying @Query` no Spring Data. Quando o update não afeta linha alguma, o caso
de uso relê a chave e chama `revoke()` de novo na instância relida — que então lança o erro do
domínio, com o mesmo `code`.

**Rationale**: cobre o edge case de duas exclusões simultâneas (FR-012: o instante gravado é o da
primeira, e não muda) sem duplicar a regra nem o `code` em dois lugares. O `code` continua sendo
constante `private static final` de um único tipo.

**Alternativas consideradas**:
- *`@Lock(PESSIMISTIC_WRITE)` na leitura*: rejeitada. Exigiria transação explícita em volta do
  caso de uso — e a constituição reserva o `Transactor` para escrita em mais de um agregado.
  Um `update` condicional já é atômico sozinho.
- *`@Version` (optimistic locking)*: rejeitada. A colisão vira `OptimisticLockException`, que
  chega ao cliente como `internal.unexpected` (500) em vez do 409 que a spec pede.
- *Checagem de `isRevoked()` no caso de uso*: rejeitada. Tiraria a regra do domínio e ainda
  assim perderia a corrida.

---

## D-06 — Identificador malformado responde 404, não 400

**Decisão**: `ApplicationId` e `ApiKeyId` passam a validar o formato UUID na construção —
`Id` ganha o predicado `protected static boolean isUuid(String)` e cada subclasse lança o
`DomainException(VALIDATION, "<contexto>.id_invalid", …)` com o próprio `code`. O caso de uso
recebe os identificadores como `String` no `Input` e os converte dentro de um `try/catch` sobre
`DomainException`, traduzindo falha de formato para `ApplicationNotFound` / `ApiKeyNotFound`.

**Rationale**: a spec pede explicitamente que "malformado" e "inexistente" sejam
indistinguíveis para quem chama — distingui-los entrega ao atacante um oráculo de formato. Isso
não é `try/catch` traduzindo erro para HTTP (o que a constituição proíbe na borda): é o caso de
uso decidindo que a ausência de recurso engloba o identificador que não pode existir.

A validação vive na subclasse, não em `Id`: o `Id` do core é deliberadamente opaco e não impõe
formato a identificador nenhum. Quem escolhe UUID é o agregado — e `Id.newValue()` já gerava
UUID, então o predicado apenas torna a escolha verificável dos dois lados. Os `code`s
`application.id_invalid` e `api_key.id_invalid` **nunca chegam ao cliente**: existem para o teste
de domínio e são capturados pelo caso de uso antes da borda.

**Alternativas consideradas**:
- *Validar formato no DTO com `@Pattern`*: rejeitada. Devolveria 400 e revelaria o formato
  interno do identificador — que o `Id` do core define deliberadamente como opaco.
- *Validar UUID no próprio `Id`*: rejeitada. Prenderia todo identificador futuro a UUID, quando
  a razão de ser do `Id` é justamente não ter formato. A coluna do banco (`varchar(64)`, não
  `uuid`) foi escolhida com o mesmo cuidado.
- *Não validar nada, contando com o `findById` vazio*: rejeitada. Funciona por acidente — o 404
  sairia da consulta, não da regra —, e o dia em que a busca ganhar cache ou índice parcial o
  identificador lixo passa a bater no banco sem necessidade.

## D-07 — O `Presenter` entra aqui

**Decisão**: esta feature introduz `core/presenter/Presenter.java` e o primeiro presenter de
infra, e migra `CreateApplicationResponseDTO.from(...)` para esse formato.

**Rationale**: não é escolha — é dívida registrada. O Princípio IV da constituição marca o
presenter como o único ponto ainda não implementado e determina que "o primeiro endpoint escrito
sob esta constituição" o introduza e faça a migração. Este é esse endpoint. Deixar para depois
tornaria a constituição aspiracional, que é justamente o que ela declara não ser.

---

## D-08 — Índices e constraints da tabela

**Decisão**: `api_keys` nasce com chave estrangeira para `applications(id)`, índice em
`application_id`, índice em `prefix` e `unique` em **`secret_hash`** — não em `prefix`.

**Rationale**: `application_id` participa do filtro do `DELETE` (a chave só é gerenciável no
contexto da aplicação que a emitiu) e da junção — a constituição exige índice na mesma migration
que introduz a consulta.

A unicidade fica no hash porque é ele a identidade real do segredo: com 256 bits de entropia,
colisão de `secret_hash` é inatingível na prática, e a constraint é a rede de proteção que garante
que dois segredos distintos nunca se confundam quando a verificação nascer. O `prefix` é uma
**dica pública**, não um identificador — quem identifica é o `id`. É como Stripe e GitHub tratam
o assunto: o prefixo (`sk_live_`, `ghp_`) marca tipo e ambiente e é deliberadamente não-único; a
dica que distingue duas chaves numa listagem (`sk_live_…c5d6`) convive com repetição sem
problema, porque a linha tem chave primária própria.

Isso remove um caminho de erro inteiro: com `unique (prefix)`, uma colisão de 8 caracteres
aleatórios viraria `DataIntegrityViolationException` → `internal.unexpected` (500), um status não
declarado no OpenAPI e em conflito direto com FR-018 e SC-004, que exigem código estável em todo
caminho de recusa. Sem a constraint, não há colisão a tratar, nem retry a escrever, nem tentativa
esgotada a nomear. SC-007 continua de pé: a trilha de investigação é `id` + `prefix` + `label`,
e o `id` já é único por construção.

**Alternativas consideradas**:
- *`unique (prefix)` com retry na geração*: rejeitada. Paga lógica de retry, um teste de colisão
  forçada e um erro novo para o estouro das tentativas, tudo para tornar único um campo que o
  mundo real trata como não-único. A constituição barra a complexidade que não se paga.
- *`unique (prefix)` sem retry, aceitando o 500*: rejeitada. Deixaria FR-018 e SC-004 com uma
  exceção documentada — e uma exceção documentada é uma regra que o próximo endpoint vai copiar.
- *Sem índice, adicionando quando a listagem existir*: rejeitada. A consulta já existe nesta
  fatia — é o `findByIdAndApplicationId` da revogação.
