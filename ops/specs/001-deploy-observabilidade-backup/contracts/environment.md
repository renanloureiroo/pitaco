# Contrato de ambiente — variáveis e propriedades por serviço

**Feature**: `001-deploy-observabilidade-backup` · Fonte: `compose.yaml`, `compose.local.yaml`,
`.env.example`, `backend/src/main/resources/application.yml`, `ops/backup/*.sh`.

Este é o contrato entre quem hospeda e a stack: o que precisa ser definido, com que formato, e o
que acontece quando falta. Tudo que não está aqui é detalhe interno e pode mudar.

---

## `.env` (raiz do repositório, só no servidor)

| Variável | Formato | Obrigatória | Padrão | Efeito |
| --- | --- | --- | --- | --- |
| `POSTGRES_DB` | identificador | sim | — (modelo: `pitaco`) | nome do banco criado na primeira subida do volume |
| `POSTGRES_USER` | identificador | sim | — (modelo: `pitaco`) | dono do banco; usado pela API e pelo backup |
| `POSTGRES_PASSWORD` | texto | sim | — (modelo: vazio) | senha; gerar com `openssl rand -base64 24` |
| `BACKUP_REMOTE` | `remoto:caminho` do rclone | com `--profile backup` | vazio | destino dos dumps; ex.: `pitacobackup:meu-bucket/pitaco` |
| `BACKUP_KEEP` | inteiro ≥ 0 | não | `14` | cópias mantidas no destino |
| `BACKUP_INTERVAL` | segundos | não | `86400` | intervalo do modo periódico |
| `RCLONE_CONFIG_PITACOBACKUP_TYPE` | tipo do rclone | com `--profile backup` | `s3` | define o remoto `pitacobackup` |
| `RCLONE_CONFIG_PITACOBACKUP_PROVIDER` | `AWS`, `Cloudflare`, `Minio`, `Other`… | com `--profile backup` | `Other` | variante do S3 |
| `RCLONE_CONFIG_PITACOBACKUP_ACCESS_KEY_ID` | texto | com `--profile backup` | vazio | credencial |
| `RCLONE_CONFIG_PITACOBACKUP_SECRET_ACCESS_KEY` | texto | com `--profile backup` | vazio | credencial |
| `RCLONE_CONFIG_PITACOBACKUP_ENDPOINT` | URL | conforme o provedor | vazio | endpoint S3 compatível |
| `RCLONE_CONFIG_PITACOBACKUP_REGION` | texto | conforme o provedor | vazio | região |
| `PITACO_API_DOCS_ENABLED` | `true`/`false` | não | `false` | liga Swagger UI e `/v3/api-docs` (comentada no modelo) |

O valor de `POSTGRES_DB`, `POSTGRES_USER` e `POSTGRES_PASSWORD` só vale na **primeira** subida do
volume `pg_data`; depois disso, mudar o `.env` não muda o banco — só o que a API tenta usar.

---

## Serviço `postgres`

| Variável | Valor |
| --- | --- |
| `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD` | do `.env` |
| `TZ`, `PGTZ` | `UTC` |

`ulimits.core: 0`.

## Serviço `api`

| Variável | Valor | Nota |
| --- | --- | --- |
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://postgres:5432/${POSTGRES_DB}` | pela rede `internal` |
| `SPRING_DATASOURCE_USERNAME` | `${POSTGRES_USER}` | |
| `SPRING_DATASOURCE_PASSWORD` | `${POSTGRES_PASSWORD}` | |
| `OTEL_EXPORTER_OTLP_ENDPOINT` | `http://lgtm:4318` | base dos três sinais; **não** usar `MANAGEMENT_OPENTELEMETRY_OTLP_ENDPOINT`, que é ignorada |
| `PITACO_API_DOCS_ENABLED` | ausente em produção; `true` na sobreposição local | |
| `SPRING_PROFILES_ACTIVE` | **ausente** | sem perfil, vale só o `application.yml`, que é o padrão de produção |

Fixado na imagem: `JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70 -XX:+UseSerialGC -Xss512k
-Duser.timezone=UTC"`.

### Propriedades de produção no `application.yml`

| Propriedade | Valor | Por quê |
| --- | --- | --- |
| `server.servlet.context-path` | `/api` | prefixo de API, documentação e Actuator |
| `server.forward-headers-strategy` | `framework` | URL externa correta atrás do túnel |
| `server.tomcat.accesslog.*` | ligado, `/dev/stdout`, padrão `%t "%r" %s %b %{ms}Tms` | uma linha por requisição |
| `spring.docker.compose.enabled` | `false` | não há compose ao lado do jar em produção |
| `spring.datasource.hikari.maximum-pool-size` | `10` | servidor pequeno |
| `spring.jpa.hibernate.ddl-auto` | `validate` | schema nasce da migration |
| `management.endpoints.web.exposure.include` | `health,info` | superfície mínima |
| `management.tracing.sampling.probability` | `1.0` | volume baixo |
| `springdoc.api-docs.enabled`, `springdoc.swagger-ui.enabled` | `${PITACO_API_DOCS_ENABLED:false}` | documentação desligada por padrão |

## Serviço `painel`

| Variável | Valor | Nota |
| --- | --- | --- |
| `PITACO_API_URL` | `http://api:8080/api` | lida só no servidor; ausente, o painel falha na primeira chamada com mensagem explícita |
| `PORT` | `3001` | |

Fixado na imagem: `NODE_ENV=production`, `NEXT_TELEMETRY_DISABLED=1`, `HOSTNAME=0.0.0.0`.

## Serviço `backup` (perfil `backup`)

| Variável | Valor |
| --- | --- |
| `PGHOST` | `postgres` |
| `PGUSER`, `PGPASSWORD`, `PGDATABASE` | `${POSTGRES_USER}`, `${POSTGRES_PASSWORD}`, `${POSTGRES_DB}` |
| `BACKUP_REMOTE`, `BACKUP_KEEP`, `BACKUP_INTERVAL` | do `.env`, com os padrões acima |
| `RCLONE_CONFIG_PITACOBACKUP_*` | do `.env`, com `TYPE=s3` e `PROVIDER=Other` por padrão |

Fixado na imagem: `RCLONE_CONFIG=/etc/rclone/rclone.conf` (arquivo vazio), comando padrão
`backup.sh --loop`.

## Ensaio de restore (`ops/backup/restore-test.sh`, no host)

| Variável | Obrigatória | Efeito |
| --- | --- | --- |
| `BACKUP_REMOTE` | sim (pelo ambiente ou pelo arquivo passado como argumento) | destino lido |
| `RCLONE_CONFIG_*` | conforme o destino | repassadas ao container do ensaio |
| `BACKUP_LOCAL_DIR` | não | diretório do host montado somente leitura em `/backups` (usar com `BACKUP_REMOTE=:local:/backups`) |
| `BACKUP_IMAGE` | não | tag da imagem construída; padrão `pitaco-backup:restore-test` |
