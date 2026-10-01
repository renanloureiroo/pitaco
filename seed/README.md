# Demo completa

Dados fictícios para demonstrar tudo o que o painel mostra: visão geral com tendência e
segmentos, resultados de todos os tipos de pergunta, comportamento (funil, tempo, dispensas,
atrito), versões, ciclo de vida, saúde do SDK e privacidade.

| Arquivo | O que é |
| --- | --- |
| `demo-completo.sql.gz` | Os dados, em `COPY` do Postgres (só dados, sem schema) |
| `datas.sql` | Desloca todos os instantes da demo para a exibição mais recente ficar a uma hora de agora |
| `carregar-demo.sh` | Carrega tudo numa transação, pelo container do Postgres do compose |

## O que tem dentro

| Item | Conteúdo |
| --- | --- |
| Aplicações | **Loja Aurora** (`demo-completo`), **Aurora Entregas** (`demo-completo-entregas`) e **Aurora Legado** (`demo-completo-legado`, desativada) |
| Chaves | `ios-producao`, `android-producao`, `staging-antigo` (revogada), `app-entregador`, `legado` |
| Pesquisas | NPS trimestral (2 versões, perguntas condicionais), Checkout (todos os tipos, amostragem 60%, segmentação), CSAT do atendimento, Onboarding CES (só Android), Próxima feature (encerrada por cota), Feedback do novo app (pausada), Teste de preço (encerrada), Pesquisa de cancelamento (rascunho) e Satisfação do entregador |
| Volume | ~4.050 exibições em 88 dias, ~3.900 respondentes, ~40 mil eventos de interação |
| Padrões para a análise achar | NPS maior no plano premium e menor no free; Android reclama de estabilidade; free reclama de frete |
| Saúde e privacidade | 3 versões do SDK, supressões, erros de SDK de 5 tipos e 3 exclusões de titular auditadas |

## Como carregar

Na raiz do repositório, com o stack no ar (a `api` precisa ter subido uma vez para criar o
schema):

```bash
seed/carregar-demo.sh              # carrega; recusa se a demo já existir
seed/carregar-demo.sh --substituir # apaga a demo anterior e carrega de novo
seed/carregar-demo.sh --datas      # só traz as datas da demo para perto de agora
```

Leva poucos segundos: é um `COPY` direto no banco, sem passar pela API. Mexe apenas nas
aplicações com slug `demo-completo*`; as aplicações reais não são tocadas.

As datas são relativas: a cada carga a exibição mais recente fica a uma hora de agora. Com o
tempo os gráficos de "últimos 7/30 dias" esvaziam; rode `--datas` para renovar.

O dump é só de dados e casa com o schema das migrations atuais. Se uma migration futura mudar
uma tabela da demo, a carga falha por inteiro (é uma transação só) e o dump precisa ser
regerado.
