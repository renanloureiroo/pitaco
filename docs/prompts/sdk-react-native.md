# Prompt — SDK React Native do Pitaco

Construa o SDK `@pitaco/react-native` no repositório `/Users/renanloureiro/www/pitaco`, na pasta nova `sdk/`, junto com as mudanças de backend e painel que ele exige. Trabalhe em fases sequenciais, uma por subagente, e só avance quando a fase anterior estiver verde. Não pergunte nada: as decisões de produto estão tomadas abaixo, e o que não estiver aqui você decide e registra no relatório. Não faça commit.

## Leitura obrigatória antes de qualquer código

- Vault `/Users/renanloureiro/Documents/Obsidian Vault/Pitaco`: `Arquitetura do Pitaco.md` e as features `04`, `06`, `07`, `08`, `09` e `10`.
- `backend/docs/backend/contrato-sdk.md`: é a fonte de verdade das rotas, payloads, cabeçalhos, códigos e idempotência. `backend/docs/backend/proxy.md` para a topologia com gateway.
- `backend/AGENTS.md` antes de tocar no backend. `painel/.specify/memory/constitution.md` e `painel/AGENTS.md` antes de tocar no painel.

## Restrições que não se negociam

1. **Zero módulos nativos.** Instalar é `npm install`, sem rebuild, e funciona no Expo Go. O SDK não importa `react-native-reanimated`, `react-native-gesture-handler`, `@gorhom/bottom-sheet`, `react-native-safe-area-context`, `@react-native-async-storage/async-storage` nem `react-native-mmkv`. Só `react` e `react-native` como peer dependencies.
2. **Degradação silenciosa.** Nenhuma falha do Pitaco chega ao usuário final nem derruba a árvore do app. Configuração inválida é visível em desenvolvimento (`__DEV__`, `console.warn` com instrução de correção) e silenciosa em produção.
3. **O SDK não decide elegibilidade.** Ele exibe o que a API mandou. Amostragem, histórico, cota, descanso e desempate são do servidor.
4. **Catálogo de eventos fixo.** Os eventos de interação são definidos pelo Pitaco, versionados e iguais para toda aplicação. O app hospedeiro pode ouvir, nunca criar tipo novo nem alterar o payload.
5. **Sem dado pessoal.** Nenhum evento carrega conteúdo de texto livre. O SDK nunca envia nada para ferramenta de terceiros, inclusive o Sentry do app hospedeiro.

## Decisões de arquitetura

### Três camadas, e o tracking mora na de baixo

```
core (headless)      máquina de estado da pesquisa, fila, transporte, eventos, tempo
ui (padrão)          componentes por tipo de pergunta, tema, slots, textos
presentation         bottom sheet (padrão), modal, inline
```

- **A máquina de estado do core é a única forma de mudar o estado da pesquisa.** Selecionar, trocar, voltar, avançar, pular, dispensar e concluir são ações do core. Os eventos de interação são emitidos pelo core como consequência dessas transições, nunca pelos componentes de UI.
- **Consequência:** qualquer UI, a padrão, uma substituição por tipo ou uma UI inteiramente própria construída sobre o hook headless, produz exatamente o mesmo fluxo de eventos. Customizar a aparência nunca quebra o tracking.
- A camada de UI padrão não tem acesso privilegiado. Ela usa a mesma API pública que um app usaria para montar a própria UI.

### Apresentação: bottom sheet próprio, sem gorhom

- **O padrão é bottom sheet.** Pesquisa de uma a cinco perguntas, disparada por evento, é interrupção curta. O sheet mantém o contexto do app visível, parece parte do produto e oferece o gesto de dispensar mais natural que existe. Tela cheia é pesada demais para esse uso e fica como opção.
- **Não usamos `@gorhom/bottom-sheet` como dependência.** Ele exige Reanimated e Gesture Handler, que são módulos nativos com plugin de Babel, rebuild e conflito de versão major com o que o app hospedeiro já usa. Isso quebra a restrição 1 e ataca o risco número três do roadmap, a fricção de integração.
- O sheet é implementado com o que o React Native já traz: `Modal` transparente, `Animated` com `useNativeDriver: true`, `PanResponder` para arrastar e soltar, `KeyboardAvoidingView` para o texto livre, `BackHandler` no Android e `AccessibilityInfo.isReduceMotionEnabled` para cortar animação.
- Safe area sem dependência: aceite `insets` na configuração, ou uma função `getInsets` fornecida pelo app, com padrão conservador por plataforma. Documente como passar o `useSafeAreaInsets` de quem já usa `react-native-safe-area-context`.
- **Quem já usa gorhom não fica de fora.** O componente `<PitacoSurveyContent />` renderiza a pesquisa ativa sem contêiner nenhum, e pode ser colocado dentro do sheet do próprio app. O guia traz o exemplo com gorhom e com uma tela do React Navigation.
- `presentation`: `"bottom-sheet"` (padrão), `"modal"` (tela cheia) ou `"inline"` (o app posiciona `<PitacoSurveyContent />` onde quiser e o SDK não abre contêiner).

### Níveis de customização, do mais leve ao total

1. **Tema** num ponto único: cores, tipografia, raio, espaçamento, claro e escuro acompanhando `useColorScheme` ou um esquema forçado pelo app. Valor inválido cai no padrão. Contraste mínimo verificado em desenvolvimento, com aviso.
2. **Textos**: todos os rótulos de interface (Próxima, Voltar, Enviar, Fechar, agradecimento, obrigatória) em pt-BR por padrão e substituíveis por um objeto `strings`.
3. **Slots de moldura**: cabeçalho, barra de progresso, rodapé com botões, botão de fechar e tela de agradecimento, cada um substituível por componente.
4. **Renderização por tipo de pergunta** (feature 8): `renderers={{ nps: MeuNps }}`. O componente recebe a pergunta, o valor atual, as ações do core (`select`, `deselect`, `setText`), o erro de validação e o tema. Validação e envio continuam com o SDK. Cada substituição roda dentro de error boundary próprio e, se falhar, cai no renderizador padrão daquele tipo e emite o relatório de erro.
5. **Headless total**: `usePitacoSurvey()` expõe o estado (pesquisa ativa, pergunta atual, respostas, progresso, erros, podeVoltar, podeAvançar) e as ações. O app monta a UI inteira, e o tracking continua completo porque as ações são do core.

## Superfície pública

```tsx
<PitacoProvider
  baseUrl="https://pitaco.exemplo.com/api"   // obrigatório
  apiKey="pk_..."                            // obrigatório
  respondent={{ reference: userIdHash }}     // opcional; sem ele vale o deviceId
  attributes={{ plano: "pro" }}              // opcional; nunca dado pessoal
  storage={createAsyncStorageAdapter(AsyncStorage)}  // opcional; o app passa a própria instância
  presentation="bottom-sheet"
  theme={...} strings={...} renderers={...} slots={...}
  onEvent={(event) => analytics.track(event.type, event)}  // só leitura
  errorReporting={true}
  debug={__DEV__}
>
```

- `usePitaco()` devolve `track(eventName, attributes?)`, `setRespondent`, `setAttributes`, `block(reason)` e `unblock(reason)` para telas em que nada pode aparecer, `defer()` e `release()` para adiar e liberar uma pesquisa disponível (feature 8), e `reset()` para logout.
- `<PitacoBlock />` bloqueia enquanto estiver montado, para uso declarativo na tela de pagamento.
- `usePitacoSurvey()` e `<PitacoSurveyContent />` para os modos headless e inline. O hook expõe `available`, que muda quando há pesquisa pronta para exibir, e as ações do core, incluindo `dismiss(via)` para o contêiner do app informar como a pessoa fechou, como no `onDismiss` de um sheet do gorhom. `<PitacoSurveyContent />` aceita `onFinish` para o app fechar o próprio contêiner ou voltar a navegação ao concluir.
- Adaptadores de storage em subpaths (`@pitaco/react-native/storage/async-storage`, `.../storage/mmkv`) que **recebem a instância do app** e não importam o pacote nativo. Sem adaptador, o SDK usa memória e avisa em desenvolvimento que a fila não sobrevive a um reinício.
- `@pitaco/react-native/preview` exporta `<PitacoPreview schema={...} />`: renderizador sobre um schema em memória, sem transporte, sem sessão e sem envio. Os eventos são entregues só a `onEvent`.

## Catálogo de eventos de interação, versão 1

Todo evento tem o envelope:

```ts
type PitacoEvent = {
  catalogVersion: 1;
  type: EventType;
  displayId: string;        // o mesmo id gerado no dispositivo para a exibição
  seq: number;              // monotônico por exibição, começa em 1; chave de idempotência
  occurredAt: string;       // ISO 8601, relógio do dispositivo
  elapsedMs: number;        // relógio monotônico desde survey_presented; imune a ajuste de hora
  questionKey?: string;     // chave estável da pergunta, quando o evento é de pergunta
  data: EventData[type];    // payload fechado por tipo
};
```

| Tipo | Quando | `data` |
| --- | --- | --- |
| `survey_presented` | O contêiner ficou visível ao usuário | `presentation`, `questionCount`, `renderableCount`, `triggerEvent` |
| `question_viewed` | Uma pergunta passou a ser a atual | `position`, `visit` (1 na primeira vez, 2 ao voltar a ela…), `from`: `start` \| `next` \| `back` |
| `answer_selected` | Primeira escolha numa pergunta de escolha única, avaliação, escala ou NPS, ou opção adicionada em múltipla escolha | `value` |
| `answer_changed` | Escolha trocada numa pergunta que já tinha valor | `from`, `to` |
| `answer_deselected` | Opção removida em múltipla escolha, ou valor limpo | `value` |
| `text_focused` | Campo de texto livre recebeu foco | — |
| `text_edited` | Texto alterado, com debounce de 1 s | `length` |
| `text_blurred` | Campo perdeu o foco | `length` |
| `validation_blocked` | Tentou avançar com obrigatória em branco | `reason`: `required_missing` |
| `question_skipped` | Avançou de pergunta opcional sem responder | — |
| `question_not_applicable` | A condição pulou a pergunta | `sourceKey` |
| `navigated_next` | Avançou | `toKey` |
| `navigated_back` | Voltou | `toKey` |
| `question_left` | A pergunta deixou de ser a atual, por qualquer motivo | `visit`, `to`: `next` \| `back` \| `dismiss` \| `complete`, `durationMs`, `activeMs`, `answered` |
| `survey_backgrounded` | O app foi para segundo plano com a pesquisa aberta | — |
| `survey_foregrounded` | O app voltou com a pesquisa aberta | `backgroundMs` |
| `survey_dismissed` | O usuário fechou | `via`: `close_button` \| `swipe` \| `backdrop` \| `hardware_back` \| `navigation` \| `programmatic`, `position`, `answeredCount` |
| `survey_completed` | Enviou a última pergunta | `answeredCount`, `skippedCount`, `notApplicableCount`, `activeMs` |

Regras do catálogo:

- **Tempo em cada pergunta:** `durationMs` é o tempo de relógio monotônico entre `question_viewed` e `question_left`. `activeMs` desconta o tempo em segundo plano (`AppState`). Cada visita a uma pergunta tem os próprios tempos; o painel soma as visitas.
- **`value`** é o `value` da opção ou o número escolhido, nunca o rótulo. Texto livre só aparece como `length`.
- Eventos antes de existir exibição (bloqueio, adiamento, descarte do adiamento) não entram no catálogo enviado ao servidor. Vão só para `onEvent`, com tipos próprios prefixados `placement_` e documentados à parte.
- Mudar o catálogo é mudança de contrato: tipo novo exige `catalogVersion` novo, e o servidor precisa aceitar versões anteriores.
- O catálogo mora num único módulo tipado (`union` discriminada), exportado para o painel e para os testes. O teste de contrato falha se um tipo existir no SDK e não no backend, ou o contrário.

## Fases

### Fase 1 — Backend: ingestão e leitura de eventos

- `POST /collect/displays/{displayId}/events` com lote de até 100 eventos, autenticado pela chave e dentro do rate limit. Responde 202.
- Tabela `survey_display_events` com `unique (display_id, seq)` para idempotência do reenvio, `ON DELETE CASCADE` a partir da exibição (a exclusão de respondente e a retenção já alcançam), e índices para as leituras do painel.
- Validação: a exibição precisa ser da aplicação da chave (senão 202 descartado, sem revelar existência). Tipo desconhecido é descartado e contado, nunca 400, para SDK mais novo não quebrar. Campos extras são ignorados. Qualquer campo com conteúdo textual em evento de texto é descartado. Teto de eventos por exibição e janela máxima de aceitação após a abertura, ambos configuráveis.
- Leitura `GET /applications/{id}/surveys/{surveyId}/results/behavior` com os mesmos recortes dos resultados (período, versão, atributo): funil por pergunta (vista, respondida, pulada, abandonada nela), mediana e p90 de `activeMs` por pergunta, taxa de volta, taxa de troca de resposta, bloqueios de validação, e distribuição de `via` na dispensa.
- `SdkFeature` e o catálogo de capacidades ganham o recurso de eventos de interação. `contrato-sdk.md` documenta a rota e o catálogo inteiro.
- Siga `backend/AGENTS.md` inteiro, com E2E cobrindo idempotência, isolamento entre aplicações, tipo desconhecido, texto descartado e os números da leitura.

### Fase 2 — SDK core headless

- Projeto em `sdk/` com TypeScript estrito, `react-native-builder-bob`, Jest com `@testing-library/react-native`, ESLint, e `npm run verify` como portão.
- Tipos do contrato gerados do OpenAPI do backend (`/api/v3/api-docs`) com `openapi-typescript`, com script de regeneração.
- Cliente HTTP com timeout curto na elegibilidade (padrão 3 s), `X-Pitaco-Key` e `X-Pitaco-Sdk-Version` em toda requisição, e nenhuma exceção escapando.
- Identidade: `deviceId` gerado uma vez e persistido; `displayId` gerado no dispositivo com UUID v4 (`crypto.randomUUID` quando existir, senão gerador próprio testado).
- Fila local persistente com respostas e eventos no mesmo armazenamento: grava antes de enviar, reenvia com backoff e em cada abertura do app, respeita `Retry-After` do 429, tem teto de tamanho e de idade, e nunca duplica (idempotência pelo `displayId` e pelo `seq`).
- Máquina de estado da pesquisa como função pura testável: descarte silencioso de tipo e campo desconhecidos, avaliação local da condição, `NOT_APPLICABLE`, validação de obrigatória, voltar e corrigir, dispensa com respostas parciais, e emissão dos eventos do catálogo.
- Supressão: sem pergunta renderizável, nada é exibido, nenhuma exibição é aberta e `POST /collect/suppressions` é enviado com o motivo.
- A exibição só é aberta (`POST /collect/displays`) quando o contêiner fica visível de fato.
- Relatório de erro próprio (`POST /collect/sdk-errors`), desligável, sem dado de usuário nem resposta, com desistência silenciosa e sem retroalimentação.

### Fase 3 — UI padrão e apresentação

- Os seis tipos do catálogo, com rótulos de escala, aviso de texto livre (`freeTextNotice`), progresso, voltar, fechar em toda pergunta, e agradecimento curto que fecha sozinho.
- Bottom sheet, modal e inline conforme as decisões acima, com teclado, safe area injetável, voltar do Android e movimento reduzido.
- Tema, textos, slots e renderização por tipo, cada substituição isolada em error boundary com queda para o padrão.
- Acessibilidade: rótulos e papéis para leitor de tela, foco movido para a pergunta nova a cada navegação, alvos de toque de ao menos 44 pt, e respeito à escala de fonte do sistema.

### Fase 4 — Controle de onde e quando

- `track` dispara a consulta, e só uma pesquisa é exibida por sessão de app (feature 6), mesmo após dispensa. Reabrir o app zera o limite.
- `block`, `unblock` e `<PitacoBlock />`: com bloqueio ativo, a pesquisa disponível não é exibida.
- `defer` e `release` com prazo máximo configurável; estourado o prazo, a pesquisa é descartada sem abrir exibição.

### Fase 5 — Projeto de exemplo

O exemplo mora junto do SDK, em `sdk/example/`, no mesmo molde das bibliotecas React Native criadas com `create-react-native-library`. Não é demonstração descartável: é o harness de integração que as features 4 e 8 pedem, e o que o guia promete a quem nunca viu o Pitaco.

**Estrutura e ligação com o SDK**

- App Expo com Expo Router, TypeScript estrito, rodável no Expo Go em iOS e Android sem nenhum build nativo. Se algum cenário exigir módulo nativo, a restrição 1 foi violada e o problema está no SDK, não no exemplo.
- O exemplo consome o **código-fonte** do SDK, não o pacote publicado: `metro.config.js` com `watchFolders` apontando para `sdk/` e resolução de `@pitaco/react-native` para `../src`, com uma única cópia de `react` e `react-native` (bloqueie as de `sdk/node_modules`). Editar o SDK recarrega o exemplo na hora.
- `react-native-reanimated`, `react-native-gesture-handler`, `@gorhom/bottom-sheet`, `react-native-safe-area-context`, `@react-native-async-storage/async-storage` e `react-native-mmkv` são dependências **do exemplo**, nunca do SDK. É assim que o exemplo prova que o SDK convive com elas sem exigi-las.

**Conexão com o backend**

- Configuração por `EXPO_PUBLIC_PITACO_BASE_URL` e `EXPO_PUBLIC_PITACO_API_KEY` num `.env` com `.env.example` documentado. O README explica o endereço certo por alvo: `localhost` no simulador iOS, `10.0.2.2` no emulador Android e o IP da rede local em aparelho físico.
- Script `sdk/example/scripts/seed.ts` que usa a API administrativa do backend local para criar uma aplicação de exemplo, emitir a chave, criar uma pesquisa com os seis tipos de pergunta, rótulos de escala, uma condição e aviso de texto livre, configurar o disparo e publicar. Ele escreve a chave no `.env` do exemplo. Rodar o seed duas vezes não duplica nada.
- Um segundo perfil de configuração aponta para um proxy transparente local, para exercitar a topologia com gateway do ADR-0008. Use um `Caddyfile` ou um script Node pequeno em `sdk/example/proxy/`, seguindo `backend/docs/backend/proxy.md`.

**Cenários, um por tela, listados numa tela inicial**

1. **Sheet do SDK:** só o provider e um botão que chama `track`, com o bottom sheet próprio do SDK, feito só com `Modal`, `Animated` e `PanResponder` do React Native. É a integração mínima e o código que o guia mostra. Precisa exibir arrastar para fechar, toque no fundo para fechar, voltar do Android, teclado subindo no texto livre e animação cortada com movimento reduzido.
2. **Com gorhom:** a pesquisa dentro de um `BottomSheetModal` do `@gorhom/bottom-sheet`, com snap points, backdrop, arrastar para fechar, teclado do texto livre tratado pelo próprio gorhom e `useSafeAreaInsets` passado ao SDK. O app escuta a pesquisa disponível pelo hook, abre o sheet dele e renderiza `<PitacoSurveyContent />` dentro. Fechar o sheet pelo gesto do gorhom precisa virar `survey_dismissed` com `via: "swipe"`, o que exige uma ação de dispensa explícita no core que o app chama no `onDismiss` do gorhom.
3. **Como tela:** a pesquisa como uma rota do Expo Router, empilhada na navegação. Com `presentation="inline"`, o app recebe a pesquisa disponível, faz `router.push("/pesquisa")` e a rota renderiza `<PitacoSurveyContent />` em tela cheia, com cabeçalho nativo da navegação. Concluir volta sozinho para a tela anterior. Sair pela seta do cabeçalho, pelo gesto de voltar do iOS ou pelo voltar do Android com a pesquisa aberta vira `survey_dismissed` com `via: "navigation"`: o core registra a dispensa quando `<PitacoSurveyContent />` desmonta com a pesquisa em andamento.
4. **Comparar apresentações:** uma tela com três botões, **Sheet do SDK**, **Gorhom** e **Tela**, todos abrindo **a mesma pesquisa** do seed nas três formas dos cenários 1, 2 e 3. Um seletor alterna tema claro e escuro. O modal em tela cheia fica fora da comparação e aparece só no cenário 10. A pesquisa é carregada uma vez e reaberta de forma local, sem nova consulta de elegibilidade, para dar para alternar à vontade. Os eventos emitidos em cada forma aparecem lado a lado no painel de depuração, para comparar que a sequência é a mesma.
5. **Tema:** claro, escuro e um tema com valores inválidos que degrada para o padrão.
6. **Textos:** rótulos substituídos em outro idioma.
7. **Renderizador substituído:** um NPS desenhado pelo app convivendo com os tipos padrão na mesma pesquisa, mais um renderizador que lança erro de propósito e cai no padrão.
8. **Slots:** cabeçalho e rodapé próprios.
9. **Headless:** UI inteiramente própria sobre `usePitacoSurvey()`.
10. **Modal e inline:** o modal em tela cheia do SDK, e `<PitacoSurveyContent />` embutido no meio de uma tela com rolagem, sem contêiner.
11. **Bloqueio:** uma tela de pagamento com `<PitacoBlock />`, onde o evento dispara e nada aparece.
12. **Adiamento:** pesquisa adiada numa tela e liberada em outra, e um adiamento que estoura o prazo e é descartado.
13. **Sem rede:** responder em modo avião, fechar o app, reabrir com rede e ver a resposta chegar uma vez.
14. **Falhas:** chave inválida, endereço inalcançável e API lenta, com o app seguindo navegável.
15. **Proxy:** o mesmo fluxo pela topologia com gateway.

A reabertura local do cenário 4 é recurso **só do exemplo**: use o `<PitacoPreview />` alimentado pelo schema da pesquisa do seed, para não abrir exibição nem contaminar os resultados. Os cenários 1, 2 e 3 usam o fluxo real, com exibição e resposta gravadas no backend.

**Capturas**

- Com simulador disponível, gere capturas da mesma pergunta nas três formas do cenário 4, Sheet do SDK, Gorhom e Tela, em claro e escuro, em iOS e Android, e salve em `sdk/example/docs/capturas/`. Grave também um vídeo curto de cada forma com a pesquisa percorrida do começo ao fim. O README do SDK mostra as capturas lado a lado na seção de apresentação. Sem simulador, registre no relatório que as capturas ficaram por fazer.

**Painel de depuração do exemplo**

- Uma aba fixa que mostra, ao vivo, o que o SDK está fazendo: os eventos do catálogo recebidos em `onEvent` com o payload completo, o conteúdo da fila local, as requisições feitas e as respostas, e o estado da identidade. É por aqui que se confere, a olho, que as quatro UIs produzem a mesma sequência de eventos.
- Um botão para limpar storage e identidade, simulando instalação nova, e outro para simular o app reaberto, zerando o limite de uma pesquisa por sessão.

**Teste do exemplo**

- Fluxos Maestro em `sdk/example/.maestro/` para os cenários 1, 2, 3, 7, 9, 11 e 13, rodáveis localmente contra o backend com o seed aplicado. Se o ambiente não tiver simulador disponível, deixe os fluxos escritos, valide a sintaxe e registre no relatório que não foram executados.
- `npm run typecheck` e `npm run lint` do exemplo entram no `verify` do SDK.

### Fase 6 — Documentação

- `sdk/README.md` com o guia do zero à primeira pesquisa exibida, escrito para ser seguido junto do cenário 1 do exemplo.
- Referência de todas as opções com seus padrões, e o catálogo de eventos completo com um exemplo de payload por tipo.
- Seção de privacidade: o que o SDK envia, o que nunca envia, e o aviso de não mandar dado pessoal em `attributes` e `reference`.
- Seção "por que a pesquisa não apareceu", cobrindo cada motivo de silêncio, com o painel de depuração do exemplo como ferramenta de diagnóstico.
- Guias curtos de integração com gorhom, React Navigation e UI headless, cada um apontando para o cenário correspondente do exemplo.

### Fase 7 — Painel

- Preview (feature 2): o painel passa a renderizar `<PitacoPreview />` com `react-native-web`, no construtor da pesquisa, a partir do estado atual do formulário, sem salvar. Ao lado, a lista ao vivo dos eventos que o preview emitiu, para o pesquisador ver exatamente o que será medido. O preview não cria exibição nem resposta.
- Resultados: seção "Comportamento" com a leitura da fase 1, respeitando os recortes da tela, e com a definição de cada métrica visível junto dos números.
- Simulador `e2e/stub-api` e E2E cobrindo as duas telas.

### Fase 8 — Integração contínua

- `.github/workflows/sdk.yml` rodando o `verify` do SDK e do exemplo, o teste de contrato do catálogo contra o backend, e um `npx expo export` do exemplo para provar que o bundle monta sem módulo nativo exigido pelo SDK.

## Definição de pronto

- `sdk`: `npm run verify` verde, com testes de unidade da máquina de estado cobrindo cada evento do catálogo, a ordem entre eles e os tempos com relógio falso, e testes de componente para cada tipo de pergunta, cada apresentação e cada nível de customização.
- Teste de contrato provando que a UI padrão, uma UI com renderizador substituído e uma UI headless produzem **a mesma sequência de eventos** para o mesmo roteiro de interação.
- Casos de borda da feature 4 cobertos: API fora do ar, lenta, com resposta malformada, chave inválida, pergunta desconhecida, só perguntas desconhecidas, resposta sem rede enviada exatamente uma vez ao reabrir, reenvio sem duplicar, item velho descartado, dispensa na segunda pergunta preservando a primeira.
- `sdk/example`: sobe no Expo Go contra o backend local com o seed aplicado, e os quinze cenários funcionam. O cenário 1 é seguido do zero usando só o README, sem conhecimento prévio.
- `backend`: `./mvnw verify` verde. `painel`: `npm run verify` verde, sem rodar junto com o Maven e sem servidor antigo nas portas 3001 e 4010.
- Relatório final com as decisões tomadas, o que ficou de fora e o resultado exato de cada comando.
