#!/usr/bin/env bash
set -euo pipefail
umask 077
export TZ=Asia/Seoul

snapshot_counts() {
  gzip -dc -- "$1" | awk '
    /^INSERT INTO .reservations. VALUES / { reservations++ }
    /^INSERT INTO .payments. VALUES / { payments++ }
    /^INSERT INTO .tickets. VALUES / { tickets++ }
    END { printf "%d\t%d\t%d\n", reservations, payments, tickets }
  '
}

backup() {
  : "${BACKUP_DIR:?BACKUP_DIR를 지정하세요}"
  : "${BACKUP_COPY_DIR:?별도 저장 장치의 BACKUP_COPY_DIR를 지정하세요}"
  local copy_dir backup_dir name
  copy_dir=$(cd -- "$BACKUP_COPY_DIR" && pwd -P)
  mkdir -p -- "$BACKUP_DIR"
  backup_dir=$(cd -- "$BACKUP_DIR" && pwd -P)
  if [[ $backup_dir == "$copy_dir" ]]; then
    echo '백업 저장 경로와 복사 경로가 동일합니다.' >&2
    exit 1
  fi

  name="cafeteria_$(date +%Y%m%d_%H%M%S).sql.gz"
  tmp_backup=$(mktemp "$backup_dir/.cafeteria.XXXXXX")
  tmp_copy=''
  trap 'rm -f -- "$tmp_backup" "${tmp_copy:-}"' EXIT
  mysqldump --single-transaction --no-tablespaces --set-gtid-purged=OFF \
    --skip-extended-insert cafeteria_db | gzip > "$tmp_backup"
  gzip -t -- "$tmp_backup"
  mv -- "$tmp_backup" "$backup_dir/$name"
  tmp_copy=$(mktemp "$copy_dir/.cafeteria.XXXXXX")
  cp -- "$backup_dir/$name" "$tmp_copy"
  mv -- "$tmp_copy" "$copy_dir/$name"
  find "$backup_dir" "$copy_dir" -maxdepth 1 -type f -name 'cafeteria_????????_??????.sql.gz' -mtime +6 -delete
  printf '백업 저장 및 복사 완료 (%s)\n' "$name"
}

verify() {
  local file=$1
  local database=cafeteria_restore_test
  local tables expected actual
  expected=$(snapshot_counts "$file")
  tables=$(mysql --batch --skip-column-names "$database" \
    -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE()")
  if [[ $tables != 0 ]]; then
    echo '복원에는 비어 있는 cafeteria_restore_test 데이터베이스가 필요합니다.' >&2
    exit 1
  fi

  gzip -dc -- "$file" | mysql "$database"
  actual=$(mysql --batch --skip-column-names "$database" \
    -e 'SELECT (SELECT COUNT(*) FROM reservations), (SELECT COUNT(*) FROM payments), (SELECT COUNT(*) FROM tickets)')
  if [[ $expected != "$actual" ]]; then
    printf '복원 행 수 불일치 (예약/결제/식권)\n백업\t%s\n복원\t%s\n' "$expected" "$actual" >&2
    exit 1
  fi
  printf '복원 행 수 일치 (예약/결제/식권)\t%s\n' "$actual"
}

case "${1:-}" in
  backup) backup ;;
  verify)
    [[ $# -eq 2 ]] || { echo '사용법: backup.sh verify 백업파일.sql.gz' >&2; exit 2; }
    verify "$2"
    ;;
  *) echo '사용법: backup.sh backup | backup.sh verify 백업파일.sql.gz' >&2; exit 2 ;;
esac
