# Como contribuir

Obrigado pelo interesse no Pitaco. Este guia é curto de propósito: o detalhe de cada parte está
nos documentos que ele aponta.

## Ambiente

Siga a seção [Como rodar localmente](README.md#como-rodar-localmente) do README. Cada parte tem
o próprio guia:

- Backend: [`backend/README.md`](backend/README.md) e [`backend/AGENTS.md`](backend/AGENTS.md)
- Painel: [`painel/README.md`](painel/README.md) e [`painel/DESIGN.md`](painel/DESIGN.md)
- SDK: [`sdk/README.md`](sdk/README.md) e [`sdk/example/README.md`](sdk/example/README.md)

Antes de abrir um pull request, rode o portão da parte que você mexeu. É o mesmo que o CI roda:

| Parte | Comando |
| --- | --- |
| Backend | `cd backend && ./mvnw verify` |
| Painel | `cd painel && npm run verify` |
| SDK | `cd sdk && npm run verify` |

## Regras de arquitetura

O backend e o painel têm uma constituição com princípios obrigatórios e portões de qualidade, em
`backend/.specify/memory/constitution.md` e `painel/.specify/memory/constitution.md`. As mais
importantes no backend:

- Nada de Spring, JPA ou HTTP em `core` nem em `modules/*/domain`.
- Mudança de schema entra como migration nova; migration aplicada não se edita.
- Endpoint novo ou alterado vem com OpenAPI completo e teste E2E.

Funcionalidade nova passa pelo fluxo de especificação do [Spec Kit](https://github.com/github/spec-kit):
spec, plano e tarefas em `backend/specs/` ou `painel/specs/`, numeradas em sequência
(`NNN-nome-da-funcionalidade`).

## Branches

Crie a branch a partir da `main`, no formato `tipo/descricao-curta`, por exemplo
`infra/deploy-homelab` ou `fix/seed-campo-nulo`. Funcionalidade que nasce de uma spec usa o nome
da spec, por exemplo `003-survey-authoring`.

## Commits

O histórico segue [Conventional Commits](https://www.conventionalcommits.org/pt-br/), com a
descrição em português:

```text
tipo(escopo opcional): descrição curta no presente
```

- Tipos em uso: `feat`, `fix`, `refactor`, `docs` e `chore`.
- Escopos em uso: `painel`, `seed` e `ops`. Use o nome da parte que o commit toca (`backend`,
  `painel`, `sdk`, `seed`, `ops`).

Exemplos do histórico:

```text
feat(seed): demo completa como dump SQL carregado em segundos
fix(painel): build do Docker sem segfault no npm
docs(ops): guia de publicação no homelab
```

## Issues

- **Bug:** descreva o que aconteceu, o que você esperava e como reproduzir. Diga qual parte
  (backend, painel ou SDK) e a versão ou commit. Para o SDK, inclua plataforma (iOS ou Android) e
  a versão do React Native ou do Expo.
- **Sugestão de funcionalidade:** explique o problema antes da solução. Para mudança grande, abra
  a issue e espere a conversa antes de escrever código.
- **Vulnerabilidade:** não abra issue pública. Siga o [`SECURITY.md`](SECURITY.md).

## Pull requests

1. Faça um fork e crie a branch a partir da `main`.
2. Mantenha o PR focado em uma mudança só.
3. Rode o portão da parte alterada (tabela acima) e garanta que ele passa.
4. Abra o PR para a `main` explicando o que muda, por quê e como testar. Se o PR resolve uma
   issue, cite-a (`Closes #123`).
5. Uma violação consciente de alguma regra da constituição precisa estar justificada no PR, com
   o motivo e o custo da alternativa.

Ao contribuir, você concorda que sua contribuição seja distribuída sob a [licença MIT](LICENSE)
do projeto.
