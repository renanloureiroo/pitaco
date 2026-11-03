---
tipo: adr
numero: 0002
status: aceito
data: 2026-09-12
supera:
superado_por:
tags:
  - pitaco
  - adr
---

# ADR-0002 — O SDK fala com `/collect` por `baseUrl` e chave, sem saber se há gateway no caminho

> O SDK recebe `baseUrl` e `apiKey`, as duas obrigatórias, e acrescenta `/collect/...` ao
> endereço; quem quiser pôr o próprio gateway na frente aponta a `baseUrl` para ele, e o gateway
> é transparente.

## Contexto

Em 2026-09-12 a superfície pública `/collect` já existia (`004-response-collection`), autenticada
pela chave da aplicação no header `X-Pitaco-Key`, com limite de requisições por chave e por
origem. O SDK React Native ia nascer em seguida.

Parte dos apps que integram ferramentas de pesquisa não deixa o aplicativo falar com domínio de
terceiro: toda chamada de saída passa por um gateway próprio, por política de rede, para
observabilidade ou para controlar o endereço sem publicar versão nova do app. Ao mesmo tempo, o
caso comum é falar direto com o Pitaco, e o SDK precisava ser simples de configurar nesse caso.

A chave da aplicação mora no bundle do app e pode ser extraída. A identidade do respondente sai do
dispositivo e pode ser forjada. Nenhuma das duas fica confiável por passar por um gateway.

## Alternativas consideradas

### Opção A — SDK com dois modos (direto e via gateway)

Um parâmetro de modo, com endereço e cabeçalhos diferentes em cada um. Deixaria explícito no
código qual topologia está em uso e permitiria ao gateway mandar uma credencial de servidor.

### Opção B — Gateway com credencial privilegiada

O gateway troca a chave do app por uma credencial de servidor e pode sobrescrever identidade e
atributos. Daria uma origem confiável para quem tem backend próprio.

### Opção C — Uma configuração só, gateway transparente

O SDK só conhece `baseUrl` e `apiKey`. O gateway, quando existe, repassa `/collect/*` sem mudar
corpo, chave nem cabeçalhos relevantes.

## Decisão

Opção C. O que decidiu foi manter um único caminho de código no SDK, testável de ponta a ponta
igual nas duas topologias, e não criar uma credencial de servidor que precisaria de emissão,
rotação e revogação próprias antes de existir um caso real que pedisse isso.

## Consequências

**Aceitamos:** o gateway não protege a chave nem torna a identidade confiável; a defesa contra
abuso continua sendo o limite de requisições. O gateway precisa repassar a origem do cliente
(`X-Forwarded-For`) para o limite por origem funcionar, e o backend precisa confiar só nos proxies
listados.

**Ganhamos:** um SDK sem modo e sem ramificação; a mesma suíte de testes vale para as duas
topologias; integrar com gateway é configuração, não código.

**Muda no repositório:** `docs/backend/contrato-sdk.md` documenta a `baseUrl`;
`docs/backend/proxy.md` documenta o contrato do gateway transparente; o app de exemplo do SDK tem
um perfil que passa por um proxy local (`sdk/example/proxy/`).

## Quando revisitar

Quando um integrador real precisar que o servidor dele atribua identidade ou atributos que o
dispositivo não pode forjar — aí uma credencial de servidor passa a ter caso concreto.
