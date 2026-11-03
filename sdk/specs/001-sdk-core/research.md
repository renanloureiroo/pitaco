# Research: SDK React Native — core headless

**Feature**: `001-sdk-core` · **Date**: 2026-09-12 · **Spec**: [spec.md](./spec.md)

Cada decisão abaixo resolveu uma incógnita antes ou durante o desenho do core. Formato: decisão,
porquê e alternativas descartadas.

---

## D-01 — Versões da cadeia de ferramentas

**Decision**: TypeScript 5.9, Jest 29 com `@react-native/jest-preset` 0.87, React Native 0.87.1 e
React 19.2 em desenvolvimento, `@testing-library/react-native` 14 com `test-renderer`. Peers
`react >=18.2.0` e `react-native >=0.74.0`.

**Rationale**: o `openapi-typescript` 7.13 exige TS 5 e o `typescript-eslint` 8.70 aceita menos
de 6.1; o preset de Jest do React Native 0.87 depende de `babel-jest` 29. Os peers ficam baixos
porque o SDK só usa API estável do React Native, e o app de exemplo prova o SDK numa versão
anterior (0.86, a do Expo Go).

**Alternatives considered**: TS 6 (quebraria o `typescript-eslint`); Jest 30 (incompatível com o
preset); peers iguais à versão de desenvolvimento (excluiria apps que ainda não migraram, sem
ganho).

---

## D-02 — Tipos do contrato gerados de um snapshot do OpenAPI

**Decision**: `openapi/pitaco.json` é versionado. `npm run gen:api` (`scripts/gen-api.mjs`) tenta
baixar `PITACO_OPENAPI_URL` (padrão `http://localhost:8080/api/v3/api-docs`), remove `servers` e
atualiza o snapshot; sem backend no ar, usa o snapshot. Gera `src/api/openapi.ts` e o resto do SDK
importa aliases de `src/api/types.ts`.

**Rationale**: o build nunca depende da API estar de pé, e a troca de nome de um DTO no backend
mexe num arquivo só. `servers` muda com a porta local e só polui o diff.

**Alternatives considered**: tipos escritos à mão (divergem do backend sem aviso); gerar no
`postinstall` (exigiria backend no ar para instalar).

---

## D-03 — `value` de `AnswerDTO` tipado à mão

**Decision**: `SubmissionAnswer` e `SubmissionRequest` em `src/api/types.ts` redefinem `value`
como `AnswerValueWire | null`.

**Rationale**: o OpenAPI publica `value` com `type: null` ao lado do `oneOf`, e o gerador produz
`null & (string | number | string[])`, que o TypeScript reduz a `never`. A forma real está no
contrato: ausente em `SKIPPED` e `NOT_APPLICABLE`; texto, inteiro ou lista de textos em
`ANSWERED`.

**Alternatives considered**: corrigir o snapshot à mão (seria sobrescrito no próximo
`gen:api`); `as any` (proibido pela constituição).

---

## D-04 — Máquina de estado como função pura

**Decision**: `transition(estado, ação, instante) → { estado, eventos }` em
`core/machine/machine.ts`. O instante (`{ mono, wall }`) entra como argumento; nada de relógio,
timer ou rede dentro dela. A `SurveySession` segura o estado, aplica o relógio injetado e agenda o
debounce do texto, que só acorda a máquina com `tick` — quem decide se venceu é a máquina.

**Rationale**: testável sem renderizar nem esperar; o mesmo roteiro aplicado à máquina, ao runtime
e à UI dá o mesmo resultado, o que sustenta a `003-sdk-eventos-interacao`.

**Alternatives considered**: estado em reducer do React (prenderia o core ao React e impediria o
preview sem árvore); classe com métodos mutáveis (perderia a comparação de estado de referência).

---

## D-05 — A exibição abre no `present()`, e as gravações são serializadas

**Decision**: a oferta cria a sessão em `ready`; só a ação `present` leva a `presented` e faz o
runtime gravar a abertura. Todas as gravações de fila passam por uma cadeia única
(`this.pipeline = this.pipeline.then(...)`), e a abertura de uma exibição é gravada antes dos
eventos e do envio dela.

**Rationale**: o contrato diz que pesquisa que não aparece não conta como exibida. A cadeia evita
que, com storage assíncrono, um envio seja persistido antes da própria abertura.

**Alternatives considered**: abrir na chegada da elegibilidade (contaria exibição de pesquisa
nunca vista); gravar em paralelo (ordem não garantida).

---

## D-06 — Transporte que classifica em vez de lançar

**Decision**: `HttpClient.post` devolve `HttpOutcome`: `success`, `malformed` (2xx com corpo que
deveria ser JSON e não é), `retry` (rede, timeout, 5xx, 408), `rate_limited`, `unauthorized`
(401/403), `rejected` (demais 4xx) e `blocked` (ainda dentro de um `Retry-After`). O timeout é uma
corrida contra um timer, com `AbortController` quando existe; a promessa perdedora tem `catch`
para nunca ficar sem tratamento.

**Rationale**: cada chamador (elegibilidade, fila, relatório) trata o resultado de um jeito, sem
`try/catch` espalhado; nenhum erro escapa por promessa não tratada.

**Alternatives considered**: lançar e capturar em cada chamador (fácil esquecer um); depender do
`signal` do `fetch` para o timeout (nem todo `fetch` de React Native o respeita).

---

## D-07 — `fetch` global lido a cada chamada

**Decision**: o runtime usa um `fetch` preguiçoso que lê `globalThis.fetch` na hora de cada
requisição.

**Rationale**: um polyfill instalado depois da montagem, ou o embrulho de diagnóstico do app de
exemplo, passam a valer sem reconfigurar o SDK.

**Alternatives considered**: capturar o `fetch` na criação (ignoraria embrulhos posteriores);
pedir `fetch` como prop (superfície pública sem caso real).

---

## D-08 — Uma fila persistente por par (`baseUrl`, `apiKey`)

**Decision**: um documento JSON `{ version: 1, items, blockedUntil }` na chave
`@pitaco/v1:queue:<hash>`, onde o hash é djb2 em base 36 de `baseUrl|apiKey`. Itens:
`open_display`, `submission`, `events` (um item por exibição, acumulando por `seq`) e
`suppression`. Limites padrão: 200 itens, 3 dias, 500 eventos por exibição, lotes de 100, backoff
de 2 s × 2ⁿ até 5 min.

**Rationale**: um documento só simplifica restauração e ordem. A idade máxima de 3 dias cabe na
janela de 7 dias que o servidor aceita eventos; 500 por exibição é o teto do servidor; 100 é o
máximo de um lote. Separar por chave impede que trocar de ambiente mande respostas para o backend
errado.

**Alternatives considered**: uma chave por item (restauração sem ordem garantida); fila sem teto
(storage cresce indefinidamente num app que nunca volta a ter rede).

---

## D-09 — Regras de descarte e de nova tentativa

**Decision**: 2xx e `202` com corpo ilegível removem; rede, timeout e 5xx mantêm com backoff e
interrompem a passada; `429` pausa a fila inteira e persiste o prazo; 401/403 descartam e avisam o
runtime; demais 4xx descartam e viram relatório de erro. Abertura descartada leva junto envio e
eventos da mesma exibição. A montagem do Provider e a volta do segundo plano forçam uma passada
ignorando o backoff, nunca o `Retry-After`.

**Rationale**: é a tabela de "Fila local e idempotência" do contrato. Tentar de novo uma recusa
definitiva não muda o resultado e gasta o limite.

**Alternatives considered**: reenviar envio com `404` para trás da abertura (o próprio desenho já
nunca envia antes da abertura, então o caso não ocorre).

---

## D-10 — Elegibilidade com timeout curto e configurável

**Decision**: 3000 ms por padrão, `eligibilityTimeoutMs` entre 250 e 30000; demais rotas 10000 ms.

**Rationale**: a elegibilidade está no caminho do usuário — a pesquisa que chega tarde demais
aparece fora de contexto. As outras rotas vão pela fila e podem esperar mais.

**Alternatives considered**: timeout único (ou longo demais para a consulta, ou curto demais para o
envio de um lote grande).

---

## D-11 — Chave recusada desliga a sessão; rede fora avisa uma vez

**Decision**: `401`/`403` ligam `keyRejected`: `track` deixa de consultar até o runtime ser
recriado, com um único aviso. Rede inalcançável gera um único aviso com o endereço certo por alvo
(`localhost` no simulador iOS, `10.0.2.2` no emulador Android, IP da máquina em aparelho).

**Rationale**: insistir com chave errada só gasta o limite por origem. O erro mais comum de
integração é o endereço errado no emulador; o aviso diz exatamente isso.

**Alternatives considered**: tentar de novo a cada `track` (ruído e custo); aviso a cada falha
(console inutilizável).

---

## D-12 — Provider cria o runtime no render e liga no efeito

**Decision**: o runtime nasce num `useState` preguiçoso, sem tocar storage, rede nem `AppState`.
`attach()` no efeito carrega identidade e fila, ouve o `AppState` e força o envio pendente;
`detach()` na limpeza. O runtime é recriado só quando mudam `baseUrl`, `apiKey`, `presentation`,
`errorReporting`, `debug`, `eligibilityTimeoutMs`, `deferTimeoutMs` ou a instância de `storage`.
`onEvent`, respondente e atributos são trocados por setter. O runtime substituído recebe
`dispose()`; o desmonte simples só `detach()`.

**Rationale**: React comita efeitos de baixo para cima — um `track` no efeito de montagem de um
filho rodaria antes de um runtime criado em efeito. `attach`/`detach` idempotentes sobrevivem ao
StrictMode. `dispose()` no substituído impede que um temporizador antigo continue emitindo pelo
ouvinte antigo; no desmonte simples ele quebraria o StrictMode, que remonta o mesmo runtime.

**Alternatives considered**: criar no efeito (perde o primeiro `track`); recriar a cada mudança de
`onEvent` (perderia a pesquisa em andamento).

---

## D-13 — Storage seguro com cópia em memória

**Decision**: `SafeStorage` envolve o adaptador: leitura que falha cai na cópia em memória;
gravações e remoções passam numa cadeia e saem na ordem pedida; falha é informada ao runtime, que
reporta `storage_error`.

**Rationale**: a sessão continua funcionando com o storage quebrado, e a última gravação sempre
vence.

**Alternatives considered**: propagar o erro (violaria a degradação silenciosa); gravar em paralelo
(última gravação poderia perder).

---

## D-14 — Adaptadores que recebem a instância

**Decision**: `createAsyncStorageAdapter(AsyncStorage)` e `createMmkvAdapter(mmkv)` em subpaths
próprios. O MMKV aceita a API das versões 2 e 3 (`delete`) e a da 4 (`remove`). Instância que não
tem a forma esperada cai para memória com aviso.

**Rationale**: o SDK não importa o pacote nativo e não força a dependência em quem não a tem; quem
já tem um dos dois passa o que já usa.

**Alternatives considered**: importar dinamicamente (bundlers resolvem estaticamente e quebrariam
quem não instalou); um adaptador genérico só (MMKV é síncrono e tem nomes diferentes).

---

## D-15 — UUID v4 com três fontes

**Decision**: `crypto.randomUUID` → `crypto.getRandomValues` → gerador próprio com `Math.random`,
resolvidos na hora de cada chamada; qualquer fonte que lance ou devolva algo que não é v4 cede à
próxima.

**Rationale**: o React Native não traz `crypto` sem polyfill, então o caminho próprio é o comum; um
polyfill instalado depois passa a valer. O `displayId` é chave de idempotência e precisa ser v4
válido sempre.

**Alternatives considered**: depender de pacote de UUID (dependência de runtime proibida).

---

## D-16 — Identidade do dispositivo

**Decision**: `deviceId` na chave `@pitaco/v1:device-id`; valor salvo que não é UUID é trocado;
chamadas simultâneas compartilham a mesma promessa de carga; `rotate()` grava outro.

**Rationale**: o contrato exige `deviceId` sempre na elegibilidade; ele precisa ser estável entre
aberturas e trocável no logout.

**Alternatives considered**: identificador do sistema operacional (exigiria módulo nativo e seria
dado pessoal).

---

## D-17 — Logout preserva a fila

**Decision**: `reset()` dispensa a pesquisa aberta com `programmatic`, esquece `reference` e
atributos, gira o `deviceId` e não toca na fila.

**Rationale**: o que o usuário anterior respondeu ainda precisa chegar; o próximo usuário não pode
herdar o histórico de elegibilidade do anterior.

**Alternatives considered**: limpar a fila (perde resposta legítima); manter o `deviceId` (o
servidor trataria duas pessoas como uma).

---

## D-18 — Normalização do schema e condição que falha fechado

**Decision**: tipo desconhecido é pulado e guardado para a supressão (até 20 tipos, 40
caracteres); pergunta conhecida com forma quebrada sai e é contada à parte; opção sem `value`,
longa (mais de 120) ou repetida sai; NPS é sempre 0 a 10; faixa de escala até 100 pontos. Na
condição: origem sem resposta, pulada, posterior, ela mesma ou descartada, ou número ilegível,
tornam a pergunta não aplicável.

**Rationale**: é a regra do contrato para compatibilidade adiante. Falhar fechado evita mostrar
pergunta que o autor só queria para um subconjunto.

**Alternatives considered**: recusar a pesquisa inteira por uma pergunta desconhecida (um SDK
antigo pararia de exibir qualquer pesquisa nova).

---

## D-19 — Submissão derivada do estado final

**Decision**: `buildSubmission(state)`: em `COMPLETED`, toda renderizável entra; em `DISMISSED`,
só o que foi alcançado (visitas > 0 ou pulada pela condição). Resposta a pergunta que deixou de se
aplicar vai como `NOT_APPLICABLE` sem valor.

**Rationale**: dispensa é dado, não erro, e o servidor preserva o parcial; mandar perguntas que a
pessoa nunca viu como `SKIPPED` distorceria a taxa de pulo.

**Alternatives considered**: mandar tudo como `SKIPPED` na dispensa (distorce a leitura).

---

## D-20 — Relatório de erro próprio e contido

**Decision**: fora da fila, sem esperar resposta. Mensagem montada pelo SDK
(`<etapa>: <nome do erro>`), nunca `error.message`; contexto com até 10 chaves alfanuméricas e
valores de até 80 caracteres, descartando nomes que sugerem dado pessoal (`mail`, `user`,
`device`, `answer`, `text`, `value`, `token`, `reference`, `respondent`, `attribute`, `name`,
`phone`, `key`, terminado em `id`). Mesmo erro uma vez, teto de 10 por sessão, falha desiste em
silêncio. `network_error` não é reportado (reportar rede fora pela rede não faz sentido); erro do
`onEvent` do app não é reportado.

**Rationale**: o canal serve à saúde do SDK no painel; precisa ser útil sem nunca carregar dado da
pessoa nem gerar laço de retroalimentação.

**Alternatives considered**: usar a ferramenta de erro do app (proibido pelo Princípio V); pôr na
fila (relatório velho perde valor e ocuparia o teto).
