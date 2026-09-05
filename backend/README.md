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
| Cache | Redis |
| Observabilidade | OpenTelemetry + stack Grafana LGTM |
| Documentação | springdoc-openapi (Swagger UI) |
| Testes | JUnit 5 + Testcontainers |
| Build | Maven (wrapper incluso) |

---

## Arquitetura

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

---

## Rodando localmente

**Pré-requisitos:** JDK 21 e Docker.

```bash
# 1. crie sua config local a partir do exemplo
cp src/main/resources/application-local.yml.example src/main/resources/application-local.yml

# 2. suba a aplicação (Postgres, Redis e Grafana LGTM sobem junto)
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

```bash
./mvnw test           # suíte completa
./mvnw verify         # testes + empacotamento
```

Os testes de integração usam Testcontainers (`TestcontainersConfiguration`) para Postgres, Redis e a stack LGTM — Docker precisa estar rodando, e nenhum serviço externo é necessário.

---

## Build

```bash
./mvnw clean package
java -jar target/pitaco-0.0.1-SNAPSHOT.jar
```

---

## Convenções

- **Migrations** ficam em `src/main/resources/db/migration`, no padrão Flyway `V<yyyyMMddHHmmss>__descricao.sql` — o timestamp (em UTC) evita que duas branches disputem o mesmo número de versão. `ddl-auto` é `validate`: o schema nasce da migration, nunca do Hibernate.
- **Javadoc** documenta a decisão, não a assinatura. Se o comentário só repete o nome do método, ele não precisa existir.
- **Rotas** não repetem o prefixo: ele vem do `context-path`. Escreva `@RequestMapping("/pitacos")`, não `@RequestMapping("/api/pitacos")`.
- **Nada de Spring no `core`.** Anotação de framework, `jakarta.persistence`, `HttpStatus` — tudo isso vive em `infra`.
