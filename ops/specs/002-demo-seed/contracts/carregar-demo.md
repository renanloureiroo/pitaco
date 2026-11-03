# Contrato — `seed/carregar-demo.sh`

**Feature**: `002-demo-seed` · Executado na raiz do repositório (o script se posiciona nela
sozinho), com a stack de `ops 001-deploy-observabilidade-backup` no ar.

```text
seed/carregar-demo.sh              # carrega; recusa se a demo já existir
seed/carregar-demo.sh --substituir # apaga a demo anterior e carrega de novo
seed/carregar-demo.sh --datas      # só traz as datas da demo existente para perto de agora
```

Um argumento por execução; os demais são ignorados.

## Pré-condições

| Condição | Como é verificada | Sem ela |
| --- | --- | --- |
| Serviço `postgres` da composição da raiz no ar | `docker compose exec -T postgres …` | erro do Docker Compose; código ≠ 0 |
| Schema do Pitaco criado (a API subiu ao menos uma vez) | `select 1 from flyway_schema_history limit 1` | recusa (abaixo), código `1` |
| `gunzip` no host | usado só nos modos de carga | erro do shell; transação não confirmada |

O `psql` é o do container, com `-v ON_ERROR_STOP=1 -q -X`, usuário `$POSTGRES_USER` e banco
`$POSTGRES_DB` do próprio container.

## Modos

| Modo | Demo presente? | Efeito | Saída (stdout) | Código |
| --- | --- | --- | --- | --- |
| *(nenhum)* | não | `begin` → limpeza → cópia → datas → `commit` | `Carregando a demo…`, `Aplicações: 3`, `Exibições: 4047`, `Pronto. Abra o painel em Aplicações → Loja Aurora.` | `0` |
| *(nenhum)* | sim | nada | `A demo já existe (<n> aplicações demo-completo*). Use --substituir para recarregar.` (stderr) | `1` |
| `--substituir` | indiferente | `begin` → limpeza → cópia → datas → `commit` | igual à carga | `0` |
| `--datas` | sim | `begin` → datas → `commit` | `Datas da demo atualizadas para perto de agora.` | `0` |
| `--datas` | não | nada | `Não há demo carregada para atualizar.` (stderr) | `1` |
| outro | — | nada | `Uso: seed/carregar-demo.sh [--substituir \| --datas]` (stderr) | `2` |
| qualquer | schema ausente | nada | `O banco ainda não tem o schema do Pitaco. Suba a api uma vez (docker compose up -d api)` e `para as migrations rodarem, e tente de novo.` (stderr) | `1` |

Os números do resumo são os da demo carregada, contados depois do `commit`.

## Garantias

- **Atomicidade**: os modos de carga e `--datas` enviam tudo num único `psql`, entre `begin` e
  `commit`; com `ON_ERROR_STOP`, o primeiro erro aborta e nada é confirmado.
- **Isolamento**: toda instrução de escrita é filtrada pelas aplicações com `slug like
  'demo-completo%'` (diretamente ou por pesquisa, versão, exibição, pergunta ou atributo delas).
- **Restrições**: a limpeza roda com as restrições ativas; a cópia, com
  `session_replication_role = replica` apenas dentro da transação (`set local`), restaurado para
  `origin` antes do ajuste de datas.
- **Sem API**: nenhuma requisição HTTP; a API pode estar parada durante a carga.

## O que este comando não faz

- Não cria nem altera schema.
- Não emite chave utilizável: as chaves da demo existem só como hash de segredos fictícios.
- Não fala com outro banco que não o serviço `postgres` da composição da raiz.
