# Runbook do Pitaco

O que fazer em produção, na VPS, com o `compose.yaml` da raiz. Comandos rodam na raiz do
repositório.

## Subir

```bash
docker network create edge          # uma vez por máquina; é a rede do túnel
cp .env.example .env                # e preencha: senha do Postgres e o destino do backup
docker compose up -d --build
docker compose ps
```

A API responde em `/api/actuator/health`. Swagger e `/api/v3/api-docs` ficam desligados; para
ligar de propósito, `PITACO_API_DOCS_ENABLED=true` no ambiente da `api`.

As migrations rodam na subida da API. Uma migration nova só vale depois de reiniciar a `api`.

## Backup

O serviço `backup` faz `pg_dump` em formato custom a cada `BACKUP_INTERVAL` segundos, confere o
arquivo com `pg_restore --list` e envia para `BACKUP_REMOTE`, mantendo as `BACKUP_KEEP` cópias
mais recentes. O destino fica fora desta máquina: backup que mora na mesma VPS não é backup.

O destino é um remoto do rclone definido por ambiente, sem arquivo de configuração. No `.env`:

```dotenv
BACKUP_REMOTE=pitacobackup:meu-bucket/pitaco
RCLONE_CONFIG_PITACOBACKUP_TYPE=s3
RCLONE_CONFIG_PITACOBACKUP_PROVIDER=Cloudflare
RCLONE_CONFIG_PITACOBACKUP_ACCESS_KEY_ID=…
RCLONE_CONFIG_PITACOBACKUP_SECRET_ACCESS_KEY=…
RCLONE_CONFIG_PITACOBACKUP_ENDPOINT=https://<conta>.r2.cloudflarestorage.com
```

Sem `BACKUP_REMOTE`, o serviço falha na subida e reinicia em laço. É proposital: backup
desligado em silêncio é pior que barulho no log.

### Backup manual

```bash
docker compose run --rm backup backup.sh
docker compose logs --tail 20 backup      # "enviado arquivo=… bytes=…"
```

### Restaurar

Substitui o conteúdo do banco de produção. Pare quem escreve antes.

```bash
docker compose stop api painel
docker compose run --rm backup restore.sh --yes                          # o mais recente
docker compose run --rm backup restore.sh --yes pitaco-20260912T034700Z.dump
docker compose start api painel
```

### Testar o restore

Restaura o dump mais recente num Postgres descartável e confere: `flyway_schema_history`
presente, nenhuma migration com falha, e as contagens de `applications` e `survey_displays`.
Não toca no banco de produção; do ambiente real, só lê o destino dos backups.

```bash
ops/backup/restore-test.sh .env
```

Rode depois de configurar o destino, e depois a cada mudança de schema relevante. Backup cujo
restore nunca foi testado não é backup.

Para ensaiar sem object storage, com dumps num diretório local:

```bash
BACKUP_REMOTE=:local:/backups BACKUP_LOCAL_DIR="$PWD/backups" ops/backup/restore-test.sh
```

## Chave de API comprometida

A chave é pública por natureza, porque vive no bundle do app. "Comprometida" quer dizer abuso:
tráfego anômalo, `429` constante, eventos que o app não dispara.

1. No painel, em **Aplicações → a aplicação → Chaves**, emita uma chave nova com rótulo claro.
2. Publique a versão do app com a chave nova.
3. Acompanhe o último uso da chave antiga. Quando o tráfego legítimo tiver migrado, **revogue**.
   A revogação vale na hora; as outras chaves da aplicação seguem funcionando.

Com abuso ativo e sem tempo para esperar o app, revogue já. O app antigo passa a receber `401`, e
o SDK trata isso com o mesmo silêncio de sempre: nada aparece e nada quebra.

## Desativar uma aplicação em incidente

Pesquisa errada no ar, app em momento crítico, qualquer razão para parar tudo de uma aplicação:

- No painel, em **Aplicações → a aplicação → Desativar**. Para de entregar pesquisa na hora.
- O histórico continua acessível e exportável. Desativar não é excluir.
- Para voltar, **Reativar** na mesma tela.

Para parar só uma pesquisa, **Pausar** na tela dela é reversível; **Encerrar** não é.

## O que olhar na aba Saúde

Em **Aplicações → a aplicação → Saúde**:

- **Versões do SDK**: a distribuição do tráfego recente por versão. Versão marcada como "sumiu do
  tráfego" pode deixar de ser sustentada.
- **Erros do SDK**: falhas reportadas pelo próprio SDK, por tipo e versão. Um tipo que dispara
  de repente depois de um release do app é o primeiro lugar para olhar.

Na tela de **Resultados** de cada pesquisa:

- **Supressão relevante**: a pesquisa chega, mas o SDK não sabe renderizá-la. A tela diz o motivo
  e a partir de que versão ela funcionaria. A correção é de escopo da pesquisa ou de versão do
  app, não de disparo.
- **Evento nunca recebido**: nenhuma consulta com o evento configurado chegou desta aplicação. A
  correção é do disparo: nome do evento ou integração do app.

As duas situações produzem zero respostas por motivos opostos. Confundi-las é mexer no lugar
errado.
