# Seed `demo-completo`

Popula um backend Pitaco com dados fictícios suficientes para demonstrar **tudo** o que o painel
mostra: visão geral com tendência e segmentos, resultados de todos os tipos de pergunta,
comportamento (funil, tempo, dispensas, atrito), versões, ciclo de vida, saúde do SDK e
privacidade.

## O que ele cria

| Item | Conteúdo |
| --- | --- |
| Aplicações | **Loja Aurora** (`demo-completo`, principal), **Aurora Entregas** (secundária) e **Aurora Legado** (desativada) |
| Chaves | `ios-producao`, `android-producao`, `staging-antigo` (revogada), `app-entregador`, `legado` |
| Pesquisas | NPS trimestral (modelo NPS + perguntas condicionais, **2 versões**), Checkout (todos os tipos, amostragem 60%, segmentação), CSAT do atendimento, Onboarding CES (só Android), Próxima feature (**encerrada por cota**), Feedback do novo app (**pausada**), Teste de preço (**encerrada**, só BR), Pesquisa de cancelamento (**rascunho**) e, na aplicação secundária, Satisfação do entregador |
| Respondentes | Milhares de personas com `plano`, `plataforma`, `pais`, `versao_app` e `canal` — com correlações para a análise por segmento encontrar (premium mais satisfeito, Android reclama de estabilidade, plano free de frete) |
| Exibições | ~4.500 na escala 1, com concluídas, dispensadas (6 vias) e abandonadas, espalhadas pelos últimos 90 dias com sazonalidade semanal e crescimento |
| Eventos de interação | Fluxo completo do SDK por exibição: visualização, seleção, troca, foco em texto, validação, volta, segundo plano, dispensa e conclusão; parte do SDK 1.0.0 sem eventos, para a cobertura instrumentada ser realista |
| Saúde | Uso de 3 versões do SDK, supressões (`MATRIX`) e erros de SDK de 5 tipos |

As respostas passam pelas **mesmas rotas que o SDK usa** (`/collect/*`), então tudo é validado
pelo backend de verdade. Como a API grava a hora do servidor, o seed gera um SQL que move cada
exibição para a data simulada (mantendo as durações) e o aplica com `psql`.

## Como rodar

Requer Node 22.18+ (roda TypeScript direto) e, para as datas, `psql` no PATH.

```bash
# backend local (backend/compose.yaml) — com reescrita de datas
node seed/demo-completo.ts --database-url postgres://myuser:secret@localhost:5432/mydatabase

# recriar do zero (apaga as aplicações demo-completo* antes)
node seed/demo-completo.ts --reset --database-url postgres://myuser:secret@localhost:5432/mydatabase

# rápido, ~30% do volume
node seed/demo-completo.ts --scale 0.3 --database-url …

# outro backend; sem --database-url o SQL das datas fica em seed/out/ para aplicar à mão
node seed/demo-completo.ts --base-url https://pitaco.exemplo.com/api
psql "$DATABASE_URL" -f seed/out/demo-completo-datas.sql
```

| Flag | Padrão | O quê |
| --- | --- | --- |
| `--base-url` | `$PITACO_API_URL` ou `http://localhost:8080/api` | API com o context-path `/api` |
| `--database-url` | `$DATABASE_URL` | Postgres do backend, para reescrever as datas |
| `--scale` | `1` | Multiplica o volume (0–5) |
| `--reset` | — | Apaga a demo anterior (exige `--database-url`) |
| `--concurrency` | `8` | Exibições simuladas em paralelo |
| `--seed` | `20261001` | Semente: a mesma semente gera a mesma demo |

A escala 1 leva alguns minutos num backend local. O seed respeita `429`/`Retry-After`; ele
espalha as origens pelo cabeçalho `CF-Connecting-IP`, que o backend só aceita quando a conexão
vem de um proxy confiável (localhost incluso). Contra um backend remoto, o limite de 120
requisições por minuto por origem vale e o seed fica mais lento — use `--scale` menor.

Em produção (`compose.yaml` da raiz), o Postgres não publica porta; aplique o SQL pelo container:

```bash
docker compose exec -T postgres psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" < seed/out/demo-completo-datas.sql
```
