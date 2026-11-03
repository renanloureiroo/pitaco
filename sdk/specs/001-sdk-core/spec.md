# Feature Specification: SDK React Native — core headless

**Feature Branch**: `001-sdk-core`

**Created**: 2026-09-12

**Status**: Implemented

**Input**: User description: "Quero um SDK React Native que qualquer app instale com npm install, chame track num evento e mostre a pesquisa que o backend mandar, sem nunca quebrar o app."

## Contexto

O backend do Pitaco já expõe a superfície pública `/collect`: elegibilidade, abertura de
exibição, envio de respostas, supressão e relatório de erro, todas autenticadas por
`X-Pitaco-Key`. Falta o outro lado: o código que roda dentro do app hospedeiro, decide quando
perguntar ao servidor, guarda o que a pessoa respondeu e entrega tudo de volta, inclusive sem
rede.

Esta feature entrega esse lado sem interface: o **core headless** do SDK `@pitaco/react-native`.
Um app consegue integrar o Pitaco, disparar a consulta por evento, receber a pesquisa, percorrê-la
por ações e ter abertura e respostas entregues ao servidor exatamente uma vez — tudo pela API
pública de hooks, sem desenhar nada. A UI padrão e as apresentações são da feature
`002-sdk-ui-apresentacoes`; o catálogo de eventos de interação, da `003-sdk-eventos-interacao`;
bloqueio, adiamento e limite por sessão, da `004-sdk-controle-onde-quando`.

Três fronteiras desenham o escopo:

- **O SDK não decide elegibilidade.** Amostragem, histórico, cota, descanso e desempate são do
  servidor. O SDK pergunta e exibe o que veio; a única regra que ele avalia localmente é a
  condição entre perguntas de uma versão já entregue.
- **Falha do Pitaco é silêncio no app.** Nenhuma falha de rede, de servidor, de configuração ou
  de armazenamento chega ao usuário final. Quem integra vê, em desenvolvimento, o que está errado
  e como corrigir.
- **Zero módulo nativo.** O SDK depende só de `react` e `react-native`. Armazenamento persistente
  entra por adaptador que recebe a instância que o app já tem.

## Clarifications

### Session 2026-09-12

- Q: A exibição é aberta no servidor quando a elegibilidade devolve a pesquisa ou quando ela
  aparece? → A: Quando aparece. A pesquisa fica "pronta" ao chegar, e a abertura
  (`POST /collect/displays`) só sai quando a UI informa que o contêiner ficou visível de fato
  (`present()`). Pesquisa que não aparece não conta como exibida.
- Q: O SDK precisa de armazenamento persistente para funcionar? → A: Não. Sem adaptador, ele usa
  memória e avisa em desenvolvimento que a fila não sobrevive a um reinício. Com adaptador, a
  fila e o `deviceId` persistem.
- Q: Qual o tempo máximo da consulta de elegibilidade? → A: 3 segundos por padrão, configurável
  entre 250 ms e 30 s. Depois disso a consulta é abandonada e nada aparece; as outras rotas têm
  10 s.
- Q: O que o SDK faz quando a chave é recusada (401/403)? → A: Para de consultar até o fim da
  sessão do runtime, com um único aviso de correção em desenvolvimento.
- Q: Logout apaga o que o usuário anterior respondeu e ainda não foi entregue? → A: Não. `reset()`
  dispensa a pesquisa aberta, esquece respondente e atributos e troca o `deviceId`, mas a fila
  continua: o que já foi respondido precisa chegar.
- Q: Como o SDK lida com tipos de pergunta que não conhece? → A: Pula em silêncio. Se não sobrar
  pergunta renderizável, não exibe nada, não abre exibição e envia a supressão com o motivo.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Integrar e receber a pesquisa disparada por um evento (Priority: P1)

Quem desenvolve o app envolve a raiz com `<PitacoProvider baseUrl apiKey>` e chama
`usePitaco().track("checkout_completed")` no momento certo. Se o servidor tiver pesquisa para
aquele respondente, ela fica disponível em `usePitacoSurvey()`; quando o contêiner do app a mostra,
a exibição é aberta no servidor; quando a pessoa conclui ou dispensa, respostas e desfecho são
enviados.

**Why this priority**: é o produto. Sem consulta, oferta, abertura e envio, não existe coleta pelo
SDK, e todas as outras histórias dependem deste caminho.

**Independent Test**: com o servidor falso do contrato, montar o Provider, chamar `track`,
verificar que a pesquisa ficou disponível sem abertura, chamar `present()`, responder pelas ações
do hook, concluir e conferir que o servidor recebeu uma abertura e um envio `COMPLETED` com as
respostas.

**Acceptance Scenarios**:

1. **Given** um Provider configurado e uma pesquisa publicada para o evento, **When** o app chama
   `track(evento)`, **Then** o SDK consulta a elegibilidade com o evento, o respondente e os
   atributos, e `usePitacoSurvey().available` passa a ser verdadeiro.
2. **Given** uma pesquisa disponível ainda não mostrada, **When** nenhum contêiner chama
   `present()`, **Then** nenhuma exibição é aberta no servidor.
3. **Given** uma pesquisa disponível, **When** o contêiner chama `present()`, **Then** a abertura
   sai com o `displayId` gerado no dispositivo, o respondente, os atributos do instante e a versão
   do SDK.
4. **Given** uma pesquisa em andamento, **When** a pessoa responde e conclui pelas ações do hook,
   **Then** o envio leva desfecho `COMPLETED` e todas as perguntas renderizáveis como `ANSWERED`,
   `SKIPPED` ou `NOT_APPLICABLE`.
5. **Given** uma pesquisa em andamento, **When** a pessoa dispensa na segunda pergunta, **Then** o
   envio leva desfecho `DISMISSED` com a primeira resposta preservada e sem as perguntas que ela
   não alcançou.
6. **Given** nenhuma pesquisa para o evento, **When** o app chama `track`, **Then** nada fica
   disponível e nenhuma exibição é aberta.
7. **Given** uma pesquisa já em andamento, **When** outra chega por um novo `track`, **Then** a
   nova é ignorada.
8. **Given** uma pesquisa disponível, **When** ela é dispensada antes de o contêiner ficar
   visível, **Then** ela é descartada sem abrir exibição e sem envio.

---

### User Story 2 - O app nunca quebra por causa do Pitaco (Priority: P1)

O Pitaco é acessório no app hospedeiro. API fora do ar, lenta, com resposta malformada, chave
errada, limite de requisição, armazenamento quebrado e configuração inválida precisam resultar em
silêncio para o usuário final — e num aviso claro, em desenvolvimento, para quem integra.

**Why this priority**: um SDK que derruba ou trava o app hospedeiro é removido na primeira
ocorrência. É condição de existência, não refinamento.

**Independent Test**: para cada falha simulada no servidor falso (rede fora, latência acima do
timeout, corpo que não é JSON, 401, 429), chamar `track` e conferir que nada lança, nada fica
disponível, nenhuma exibição é aberta e o aviso de desenvolvimento sai uma única vez.

**Acceptance Scenarios**:

1. **Given** o backend inalcançável, **When** o app chama `track`, **Then** a promessa resolve sem
   erro, nada aparece e o integrador recebe um aviso com a dica de endereço por alvo (simulador
   iOS, emulador Android, aparelho físico).
2. **Given** a elegibilidade demorando mais que o timeout, **When** o prazo vence, **Then** a
   consulta é abandonada, a conexão é abortada e nada aparece.
3. **Given** a chave recusada, **When** o app chama `track`, **Then** nada aparece, o integrador
   vê como corrigir uma vez, e as consultas seguintes nem saem até o fim da sessão do runtime.
4. **Given** um `429` com `Retry-After`, **When** o app chama `track` de novo antes do prazo,
   **Then** nenhuma requisição sai até o prazo vencer.
5. **Given** `baseUrl` ou `apiKey` ausentes ou inválidos, **When** o Provider monta, **Then** o
   SDK fica desligado, os hooks viram operações nulas, a árvore do app renderiza normalmente e,
   só em desenvolvimento, sai a instrução de correção.
6. **Given** valores inválidos de apresentação ou de timeout, **When** o Provider monta, **Then**
   o SDK usa o padrão daquele valor e avisa em desenvolvimento.
7. **Given** um `onEvent` do app que lança, **When** um evento é entregue, **Then** o SDK segue,
   avisa uma vez e não reporta como erro próprio.
8. **Given** o adaptador de storage falhando, **When** o SDK grava ou lê, **Then** a sessão
   continua com cópia em memória e a falha é reportada como `storage_error`.
9. **Given** um hook chamado fora do Provider, **When** o componente renderiza, **Then** o hook
   devolve ações inertes e avisa em desenvolvimento.

---

### User Story 3 - Percorrer a pesquisa pela máquina de estado (Priority: P1)

Selecionar, trocar, desmarcar, digitar, avançar, voltar, pular, dispensar e concluir são ações de
uma máquina de estado pura no core. Ela valida obrigatória, avalia a condição localmente, marca o
que ficou não aplicável e monta o envio. É ela que qualquer UI — a padrão, uma substituída ou uma
própria — aciona.

**Why this priority**: é o que garante que toda UI produza o mesmo estado e o mesmo envio; sem
ela, cada interface reimplementaria a regra de negócio da pesquisa.

**Independent Test**: com o schema de referência (seis tipos e uma condição), aplicar à máquina
um roteiro de ações com relógio falso e conferir estado, progresso e o envio resultante.

**Acceptance Scenarios**:

1. **Given** uma pergunta obrigatória em branco, **When** a pessoa tenta avançar ou concluir,
   **Then** a máquina não avança e expõe o erro de validação; responder limpa o erro.
2. **Given** uma pergunta condicionada a uma resposta anterior, **When** a condição não é
   satisfeita, **Then** a pergunta é pulada e vai no envio como `NOT_APPLICABLE`, sem valor.
3. **Given** a pessoa voltou e trocou a resposta de origem, **When** a condição passa a ser
   satisfeita, **Then** a pergunta antes pulada passa a se aplicar.
4. **Given** uma pergunta opcional em branco, **When** a pessoa avança, **Then** ela vai no envio
   como `SKIPPED`.
5. **Given** uma escolha que não pertence à pergunta (rótulo no lugar do `value`, número fora da
   faixa, fração, escolha em texto livre), **When** chega à máquina, **Then** é ignorada.
6. **Given** texto livre, **When** a pessoa digita além de 2000 caracteres, **Then** o valor é
   limitado a 2000.
7. **Given** uma ação com `questionKey` de outra pergunta, **When** chega à máquina, **Then** é
   ignorada.
8. **Given** a última pergunta aplicável, **When** a pessoa avança, **Then** a pesquisa é
   concluída; `complete()` antes dela é ignorado.
9. **Given** um schema com campo desconhecido, `null` no lugar de ausente ou ordem embaralhada,
   **When** é normalizado, **Then** o campo é ignorado, `null` e ausente valem o mesmo e as
   perguntas seguem `position`.

---

### User Story 4 - Respostas chegam uma vez, mesmo sem rede (Priority: P2)

A pessoa responde em modo avião, fecha o app e reabre com rede. A resposta precisa chegar ao
servidor uma única vez, depois da abertura da exibição, sem que o app faça nada.

**Why this priority**: perder resposta por falta de rede é perder o dado que justifica o produto;
duplicar resposta corrompe o resultado. Vem depois das três primeiras porque o caminho com rede já
entrega valor.

**Independent Test**: com o servidor falso fora do ar, concluir uma pesquisa; recriar o runtime
sobre o mesmo storage com o servidor de volta e conferir que a abertura e o envio saem uma vez, na
ordem, e que a fila fica vazia.

**Acceptance Scenarios**:

1. **Given** abertura e envio produzidos, **When** a fila os recebe, **Then** eles são gravados no
   storage antes de qualquer tentativa de envio.
2. **Given** a abertura ainda não entregue, **When** a fila envia, **Then** nada que depende dela
   (envio, eventos) sai antes.
3. **Given** rede fora, timeout ou 5xx, **When** o envio falha, **Then** o item fica e é tentado
   de novo com espera exponencial, e também a cada abertura do app e volta do segundo plano.
4. **Given** um `429` com `Retry-After`, **When** a fila tenta enviar, **Then** a fila inteira
   pausa até o prazo, inclusive depois de o app reabrir.
5. **Given** uma resposta perdida no caminho e reenviada, **When** o servidor a reconhece como
   reenvio idêntico, **Then** ela continua uma só.
6. **Given** uma recusa definitiva (400, 401, 404, 409, 422), **When** o servidor responde,
   **Then** o item é descartado; uma abertura descartada leva junto o que dependia dela.
7. **Given** itens mais velhos que a idade máxima ou além do teto de tamanho, **When** a fila é
   drenada, **Then** eles são descartados sem alarde, os mais antigos primeiro.
8. **Given** conteúdo corrompido no storage, **When** a fila carrega, **Then** ele é ignorado e a
   fila segue vazia.

---

### User Story 5 - Identificar o respondente sem dado pessoal (Priority: P2)

Sem identificação do app, o respondente é o dispositivo: um UUID gerado uma vez e persistido. Com
`respondent.reference`, o app passa uma referência opaca que prevalece no servidor. Atributos de
segmentação acompanham a consulta e a abertura. No logout, o próximo usuário do aparelho não
herda o histórico do anterior.

**Why this priority**: sem identidade estável o servidor não aplica descanso, histórico nem cota;
mas ela só precisa existir depois que o fluxo básico funciona.

**Independent Test**: criar o runtime sobre um storage vazio, conferir que o `deviceId` é gerado e
persistido; recriar e conferir que é o mesmo; chamar `reset()` e conferir que muda e que a fila
não foi apagada.

**Acceptance Scenarios**:

1. **Given** um storage vazio, **When** o SDK precisa do `deviceId`, **Then** gera um UUID v4 e o
   persiste; chamadas simultâneas recebem o mesmo valor.
2. **Given** um valor salvo que não é UUID, **When** o SDK carrega, **Then** ele é trocado.
3. **Given** `respondent.reference`, **When** o SDK consulta a elegibilidade, **Then** envia a
   referência e o `deviceId`; sem referência, só o `deviceId`.
4. **Given** atributos fora do formato (nome vazio, valor objeto, valor longo, mais de 50),
   **When** chegam ao SDK, **Then** os inválidos são ignorados com aviso em desenvolvimento.
5. **Given** um logout, **When** o app chama `reset()`, **Then** a pesquisa aberta é dispensada
   como `programmatic`, referência e atributos são esquecidos, o `deviceId` é trocado e a fila
   permanece.
6. **Given** o storage quebrado, **When** o SDK precisa do `deviceId`, **Then** a identidade vale
   pelo menos para a sessão.

---

### User Story 6 - O servidor fica sabendo do que deu errado no SDK (Priority: P3)

Quando a pesquisa chega e o SDK não sabe desenhar nenhuma pergunta, o servidor é avisado por uma
supressão. Quando o próprio SDK falha (resposta malformada, storage, renderização), ele manda um
relatório de erro pelo canal do Pitaco — nunca pelo do app, e sem nada da pessoa.

**Why this priority**: é o que alimenta a saúde do SDK no painel. Útil, mas o app funciona sem.

**Independent Test**: devolver na elegibilidade uma pesquisa só com tipos desconhecidos e conferir
que nada é exibido, nenhuma exibição é aberta e a supressão é enviada; provocar uma falha interna
e conferir o relatório enviado, sem dado de usuário e uma vez só.

**Acceptance Scenarios**:

1. **Given** uma pesquisa com um tipo desconhecido e outros conhecidos, **When** ela chega,
   **Then** o desconhecido é pulado e a resposta chega normalmente.
2. **Given** uma pesquisa só com tipos desconhecidos, **When** ela chega, **Then** nada é
   exibido, nenhuma exibição é aberta, e a supressão `unknown_question_type` com os tipos vai
   pela fila.
3. **Given** uma falha interna, **When** o relatório é montado, **Then** a mensagem traz a etapa e
   o nome do erro, nunca a mensagem original, e o contexto só leva chaves técnicas permitidas.
4. **Given** o mesmo erro repetido, **When** é reportado, **Then** sai uma vez; e há teto de dez
   relatórios por sessão.
5. **Given** `errorReporting={false}`, **When** há falha interna, **Then** nada é enviado.
6. **Given** o envio do relatório falhando, **When** a rede recusa, **Then** o SDK desiste em
   silêncio e não gera outro relatório.

---

### Edge Cases

- **Corpo da elegibilidade que não é JSON, ou JSON sem a forma esperada**: nada aparece, o
  integrador é avisado (pode ser um gateway alterando o corpo) e sai um `malformed_response`.
- **Pergunta de tipo conhecido com forma quebrada** (sem opções, faixa inválida, condição
  ilegível): sai do schema e é contada à parte para o relatório, porque é defeito do servidor e
  não versão mais nova.
- **Opção sem `value`, com `value` acima de 120 caracteres ou repetida**: descartada.
- **NPS com faixa diferente de 0 a 10**: vale sempre 0 a 10, com os rótulos que vierem.
- **Condição que aponta para pergunta posterior, para si mesma, para pergunta descartada ou para
  origem pulada**: falha fechado, a pergunta fica não aplicável.
- **`fetch` global trocado depois da montagem** (polyfill, embrulho de diagnóstico): vale, porque
  o SDK o lê a cada chamada.
- **`crypto.randomUUID` ausente** (React Native sem polyfill): o UUID sai de
  `crypto.getRandomValues` ou, sem ele, de gerador próprio, sempre v4 válido.
- **Relógio do dispositivo ajustado durante a pesquisa**: tempos medidos pelo relógio monotônico
  nunca voltam.
- **`202` com corpo ilegível numa rota da fila**: conta como entregue.
- **StrictMode** (montar, desmontar, montar de novo): o runtime é ligado e desligado sem perder
  fila nem identidade.
- **Configuração do Provider trocada em tempo de execução** (`baseUrl`, chave, apresentação,
  timeouts, storage): nasce um runtime novo; o anterior é encerrado e o que ele já gravou na fila
  persistente sai pelo novo.

## Requirements *(mandatory)*

### Functional Requirements

**Integração e configuração**

- **FR-001**: O SDK MUST ser configurado por um `PitacoProvider` com `baseUrl` e `apiKey`
  obrigatórios, onde `baseUrl` é o prefixo que serve `/collect`, do Pitaco ou de um gateway.
- **FR-002**: Configuração sem `baseUrl` http(s) válido (sem query nem fragmento) ou sem `apiKey`
  MUST desligar o SDK sem lançar; os demais valores inválidos MUST cair no padrão.
- **FR-003**: Todo aviso de configuração ou de uso MUST sair só em desenvolvimento (`__DEV__`),
  prefixado `[Pitaco]` e com a instrução de correção; em produção, silêncio.
- **FR-004**: O SDK MUST depender em runtime apenas de `react` e `react-native` como peer
  dependencies.

**Elegibilidade e oferta**

- **FR-005**: `track(evento, atributos?)` MUST consultar `POST /collect/eligibility` com o nome do
  evento (até 80 caracteres), o respondente e a união dos atributos do Provider com os da chamada.
- **FR-006**: `track` MUST NOT rejeitar nem lançar em nenhum caminho.
- **FR-007**: A consulta de elegibilidade MUST ter timeout próprio, 3000 ms por padrão,
  configurável entre 250 e 30000 ms; as demais rotas, 10000 ms.
- **FR-008**: Uma pesquisa recebida MUST ser normalizada: tipo desconhecido pulado, campo
  desconhecido ignorado, `null` igual a ausente, perguntas ordenadas por `position`.
- **FR-009**: Com pergunta renderizável, a pesquisa MUST ficar disponível para a UI sem abrir
  exibição; sem pergunta renderizável, MUST ser suprimida.
- **FR-010**: Com uma pesquisa pronta ou em exibição, outra que chegar MUST ser ignorada.

**Exibição e envio**

- **FR-011**: O `displayId` MUST ser um UUID v4 gerado no dispositivo para cada oferta.
- **FR-012**: A abertura (`POST /collect/displays`) MUST sair só quando a UI chamar `present()`,
  levando `displayId`, pesquisa, versão, respondente, atributos do instante e versão do SDK.
- **FR-013**: Dispensar antes de `present()` MUST descartar a pesquisa sem abertura nem envio.
- **FR-014**: Ao concluir, o envio MUST levar desfecho `COMPLETED` e toda pergunta renderizável
  como `ANSWERED`, `SKIPPED` ou `NOT_APPLICABLE`.
- **FR-015**: Ao dispensar, o envio MUST levar desfecho `DISMISSED` apenas com o que a pessoa
  alcançou: respondidas, vistas em branco como `SKIPPED` e puladas pela condição como
  `NOT_APPLICABLE`.
- **FR-016**: Resposta a uma pergunta que deixou de se aplicar MUST ir como `NOT_APPLICABLE`, sem
  valor; tipos desconhecidos MUST NOT entrar no envio.

**Máquina de estado**

- **FR-017**: O estado da exibição MUST mudar apenas por ações da máquina de estado do core
  (`present`, `select`, `deselect`, `setText`, `focusText`, `blurText`, `next`, `back`, `dismiss`,
  `complete`, mais `background`, `foreground` e `tick` internos), implementada como função pura.
- **FR-018**: A máquina MUST bloquear avanço e conclusão com obrigatória em branco, expondo o erro
  de validação até a pergunta ser respondida.
- **FR-019**: A máquina MUST avaliar a condição localmente, falhando fechado, e reavaliar quando a
  origem muda.
- **FR-020**: A máquina MUST aceitar só o `value` de uma opção ou um inteiro dentro da faixa, e
  limitar texto livre a 2000 caracteres.
- **FR-021**: Ações com `questionKey` diferente da pergunta atual MUST ser ignoradas.
- **FR-022**: `next` na última pergunta aplicável MUST concluir; `complete` antes dela MUST ser
  ignorado.
- **FR-023**: O estado exposto à UI MUST incluir disponibilidade, situação, `displayId`, resumo da
  pesquisa, perguntas, pergunta atual, respostas, valor atual, progresso estimado, erro de
  validação, pergunta com foco, `canGoBack`, `canGoNext` e `isLast`.

**Fila local**

- **FR-024**: Abertura, envio, eventos e supressão MUST ser gravados na fila persistente antes de
  qualquer tentativa de envio.
- **FR-025**: A fila MUST enviar em ordem, e nada que depende de uma abertura MUST sair antes
  dela.
- **FR-026**: 2xx MUST remover o item; rede, timeout e 5xx MUST manter e tentar de novo com espera
  exponencial (2 s × 2ⁿ, até 5 min); a abertura do app e a volta do segundo plano MUST forçar uma
  tentativa sem esperar o backoff.
- **FR-027**: `429` MUST pausar a fila inteira até o `Retry-After`, e o prazo MUST persistir entre
  reinícios.
- **FR-028**: 400, 401, 404, 409 e 422 MUST descartar o item; a abertura descartada MUST levar
  junto o que dependia dela; recusa que não seja de chave MUST virar relatório de erro.
- **FR-029**: A fila MUST NOT duplicar: uma abertura e um envio por `displayId`, eventos únicos por
  (`displayId`, `seq`).
- **FR-030**: A fila MUST ter teto de 200 itens e 3 dias de idade, descartando o excedente a
  partir do mais antigo.

**Identidade e storage**

- **FR-031**: O `deviceId` MUST ser gerado uma vez, persistido e enviado em toda elegibilidade e
  abertura; `respondent.reference` MUST prevalecer quando presente.
- **FR-032**: `setRespondent` e `setAttributes` MUST trocar a identificação e os atributos sem
  recriar o runtime.
- **FR-033**: `reset()` MUST dispensar a pesquisa aberta como `programmatic`, esquecer referência
  e atributos e gerar outro `deviceId`, sem apagar a fila.
- **FR-034**: O SDK MUST oferecer adaptadores para AsyncStorage e MMKV em subpaths próprios, que
  recebem a instância do app e não importam o pacote nativo; instância errada MUST cair para
  memória com aviso.
- **FR-035**: Sem adaptador, o SDK MUST usar memória e avisar em desenvolvimento que a fila não
  sobrevive a reinício.
- **FR-036**: Nenhuma falha do adaptador de storage MUST escapar; gravações MUST sair na ordem
  pedida.

**Transporte**

- **FR-037**: Toda requisição MUST levar `X-Pitaco-Key`, `X-Pitaco-Sdk-Version` e JSON.
- **FR-038**: `401`/`403` MUST desligar as consultas até o fim da sessão do runtime, com um único
  aviso.
- **FR-039**: Rede inalcançável MUST gerar um único aviso com a dica de endereço por alvo.
- **FR-040**: `Retry-After` MUST ser lido em segundos ou em data HTTP, com piso de 1 s, teto de
  1 h e 30 s quando ausente.

**Supressão e relatório de erro**

- **FR-041**: Sem pergunta renderizável, o SDK MUST enviar `POST /collect/suppressions` pela fila
  com `reason: unknown_question_type` e os tipos encontrados.
- **FR-042**: Falhas internas MUST ser reportadas por `POST /collect/sdk-errors`, fora da fila,
  sem esperar resposta, com mensagem montada pelo SDK e contexto filtrado.
- **FR-043**: O relatório MUST ser desligável, MUST NOT repetir o mesmo erro, MUST ter teto de dez
  por sessão e MUST desistir em silêncio quando falhar.

**Contrato**

- **FR-044**: Os tipos das rotas `/collect` MUST ser gerados do OpenAPI do backend por um script
  de regeneração que funciona sem backend no ar, a partir de um snapshot versionado.

### Key Entities

- **Runtime**: a instância viva do SDK para um par (`baseUrl`, `apiKey`). Orquestra
  elegibilidade, sessão, fila, identidade e relatório; nasce sem efeito colateral e é ligado e
  desligado do ciclo de vida do app.
- **Pesquisa normalizada**: a versão publicada recebida, só com perguntas renderizáveis, mais a
  contagem total, os tipos desconhecidos e as perguntas malformadas.
- **Sessão da exibição**: uma oferta com `displayId`, segurando o estado da máquina, aplicando
  ações com o relógio e entregando o resultado ao runtime.
- **Estado da máquina**: situação (`ready`, `presented`, `completed`, `dismissed`, `discarded`),
  pergunta atual, trilha, respostas, visitas, puladas, erro de validação, foco e tempos.
- **Item da fila**: abertura, envio, lote de eventos ou supressão, com tentativas e próxima
  tentativa.
- **Identidade do dispositivo**: o `deviceId` persistido, mais a referência opcional do app.
- **Relatório de erro**: tipo, mensagem, contexto técnico e instante, nunca dado de usuário.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Integrar o SDK num app React Native exige instalar um pacote, envolver a raiz com o
  Provider e chamar `track` — zero passo nativo, verificável num app Expo Go.
- **SC-002**: Em 100% dos cenários de falha cobertos (rede fora, lentidão, corpo malformado, chave
  inválida, limite de requisição, storage quebrado, configuração inválida, ouvinte que lança),
  nenhuma exceção chega ao app e nenhuma pesquisa aparece.
- **SC-003**: Uma resposta dada sem rede é recebida pelo servidor exatamente uma vez depois da
  próxima abertura do app com rede, sem ação do app.
- **SC-004**: Nenhuma exibição é aberta no servidor para pesquisa que não ficou visível.
- **SC-005**: A consulta de elegibilidade nunca segura o app por mais que o timeout configurado
  (3 s por padrão).
- **SC-006**: Nenhum relatório de erro, aviso ou payload produzido pelo core contém texto
  digitado, rótulo de opção, valor de atributo ou a chave inteira.
- **SC-007**: O pacote publicado não tem nenhum `import`/`require` de módulo nativo além de
  `react` e `react-native`.

## Assumptions

- **O contrato é o do backend.** Rotas, formas, códigos e idempotência são os de
  `backend/docs/backend/contrato-sdk.md`; quando divergem do OpenAPI, o OpenAPI vence, e os tipos
  do SDK são gerados dele.
- **Sessão de runtime ≈ sessão de app.** O Provider normalmente nasce uma vez por processo; o que
  vale "até o fim da sessão" vale até o runtime ser recriado.
- **O app passa a própria instância de storage.** O SDK nunca escolhe nem instala um pacote de
  armazenamento.
- **Atributos e referência vêm do app sem dado pessoal.** O SDK não tem como saber se um valor é
  pessoal; aplica limites de formato e avisa.
- **A interface vem depois.** Esta feature expõe o estado e as ações; quem desenha é a
  `002-sdk-ui-apresentacoes` ou o próprio app.

## Dependencies

- `backend 004-response-collection`: elegibilidade, abertura de exibição, envio de respostas,
  idempotência por `displayId` e regras de respondente.
- `backend 003-survey-authoring`: forma da versão publicada (tipos, opções, faixa, condição, aviso
  de texto livre) que o SDK normaliza.
- `backend/docs/backend/contrato-sdk.md`: supressões, relatório de erro e regras da fila local.
- Consumidores: `002-sdk-ui-apresentacoes`, `003-sdk-eventos-interacao`,
  `004-sdk-controle-onde-quando`, `005-sdk-preview-web`, `006-sdk-app-exemplo`.
