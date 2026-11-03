---
name: "pitaco-backend"
description: "Roteiro para escrever código no backend do Pitaco seguindo o padrão do projeto — criar módulo, caso de uso, entidade, value object, repositório, erro, DTO, presenter, controller ou migration. Use ao criar ou alterar qualquer código Java em backend/src/main."
argument-hint: "O que vai ser criado (ex.: 'caso de uso de arquivar pesquisa')"
user-invocable: true
disable-model-invocation: false
---

Roteiro de criação de código no backend do Pitaco. As regras invioláveis estão em
`backend/AGENTS.md`; os **exemplos completos de cada arquivo** estão em
`docs/backend/arquitetura.md` §6 — leia essa seção antes de escrever o primeiro
arquivo de um módulo novo, e sempre que a assinatura de uma peça estiver em dúvida.

## Antes de escrever

Responda três perguntas. Elas mudam tudo o que vem depois:

1. **É módulo novo ou já existe?** Módulo novo → percurso completo abaixo. Módulo
   existente → vá para "Caso de uso em módulo existente".
2. **O tipo novo é entidade ou value object?** Se dois objetos com todos os
   atributos iguais forem coisas *diferentes*, é entidade (estende `Entity<ID>`).
   Se forem *a mesma coisa*, é value object (`record`).
3. **O tipo vai para `core` ou para o módulo?** `core` só quando o **segundo**
   módulo precisa dele. Na dúvida, nasce no módulo.

Antes de criar qualquer peça, procure a equivalente em `modules/app` e siga-a. O
módulo `app` é a referência viva do padrão.

## Percurso completo — módulo novo

Nesta ordem. Cada passo com teste antes do código (veja a skill `pitaco-tests`).

1. **`domain/entities/<X>Id.java`** — estende `Id`, construtor privado,
   `generate()` e `of()`.
2. **`domain/valueobjects/*.java`** — `record` que valida no compact constructor,
   lançando `DomainException(ErrorType.VALIDATION, CODE, msg)`. Código do erro em
   constante privada. Factory `of(...)`. `toString()` devolve o valor cru.
3. **`domain/entities/<X>.java`** — estende `Entity<<X>Id>`, `@Getter`, construtor
   privado, `create(...)` e `restore(...)`, mutação por método de negócio que chama
   `touch()` no fim. Validação de campo em setter privado.
4. **`application/repositories/<X>Repository.java`** — interface pura, na linguagem
   do domínio. **Só os métodos que algum caso de uso chama.** Nada de CRUD por reflexo.
5. **`application/errors/`** — uma classe por erro recorrente, estendendo a família
   certa (`ConflictException`, `NotFoundException`, …), com `code` em constante e o
   dado relevante como campo acessível.
6. **`application/usecases/<Ação><X>UseCase.java`** — implementa `UseCase<I, O>`,
   `@Slf4j`, construtor recebendo as portas, `Input`/`Output` como `record`
   aninhados, `log.info` do sucesso no fim. **Zero anotação de framework.**
7. **Migration** `V<yyyyMMddHHmmss>__descricao.sql` com timestamp UTC. Constraint de
   unicidade no banco. Índice para todo campo que será consultado.
8. **`infra/database/jpa/`** — `entities/<X>JpaEntity` (Lombok `@Getter @Setter
   @NoArgsConstructor(access = PROTECTED) @AllArgsConstructor`), `mappers/<X>JpaMapper`
   (final, construtor privado, `toJpa`/`toDomain` estáticos, chamando `restore`),
   `repositories/<X>JpaRepository` (Spring Data) e `repositories/<X>RepositoryJpa`
   (`@Repository`, implementa a porta).
9. **`infra/http/dtos/<Ação><X>RequestDTO.java`** — `record` com Bean Validation.
   Cada mensagem de constraint **espelha** a do value object. Método `toInput()`.
10. **`infra/http/presenters/<Ação><X>Presenter.java`** — `final class`, construtor
    privado, um `public static ResponseDTO present(Output output)`. Mesmo molde do
    mapper JPA: tradução pura, sem estado, sem bean. Não existe interface `Presenter`.
11. **`infra/http/controllers/<X>ControllerSwagger.java`** — `@Tag`, `@Operation` e um
    `@ApiResponse` para **cada** status possível, com `ApiErrorResponse` /
    `ApiValidationErrorResponse` e media type `application/problem+json`.
12. **`infra/http/controllers/<X>Controller.java`** — implementa a interface, sem
    lógica: chama o caso de uso, passa o `Output` ao presenter, devolve. Criação →
    201 com `Location`.
13. **`infra/config/UseCasesConfiguration.java`** — um `@Bean` por caso de uso.

## Caso de uso em módulo existente

1. Método novo na porta de repositório, se precisar
2. Mesmo método no fake de `testsupport/repositories`
3. Teste do caso de uso, sobre o fake
4. O caso de uso
5. Implementação no adaptador JPA
6. DTO, presenter, `@ApiResponse`, método no controller
7. Teste E2E
8. `@Bean` no `UseCasesConfiguration`

## Erro novo

Existe um `ErrorType` que descreve a natureza? → só uma classe em
`application/errors/`.

Nenhum serve? → acrescente ao enum **e** ao mapeamento em `ErrorTypeHttpStatus`. O
`switch` de nível de log em `ApiExceptionHandler` é exaustivo e vai acusar. É
decisão de arquitetura: vale um ADR em `docs/adrs/`.

## Os erros que este projeto castiga

- `@Service` no caso de uso — cabeie por `@Bean`
- `import org.springframework` em `core` ou `domain`
- validar no caso de uso o que é invariante do domínio
- `try/catch` traduzindo erro para HTTP no controller
- `@RequestMapping("/api/x")` — o prefixo vem do `context-path`
- mapper chamando `create` em vez de `restore` (regera id e carimbos)
- fundir entidade de domínio com entidade JPA
- devolver `null` onde cabe `Optional`
- editar migration já aplicada
- `LoggerFactory` em vez de `@Slf4j`
- logar o valor rejeitado da entrada do usuário
- Javadoc que repete o nome do método

## Forma do presenter

```java
public final class IssueApiKeyPresenter {

  private IssueApiKeyPresenter() {}

  public static IssueApiKeyResponseDTO present(IssueApiKeyUseCase.Output output) {
    return new IssueApiKeyResponseDTO(output.id(), /* ... */ output.createdAt());
  }
}
```

O controller chama `IssueApiKeyPresenter.present(output)`. Não injete presenter, não
crie interface para ele: é função pura de tradução, como `ApiKeyJpaMapper`. Se um dia
um presenter precisar de dependência real, aí sim vira `@Component` injetado — quando o
caso concreto existir, não por antecipação.

## Transação

Declare a `@Transactional` **do projeto** (`core.transaction`) na classe do caso de uso,
nunca a do Spring e nunca escondida num repositório Spring Data. Quem a implementa é o
`TransactionalAdvisor`. Entra quando há duas escritas **ou** quando uma leitura e uma
escrita precisam do mesmo instante do banco. Um `save` sozinho não precisa.

## Antes de dar por pronto

- [ ] `./mvnw verify` verde
- [ ] nenhum import de framework em `core` / `domain`
- [ ] `@ApiResponse` para todo status possível
- [ ] mensagem do DTO espelha a do value object
- [ ] índice para todo campo consultado; sem N+1; listagem paginada
- [ ] README atualizado se alguma decisão arquitetural mudou
