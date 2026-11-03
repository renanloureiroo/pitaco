# Features

Cada subdiretório aqui é uma feature autocontida, conforme o Princípio I da constituição
(`.specify/memory/constitution.md`).

## Estrutura de uma feature

```
src/features/<feature>/
├── components/     # componentes de UI específicos da feature
├── hooks/          # hooks específicos da feature
├── api/            # acesso a dados (server actions, fetchers)
├── schemas/        # validação de entrada externa
├── __tests__/      # testes Vitest da feature
└── index.ts        # ÚNICA fronteira pública da feature
```

## Regras

- Outras features importam **apenas** de `@/features/<feature>` (o `index.ts`).
  Importar `@/features/<feature>/components/Foo` é violação.
- Features não podem depender ciclicamente umas das outras.
- Código usado por duas ou mais features sobe para `src/shared/`
  (ou `src/components/ui/`, se for primitivo de UI).
- `src/app/` apenas roteia e compõe features; nunca implementa regra de negócio.
