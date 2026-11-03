# Quickstart — validar `002-demo-seed`

Como provar que a demo carrega, isola, desfaz e renova. Contrato do comando em
[`contracts/carregar-demo.md`](./contracts/carregar-demo.md); conteúdo em
[`contracts/demo-dataset.md`](./contracts/demo-dataset.md).

## Pré-requisitos

- A stack de `ops 001-deploy-observabilidade-backup` no ar — em produção, ou inteira na máquina
  local:

  ```bash
  docker compose -f compose.yaml -f compose.local.yaml up -d --build
  ```

- A `api` subiu ao menos uma vez (migrations aplicadas).

Atalho para consultas abaixo:

```bash
q() { docker compose exec -T postgres sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -tAX -c "$1"' psql "$1"; }
```

---

## 1. Recusa sem schema

Num banco novo, antes de a `api` subir (por exemplo, `docker compose up -d postgres` num volume
limpo):

```bash
seed/carregar-demo.sh ; echo "código=$?"
```

Esperado: `O banco ainda não tem o schema do Pitaco. Suba a api uma vez …` e `código=1`.

## 2. Carga

```bash
q "select count(*) from applications where slug not like 'demo-completo%'"   # anote: N
time seed/carregar-demo.sh
```

Esperado: `Aplicações: 3`, `Exibições: 4047`, `Pronto. Abra o painel em Aplicações → Loja
Aurora.`, em poucos segundos (SC-001).

## 3. Isolamento

```bash
q "select count(*) from applications where slug not like 'demo-completo%'"   # igual a N
```

Com uma aplicação real no banco (ou a do app de exemplo, slug `demo`), conferir que as contagens
das tabelas dela antes e depois da carga são iguais (SC-003).

## 4. Recusa de demo duplicada

```bash
seed/carregar-demo.sh ; echo "código=$?"
```

Esperado: `A demo já existe (3 aplicações demo-completo*). Use --substituir para recarregar.` e
`código=1`.

## 5. Datas perto de agora

```bash
q "select now() - max(opened_at) from survey_displays d join applications a on a.id = d.application_id where a.slug like 'demo-completo%'"
```

Esperado: cerca de `01:00:00` logo após a carga. No painel, **Loja Aurora → Visão geral**: os
gráficos de 7 e 30 dias com dados (SC-005).

Preservação das distâncias — a duração das exibições não muda entre duas renovações:

```bash
q "select sum(closed_at - opened_at) from survey_displays d join applications a on a.id = d.application_id where a.slug like 'demo-completo%'"
seed/carregar-demo.sh --datas
q "select sum(closed_at - opened_at) from survey_displays d join applications a on a.id = d.application_id where a.slug like 'demo-completo%'"
```

Os dois valores iguais. `--datas` rodado de novo logo em seguida mantém a exibição mais recente a
cerca de uma hora de agora.

## 6. Telas do painel

Percorrer, na **Loja Aurora**: aplicações (três, Aurora Legado desativada), chaves (uma
revogada), pesquisas (rascunho, publicada, pausada, encerrada, encerrada por cota), NPS com duas
versões, resultados do Checkout com todos os tipos de pergunta, comportamento (funil, tempo,
dispensas, atrito), exibições e respondentes, saúde do SDK (três versões, supressões, cinco tipos
de erro) e privacidade (três exclusões auditadas). Nenhuma vazia (SC-002). Na análise por
segmento, NPS maior em `plano=premium` e menor em `plano=free`.

## 7. Substituição

Alterar algo da demo pelo painel (pausar uma pesquisa publicada, revogar uma chave), depois:

```bash
seed/carregar-demo.sh --substituir
q "select count(*) from survey_displays d join applications a on a.id = d.application_id where a.slug like 'demo-completo%'"
```

Esperado: `4047` e o estado original de volta. Repetir duas vezes: mesmas contagens (SC-006).

## 8. Atomicidade

Simular um dump incompatível — por exemplo, com uma cópia de trabalho do script apontando `DUMP`
para um arquivo truncado (`zcat seed/demo-completo.sql.gz | head -n 30000 | gzip > /tmp/truncado.sql.gz`)
— e rodar com `--substituir` sobre uma demo carregada.

Esperado: erro do `psql`, código ≠ 0, e a contagem de exibições da demo igual à de antes (SC-004).

## 9. Argumento desconhecido e `--datas` sem demo

```bash
seed/carregar-demo.sh --qualquer ; echo "código=$?"     # Uso: … e código=2
```

Num banco sem demo:

```bash
seed/carregar-demo.sh --datas ; echo "código=$?"        # Não há demo carregada … e código=1
```
