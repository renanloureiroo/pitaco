# Research: Operação — implantação auto-hospedada, observabilidade e backup

**Feature**: `001-deploy-observabilidade-backup` · **Date**: 2026-09-08 · **Spec**: [spec.md](./spec.md)

Cada decisão abaixo resolveu uma incógnita de operação antes de escrever os arquivos. Algumas
nasceram de falha observada em servidor real; quando é o caso, o sintoma está no Rationale.

---

## D-01 — Docker Compose num servidor só, imagens construídas no próprio servidor (FR-001, FR-010, FR-011)

**Decisão**: a produção é um `compose.yaml` na raiz do repositório. Publicar é `git pull` no
clone do servidor seguido de `docker compose up -d --build`. Não há registro de imagens nem
workflow de entrega.

**Rationale**: o alvo é quem auto-hospeda numa máquina só. Um registro exigiria credencial,
versionamento de tag e um passo de publicação a mais, para entregar a mesma imagem que o próprio
servidor constrói em poucos minutos — e o BuildKit reaproveita camadas e o cache do Maven e do
npm entre builds. Voltar de versão é `git checkout` de um commit anterior e reconstruir.

**Alternativas consideradas**: imagens publicadas num registro pelo CI — rejeitada por adicionar
credencial e etapa sem ganho para um servidor só; Kubernetes ou similar — rejeitada por
complexidade sem segundo nó que a justifique.

---

## D-02 — Nenhuma porta publicada; a entrada é um túnel reverso (FR-002)

**Decisão**: nenhum serviço declara `ports`. O túnel reverso (ex.: Cloudflare Tunnel) vive fora
da composição, numa rede Docker externa `edge`, e fala com a API e o painel por dentro dela.

**Rationale**: sem porta aberta não há superfície para varredura, nem firewall a manter, nem
certificado TLS a renovar no servidor — o túnel termina o TLS. A rede `edge` é criada uma vez por
máquina (`docker network create edge`) e declarada `external: true`, porque ela é compartilhada
com outros projetos e com a observabilidade, e não pode ser apagada por um `docker compose down`
do Pitaco.

**Alternativas consideradas**: proxy reverso publicando 80/443 no host — rejeitada por exigir
porta aberta e certificado; publicar só em `127.0.0.1` — rejeitada porque o túnel roda em
container e alcançaria o host por um caminho mais frágil que a rede Docker.

---

## D-03 — Duas redes, banco só na interna, aliases prefixados na compartilhada (FR-003, FR-004)

**Decisão**: rede `internal` (da stack) e `edge` (externa). `postgres` só na `internal`. `api` e
`painel` nas duas; na `edge`, com os aliases `pitaco-api` e `pitaco-painel`. O painel chama a API
por `http://api:8080/api`, pela `internal`.

**Rationale**: a `edge` é compartilhada; um banco nela ficaria alcançável por qualquer container
de outro projeto. Os aliases prefixados evitam que um `api` genérico colida com o serviço de mesmo
nome de outro projeto na mesma rede. O painel não precisa sair pela `edge` para achar a API: a
URL é lida só no servidor do Next.js, nunca no navegador.

**Alternativas consideradas**: uma rede só — rejeitada por expor o banco; o painel chamando a API
pelo domínio público — rejeitada por fazer uma volta pela internet e pelo túnel para uma chamada
entre dois containers vizinhos.

---

## D-04 — Sobreposição local em vez de uma segunda composição de produção (FR-014)

**Decisão**: `compose.local.yaml` é somado ao `compose.yaml` (`-f compose.yaml -f
compose.local.yaml`) e só acrescenta: portas `8080` e `3001`, `PITACO_API_DOCS_ENABLED=true` na
API, um serviço `lgtm` (Grafana em `3000`) e a rede `edge` deixando de ser externa.

**Rationale**: testar a stack de produção numa máquina de desenvolvimento não pode exigir túnel nem
uma observabilidade compartilhada. Uma sobreposição mantém um único lugar onde os serviços são
definidos — o arquivo de produção — e deixa explícito, em poucas linhas, tudo o que difere. O
serviço `lgtm` entra na `edge` com o mesmo nome que a API já procura (`http://lgtm:4318`), então a
API não muda de configuração. A imagem do LGTM fica com versão fixa na sobreposição.

**Alternativas consideradas**: um segundo arquivo completo para uso local — rejeitada por
duplicar a definição dos serviços e deixar as duas divergirem; reaproveitar `backend/compose.yaml`
— rejeitada porque aquele arquivo existe para o ciclo `./mvnw spring-boot:run` e não constrói as
imagens.

---

## D-05 — Cabeçalhos encaminhados pelo túnel, com estratégia `framework` (FR-013)

**Decisão**: `server.forward-headers-strategy: framework` no `application.yml`.

**Rationale**: o túnel termina o TLS e fala HTTP com o container. Sem ler `X-Forwarded-Proto` e
`X-Forwarded-Host`, a aplicação se enxerga em `http` e monta errado tudo que deriva da requisição:
o `servers` do OpenAPI e o `Location` do `201`. `framework` (o `ForwardedHeaderFilter`) foi
preferido a `native` (o `RemoteIpValve` do Tomcat) porque nenhuma porta é publicada: o único par
que alcança a API é o túnel, não há de onde chegar um cabeçalho forjado, e não se depende de o IP
do túnel cair na faixa que o `RemoteIpValve` considera interna. Coberto por `OpenApiConfigTest`,
que pede o contrato com `X-Forwarded-Proto: https` e confere a URL anunciada.

**Alternativas consideradas**: `native` — rejeitada pela dependência da faixa de IP do proxy;
nenhuma estratégia — rejeitada pelas URLs erradas.

---

## D-06 — Um endereço OTLP/HTTP para os três sinais (FR-018, FR-019)

**Decisão**: a composição define só `OTEL_EXPORTER_OTLP_ENDPOINT=http://lgtm:4318`. A amostragem
fica em `management.tracing.sampling.probability: 1.0`.

**Rationale**: o Spring Boot 4 mapeia essa variável do SDK do OpenTelemetry para as três
propriedades de exportação — traces, logs e o `OtlpMeterRegistry` do Micrometer — acrescentando
`/v1/traces`, `/v1/logs` e `/v1/metrics`. `4318` é OTLP sobre HTTP (`4317` seria gRPC). A armadilha
documentada no próprio `compose.yaml`: `MANAGEMENT_OPENTELEMETRY_OTLP_ENDPOINT` **não existe** no
Boot 4 e é ignorada em silêncio; sem endpoint, os exportadores de trace e de log nem são criados
(são condicionais à propriedade) e só a métrica continua saindo — o sintoma é Grafana com métrica
e sem trace nem log. Amostragem de 100% porque, com dezenas de requisições por dia, 10% significa
quase nenhum trace; o comentário no `application.yml` diz quando reduzir.

**Alternativas consideradas**: um endereço por sinal — rejeitada por triplicar configuração sem
necessidade, já que o LGTM recebe os três no mesmo ponto; gRPC — rejeitada sem ganho para o
volume e com mais uma porta a lembrar.

---

## D-07 — Ponte Logback → OpenTelemetry instalada no construtor, com MDC (FR-020, FR-021)

**Decisão**: `logback-spring.xml` mantém o console do Spring Boot e acrescenta o
`OpenTelemetryAppender` com `captureMdcAttributes=*`; `OpenTelemetryLogbackConfig` chama
`OpenTelemetryAppender.install(openTelemetry)` no construtor. O access log do Tomcat é ligado com
destino `/dev/stdout`, sem rotação, sem buffer, no padrão `%t "%r" %s %b %{ms}Tms`.

**Rationale**: o starter do OpenTelemetry fornece o provedor de log, mas não conecta o log da
aplicação a ele — sem o appender de terceiro, nenhum log chega ao Loki. O MDC carrega `traceId` e
`spanId`, que é o que permite pular do trace para os logs da requisição. O Logback sobe antes do
contexto do Spring, então o appender nasce sem destino; instalar no construtor, e não num listener
tardio, perde o mínimo de linhas do boot. O access log é a única camada que enxerga requisição que
morre antes do controller (404, 405, corpo malformado); no stdout, ele aparece junto do console
da aplicação no log do container e não deixa arquivo para rotacionar. Por ser escrito pelo Tomcat
e não pelo Logback, ele não vai ao Loki — quem chega lá é o log da aplicação. `%{ms}T` deixa a unidade explícita.

**Alternativas consideradas**: instrumentação automática por `-javaagent` do OpenTelemetry — rejeitada por instrumentar por fora
o que o starter já instrumenta por dentro e por exigir mudar o `ENTRYPOINT`; access log em arquivo
— rejeitada pelo disco e pela rotação.

---

## D-08 — Documentação desligada por padrão, uma variável liga tudo (FR-015, FR-016)

**Decisão**: `springdoc.api-docs.enabled` e `springdoc.swagger-ui.enabled` valem
`${PITACO_API_DOCS_ENABLED:false}`. O redirecionamento da raiz para a UI
(`SwaggerUiRedirectConfiguration`) é condicional a `springdoc.swagger-ui.enabled=true`. O perfil
local e a anotação `@E2E` ligam a documentação.

**Rationale**: o caminho `/api` precisa passar pelo túnel sem a autenticação do painel, porque o
SDK depende da superfície de coleta. Com a documentação ligada nesse caminho, o mapa inteiro da
API — inclusive a superfície administrativa — ficaria público. Uma variável só, aplicada aos dois,
evita o estado meio ligado (UI sem especificação, ou o contrário). O redirecionamento reusa a
mesma propriedade para não ter segunda fonte de verdade; é uma valve na engine do Tomcat, porque a
raiz do host fica fora do `context-path` e nunca chega ao `DispatcherServlet`, e devolve `Location`
relativo para que o navegador o resolva contra o host e o esquema externos. Com ele, o
redirecionamento de `/api` para `/api/` do Tomcat é desligado, para não haver salto intermediário.
A condição é provada num contexto mínimo (`SwaggerUiRedirectConfigurationTest`), sem abrir um
segundo contexto `@E2E` com outra pilha de containers.

**Alternativas consideradas**: deixar a documentação ligada e bloquear no túnel — rejeitada por
depender de uma regra externa ao repositório; duas variáveis — rejeitada pelo estado meio ligado.

---

## D-09 — Actuator com saúde e informações, e nada mais (FR-017)

**Decisão**: `management.endpoints.web.exposure.include: health,info`; saúde em
`/api/actuator/health`.

**Rationale**: é o necessário para saber se o processo está de pé, e é o que o guia de publicação
usa para conferir uma versão nova. Cada endpoint a mais é superfície exposta atrás de um túnel
público.

**Alternativas consideradas**: expor `metrics` ou `prometheus` — rejeitada porque as métricas já
saem por OTLP (D-06), sem endpoint público.

---

## D-10 — Imagens em base glibc, não Alpine (FR-010, FR-011)

**Decisão**: a API roda em `eclipse-temurin:21-jre` (Ubuntu); o painel constrói e roda em
`node:24-bookworm-slim` (Debian).

**Rationale**: em servidor real, a JVM do `temurin` Alpine terminou em falha de segmentação na
subida em kernels recentes, e o `npm` do `node` Alpine morreu com `exit 139` no install do painel.
A troca para glibc resolveu os dois. No painel, build e runtime precisam da mesma libc, porque a
saída autônoma do Next.js carrega binários nativos compilados no estágio de build.

**Alternativas consideradas**: manter Alpine e fixar versões — rejeitada por não haver versão
comprovadamente estável e pelo ganho de tamanho ser pequeno diante de um crash na subida.

---

## D-11 — Imagem da API em dois estágios, sem privilégio, JVM dimensionada para o container (FR-010)

**Decisão**: estágio `maven:3.9-eclipse-temurin-21` com `dependency:go-offline` antes de copiar o
código e cache montado em `/root/.m2`; empacotamento com `-DskipTests`. Estágio final só JRE,
usuário de sistema `app`, `JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70 -XX:+UseSerialGC -Xss512k
-Duser.timezone=UTC"`. No `application.yml`, `hikari.maximum-pool-size: 10`.

**Rationale**: copiar o `pom.xml` e resolver dependências antes do código faz a camada de
dependências sobreviver a mudanças de código. Os testes já rodaram no portão do backend; no
servidor eles exigiriam Docker para o Testcontainers dentro do build. `SerialGC` e pilha menor
reduzem memória num servidor pequeno com threads virtuais ligadas; 70% deixa folga para memória
fora da heap. Pool pequeno é mais saudável que pool grande e ocioso num servidor modesto.

**Alternativas consideradas**: imagem com JDK completo — rejeitada pelo tamanho; rodar a suíte no
build da imagem — rejeitada pelo motivo acima.

---

## D-12 — Painel compila o SDK do repositório e instala como cópia (FR-011, FR-012)

**Decisão**: o contexto de build do painel é a raiz do repositório. O Dockerfile copia `sdk/`, roda
`npm ci && npm run build` nele, depois `npm ci --install-links` em `painel/`, `npm run build` com
`output: "standalone"` e copia `.next/standalone`, `.next/static` e `public` para o estágio final,
com usuário `app`, porta `3001` e `HOSTNAME=0.0.0.0`. O `.dockerignore` da raiz tira testes,
documentação, specs, `ops/`, `.env*` e o app de exemplo do SDK.

**Rationale**: o painel depende do SDK por `file:../sdk` (a pré-visualização usa o mesmo
renderizador do app). O pacote precisa estar compilado, e `--install-links` copia em vez de criar
link simbólico, porque o Turbopack recusa módulo fora da raiz do projeto. `npm ci` instala o
lockfile exato. O build roda direto em `/app/painel`, sem workspace na raiz, para a saída autônoma
sair plana. Os testes saem do contexto inteiros — inclusive `vitest.setup.ts` —, porque o `next
build` faz type-check do que sobrar, e um pedaço da suíte sem o outro não compila.

**Alternativas consideradas**: publicar o SDK num registro e instalar por versão — rejeitada por
exigir publicação a cada mudança do SDK para o painel enxergá-la; link simbólico — rejeitado pelo
Turbopack.

---

## D-13 — Banco sem core dump (FR-008)

**Decisão**: `ulimits.core: 0` no serviço `postgres`.

**Rationale**: cada queda do Postgres gravava um arquivo de core de centenas de megabytes na pasta
de dados. Com `restart: unless-stopped`, quedas em laço — por hardware instável, por exemplo —
acumularam esses arquivos até esgotar o disco e derrubar o servidor inteiro. O core dump não tem
uso para quem opera a stack; desligá-lo transforma uma falha que derruba tudo numa falha que só
reinicia o banco. Arquivos `core.*` já gravados num volume antigo podem ser apagados: não fazem
parte do banco.

**Alternativas consideradas**: limitar a quantidade de reinícios — rejeitada porque deixaria a
stack fora do ar numa queda transitória; mover os cores para outro diretório — rejeitada por
continuar enchendo um disco.

---

## D-14 — Backup com `pg_dump` em formato custom e `rclone` configurado só por ambiente (FR-023 a FR-026)

**Decisão**: imagem própria sobre `postgres:16-alpine` com `rclone`. `pg_dump --format=custom
--no-owner` para `pitaco-<yyyyMMddTHHmmssZ>.dump`; `pg_restore --list` confere o arquivo antes do
envio; `rclone copyto` envia. O remoto `pitacobackup` é definido inteiro por
`RCLONE_CONFIG_PITACOBACKUP_*`; o arquivo de configuração do rclone existe vazio só para calar o
aviso de configuração ausente.

**Rationale**: a mesma imagem base do banco garante `pg_dump`/`pg_restore` da mesma versão
principal. Formato custom é comprimido e restaura seletivamente. `--no-owner` deixa o dump
restaurável em outro banco com outro usuário — inclusive no ensaio. Conferir com `pg_restore
--list` antes do envio evita gastar upload e ocupar uma cópia da retenção com um arquivo que não
restaura. Configuração só por ambiente evita arquivo com segredo montado no container. O nome com
instante UTC ordena cronologicamente por ordem alfabética — é o que a retenção e o restore usam.

**Alternativas consideradas**: `pg_dump` em SQL puro — rejeitada pelo tamanho e por não permitir
`--list`; ferramenta de backup contínuo (WAL) — rejeitada pela complexidade para o volume e o
objetivo de recuperação de um dia; `aws` CLI — rejeitada por prender a um provedor.

---

## D-15 — Retenção por contagem e laço com uma execução por processo (FR-027, FR-028, FR-029)

**Decisão**: depois de cada envio, `rclone lsf` lista os `pitaco-*.dump` do destino, ordena e
apaga os mais antigos além de `BACKUP_KEEP` (14). `backup.sh --loop` executa `"$0"` num
subprocesso, registra falha e dorme `BACKUP_INTERVAL` (86400 s). Sem `BACKUP_REMOTE`, sai com
código 1 e mensagem.

**Rationale**: contagem é previsível em espaço e não depende do relógio do destino. O filtro pelo
padrão de nome garante que nada além dos dumps do Pitaco seja apagado no mesmo prefixo. Rodar cada
execução num processo próprio mantém `set -e` valendo por inteiro: uma falha no dump nunca segue
para o envio, e uma execução que falha não mata o laço. Falhar alto sem destino é proposital:
backup desligado em silêncio é pior que barulho no log.

**Alternativas consideradas**: retenção por idade — rejeitada porque, com o backup parado, apagaria
as últimas cópias boas; `cron` dentro da imagem — rejeitada por um processo a mais para um
intervalo fixo que um `sleep` resolve.

---

## D-16 — Backup como serviço opcional, atrás de perfil (FR-030)

**Decisão**: o serviço `backup` está no `compose.yaml` com `profiles: [backup]`, só na rede
`internal`, dependente do banco saudável. Sobe com `docker compose --profile backup up -d`. Os
comandos manuais usam o mesmo serviço (`docker compose --profile backup run --rm backup …`).

**Rationale**: a stack mínima precisa subir sem object storage configurado. Com o serviço sempre
declarado, um destino ausente fazia o container sair com erro e o reinício automático o colocava
em laço por construção (D-15). O perfil separa as duas intenções: subir o Pitaco, e ligar o backup
depois de preencher o destino. A saída para a internet é a do bridge padrão; o serviço não precisa
da `edge`.

**Alternativas consideradas**: serviço sempre ativo — rejeitada pelo laço de reinício em
instalação nova; agendador do host chamando a imagem — rejeitada por espalhar a operação para fora
do repositório; imagem fora da composição — rejeitada porque o restore precisaria repetir à mão
as variáveis de conexão que a composição já tem.

---

## D-17 — Restore exige `--yes`; ensaio num Postgres descartável (FR-031 a FR-034)

**Decisão**: `restore.sh` só age com `--yes`; aceita nada (o mais recente do destino), um nome do
destino ou um caminho local existente; confere com `pg_restore --list` e restaura com `--clean
--if-exists --no-owner --exit-on-error`. `restore-test.sh` constrói a imagem, cria uma rede e um
`postgres:16-alpine` descartáveis com nome por PID, espera prontidão **por TCP**, roda
`restore.sh --yes` contra ele e confere `flyway_schema_history`; `trap` remove container e rede.

**Rationale**: restore substitui o banco inteiro; a confirmação explícita impede o acidente, e a
recusa diz qual banco e qual servidor seriam atingidos. `--clean --if-exists` torna o restore
repetível depois de uma interrupção; `--exit-on-error` não deixa seguir em silêncio sobre um erro.
O ensaio prova o que importa — que o último dump restaura e traz o schema com todas as migrations
aplicadas com sucesso — sem tocar em produção. A espera é por TCP porque, na inicialização da
imagem oficial, um servidor temporário escuta só no socket; aceitar a conexão dele seria restaurar
num banco prestes a reiniciar. As contagens de `applications` e `survey_displays` dão a quem roda
uma conferência de ordem de grandeza. Para ensaiar sem object storage, `BACKUP_REMOTE=:local:/backups`
com `BACKUP_LOCAL_DIR` montado somente leitura.

**Alternativas consideradas**: ensaio contra o banco de produção — rejeitado por escrever onde
só se queria ler; restore em transação única — não adotado, porque `--clean --if-exists` já torna
o restore repetível do começo e a conferência com `--list` pega o arquivo ilegível antes de
qualquer escrita.

---

## D-18 — Registro de backup e restore em linhas `chave=valor` com instante UTC (FR-035)

**Decisão**: `printf '%s <operação> %s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$*"`, com pares como
`arquivo=… bytes=…` e `arquivo=… banco=…`.

**Rationale**: uma linha por ação, legível no `docker compose logs` e filtrável no Loki sem parser
dedicado. Nenhuma linha carrega credencial do banco ou do object storage.

**Alternativas consideradas**: JSON — rejeitado por ser mais difícil de ler no terminal, que é
onde quem opera vê primeiro.

---

## D-19 — Um portão de CI por parte, filtrado por caminho (FR-037 a FR-042)

**Decisão**: quatro workflows — `backend`, `painel`, `sdk`, `compose` — cada um com `paths` da sua
parte e do próprio arquivo, em `push` na `main` e em `pull_request`, `permissions: contents:
read`, `timeout-minutes` próprio. `concurrency` com `cancel-in-progress` nos três primeiros. Node
lido de `painel/.nvmrc` no painel e no SDK. Resultados do Surefire e rastros do Playwright como
artefato em falha, retidos por sete dias.

**Rationale**: um monorepo com três tecnologias não deve rodar Maven porque um texto do painel
mudou. O painel embute o SDK (D-12), então o portão do painel também dispara por `sdk/src/**` e
`sdk/package.json`, e instala o SDK pelo mesmo caminho da imagem — o que quebraria no build da
imagem quebra antes, no PR. O portão do backend roda `./mvnw verify` direto no runner, que já tem
Docker para o Testcontainers. O portão do painel roda o E2E contra o simulador de API, sem backend.
O portão do SDK roda o teste de contrato do catálogo contra a especificação da API e exporta o app
de exemplo para as duas plataformas. O portão `compose` valida a composição com um `.env`
fictício (`POSTGRES_PASSWORD=ci`, `BACKUP_REMOTE=:local:/backups`) em três combinações — sozinha,
com o perfil `backup` e somada à sobreposição local, porque um serviço atrás de perfil ou numa
sobreposição não entra na validação padrão —, roda `shellcheck` nos scripts e constrói a imagem de
backup; ele não cancela execução anterior porque é curto.

**Alternativas consideradas**: um workflow único com jobs condicionais — rejeitado por esconder
qual parte falhou e por rodar setup de todas; construir as imagens da API e do painel no CI —
rejeitado por dobrar o tempo dos portões sem publicar nada, já que as imagens são construídas no
servidor (D-01).
