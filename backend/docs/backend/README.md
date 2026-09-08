# Documentação do backend

| Documento | O que traz |
| --- | --- |
| [Arquitetura](arquitetura.md) | as camadas e a direção da dependência, as peças do núcleo (`Id`, `Entity`, `ErrorType`, `UseCase`), o fluxo de uma requisição ponta a ponta e o manual de criação de módulo, caso de uso, erro e endpoint |
| [Testes](testes.md) | os seis tipos de teste do projeto, o ferramental de `testsupport`, a receita de cada tipo e o mínimo obrigatório de um endpoint |

Complementam:

- [`../../.specify/memory/constitution.md`](../../.specify/memory/constitution.md) — os princípios obrigatórios, em forma de regra.
- [`../adrs`](../adrs) — as decisões tomadas e o contexto de cada uma.

**A divisão:** a constituição diz *o que é obrigatório*, os ADRs dizem *por que se
decidiu assim*, e estes documentos dizem *como fazer*. Quando divergirem, a
constituição vence e o documento está desatualizado.
