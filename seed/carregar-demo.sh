#!/usr/bin/env bash
# Carrega a demo completa no Postgres do compose de produção, em uma transação só.
#
# Uso (na raiz do repositório, com o stack no ar e as migrations já aplicadas pela api):
#   seed/carregar-demo.sh              # carrega; recusa se a demo já existir
#   seed/carregar-demo.sh --substituir # apaga a demo anterior e carrega de novo
#   seed/carregar-demo.sh --datas      # só traz as datas da demo existente para perto de agora
#
# Mexe apenas nas aplicações com slug demo-completo*. Nada passa pela API: é um COPY direto, que
# leva segundos.
set -euo pipefail

cd "$(dirname "$0")/.."
DUMP="seed/demo-completo.sql.gz"
modo="${1:-carregar}"

psql_compose() {
  docker compose exec -T postgres sh -c \
    'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -v ON_ERROR_STOP=1 -q -X "$@"' psql "$@"
}

if ! psql_compose -tAc "select 1 from flyway_schema_history limit 1" >/dev/null 2>&1; then
  echo "O banco ainda não tem o schema do Pitaco. Suba a api uma vez (docker compose up -d api)" >&2
  echo "para as migrations rodarem, e tente de novo." >&2
  exit 1
fi

existentes="$(psql_compose -tAc "select count(*) from applications where slug like 'demo-completo%'")"

reset_sql() {
  local apps="(select id from public.applications where slug like 'demo-completo%')"
  cat <<SQL
delete from public.survey_displays where application_id in ${apps};
delete from public.respondents where application_id in ${apps};
delete from public.suppression_events where application_id in ${apps};
delete from public.sdk_error_reports where application_id in ${apps};
delete from public.sdk_version_daily_usage where application_id in ${apps};
delete from public.sdk_version_usage where application_id in ${apps};
delete from public.surveys where application_id in ${apps};
delete from public.application_attributes where application_id in ${apps};
delete from public.application_events where application_id in ${apps};
delete from public.deletion_audits where application_id in ${apps};
delete from public.retention_runs where application_id in ${apps};
delete from public.api_keys where application_id in ${apps};
delete from public.applications where id in ${apps};
SQL
}

case "$modo" in
  --datas)
    if [ "$existentes" = "0" ]; then
      echo "Não há demo carregada para atualizar." >&2
      exit 1
    fi
    { echo "begin;"; cat seed/datas.sql; echo "commit;"; } | psql_compose
    echo "Datas da demo atualizadas para perto de agora."
    exit 0
    ;;
  --substituir) ;;
  carregar)
    if [ "$existentes" != "0" ]; then
      echo "A demo já existe ($existentes aplicações demo-completo*). Use --substituir para recarregar." >&2
      exit 1
    fi
    ;;
  *)
    echo "Uso: seed/carregar-demo.sh [--substituir | --datas]" >&2
    exit 2
    ;;
esac

echo "Carregando a demo…"
{
  echo "begin;"
  reset_sql
  # A pergunta condicional referencia outra pergunta da mesma tabela: com as FKs ativas, a ordem
  # das linhas do COPY importaria. Desligadas só nesta transação; o dump é consistente.
  echo "set local session_replication_role = replica;"
  gunzip -c "$DUMP"
  echo "set local session_replication_role = origin;"
  echo "set search_path = public;"
  cat seed/datas.sql
  echo "commit;"
} | psql_compose -o /dev/null

psql_compose -tAc "select 'Aplicações: ' || count(*) from applications where slug like 'demo-completo%'
  union all select 'Exibições: ' || count(*) from survey_displays where application_id in
    (select id from applications where slug like 'demo-completo%')"
echo "Pronto. Abra o painel em Aplicações → Loja Aurora."
