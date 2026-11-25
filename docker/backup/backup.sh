#!/bin/sh
# Backup diário do Postgres (aplicação e Keycloak), com retenção.
#
# Guarda um arquivo por dia em /backups e apaga os mais antigos que BACKUP_KEEP_DAYS.
# O prontuário tem guarda mínima de 5 anos: isso é sobre o DADO, que continua no banco;
# aqui se define por quantos dias manter as CÓPIAS de segurança para recuperação de desastre.
#
# IMPORTANTE: o conteúdo clínico vai cifrado no backup. Sem a chave APP_CRYPTO_KEY ele não
# pode ser lido — guarde a chave em outro lugar, nunca junto dos backups.
set -eu

: "${PGHOST:=postgres}"
: "${PGUSER:?defina PGUSER}"
: "${PGPASSWORD:?defina PGPASSWORD}"
: "${BACKUP_DATABASES:=psychology keycloak}"
: "${BACKUP_KEEP_DAYS:=30}"
: "${BACKUP_INTERVAL_SECONDS:=86400}"
export PGPASSWORD

run_backup() {
  stamp=$(date +%Y%m%d-%H%M%S)
  for db in $BACKUP_DATABASES; do
    target="/backups/${db}-${stamp}.dump"
    # Formato "custom": compacto e restaurável por tabela/esquema com pg_restore.
    if pg_dump -h "$PGHOST" -U "$PGUSER" -d "$db" -Fc -f "${target}.tmp"; then
      mv "${target}.tmp" "$target"
      echo "$(date -Iseconds) backup ok: $target ($(du -h "$target" | cut -f1))"
    else
      rm -f "${target}.tmp"
      echo "$(date -Iseconds) ERRO no backup de $db" >&2
    fi
  done
  find /backups -name '*.dump' -type f -mtime +"$BACKUP_KEEP_DAYS" -print -delete | sed 's/^/removido (antigo): /'
}

# Sem argumento: roda para sempre, um backup por intervalo. Com "once": um backup e sai.
if [ "${1:-}" = "once" ]; then
  run_backup
  exit 0
fi
while true; do
  run_backup
  sleep "$BACKUP_INTERVAL_SECONDS"
done
