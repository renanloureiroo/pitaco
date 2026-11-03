# Contrato — conteúdo da demo (`seed/demo-completo.sql.gz`)

**Feature**: `002-demo-seed`

O que quem demonstra pode esperar encontrar no painel depois da carga. Os nomes de aplicação,
chave e pesquisa são estáveis; as contagens podem variar se o dump for regerado, e o que vale é a
cobertura.

## Aplicações

| Nome | Slug | Estado | Para mostrar |
| --- | --- | --- | --- |
| **Loja Aurora** | `demo-completo` | ativa | a aplicação principal: quase todas as pesquisas e exibições |
| **Aurora Entregas** | `demo-completo-entregas` | ativa | uma segunda aplicação, com sua própria pesquisa |
| **Aurora Legado (desativado)** | `demo-completo-legado` | desativada | o estado desativado |

## Chaves

| Rótulo | Aplicação | Estado |
| --- | --- | --- |
| `ios-producao` | Loja Aurora | ativa, com último uso |
| `android-producao` | Loja Aurora | ativa, com último uso |
| `staging-antigo` | Loja Aurora | revogada, nunca usada |
| `app-entregador` | Aurora Entregas | ativa, com último uso |
| `legado` | Aurora Legado | ativa, nunca usada |

Só o prefixo e o hash do segredo existem; nenhuma serve para coletar.

## Pesquisas

| Pesquisa | Aplicação | Estado | O que demonstra |
| --- | --- | --- | --- |
| NPS trimestral | Loja Aurora | publicada | duas versões (a segunda com mudança cosmética, mesmo grupo de comparabilidade), perguntas condicionais |
| Checkout — experiência de compra | Loja Aurora | publicada | todos os tipos de pergunta, amostragem de 60%, segmentação por atributo |
| CSAT do atendimento | Loja Aurora | publicada | modelo CSAT |
| Onboarding (CES) | Loja Aurora | publicada | segmentação por plataforma (só Android) |
| Próxima feature | Loja Aurora | encerrada por cota | encerramento automático |
| Feedback do novo app | Loja Aurora | pausada | pausa manual |
| Teste de preço — frete fixo (beta) | Loja Aurora | encerrada | encerramento manual, segmentação por país, amostragem de 25% |
| Pesquisa de cancelamento | Loja Aurora | rascunho | rascunho nunca publicado |
| Satisfação do entregador | Aurora Entregas | publicada | pesquisa de outra aplicação |

Tipos de pergunta presentes: NPS, escala, nota, escolha única, múltipla escolha e texto livre.

## Coleta

| Item | Volume aproximado | Detalhe |
| --- | --- | --- |
| Exibições | 4.050 em 88 dias | respondidas, dispensadas e sem desfecho (exibidas como abandonadas) |
| Respondentes | 4.050 | identificados por referência do app e por dispositivo |
| Respostas | 7.900 | respondidas, puladas e não aplicáveis (condição não satisfeita), inclusive texto livre |
| Atributos por exibição | 20.000 | `plano`, `plataforma`, `pais`, `versao_app`, `canal` |
| Eventos de interação | 40.000 | apresentação, visualização, seleção, troca, texto, validação, navegação, segundo plano, dispensa, conclusão |

**Padrões para a análise encontrar**: NPS maior no plano premium e menor no gratuito; Android
concentra reclamações de estabilidade; plano gratuito concentra reclamações de frete.

## Saúde e privacidade

| Item | Conteúdo |
| --- | --- |
| Versões do SDK | `1.0.0`, `1.1.0`, `1.2.0`, com acumulado e série diária de 30 dias |
| Supressões | por tipo de pergunta desconhecido e por recurso não suportado |
| Erros do SDK | cinco tipos: rede, renderização, armazenamento, resposta malformada, desconhecido |
| Exclusões de titular | três, auditadas |

## Formato

`pg_dump` 16 em texto, só dados, comprimido com gzip: um `COPY public.<tabela> (...) FROM stdin`
por tabela de negócio, nas 26 tabelas do schema atual. Carregável só pelo `carregar-demo.sh`, que
o envolve na transação e no ajuste de datas.
