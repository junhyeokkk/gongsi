# SSM Parameter Store 의 /gongsi/* 를 환경변수로 올린다 - backup.sh 가 source (run.sh 와 같은 규칙)
# DB_HOST 가 이미 있으면(로컬 점검) SSM 을 읽지 않는다
if [ -z "${DB_HOST:-}" ]; then
  while IFS=$'\t' read -r name value; do
    export "${name##*/}=${value}"
  done < <(aws ssm get-parameters-by-path --path "${SSM_PREFIX:-/gongsi}" --with-decryption \
    --query 'Parameters[].[Name,Value]' --output text)
fi

: "${DB_HOST:?SSM ${SSM_PREFIX:-/gongsi}/DB_HOST 없음}"
: "${DB_PASS:?SSM ${SSM_PREFIX:-/gongsi}/DB_PASS 없음}"
export DB_PORT="${DB_PORT:-5432}"
