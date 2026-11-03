# Shared

Código usado por duas ou mais features. Promova para cá apenas quando houver um segundo
consumidor real — abstração especulativa é rejeitada na revisão (ver Governança na
constituição).

- `components/` — componentes compostos e agnósticos de feature
- `hooks/` — hooks reutilizáveis
- `lib/` — funções puras e helpers

Primitivos de UI **não** vão aqui: eles vivem em `src/components/ui/`, gerados pelo shadcn/ui.
