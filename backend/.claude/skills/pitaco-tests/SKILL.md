---
name: "pitaco-tests"
description: "Roteiro para escrever testes no backend do Pitaco — value object, entidade, caso de uso, DTO, componente de borda, adaptador ou E2E. Use ao criar ou alterar qualquer coisa em backend/src/test, e antes de escrever o código que o teste vai cobrir."
argument-hint: "O que vai ser testado (ex.: 'caso de uso de arquivar pesquisa')"
user-invocable: true
disable-model-invocation: false
---

Roteiro de escrita de teste no backend do Pitaco. As regras invioláveis estão em
`backend/AGENTS.md`; as **receitas completas, com exemplo de cada tipo**, estão em
`docs/backend/testes.md` §5–§11 — leia a seção do tipo que você vai escrever antes
de começar.

## Duas decisões que explicam todo o resto

**Fake, não mock.** Mock sobre porta do projeto é proibido. Toda porta tem
implementação de teste real em `testsupport/` (`InMemoryApplicationRepository`,
`DirectTransactor`) — se a porta que você precisa não tem, escreva o fake primeiro.
Um mock afirma o que você *acha* que o colaborador faz; um fake **é** um colaborador
que faz, e continua vermelho quando o contrato muda.

> Exceção: tipo de biblioteca de terceiro cujo comportamento não é do projeto (ex.:
> `Tracer` do Micrometer). Mockar a **sua** porta é fugir do contrato; mockar a
> fronteira de uma biblioteca é economizar setup.

**Afirme sobre `type` e `code`, nunca sobre a mensagem.** `code` é contrato público;
mensagem é texto para humano e melhora com o tempo. As únicas exceções são o teste
de DTO e o E2E, onde o contrato testado é justamente que a mensagem certa chega ao
campo certo.

## Escolha o tipo mais barato que prova o que você precisa

| Tipo | Ferramenta | Quando |
|---|---|---|
| value object / entidade | JUnit puro | invariante, transição de estado |
| caso de uso | JUnit + fake in-memory | orquestração, erro de negócio |
| DTO / validação | `Validation.buildDefaultValidatorFactory()` | constraints e `toInput()` |
| componente de borda | MockMvc `standaloneSetup` | `@RestControllerAdvice` isolado |
| adaptador | fake que registra eventos | integração com colaborador de framework |
| E2E | `@E2E` + `RestTestClient` | o contrato HTTP ponta a ponta |

Os cinco primeiros rodam em milissegundos, sem contexto Spring. **E2E não é lugar de
varrer combinação de entrada** — isso é trabalho do teste de value object ou de DTO.
E2E prova que as peças se encaixam.

## Convenções

- Classe `<Alvo>Test` / `<Alvo>E2ETest`, package-private (sem `public`)
- Método: frase em português, snake_case, descrevendo o comportamento
- `@DisplayName` na classe (o alvo) e nos métodos cujo nome não basta
- O objeto testado chama-se `sut` quando montado no `@BeforeEach`
- AssertJ sempre — `assertThat`, `assertThatThrownBy`. Nunca `assertTrue(a.equals(b))`
- Família de entrada → `@ParameterizedTest`. Para "ausente", o trio
  `@NullAndEmptySource` + `@ValueSource(strings = "   ")`
- Dado vem de factory (`ApplicationFactory.anApplication()`), nunca de construção
  manual repetida. Agregado novo → factory nova, com `build()`, `buildSavedIn(repo)`
  e `as<Uso>Input()`
- Corpo: montar, agir, afirmar, separados por linha em branco. Sem comentários
  `// given`

## O que cobrir, por tipo

**Value object** — cada formato aceito e rejeitado; ausência (null/vazio/branco);
**os dois lados de cada limite** no mesmo método (200 passa, 201 falha); `toString()`;
a derivação, se houver, incluindo o caso em que ela é impossível.

**Entidade** — o estado ao nascer; cada método de negócio (efeito **e** `touch()`);
cada invariante que ela guarda; `restore` preservando o que veio do banco.

**Caso de uso** — o caminho feliz completo; cada variação de entrada que muda o
resultado; cada erro lançado, usando `.extracting(...)` para afirmar sobre o dado do
erro nomeado. **Todo caminho de falha afirma também que o estado não mudou**
(`assertThat(repo.findAll()).containsExactly(existing)`) — sem isso o teste passa
mesmo se o caso de uso salvar antes de checar.

**DTO** — payload mínimo válido e completo; cada constraint violada **com a mensagem
exata**; o caso em que todos os campos falham de uma vez; `toInput()`.

**Componente de borda** — declare um controller de mentira no próprio teste que
lança o que se quer traduzir. Afirme também **o que a resposta não pode conter**
(ex.: que uma senha na mensagem da exceção não vaza).

**Adaptador** — fake que registra eventos numa lista; afirme a sequência
(`containsExactly("begin", "rollback")`) e use `isSameAs` para provar que a exceção
não é embrulhada.

**E2E** — o mínimo obrigatório de um endpoint:
- [ ] caminho feliz: status, corpo, `Location`, **e o estado lido de volta do banco**
- [ ] 400 de validação, com `$.code = "request.invalid"` e cada campo em `$.errors`
- [ ] cada erro anunciado no OpenAPI: status, `$.code` e `Content-Type`
      `application/problem+json`
- [ ] JSON malformado → 400
- [ ] em todo caminho de falha, que o estado não mudou (`count()` zero ou inalterado)

Verificar o banco depois da resposta é o que separa um E2E de um teste de
serialização.

## Armadilhas

- **`@Transactional` em E2E** — com `RANDOM_PORT` o servidor atende em outra thread;
  a transação do teste não vê o commit e o rollback não desfaz nada. Use
  `DatabaseCleaner` no `@BeforeEach`.
- **Mockar porta do projeto** — use o fake.
- **Afirmar sobre mensagem** fora de DTO e E2E.
- **Testar só o lado que falha** de um limite — sem o lado que passa, o teste não
  prova onde o limite está.
- **Caminho de falha sem afirmar o estado** — passa com o bug presente.
- **Combinação de entrada em E2E** — segundos por caso para provar o que custa 1ms.
- **Carimbo de tempo comparado a valor fixo** — afirme
  `isAfterOrEqualTo(instanteAntesDaMutação)`.

## Rodando

```bash
./mvnw test                     # suíte completa (110 testes; Docker de pé)
./mvnw test -Dtest=SlugTest     # uma classe
./mvnw verify                   # o portão antes do PR
```

Sem Docker: `-Dtest='!*E2ETest,!ContextPathTest,!OpenApiConfigTest,!PitacoApplicationTests'`
(99 testes). As três últimas não têm o sufixo `E2ETest` mas sobem contexto — por
isso a lista.

## Antes de dar por pronto

- [ ] o teste falhou antes de existir o código que o satisfaz
- [ ] nenhum mock sobre porta do projeto
- [ ] asserção de erro sobre `type` e `code`
- [ ] dado vindo de factory
- [ ] os dois lados de cada limite
- [ ] caminho de falha afirma que o estado não mudou
- [ ] E2E cobre os cinco itens obrigatórios e lê o banco de volta
- [ ] nenhum teste `@Disabled` sem justificativa escrita
- [ ] `./mvnw verify` verde
