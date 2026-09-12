#!/bin/sh
# Restaura um dump no banco apontado por PGHOST, PGPORT, PGUSER, PGPASSWORD e PGDATABASE. O que
# existir lá é substituído, por isso a execução exige --yes.
#
# Uso: restore.sh --yes                      o dump mais recente do BACKUP_REMOTE
#      restore.sh --yes pitaco-<data>.dump   um dump específico do BACKUP_REMOTE
#      restore.sh --yes /caminho/local.dump  um arquivo já baixado
set -eu

log() {
  printf '%s restore %s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$*"
}

confirmed=no
source=""
for arg in "$@"; do
  case "$arg" in
    --yes) confirmed=yes ;;
    *) source="$arg" ;;
  esac
done

if [ "$confirmed" != yes ]; then
  echo "restore.sh substitui o conteúdo de ${PGDATABASE:-?} em ${PGHOST:-?}. Repita com --yes." >&2
  exit 2
fi

work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT

if [ -n "$source" ] && [ -f "$source" ]; then
  file="$source"
else
  if [ -z "${BACKUP_REMOTE:-}" ]; then
    echo "BACKUP_REMOTE ausente: informe um arquivo local ou o destino dos backups" >&2
    exit 1
  fi
  if [ -z "$source" ]; then
    source="$(rclone lsf --files-only --include 'pitaco-*.dump' "$BACKUP_REMOTE" | sort | tail -n 1)"
    if [ -z "$source" ]; then
      echo "nenhum backup em $BACKUP_REMOTE" >&2
      exit 1
    fi
  fi
  rclone copyto "$BACKUP_REMOTE/$source" "$work/$source"
  file="$work/$source"
fi

pg_restore --list "$file" > /dev/null
pg_restore --clean --if-exists --no-owner --exit-on-error --dbname="$PGDATABASE" "$file"
log "restaurado arquivo=$(basename "$file") banco=$PGDATABASE"
