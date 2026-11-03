# Feature Specification: Saúde do SDK e proteção da superfície pública

**Feature Branch**: `010-sdk-health`

**Created**: 2026-09-12

**Status**: Implemented

**Input**: User description: "Preciso enxergar o que acontece do lado do app: quais versões do SDK estão rodando, os erros do próprio SDK (sem dado pessoal), quando uma pesquisa não aparece porque o SDK não sabe desenhar, e se o evento de disparo chegou alguma vez. E proteger a superfície pública com limite de requisições, inclusive atrás de proxy."

## Contexto

O SDK vive dentro de apps que atualizam no ritmo de outras pessoas. Uma pesquisa publicada hoje
pode chegar a um aparelho com um SDK de seis meses atrás. Até aqui o Pitaco não sabia nada disso:
uma pesquisa com zero respostas podia estar sendo suprimida por um SDK antigo, ou o evento de
disparo podia nunca ter chegado — duas causas com a mesma cara e correções opostas.

Ao mesmo tempo, a superfície pública (`/collect/**`) é alcançável por qualquer um que extraia a
chave do bundle do app. A identidade do respondente é forjável. A defesa que resta é o limite de
requisições, e ele só funciona se a origem contada não for um valor que o cliente escolhe.

Esta fatia entrega:

- **Versões do SDK em uso**: o cabeçalho de versão vira distribuição de tráfego recente e total por
  versão, com a marca de "sumiu do tráfego".
- **Canal de erros do SDK**: falhas internas do próprio SDK, com mascaramento de dado pessoal no
  servidor como rede de segurança.
- **Supressões**: o SDK avisa quando recebeu uma pesquisa e não soube desenhá-la; o aviso não é
  exibição e não estraga a taxa de resposta.
- **Saúde por pesquisa**: proporção de supressão, versão mínima que renderizaria e se o evento de
  disparo já foi visto.
- **Catálogo de capacidades**: a versão mínima do SDK para cada tipo de pergunta e cada recurso.
- **Proteção da superfície**: limite por chave, por origem e próprio para registros de erro, com
  `429` e `Retry-After`, e resolução de origem atrás de proxy confiável; e a separação entre a
  superfície do SDK e a do painel.

## Clarifications

### Session 2026-09-12

- Q: Contar cada consulta escreve no banco? → A: Não. A contagem acumula em memória por aplicação,
  versão e dia e desce em lote a cada minuto. Reiniciar perde o que não desceu: o número é
  aproximado por natureza.
- Q: Versão fora do formato recusa a consulta? → A: Não. É ignorada; a consulta segue.
- Q: Quando uma versão "sumiu do tráfego"? → A: Sem consulta há mais de 14 dias.
- Q: A proporção de tráfego é sobre o acumulado? → A: Não, sobre a janela recente (14 dias). Uma
  versão que foi maioria no ano passado não pode parecer relevante hoje.
- Q: O registro de erro pode trazer dado do usuário? → A: O contrato proíbe. O servidor ainda
  mascara e-mail e número longo na mensagem e descarta chaves e valores com cara de dado pessoal no
  contexto — rede de segurança, não licença.
- Q: Supressão cria respondente? → A: Não. Associa o respondente só se ele já existe; a
  identificação serve apenas para contar uma vez a mesma supressão na mesma versão em 24 horas, e é
  guardada como resumo irreversível.
- Q: Supressão de pesquisa de outra aplicação é 404? → A: Não. É descartada com o mesmo `202`, para
  não confirmar a existência do identificador a quem tem uma chave extraída.
- Q: Quando a supressão é relevante? → A: A partir de 5% sobre o alcance (exibidas + suprimidas) e
  de 5 supressões no período.
- Q: Qual origem conta no limite por origem atrás do túnel? → A: Só vale o que um proxy confiável
  escreveu: quem conecta direto é a própria origem; atrás de proxy confiável, o `CF-Connecting-IP`;
  sem ele, o `X-Forwarded-For` lido do fim para o começo, pulando os confiáveis.
- Q: Registros de erro gastam a cota da chave? → A: Não. Têm balde próprio (30 por minuto por
  chave), para uma tempestade de falhas não derrubar a elegibilidade.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Ver quais versões do SDK estão em uso (Priority: P1)

Quem opera o painel vê a distribuição de versões do SDK da aplicação: total acumulado, tráfego
recente com proporção, primeira e última vez vista, e quais sumiram do tráfego.

**Why this priority**: é a base de toda decisão de compatibilidade — e alimenta o aviso de
publicação.

**Independent Test**: consultar a elegibilidade com `X-Pitaco-Sdk-Version: 1.2.0`, esperar a
descarga e listar as versões.

**Acceptance Scenarios**:

1. **Given** consultas com versões diferentes, **When** listo, **Then** recebo cada versão com total,
   tráfego recente e proporção recente, da mais nova para a mais antiga.
2. **Given** uma versão sem consulta há mais de 14 dias, **When** listo, **Then** ela aparece como
   sumida do tráfego.
3. **Given** nenhuma consulta recente, **When** listo, **Then** a proporção fica ausente.
4. **Given** versão malformada no cabeçalho, **When** o SDK consulta, **Then** a consulta responde
   normalmente e a versão é ignorada.
5. **Given** versões vistas por outra aplicação, **When** listo, **Then** não aparecem.
6. **Given** aplicação inexistente, **When** listo, **Then** recebo aplicação não encontrada.

---

### User Story 2 - Receber os erros do SDK sem dado pessoal (Priority: P1)

O SDK reporta falhas internas (renderização, rede, resposta malformada, armazenamento) por um canal
próprio do Pitaco. O painel lista os registros, filtrando por tipo e versão.

**Why this priority**: sem o canal, a falha do SDK é silêncio no app — por desenho — e ninguém fica
sabendo.

**Independent Test**: enviar um registro com e-mail na mensagem e chave `userEmail` no contexto, e
conferir no banco a mensagem mascarada e a chave descartada.

**Acceptance Scenarios**:

1. **Given** um registro, **When** o SDK envia, **Then** recebe `202` e o registro é gravado com a
   mensagem mascarada e o contexto sanitizado.
2. **Given** tipo fora da lista ou ausente, **When** gravo, **Then** o tipo vira `unknown`.
3. **Given** mensagem com e-mail ou número longo, **Then** o trecho vira `[email]` ou `[número]`;
   mensagem acima de 500 caracteres é cortada; quebras de linha viram espaço.
4. **Given** chave de contexto com palavra de dado pessoal (email, user, device, answer, text,
   value, token…), valor com cara de e-mail ou número longo, lista, segundo nível de objeto ou
   excesso de chaves e tamanho, **Then** são descartados, e o resto fica.
5. **Given** instante do dispositivo no futuro além de 5 minutos, **Then** vale o do recebimento;
   instante do passado é preservado.
6. **Given** aplicação inativa, **When** o SDK envia, **Then** recebe o mesmo `202` e nada é gravado.
7. **Given** registros gravados, **When** listo, **Then** recebo do mais recente para o mais antigo,
   filtrados por tipo e por versão, só da aplicação.
8. **Given** a cota de registros esgotada, **When** o SDK consulta a elegibilidade com a mesma
   chave, **Then** a consulta é atendida.

---

### User Story 3 - Saber que a pesquisa não apareceu porque o SDK não sabe desenhar (Priority: P1)

Uma pesquisa com um tipo de pergunta novo chega a um SDK antigo. Ele não exibe nada, não abre
exibição e avisa o Pitaco. O painel mostra, por pesquisa, a proporção suprimida, por motivo e por
versão, e a versão mínima que renderizaria.

**Why this priority**: separa "ninguém responde" de "ninguém vê".

**Independent Test**: enviar uma supressão do mesmo dispositivo duas vezes e de outro dispositivo
uma vez, e conferir duas linhas gravadas, nenhuma exibição e nenhum respondente novo.

**Acceptance Scenarios**:

1. **Given** uma pesquisa publicada da aplicação, **When** o SDK envia supressão, **Then** recebe
   `202` e a supressão é gravada com motivo, versão do SDK e versão mínima calculada.
2. **Given** a mesma supressão do mesmo dispositivo na mesma versão em 24 horas, **Then** conta uma
   vez; outro dispositivo conta; envios simultâneos do mesmo dispositivo contam uma vez.
3. **Given** um respondente que nunca abriu exibição, **Then** nenhum respondente é criado; o que já
   existe é associado.
4. **Given** pesquisa de outra aplicação, versão que não pertence à pesquisa, identificador
   malformado ou aplicação inativa, **Then** recebe `202` e nada é gravado.
5. **Given** motivo fora da lista ou ausente, **Then** recebe `400` e nada é gravado.
6. **Given** supressões e exibições no período, **When** consulto a saúde da pesquisa, **Then**
   recebo exibidas, suprimidas, contagem por motivo (os dois motivos sempre presentes) e por versão,
   proporção e se é relevante.
7. **Given** poucas supressões com proporção alta, **Then** não é relevante.

---

### User Story 4 - Saber se o evento de disparo chegou (Priority: P2)

Na saúde da pesquisa, o painel mostra o evento de disparo e quando ele foi visto pela última vez —
ou que nunca foi.

**Why this priority**: é a segunda causa de "zero respostas" e a mais barata de corrigir.

**Independent Test**: publicar com um evento nunca enviado, consultar a saúde (sem última
ocorrência), enviar o evento e consultar de novo.

**Acceptance Scenarios**:

1. **Given** um evento nunca recebido, **Then** a saúde traz o nome e a última ocorrência ausente.
2. **Given** o evento já recebido, **Then** traz a última ocorrência.
3. **Given** uma pesquisa nunca publicada, **Then** não há versão mínima nem evento.
4. **Given** período invertido, **Then** recebo `400`; pesquisa de outra aplicação, `404`.

---

### User Story 5 - Proteger a superfície pública (Priority: P1)

Uma chave extraída do bundle não pode derrubar o Pitaco nem o app inteiro. O limite por chave
contém a chave vazada; o limite por origem contém a inundação anônima antes mesmo de consultar a
chave; o limite de registros de erro isola a tempestade de falhas.

**Why this priority**: sem limite, a superfície pública é um convite.

**Independent Test**: com capacidade baixa configurada, estourar o limite por chave e receber `429`
com `Retry-After`.

**Acceptance Scenarios**:

1. **Given** o limite por chave atingido, **When** a mesma chave chama de novo, **Then** recebe
   `429 rate_limit.exceeded` com `Retry-After` em segundos, nunca zero.
2. **Given** uma chave esgotada, **When** outra chave da mesma aplicação chama, **Then** é atendida.
3. **Given** o limite por origem atingido, **When** a mesma origem chama com chave válida ou
   desconhecida, **Then** recebe `429` antes da autenticação.
4. **Given** conexão direta com cabeçalhos de origem forjados, **Then** conta o endereço real.
5. **Given** um proxy confiável no caminho, **Then** vale o `CF-Connecting-IP`; sem ele, o
   `X-Forwarded-For` lido do fim, e forjar o começo da lista não abre uma origem nova.
6. **Given** um gateway do app não declarado como confiável, **Then** todo o tráfego dele conta como
   uma origem só; declarado, conta o cliente que ele informou.
7. **Given** endereço com porta, colchetes ou nome de host, **Then** porta e colchetes saem, e nome
   de host não vira consulta de DNS.
8. **Given** faixa de proxy confiável malformada na configuração, **Then** a aplicação falha na
   subida.
9. **Given** a superfície administrativa, **Then** o limite não se aplica.

---

### User Story 6 - Separar a superfície do SDK da do painel (Priority: P2)

A chave do SDK só vale em `/collect/**`. Apresentá-la no painel falha alto, em vez de ser ignorada.

**Why this priority**: garante que vazar uma chave nunca dá acesso à leitura de respostas.

**Independent Test**: chamar uma rota administrativa com `X-Pitaco-Key` e receber `403`.

**Acceptance Scenarios**:

1. **Given** uma rota pública sem chave, **Then** `401 api_key.missing`.
2. **Given** chave desconhecida ou revogada, **Then** `401 api_key.invalid`, com o mesmo corpo.
3. **Given** chave válida, **Then** a chamada atravessa e o último uso é registrado.
4. **Given** uma rota administrativa com `X-Pitaco-Key`, **Then** `403 api_key.forbidden_surface`.

---

### Edge Cases

- **Inundação de versões inventadas**: o acumulador em memória tem teto de chaves pendentes; acima
  dele, versão nova não é contada.
- **Descarga que falha**: o delta volta ao acumulador e tenta de novo, até três tentativas.
- **Contexto de erro com metade das chaves inválidas**: a metade boa é gravada; nada é recusado.
- **Supressão sem nenhuma identificação**: gravada sem deduplicação (fora do contrato).
- **Registro de erro velho**: expurgado depois de 90 dias; o total diário de versões, também; o
  acumulado por versão fica.
- **Assunto que sumiu do limitador**: expurgado depois de uma janela inteira, para não ficar
  residente.
- **IPv4 nunca cai em faixa IPv6**, nem o contrário.

## Requirements *(mandatory)*

### Functional Requirements

**Versões em uso**

- **FR-001**: A consulta de elegibilidade MUST contar a versão informada no cabeçalho de versão do
  SDK por aplicação, versão e dia; versão fora do formato semver ou acima de 40 caracteres MUST ser
  ignorada sem recusar a consulta.
- **FR-002**: O sistema MUST permitir listar as versões de uma aplicação com total acumulado,
  tráfego da janela recente, proporção recente (ausente sem tráfego recente), primeira e última
  ocorrência e a marca de sumida do tráfego, da versão mais nova para a mais antiga.
- **FR-003**: Janela recente e prazo de "sumida" MUST ser configuráveis, com padrão de 14 dias.
- **FR-004**: A contagem MUST NOT escrever no banco por consulta; MUST descer em lote periódico, com
  nova tentativa limitada em falha.

**Erros do SDK**

- **FR-005**: O sistema MUST receber registros de erro do SDK com tipo, mensagem, contexto e
  instante, respondendo `202` sem corpo.
- **FR-006**: Tipo fora de `render_error`, `network_error`, `malformed_response`, `storage_error`
  MUST virar `unknown`.
- **FR-007**: A mensagem MUST ter controles trocados por espaço, e-mail e sequência longa de dígitos
  mascarados, e ser guardada com até 500 caracteres.
- **FR-008**: O contexto MUST manter apenas texto, número, booleano e um nível de objeto, sob chaves
  no formato permitido sem palavra de dado pessoal, até 20 chaves (10 aninhadas), textos de até 200
  caracteres e cerca de 2 KB no total; o que não cabe MUST ser descartado, não recusado.
- **FR-009**: Instante ausente ou no futuro além da tolerância MUST virar o instante do recebimento.
- **FR-010**: Registro de aplicação inativa MUST ser descartado com a mesma resposta.
- **FR-011**: O sistema MUST permitir listar os registros da aplicação, paginados, do mais recente
  para o mais antigo, filtrando por tipo e por versão exata.
- **FR-012**: Registros MUST ser expurgados depois do prazo de retenção (padrão 90 dias), por job
  diário.

**Supressões e saúde**

- **FR-013**: O sistema MUST receber supressões com pesquisa, versão, motivo
  (`unknown_question_type` ou `unsupported_feature`), listas opcionais de diagnóstico e
  identificação opcional, respondendo `202` sem corpo.
- **FR-014**: Supressão MUST NOT criar exibição nem respondente; MUST associar o respondente quando
  ele já existe.
- **FR-015**: A mesma supressão do mesmo respondente na mesma versão MUST contar uma vez na janela de
  deduplicação (padrão 24 horas), inclusive sob envios simultâneos; a identificação MUST ser
  guardada apenas como resumo irreversível.
- **FR-016**: Pesquisa de outra aplicação, versão não publicada ou que não pertence à pesquisa,
  identificador malformado e aplicação inativa MUST ser descartados com a mesma resposta.
- **FR-017**: Cada supressão MUST registrar a versão mínima do SDK que renderizaria a versão
  entregue.
- **FR-018**: O sistema MUST permitir consultar a saúde de uma pesquisa num período (padrão 30 dias):
  exibidas, supressões totais, por motivo e por versão do SDK, proporção sobre o alcance e
  relevância (proporção e mínimo configuráveis, padrão 5% e 5).
- **FR-019**: A saúde MUST trazer a versão mínima da publicação atual, o evento de disparo e a
  última ocorrência dele (ausente quando nunca visto).

**Capacidades**

- **FR-020**: O sistema MUST declarar a versão mínima do SDK para cada tipo de pergunta e cada
  recurso, e a versão exigida por um conjunto MUST ser a maior das partes; tipo ou recurso novo sem
  versão declarada MUST NOT compilar.
- **FR-021**: A versão mínima de uma versão entregue MUST considerar os tipos de pergunta e os
  recursos que ela de fato usa.

**Proteção da superfície**

- **FR-022**: Toda rota pública MUST ser limitada por origem (padrão 120/min) antes da autenticação.
- **FR-023**: Toda rota pública, exceto a de registros de erro, MUST ser limitada por chave (padrão
  1.200/min); a de registros MUST ter balde próprio por chave (padrão 30/min).
- **FR-024**: Estouro MUST responder `429` com `rate_limit.exceeded` e `Retry-After` em segundos,
  nunca zero.
- **FR-025**: A origem MUST ser o par imediato quando ele não é proxy confiável; atrás de proxy
  confiável, o cabeçalho de IP do cliente configurado; sem ele, o primeiro endereço não confiável
  lendo o `X-Forwarded-For` do fim para o começo; salto ilegível encerra a leitura no par imediato.
- **FR-026**: Endereços MUST ser lidos apenas como literais, sem resolução de nome; faixas
  confiáveis em CIDR inválidas MUST impedir a subida.
- **FR-027**: O limite MUST NOT alcançar a superfície administrativa.
- **FR-028**: Credencial de aplicação apresentada na superfície administrativa MUST ser recusada com
  `403 api_key.forbidden_surface`; ausência de credencial na pública, `401 api_key.missing`;
  credencial desconhecida ou revogada, `401 api_key.invalid` com corpo idêntico.

### Key Entities

- **Uso de versão do SDK**: por aplicação e versão, total, primeira e última ocorrência; por dia, o
  total do dia.
- **Registro de erro do SDK**: aplicação, versão, tipo, mensagem mascarada, contexto sanitizado,
  instante no dispositivo e de recebimento.
- **Supressão**: aplicação, pesquisa, versão, respondente opcional, resumo de deduplicação, versão do
  SDK, motivo, versão mínima, instante.
- **Capacidade do SDK**: tipo de pergunta ou recurso e a versão mínima que o suporta.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A consulta de elegibilidade não ganha nenhuma escrita síncrona por causa da contagem
  de versões.
- **SC-002**: Nenhum registro de erro gravado contém e-mail ou sequência longa de dígitos na
  mensagem ou no contexto.
- **SC-003**: 100% das supressões repetidas do mesmo dispositivo na mesma versão dentro de 24 horas
  contam uma vez.
- **SC-004**: Uma pesquisa com zero respostas pode ser classificada pelo painel em "suprimida",
  "evento nunca visto" ou nenhum dos dois, com uma consulta.
- **SC-005**: Forjar cabeçalhos de origem não permite a um cliente escapar do limite por origem.
- **SC-006**: Esgotar os registros de erro de uma chave não reduz a cota de elegibilidade dela.

## Assumptions

- Uma instância da API: o limite em memória vale por instância.
- Hoje tudo no catálogo de capacidades exige a 1.0.0, primeira versão publicada do SDK.
- O gateway do app hospedeiro é transparente e segue o contrato do proxy (`docs/backend/proxy.md`).
- Proporções e contagens de versão são aproximadas por natureza; a decisão que elas sustentam não
  depende de exatidão.

## Dependencies

- `backend 001-api-key-management` e `backend 004-response-collection`: chave, superfície pública,
  autenticação e catálogo de entrega.
- `backend 007-application-management`: estado da aplicação e catálogo de eventos (última
  ocorrência).
- `backend 009-advanced-authoring`: consome o tráfego recente e as capacidades no aviso de
  compatibilidade; os recursos condição, rótulos e aviso de texto livre nascem lá.
- `backend 013-interaction-events`: acrescenta a capacidade de eventos de interação.
