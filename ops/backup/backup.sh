#!/bin/sh
# Dump do Postgres em formato custom, conferido com pg_restore --list antes de subir para o
# object storage, com retenção das N cópias mais recentes no destino.
#
# Configuração só por ambiente:
#   PGHOST, PGPORT, PGUSER, PGPASSWORD, PGDATABASE   conexão com o Postgres de origem
#   BACKUP_REMOTE     destino rclone: pitacobackup:bucket/pitaco, ou :local:/backups no ensaio
#   BACKUP_KEEP       cópias mantidas no destino (padrão 14)
#   BACKUP_INTERVAL   segundos entre execuções no modo --loop (padrão 86400, um dia)
#   RCLONE_CONFIG_*   o remoto do rclone, sem arquivo de configuração
#
# Uso: backup.sh            uma execução
#      backup.sh --loop     uma execução agora e depois a cada BACKUP_INTERVAL
set -eu

log() {
  printf '%s backup %s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$*"
}

require_remote() {
  if [ -z "${BACKUP_REMOTE:-}" ]; then
    log "BACKUP_REMOTE ausente: defina o destino no .env" >&2
    exit 1
  fi
}

prune() {
  keep="${BACKUP_KEEP:-14}"
  listing="$(rclone lsf --files-only --include 'pitaco-*.dump' "$BACKUP_REMOTE" | sort)"
  total="$(printf '%s\n' "$listing" | grep -c . || true)"
  excess=$((total - keep))

  if [ "$excess" -le 0 ]; then
    return
  fi

  printf '%s\n' "$listing" | head -n "$excess" | while read -r old; do
    rclone deletefile "$BACKUP_REMOTE/$old"
    log "removido arquivo=$old"
  done
}

run_once() {
  require_remote

  name="pitaco-$(date -u +%Y%m%dT%H%M%SZ).dump"
  work="$(mktemp -d)"
  trap 'rm -rf "$work"' EXIT

  pg_dump --format=custom --no-owner --file="$work/$name"
  # Dump que não lista não restaura: confere antes de gastar o upload e ocupar uma cópia.
  pg_restore --list "$work/$name" > /dev/null

  bytes="$(wc -c < "$work/$name" | tr -d ' ')"
  rclone copyto "$work/$name" "$BACKUP_REMOTE/$name"
  log "enviado arquivo=$name bytes=$bytes"

  prune
}

case "${1:-}" in
  --loop)
    require_remote
    interval="${BACKUP_INTERVAL:-86400}"
    while true; do
      # Cada execução num processo próprio: com set -e valendo por inteiro, uma falha no dump
      # nunca segue adiante para o upload.
      if ! "$0"; then
        log "falhou; nova tentativa em ${interval}s" >&2
      fi
      sleep "$interval"
    done
    ;;
  "")
    run_once
    ;;
  *)
    echo "uso: backup.sh [--loop]" >&2
    exit 2
    ;;
esac
