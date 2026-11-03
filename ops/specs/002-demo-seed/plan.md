# Implementation Plan: Demo completa com dados fictícios

**Branch**: `002-demo-seed` | **Date**: 2026-10-01 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `ops/specs/002-demo-seed/spec.md`

## Summary

Encher o painel com uma demo grande, variada e fictícia, carregada em segundos no banco da stack
sem passar pela API. Três arquivos fazem o trabalho e um quarto o explica:

1. **`seed/demo-completo.sql.gz`** — um `pg_dump` só de dados (`COPY` por tabela) das aplicações
   `demo-completo*`, gerado uma vez a partir de dados que passaram pelas rotas reais da API e do
   SDK (D-01, D-02).
2. **`seed/datas.sql`** — calcula um deslocamento único a partir da exibição mais recente e o soma
   a todo instante da demo, para ela ficar a uma hora de agora (D-05, D-06).
3. **`seed/carregar-demo.sh`** — confere o schema, recusa demo duplicada, limpa (com
   `--substituir`), copia e desloca numa transação só, pelo `psql` do container `postgres` da
   composição (D-03, D-04, D-07).
4. **`seed/README.md`** — o que tem dentro, como carregar e o limite: o dump casa com o schema
   atual.

Nenhuma linha de código de aplicação, nenhuma migration, nenhuma rota.

## Technical Context

**Language/Version**: SQL (PostgreSQL 16) e `bash` (`set -euo pipefail`).

**Primary Dependencies**: `psql` e `gunzip` — o `psql` do próprio container `postgres:16-alpine`
da composição; `gunzip` e `docker compose` no host. Nenhuma dependência nova.

**Storage**: o banco da stack (volume `pg_data`); a demo ocupa as mesmas tabelas que a coleta real.

**Testing**: não há suíte automatizada para a demo. A verificação é a do próprio script —
schema presente, demo duplicada recusada, transação única com `ON_ERROR_STOP` — e o roteiro do
`quickstart.md`, que confere contagens, isolamento, atomicidade e datas.

**Target Platform**: o servidor da stack de `ops 001-deploy-observabilidade-backup`, ou a stack
inteira na máquina local.

**Project Type**: dados de demonstração e script de carga.

**Performance Goals**: carga completa em poucos segundos (SC-001 pede menos de um minuto). É um
`COPY` direto: cerca de 80 mil linhas, 2,6 MB comprimidos.

**Constraints**: só as aplicações `demo-completo*`; tudo numa transação; sem API, sem porta
publicada; schema das migrations atuais.

**Scale/Scope**: 26 tabelas no dump — 3 aplicações, 5 chaves, 9 pesquisas, 10 versões, 28
perguntas, 4.047 exibições, 4.047 respondentes, 7.902 respostas, ~20 mil atributos de exibição,
~40 mil eventos de interação, 68 erros de SDK, 45 supressões, 3 exclusões auditadas.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

A demo não tem constituição própria. Vale a do backend (`backend/.specify/memory/constitution.md`),
dono do schema que a demo preenche; abaixo, os princípios que se aplicam a dados carregados por
fora da API.

| Princípio | Como esta feature o satisfaz | Veredito |
| --- | --- | --- |
| **I. Núcleo Independente de Framework** | Não se aplica: nada de código de aplicação. | — |
| **II. Domínio Que Se Valida** | A carga escreve direto nas tabelas, sem passar pelos construtores do domínio. O dado, porém, **nasceu** pelas rotas reais — cada aplicação, pesquisa, exibição e resposta foi validada pelo backend antes de ser exportada (D-01). As constraints do banco continuam valendo na limpeza; na cópia, as de chave estrangeira são suspensas dentro da transação só pela ordem das linhas (D-04). Ver *Complexity Tracking*. | ⚠️ justificado |
| **III. Teste É Contrato** | Não há regra de negócio nova a testar. O script se protege no caminho — schema ausente, demo duplicada, argumento desconhecido, erro no meio (transação com `ON_ERROR_STOP`) — e o `quickstart.md` traz o roteiro de verificação. | ✅ |
| **IV. Consistência da Superfície Pública** | Não se aplica: nenhuma rota nova. As telas leem a demo pelas mesmas rotas que leem dado real. | — |
| **V. Performance Por Construção** | Carga em `COPY` e uma transação, em vez de milhares de requisições; ajuste de datas em um `update` por tabela, sem laço. | ✅ |
| **Padrões Técnicos — Migrations** | A demo não cria nem altera schema; depende de `flyway_schema_history` existir e falha por inteiro se uma migration futura mudar uma tabela dela. | ✅ |
| **Padrões Técnicos — Tempo** | Todo instante do dump é `timestamptz` em UTC; o deslocamento é um `interval` somado igualmente a todos. | ✅ |
| **Padrões Técnicos — Configuração / segredo** | O dump não traz segredo: chaves só como hash irreversível de segredos fictícios, identidades de respondente sintéticas. | ✅ |

**Gate de Phase 0**: aprovado com uma entrada em *Complexity Tracking*.

### Re-avaliação após Phase 1 (design)

Reavaliado contra `data-model.md` e `contracts/`. O ponto que ganhou escrutínio foi a suspensão
das chaves estrangeiras: ela vale só entre `set local session_replication_role = replica` e
`origin`, dentro da transação, e a limpeza que vem antes roda com tudo ativo. Um dump
inconsistente não passaria pela geração, que usou a API real; e a transação inteira volta se
qualquer `COPY` falhar. Veredito final: **aprovado**, com a justificativa abaixo.

## Project Structure

### Documentation (this feature)

```text
ops/specs/002-demo-seed/
├── plan.md              # This file
├── research.md          # Phase 0: D-01 a D-08
├── data-model.md        # Phase 1: tabelas tocadas, colunas deslocadas, limpeza
├── quickstart.md        # Phase 1
├── contracts/
│   ├── carregar-demo.md # o comando: modos, saídas, códigos
│   └── demo-dataset.md  # o conteúdo e o formato do dump
├── checklists/
│   └── requirements.md
└── tasks.md             # Phase 2
```

### Source Code (repository root)

```text
seed/
├── README.md                 # conteúdo, como carregar, limites
├── carregar-demo.sh          # carregar | --substituir | --datas
├── datas.sql                 # deslocamento de todos os instantes da demo
└── demo-completo.sql.gz      # pg_dump --data-only das aplicações demo-completo*

docs/FEATURES.md              # aponta a demo no topo e na seção de operação
ops/DEPLOY.md                 # seção "Popular com dados de demonstração"
compose.local.yaml            # cita a carga como passo opcional da stack local

sdk/example/scripts/seed.ts   # seed do app de exemplo (dependência citada, não alterada)
```

**Structure Decision**: `seed/` na raiz, fora das três partes, porque a demo atravessa todas: o
schema é do backend, o consumidor é o painel e os eventos de interação são os do SDK. O seed do
app de exemplo continua em `sdk/example/scripts/`, porque serve ao app de exemplo e cria uma
aplicação pela API; os dois não se conhecem.

## Complexity Tracking

| Violation | Why Needed | Simpler Alternative Rejected Because |
| --- | --- | --- |
| Escrever direto nas tabelas, contornando o domínio (Princípio II) | Carregar a demo em segundos, sem a API de pé, sem limite de requisições e sem custo para o servidor | Gerar pela API (que foi a primeira versão) levava minutos, exigia a API no ar, esbarrava no limite de 120 requisições por minuto por origem fora da máquina local e ainda precisava de um passo direto no banco para reescrever as datas — a API grava a hora do servidor. O dado do dump foi gerado por esse caminho validado; só a carga é direta. |
