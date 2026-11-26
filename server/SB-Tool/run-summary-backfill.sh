#!/bin/bash
# 브리핑 로컬 적재 (#49, #67): 내보내기(SELECT 만) > 요약 입력 되찾기 > 브리핑 만들기(claude -p, 하이쿠) > 적용 파일 만들기
# 대상: 브리핑 구조의 글이 없는 기사 전체 (요약이 없는 기사 + 이전 구조의 요약만 있는 기사)
#
# 사용: ./run-summary-backfill.sh <SSH 자격 이름> <결과 폴더> [동시 호출 수, 기본 4]
#   예: ./run-summary-backfill.sh synology-chan4760 ~/secbrief-summary-backfill/2026-11-26
#
# 결과 폴더(저장소 밖)에 남는 것 3개: results.jsonl(기사 id + 만든 브리핑), report.md(출처별 건수), apply.sql(운영 적용 파일)
# apply.sql 은 브리핑 열이 있는 DB(V0.8.0 이후)에만 적용됨: 열이 없으면 UPDATE 가 실패하고 전체 롤백
# 요약 입력(source_text)이 든 파일은 임시 폴더에만 두고 끝나면 지움 (성공·실패 공통). 운영 DB 에는 쓰지 않음: apply.sql 은 사람이 검토 후 적용
# 중간에 멈추면 같은 결과 폴더로 다시 실행 (results.jsonl 에 있는 기사는 건너뜀)
set -euo pipefail

if [ "$#" -lt 2 ]; then
    echo "usage: $0 <ssh credential name> <output directory> [concurrency]" >&2
    exit 1
fi

SSH_CREDENTIAL_NAME=$1
OUTPUT_DIRECTORY=$2
CONCURRENCY=${3:-4}
SERVER_DIRECTORY=$(cd "$(dirname "$0")/.." && pwd)
CLAUDE_BINARY=${CLAUDE_BINARY:-claude}

# 내보내기 열 이름은 BackfillTarget.fromJson 이 읽는 이름과 같아야 함
# 대상 조건: 한 줄 결론이 없는 행. to_jsonb 로 읽어 브리핑 열이 아직 없는 DB(V0.8.0 이전)에서도 같은 SQL 이 동작 (그때는 모든 행)
EXPORT_SQL="select row_to_json(t) from (
  select a.id, a.title, a.region, a.kind, a.topic, s.code as source_code, s.display_name as source_display_name,
         s.feed_format, s.feed_url, a.original_url, a.cve_id, a.published_at, a.summary_status, a.source_text
    from article a join source s on s.id = a.source_id
   where to_jsonb(a) ->> 'summary_one_line' is null
   order by a.id) t;"

WORK_DIRECTORY=$(mktemp -d "${TMPDIR:-/tmp}/secbrief-summary-backfill.XXXXXX")
chmod 700 "$WORK_DIRECTORY"
trap 'rm -rf "$WORK_DIRECTORY"' EXIT
mkdir -p "$WORK_DIRECTORY/empty" "$OUTPUT_DIRECTORY"
OUTPUT_DIRECTORY=$(cd "$OUTPUT_DIRECTORY" && pwd)

# 1. 내보내기: sudo 가 표준 입력을 먹으므로 SQL 을 base64 로 넘김
ENCODED_SQL=$(printf '%s' "$EXPORT_SQL" | base64 | tr -d '\n')
PYTHONIOENCODING=utf-8 python3 ~/.projectops/bin/projectops_cli.py launch ssh \
    --cred "$SSH_CREDENTIAL_NAME" --sudo --max-output 50000000 --timeout 180 \
    --command "SUDO /usr/local/bin/docker exec postgres sh -c 'echo $ENCODED_SQL | base64 -d | psql -U \"\$POSTGRES_USER\" -d secbrief -At'" \
    | jq -r 'if .truncated then error("export truncated") else .stdout end' | grep '^{' > "$WORK_DIRECTORY/targets.jsonl"
echo "[summary backfill] exported : targets=$(wc -l < "$WORK_DIRECTORY/targets.jsonl" | tr -d ' ')"

export JAVA_HOME=${JAVA_HOME:-$(/usr/libexec/java_home -v 21)}
cd "$SERVER_DIRECTORY"

# 2. 요약 입력 되찾기 (공개 출처를 운영 수집기로 다시 읽음)
./gradlew -q :SB-Tool:summaryTool --args="recover --targets '$WORK_DIRECTORY/targets.jsonl' --output '$WORK_DIRECTORY/inputs.jsonl'"

# 3. 브리핑 만들기 (운영과 같은 하네스·검증, 기사마다 시도 3회·호출 6회 이내)
./gradlew -q :SB-Tool:summaryTool --args="generate --inputs '$WORK_DIRECTORY/inputs.jsonl' --results '$OUTPUT_DIRECTORY/results.jsonl' --report '$OUTPUT_DIRECTORY/report.md' --empty-directory '$WORK_DIRECTORY/empty' --claude '$CLAUDE_BINARY' --concurrency $CONCURRENCY"

# 4. 적용 파일 만들기
./gradlew -q :SB-Tool:summaryTool --args="apply-file --results '$OUTPUT_DIRECTORY/results.jsonl' --output '$OUTPUT_DIRECTORY/apply.sql'"

cat "$OUTPUT_DIRECTORY/report.md"
echo "[summary backfill] finished : output=$OUTPUT_DIRECTORY (apply.sql 은 검토 후 직접 적용)"
