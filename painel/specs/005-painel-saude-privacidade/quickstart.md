# Quickstart — validar saúde e privacidade

Como provar que esta feature funciona ponta a ponta. Contrato em
[contracts/](./contracts/backend-api.md), modelo em [data-model.md](./data-model.md).

---

## Pré-requisitos

- Node 20+ e dependências instaladas (`npm ci`)
- Contra a API real: backend no ar, com um app de exemplo usando o SDK (para versões, erros e
  supressões) e `.env.local` apontando para o backend
- Sem backend: o E2E sobe o servidor simulado e semeia versões, erros, supressões, coleta e
  agregado de retenção

---

## Portão de qualidade

```bash
npm run verify
```

Isoladamente:

```bash
npm test -- src/features/health src/features/privacy src/features/results/__tests__/retention-note.test.tsx
npm run test:e2e -- saude.spec.ts privacidade.spec.ts aplicacoes.spec.ts
```

`saude.spec.ts` se pula inteiro com `E2E_API=real`; em `privacidade.spec.ts`, os cenários que
dependem de semeadura se pulam com o motivo escrito.

---

## Roteiro de verificação manual

### 1. Versões do SDK (US1)

| Passo | O que conferir |
|---|---|
| Aplicação → barra lateral → **Saúde** | tabela com versão, tráfego recente, consultas recentes e totais, última vez vista |
| Versão sem consulta há mais de 14 dias | marca "Sumiu do tráfego" |
| Versão sem consulta recente | fatia "—", nunca 0% |
| Aplicação nova | "Nenhum SDK falou com esta aplicação ainda" |

### 2. Erros do SDK (US2)

| Passo | O que conferir |
|---|---|
| Lista de erros | tipo em português, versão, momento, mensagem |
| Filtrar por Renderização | URL com `tipo=render_error`; só erros desse tipo |
| Filtrar por versão | URL com `versao=` |
| Abrir o contexto de um erro | chaves e valores |
| `?tipo=inventado` | filtro ignorado, tela abre |
| Limpar | filtros saem da URL |

### 3. Saúde da pesquisa (US3)

| Passo | O que conferir |
|---|---|
| Pesquisa com tipo de pergunta que o SDK do app não conhece | aviso de supressão com proporção, motivo e versão mínima; sem "Nenhuma exibição ainda" |
| Pesquisa publicada cujo evento o app nunca enviou | "O evento do disparo nunca chegou" com o nome |
| App envia o evento; recarregar | o aviso some |

### 4. Política de retenção (US4)

| Passo | O que conferir |
|---|---|
| Aplicação sem prazo → **Privacidade** | "Sem política de retenção…"; sem aviso |
| Editar prazos → retenção 30 dias | a política descreve 30 dias e que o agregado continua |
| Com respostas de mais de 30 dias | aviso com data e "N respostas e M textos livres serão descartados" |
| Retenção de texto livre maior que a geral | recusa no diálogo, que continua aberto com os valores |
| Apagar um prazo no diálogo | prazo removido ("não configurado" no detalhe) |

### 5. Exclusão de respondente (US5)

| Passo | O que conferir |
|---|---|
| Formulário vazio | Excluir desabilitado |
| Informar referência e Excluir | diálogo diz "irreversível" e o que fica registrado |
| Cancelar | nada muda; respondente continua em Respondentes |
| Confirmar | "Excluído: …"; registro na lista; respondente sumiu de Respondentes |
| Procurar a referência na tela | não aparece em lugar nenhum |
| Referência inexistente | "Nada foi apagado"; nenhum registro novo |

### 6. Nota de retenção nos resultados (US6)

| Passo | O que conferir |
|---|---|
| Resultados de pesquisa com descarte, sem recorte | "Inclui agregados de respostas descartadas…" |
| Mesmo com `?periodo=7` | "… não entram neste recorte" |
| Pesquisa sem descarte | sem nota |
