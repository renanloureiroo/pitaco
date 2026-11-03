# Quickstart — validar a autoria avançada

Como provar que esta feature funciona ponta a ponta. Contrato em
[contracts/](./contracts/backend-api.md), modelo em [data-model.md](./data-model.md).

---

## Pré-requisitos

- Node 20+ e dependências instaladas (`npm ci`). O SDK entra como pacote local (`file:../sdk`):
  o diretório `sdk/` do monorepo precisa existir ao lado do `painel/`.
- Para validar contra a API real: backend do Pitaco no ar e `.env.local` apontando para ele
  (`PITACO_API_URL`, veja `.env.example`).
- Para validar sem backend: nada além do repositório — o E2E sobe o servidor de API simulado.

---

## Portão de qualidade

```bash
npm run verify     # lint + typecheck + vitest + playwright
```

Isoladamente:

```bash
npm test -- src/features/surveys src/features/applications
npm run test:e2e -- autoria-avancada.spec.ts disparo.spec.ts publicacao.spec.ts ciclo-de-vida.spec.ts pesquisas.spec.ts
```

Os cenários que dependem de dado que só o SDK cria (catálogo, cota atingida, comparabilidade nos
resultados) se pulam com `E2E_API=real`, com o motivo no próprio teste.

---

## Roteiro de verificação manual

### 1. Modelo (US1)

| Passo | O que conferir |
|---|---|
| Aplicação → Pesquisas → Nova pesquisa | Em branco marcada; NPS, CSAT e CES com descrição |
| Escolher NPS, dar nome, criar | montagem com uma pergunta de 0 a 10 e rótulos "Nada provável … Extremamente provável" |
| Repetir com CSAT e CES | escala 1 a 5 e 1 a 7, com a pergunta oficial |

### 2. Condição (US2)

| Passo | O que conferir |
|---|---|
| Primeira pergunta, abrir o formulário | a condição explica que não há origem possível |
| "Gostou?" escolha única (Sim, Não); adicionar "O que faltou?" com condição = Não | lista mostra `Exibida se P1 for "Não"` |
| Origem NPS, operador "estiver na faixa", 0 a 11 | erro junto do campo de valores; a pergunta não é criada; a condição montada continua |
| Editar a pergunta condicionada | a condição vem montada |
| Desligar a condição e salvar | o resumo some |

### 3. Reordenar e editar (US3)

| Passo | O que conferir |
|---|---|
| Três perguntas; focar a alça da terceira, Espaço, ↑ ↑, Espaço | ordem nova na hora; recarregar mantém |
| Com leitor de tela | anúncios "Pegou…", "foi movida para a posição…", "solta na posição…" |
| Soltar fora da lista | nada muda, nada é enviado |
| Editar uma pergunta | o formulário preenchido ocupa a coluna; salvar devolve a lista atualizada |

### 4. Pré-visualização (US4)

| Passo | O que conferir |
|---|---|
| Abrir a montagem com perguntas | pré-visualização ao lado, com o renderizador do SDK |
| Responder na pré-visualização | eventos listados do mais recente para o mais antigo; expandir mostra o JSON |
| Adicionar pergunta e trocar o tipo | a pré-visualização mostra o rascunho |
| Visualizar uma pergunta | só ela aparece; "Ver todas no preview" restaura |
| Alternar o tema do painel | a pré-visualização acompanha |

### 5. Duplicar (US5)

| Passo | O que conferir |
|---|---|
| Cabeçalho → Duplicar | destinos ativos; a atual como "(esta)"; aviso do que não vai |
| Escolher outra aplicação, nome em branco | montagem sob a aplicação de destino, "Cópia de …", Rascunho |
| Encerrar uma pesquisa | Renomear some do cabeçalho |

### 6. Exposição e aviso (US6)

| Passo | O que conferir |
|---|---|
| Disparo | "Sem cota", regra de desempate, política de descanso da aplicação |
| Prioridade 7, cota 50, isenção ligada, salvar, recarregar | valores mantidos; "Concluídas: 0 de 50"; explicação da isenção |
| Prioridade 101 | recusa no campo, sem chamar a API |
| Limpar a cota e salvar | "Sem cota" |
| Aviso de dado pessoal: personalizar, salvar; desligar, salvar; recarregar | texto exibido; "Desligado"; checkbox desmarcado e texto mantido |
| Cota 1 e uma conclusão chegando pelo SDK | pesquisa Encerrada; histórico "Encerrada por cota" |

### 7. Catálogo (US7)

| Passo | O que conferir |
|---|---|
| Disparo numa aplicação sem tráfego | frases de ausência; digitação livre aceita |
| Com eventos e atributos já enviados | sugestões; clicar preenche evento, atributo e valor |

### 8. Publicação consciente (US8)

| Passo | O que conferir |
|---|---|
| Outra pesquisa no ar no mesmo evento | aviso com a concorrente, prioridade, vínculo e desempate; Publicar habilitado |
| Regra `plano = pro` sem esse valor no catálogo | aviso nomeando `plano`; nenhum impedimento novo |
| Publicar v1, abrir rascunho | vai direto à montagem editável |
| Mudar uma opção; publicar sem natureza | recusa no campo, resumo preservado |
| Declarar cosmética uma mudança estrutural | recusa do backend exibida no formulário |
| Publicar como semântica | Versões: 2 linhas, natureza "Semântica", Grupo 2 na comparabilidade |
