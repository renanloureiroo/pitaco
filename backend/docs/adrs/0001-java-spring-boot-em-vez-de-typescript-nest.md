---
tipo: adr
numero: 0001
status: aceito
data: 2026-09-05
supera:
superado_por:
tags:
  - pitaco
  - adr
  - backend
---

# ADR-0001 — Java 21 e Spring Boot no backend, em vez de TypeScript e NestJS

> O backend do Pitaco é escrito em Java 21 com Spring Boot. TypeScript com NestJS
> era a escolha esperada e foi recusada. A decisão é sobre linguagem e framework;
> o PostgreSQL não está em discussão aqui.

## Contexto

O Pitaco é uma plataforma de pesquisas embutidas em aplicações de terceiros: um
SDK dispara pesquisas a partir de eventos do app hospedeiro, e o backend decide
elegibilidade, serve o schema da pesquisa e coleta as respostas. O domínio não é
trivial — pesquisa versionada, comparabilidade entre versões, política de
retenção, idempotência de reenvio vindo de fila local no device.

É um projeto pessoal. Não há time a acomodar, não há legado a integrar e não há
prazo externo. Isso amplia o que pode entrar legitimamente na decisão: numa
escolha de time, "quero praticar isto" não é argumento; aqui é.

Dois fatos sobre o autor pesaram e precisam estar registrados, porque sem eles a
decisão parece arbitrária:

- Java estava enferrujado. Havia interesse explícito em voltar a exercitá-lo em
  algo real, não em exercício de estudo.
- O GitHub pessoal tem poucos projetos em Java/Spring Boot. O Pitaco é o tipo de
  projeto — domínio não trivial, versionamento de schema, privacidade, SDK — que
  demonstra mais do que um CRUD demonstra.

## Alternativas consideradas

### TypeScript com NestJS

Era a opção mais forte pelos critérios usuais, e continua sendo. O que ela tinha:

- **Familiaridade.** Stack já dominado. As primeiras features sairiam sem curva
  nenhuma, e as decisões de arquitetura seriam tomadas com repertório pronto.
- **Velocidade inicial.** Menos cerimônia, ciclo de feedback mais curto, boot
  quase instantâneo.
- **Custo de infra.** Footprint de memória menor e deploy mais simples e barato
  em container pequeno ou serverless — relevante para um projeto pessoal, que
  paga a própria conta.
- **Uma linguagem só.** O SDK web do Pitaco é TypeScript de qualquer forma. Nest
  no backend permitiria tipos compartilhados de ponta a ponta.

### Java 21 com Spring Boot

- **Maturidade do ecossistema.** Migração, persistência, testes de integração e
  observabilidade vêm integrados e com uma escolha por problema, em vez de montar
  a mesma pilha juntando bibliotecas.
- **Exercita o que precisava ser exercitado** — o motivo pessoal acima.

## Decisão

**Java 21 com Spring Boot.**

A razão que decidiu foi pessoal, não técnica: desenferrujar Java num projeto de
verdade e ter um projeto Java/Spring de peso no portfólio. A maturidade do
ecossistema foi o que tornou essa escolha defensável em vez de apenas desejada —
ela paga parte do custo que o Node economizaria.

Não vale fingir que Java venceu num comparativo técnico. Ele não venceu: pelos
critérios de entrega, o Nest era a escolha mais eficiente, e a seção acima diz
por quê. O que aconteceu é que o critério de entrega não era o único critério
deste projeto.

O que já está no repositório reflete a decisão: Spring Boot 4.1.1 sobre Java 21,
Spring Data JPA, Flyway, Redis, Actuator com OpenTelemetry, springdoc para a
OpenAPI, Testcontainers para os testes de integração, e a JVM fixada em UTC no
plugin de build e no Surefire.

## Consequências

**Aceitamos:**

- **Curva de aprendizado.** Parte do tempo do projeto vai para reaprender Java e
  aprender Spring, não para features. Isso é o objetivo, mas continua sendo custo:
  as primeiras entregas serão mais lentas do que seriam em Nest.
- **Duas linguagens no stack.** O SDK web continua em TypeScript. Não há tipo
  compartilhado entre backend e SDK; o contrato passa a ser a OpenAPI, e mantê-la
  fiel vira responsabilidade explícita, não consequência do compilador.
- **Infra mais cara.** JVM consome mais memória que Node em repouso. Para o
  volume esperado do Pitaco isso não é problema de desempenho, é linha na fatura.
- **Boot mais lento no ciclo local.** Mitigado pelo devtools, não eliminado.

**Ganhamos:**

- Uma escolha por problema em vez de uma montagem: JPA, Flyway, Testcontainers e
  Actuator/OpenTelemetry se integram sem cola escrita à mão.
- Tipagem que sobrevive ao runtime. Value objects como `Slug` e `Name` são tipos
  de verdade — validam na construção e não podem ser confundidos com a `String`
  que carregam, ao contrário de um alias de tipo do TypeScript, que some na
  compilação.

**Muda no repositório:**

- O schema do banco é versionado em SQL com Flyway, em
  `backend/src/main/resources/db/migration`. Não há schema declarado em código de
  aplicação nem geração de migração a partir dele.
- O contrato do SDK e do painel é a OpenAPI publicada pelo springdoc. É o único
  ponto de acordo entre backend e SDK, e por isso precisa ser tratado como
  entregável, não como subproduto.

## Quando revisitar

Se o Pitaco deixar de ser projeto pessoal — outra pessoa entrando no código, ou
ele virando produto com usuário pagante —, o motivo que decidiu isto deixa de
valer, e a decisão precisa ser refeita pelos critérios de entrega. Reescrever o
backend continuaria caro; o ponto é que a justificativa registrada aqui não
sustentaria mais a escolha, e o ADR que a substituir precisa dizer o que passou a
sustentá-la.
