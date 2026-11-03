---
description: "Task list — 002-demo-seed"
---

# Tasks: Demo completa com dados fictícios

**Input**: documentos de desenho em `ops/specs/002-demo-seed/`

**Prerequisites**: `plan.md`, `spec.md`, `research.md`, `data-model.md`, `contracts/carregar-demo.md`, `contracts/demo-dataset.md`

**Tests**: não há regra de negócio nova nem suíte automatizada para a demo. Em cada história, a verificação vem antes do arquivo que ela valida: as guardas do script (schema, duplicidade, uso) antes da carga, a conferência de distância entre instantes antes do deslocamento, e o roteiro do `quickstart.md` como prova de ponta a ponta.

**Organization**: agrupadas por história de usuário, para que cada uma seja implementável e verificável isoladamente.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: paralelizável (arquivo distinto, sem dependência pendente)
- **[Story]**: US1, US2, US3, US4
- Todo caminho é relativo à raiz do repositório

---

## Phase 1: Setup (Infraestrutura compartilhada)

**Purpose**: o lugar da demo no repositório.

- [X] T001 Criar a pasta `seed/` na raiz, fora das três partes, e retirar dela o gerador anterior pela API (`seed/demo-completo.ts`) e o `.gitignore` da saída dele (D-01)

---

## Phase 2: Foundational (Pré-requisitos bloqueantes)

**Purpose**: o conjunto de dados, sem o qual nenhuma história tem o que carregar.

**⚠️ CRITICAL**: nenhuma história pode começar antes desta etapa terminar.

- [X] T002 Gerar a demo num banco com o schema atual passando pelas rotas reais da API administrativa e do SDK (aplicações, chaves, pesquisas em todos os estados, versões, coleta com eventos, supressões, erros de SDK, exclusões de titular), com padrões por segmento (D-01)
- [X] T003 Exportar só os dados das aplicações `demo-completo*` com `pg_dump` 16 em texto, sem schema, nas 26 tabelas de negócio, e comprimir em `seed/demo-completo.sql.gz` (D-02)
- [X] T004 Conferir que o dump não traz `CREATE`, nem linha de aplicação fora do prefixo, nem segredo de chave em texto claro (só `prefix` e `secret_hash`)

**Checkpoint**: `seed/demo-completo.sql.gz` versionado.

---

## Phase 3: User Story 1 - Carregar a demo em segundos (Priority: P1) 🎯 MVP

**Goal**: um comando que carrega a demo numa transação, pelo container do banco, sem API.

**Independent Test**: `quickstart.md` passos 1 a 4.

### Verificação para User Story 1 ⚠️

- [X] T005 [US1] Em `seed/carregar-demo.sh`, escrever primeiro as guardas: `set -euo pipefail`, posicionar na raiz, `psql_compose` via `docker compose exec -T postgres` com `ON_ERROR_STOP=1 -q -X` (D-03); recusa sem `flyway_schema_history` (código 1, instrução de subir a `api`); contagem de aplicações `demo-completo%` e recusa sem `--substituir` quando houver demo (código 1, D-07); uso e código 2 para argumento desconhecido

### Implementation for User Story 1

- [X] T006 [US1] Em `seed/carregar-demo.sh`, gerar a limpeza (`reset_sql`) na ordem do `data-model.md` seção 3, filtrada pelas aplicações `demo-completo%`
- [X] T007 [US1] Em `seed/carregar-demo.sh`, enviar num único `psql`: `begin`, limpeza, `set local session_replication_role = replica`, `gunzip -c seed/demo-completo.sql.gz`, `set local session_replication_role = origin`, `set search_path = public`, `seed/datas.sql`, `commit` (D-04)
- [X] T008 [US1] Em `seed/carregar-demo.sh`, imprimir ao fim a contagem de aplicações e exibições da demo e onde abri-la no painel
- [X] T009 [US1] Rodar `quickstart.md` passos 1 a 4 e 8

**Checkpoint**: demo carregável, recusas e atomicidade conferidas.

---

## Phase 4: User Story 2 - Ver todas as telas do painel com dados plausíveis (Priority: P1)

**Goal**: o conteúdo cobre todas as telas e seus estados.

**Independent Test**: `quickstart.md` passo 6.

### Verificação para User Story 2 ⚠️

- [X] T010 [US2] Listar, a partir das telas do painel, os estados que cada uma precisa mostrar (aplicação ativa/desativada, chave ativa/revogada, cinco estados de pesquisa, duas versões, todos os tipos de pergunta, três desfechos, eventos, saúde, privacidade) e conferir cada um no dump com consultas por tabela

### Implementation for User Story 2

- [X] T011 [US2] Escrever `seed/README.md`: tabela de arquivos, o que tem dentro (aplicações, chaves, pesquisas, volume, padrões para a análise, saúde e privacidade), como carregar nos três modos, e o limite do dump frente a migrations futuras (FR-021)
- [X] T012 [US2] Rodar `quickstart.md` passo 6 percorrendo as telas da Loja Aurora

**Checkpoint**: nenhuma tela vazia.

---

## Phase 5: User Story 3 - Manter os gráficos recentes cheios (Priority: P2)

**Goal**: datas relativas a cada carga e um modo para renová-las.

**Independent Test**: `quickstart.md` passo 5.

### Verificação para User Story 3 ⚠️

- [X] T013 [US3] Definir as conferências do `quickstart.md` passo 5 — distância de `max(opened_at)` até agora e soma das durações das exibições antes e depois — antes de escrever o deslocamento

### Implementation for User Story 3

- [X] T014 [US3] Escrever `seed/datas.sql`: tabelas temporárias `demo_apps` e `demo_delta` (`on commit drop`), `delta = (now() − 1 hora) − max(opened_at)` (D-05)
- [X] T015 [US3] Em `seed/datas.sql`, um `update` por tabela somando `delta` a cada coluna de instante listada no `data-model.md` seção 4
- [X] T016 [US3] Em `seed/datas.sql`, trocar o bloco de `sdk_version_daily_usage` da demo pelos mesmos dias deslocados em dias inteiros, via tabela temporária (D-08)
- [X] T017 [US3] Acrescentar o modo `--datas` em `seed/carregar-demo.sh`: recusa sem demo (código 1), senão `begin` + `seed/datas.sql` + `commit` (D-06)
- [X] T018 [US3] Rodar `quickstart.md` passos 5 e 9

**Checkpoint**: gráficos de 7 e 30 dias cheios a qualquer momento.

---

## Phase 6: User Story 4 - Recarregar a demo do zero (Priority: P3)

**Goal**: voltar ao estado original da demo sem tocar em mais nada.

**Independent Test**: `quickstart.md` passos 3 e 7.

### Verificação para User Story 4 ⚠️

- [X] T019 [US4] Definir a conferência de isolamento e repetibilidade do `quickstart.md` passos 3 e 7 (contagens fora da demo iguais; contagens da demo iguais entre substituições)

### Implementation for User Story 4

- [X] T020 [US4] Acrescentar o modo `--substituir` em `seed/carregar-demo.sh`, reaproveitando a limpeza e a carga de US1 sem a recusa de demo existente (D-07)
- [X] T021 [US4] Rodar `quickstart.md` passos 3 e 7

**Checkpoint**: substituição atômica e repetível.

---

## Phase 7: Polish & Cross-Cutting Concerns

**Purpose**: a demo encontrável por quem chega ao repositório.

- [X] T022 [P] Apontar a demo no topo e na seção de operação de `docs/FEATURES.md`
- [X] T023 [P] Trocar, em `ops/DEPLOY.md`, a seção de dados de demonstração pelo uso de `seed/carregar-demo.sh` nos três modos
- [X] T024 [P] Citar `seed/carregar-demo.sh` como passo opcional no cabeçalho de `compose.local.yaml`
- [X] T025 Rodar o `quickstart.md` inteiro na stack local

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)** → **Foundational (Phase 2)**: o dump existe antes de qualquer script
- **US1 (Phase 3)**: depende da Phase 2. T007 encadeia `seed/datas.sql`, que nasce em US3: a validação de ponta a ponta de US1 (T009) roda depois de T014–T016
- **US2 (Phase 4)**: depende da Phase 2; verificável com US1 pronta
- **US3 (Phase 5)**: depende de US1 (o modo `--datas` entra no mesmo script)
- **US4 (Phase 6)**: depende de US1
- **Polish (Phase 7)**: depende de todas

### Parallel Opportunities

- US2 (README) em paralelo com US3 (`datas.sql`) depois de US1
- Polish: T022–T024 em paralelo

---

## Implementation Strategy

### MVP (US1 + US2)

1. Phase 1 → Phase 2 (dump)
2. Phase 3 (US1) → **parar e validar**: carga em segundos, recusas, atomicidade
3. Phase 4 (US2) → **parar e validar**: todas as telas com dados

### Entrega incremental

1. Dump e carga (US1)
2. Cobertura conferida e README (US2)
3. Datas relativas e renovação (US3)
4. Substituição (US4)

---

## Notes

- Toda instrução de escrita filtra pelas aplicações `demo-completo%`
- Toda escrita roda dentro de `begin`/`commit` com `ON_ERROR_STOP`
- `session_replication_role` só com `set local`, só em volta da cópia
- O dump é regerado quando uma migration muda uma tabela da demo; não se edita à mão
- Commit após cada tarefa ou grupo lógico
