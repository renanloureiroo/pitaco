#!/usr/bin/env bash
# Ensaio de restore: sobe um Postgres descartável, restaura nele o dump mais recente do
# BACKUP_REMOTE e confere o resultado. Do ambiente real, só lê o destino dos backups.
#
# Uso: ops/backup/restore-test.sh [arquivo.env]
#
# O arquivo .env (o da raiz, por exemplo) fornece BACKUP_REMOTE e RCLONE_CONFIG_*. Sem ele, as
# variáveis vêm do ambiente. Para ensaiar sem object storage, use BACKUP_REMOTE=:local:/backups
# e BACKUP_LOCAL_DIR com o diretório do host que contém os dumps.
set -euo pipefail

here="$(cd "$(dirname "$0")" && pwd)"
image="${BACKUP_IMAGE:-pitaco-backup:restore-test}"
run_id="$$"
network="pitaco-restore-test-$run_id"
database="pitaco-restore-db-$run_id"

cleanup() {
  docker rm -f "$database" > /dev/null 2>&1 || true
  docker network rm "$network" > /dev/null 2>&1 || true
}
trap cleanup EXIT

docker build --quiet --tag "$image" "$here" > /dev/null
docker network create "$network" > /dev/null
docker run --detach --rm --name "$database" --network "$network" \
  --env POSTGRES_PASSWORD=restore-test --env POSTGRES_DB=pitaco \
  postgres:16-alpine > /dev/null

# Por TCP: o servidor temporário da inicialização só escuta no socket, e aceitar a conexão dele
# seria restaurar num banco prestes a reiniciar.
ready=no
for _ in $(seq 1 60); do
  if docker exec "$database" pg_isready --host 127.0.0.1 --username postgres > /dev/null 2>&1; then
    ready=yes
    break
  fi
  sleep 1
done
if [ "$ready" != yes ]; then
  echo "o Postgres descartável não ficou pronto" >&2
  exit 1
fi

args=(--rm --network "$network")
if [ "${1:-}" != "" ]; then
  args+=(--env-file "$1")
fi
if [ -n "${BACKUP_LOCAL_DIR:-}" ]; then
  args+=(--volume "$BACKUP_LOCAL_DIR:/backups:ro")
fi
if [ -n "${BACKUP_REMOTE:-}" ]; then
  args+=(--env BACKUP_REMOTE)
fi
while IFS= read -r variable; do
  args+=(--env "$variable")
done < <(env | sed -n 's/^\(RCLONE_CONFIG_[A-Z0-9_]*\)=.*/\1/p')
args+=(--env PGHOST="$database" --env PGUSER=postgres --env PGPASSWORD=restore-test \
  --env PGDATABASE=pitaco)

docker run "${args[@]}" "$image" sh -euc '
  restore.sh --yes

  sql() { psql --no-psqlrc --tuples-only --no-align --set ON_ERROR_STOP=1 --command "$1"; }

  if [ "$(sql "select to_regclass('"'"'public.flyway_schema_history'"'"') is not null")" != t ]; then
    echo "falhou: flyway_schema_history ausente" >&2
    exit 1
  fi

  applied="$(sql "select count(*) from flyway_schema_history where success")"
  failed="$(sql "select count(*) from flyway_schema_history where not success")"
  if [ "$applied" -eq 0 ] || [ "$failed" -ne 0 ]; then
    echo "falhou: migrations aplicadas=$applied com_falha=$failed" >&2
    exit 1
  fi

  echo "ok: migrations=$applied ultima=$(sql "select version from flyway_schema_history where success order by installed_rank desc limit 1")"
  echo "ok: applications=$(sql "select count(*) from applications") survey_displays=$(sql "select count(*) from survey_displays")"
'
