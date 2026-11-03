# Quickstart — validar resultados e comportamento

Como provar que esta feature funciona ponta a ponta. Contrato em
[contracts/](./contracts/backend-api.md), modelo em [data-model.md](./data-model.md).

---

## Pré-requisitos

- Node 20+ e dependências instaladas (`npm ci`)
- Contra a API real: backend no ar com coleta (a demo de `seed/` cria pesquisas com respostas e
  eventos de interação) e `.env.local` apontando para ele
- Sem backend: o E2E sobe o servidor simulado e semeia a coleta

---

## Portão de qualidade

```bash
npm run verify
```

Isoladamente:

```bash
npm test -- src/features/results
npm run test:e2e -- resultados.spec.ts
```

O E2E de resultados se pula inteiro com `E2E_API=real`: a coleta só nasce pelo SDK.

---

## Roteiro de verificação manual

### 1. Resultado (US1)

| Passo | O que conferir |
|---|---|
| Pesquisa publicada com coleta → aba **Resultados** | contagens, taxa e a frase da definição juntas; rótulo do fuso |
| Pesquisa de NPS criada por modelo | NPS no topo com os três grupos |
| Pergunta de escolha | contagem e proporção por opção, inclusive zero; cores estáveis |
| Múltipla escolha | nota sobre proporções somando mais de 100% |
| Escala | média e histograma |
| Pergunta sem resposta | "Ninguém respondeu esta pergunta ainda." |
| Pergunta condicionada | não aplicáveis contados à parte, com a explicação |
| Pesquisa nunca publicada | vazio com "Ir para a publicação"; sem recorte nem export |
| Publicada sem exibição | "Nenhuma exibição ainda" com "Ver o disparo" |
| Esperar 30 s com a aba visível | "Atualizado às …" muda; trocar de aba do navegador pausa |

### 2. Recorte (US2)

| Passo | O que conferir |
|---|---|
| Período "Últimos 7 dias" | todos os números mudam; URL com `periodo=7`; recorte escrito |
| Personalizado com início depois do fim | recusa no formulário, sem navegar |
| Atributo `plano`, valor `pro` | só quem enviou `pro`; URL com `atributo` e `valor` |
| Atributo `plano`, "Sem o atributo" | só quem não enviou; URL com `ausente=1` |
| Visão "Versão 2" | só a versão 2; nenhum aviso de comparabilidade |
| Consolidado com pergunta alterada entre versões semânticas | aviso "mudou entre as versões 1 e 2" |
| Copiar a URL e abrir em outra janela | mesma tela, mesmos números |
| `?versao=abc&de=lixo` | tela abre consolidada, sem erro |

### 3. Respostas abertas (US3)

| Passo | O que conferir |
|---|---|
| Ver a seção | texto, enunciado e momento; lembrete de dado pessoal |
| Buscar um termo em maiúsculas | encontra; URL com `q`; agregados iguais |
| Abrir o contexto | as outras respostas daquela exibição |
| Termo sem resultado | "Nenhuma resposta contém …" |

### 4. Export (US4)

| Passo | O que conferir |
|---|---|
| Exportar CSV | diálogo sobre dado pessoal antes de qualquer download |
| Cancelar | nada baixa |
| Confirmar com `?periodo=90` | `resultados-{id}.csv`, abre corretamente em planilha (BOM), só o recorte |
| Aba de rede do navegador | o download vem de `/api/aplicacoes/…/export`, nunca do backend |

### 5. Comportamento (US5)

| Passo | O que conferir |
|---|---|
| Resultados → cartão Comportamento → Ver comportamento | aba Comportamento |
| Indicadores | instrumentadas e cobertura; tempo ativo; troca de resposta; dispensas |
| Funil | viram, proporção, perda (−N) em relação à anterior, respondidas/puladas/pararam |
| Tempo por pergunta | mediana e p90 |
| Vias de dispensa | rótulos em português; "sem via informada" à parte |
| Definições | uma por métrica, como o backend devolveu |
| Pesquisa com SDK sem eventos | "Nenhuma exibição instrumentada neste recorte" |
