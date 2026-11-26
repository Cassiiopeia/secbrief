package kr.suhsaechan.secbrief.tool.summary;

import java.util.List;
import kr.suhsaechan.secbrief.advisory.summary.ArticleSummary;
import kr.suhsaechan.secbrief.advisory.summary.ArticleSummaryPolicy;
import kr.suhsaechan.secbrief.advisory.summary.SummaryKeyPointJson;

/**
 * 운영 DB 적용 파일(SQL) 만들기: 통과한 요약 > 임시 표에 COPY 로 싣기 > article 을 UPDATE … FROM 으로 한 번에 갱신
 * 만든 글은 SQL 문장이 아니라 COPY 데이터 줄로만 들어감 (따옴표·$$ 가 SQL 로 읽히지 않음), 한 트랜잭션, 갱신 건수 출력
 * 갱신 조건: 적용 시점에 summary_status 가 DONE 이 아닌 행만 (그 사이 운영 서버가 요약한 행은 건드리지 않음)
 */
public final class SummaryApplyFileWriter {

    // COPY 글 형식: 열 구분, 없는 값, 데이터 끝 표시
    private static final String COPY_COLUMN_DELIMITER = "\t";
    private static final String COPY_NULL_VALUE = "\\N";
    private static final String COPY_END_MARK = "\\.";

    private static final String HEADER_TEMPLATE = """
            -- secbrief 밀린 요약 적재 (#49): 로컬 도구(claude -p, 요약 하네스)가 만든 요약 %d건
            -- 갱신 대상: 적용 시점에 summary_status 가 DONE 이 아닌 행만
            -- 실행: psql -d secbrief -f 이_파일 (또는 표준 입력으로 넘김). 마지막에 loaded_row_count, updated_row_count 출력
            -- 오류가 나면 전체 롤백 (ON_ERROR_STOP + 한 트랜잭션)
            \\set ON_ERROR_STOP on
            begin;

            create temporary table summary_backfill (
                article_id            bigint primary key,
                summary               varchar(%d) not null,
                summary_key_points    text,
                summary_action_guide  varchar(%d)
            ) on commit drop;

            copy summary_backfill (article_id, summary, summary_key_points, summary_action_guide) from stdin;
            """;

    private static final String FOOTER = """

            with updated as (
                update article a
                   set summary = b.summary,
                       summary_key_points = b.summary_key_points,
                       summary_action_guide = b.summary_action_guide,
                       summary_status = 'DONE',
                       source_text = null,
                       updated_at = (now() at time zone 'Asia/Seoul')
                  from summary_backfill b
                 where a.id = b.article_id
                   and a.summary_status <> 'DONE'
                returning a.id
            )
            select (select count(*) from summary_backfill) as loaded_row_count,
                   count(*) as updated_row_count
              from updated;

            commit;
            """;

    private SummaryApplyFileWriter() {
    }

    /**
     * 통과한 결과만 실음 (열 길이를 넘는 요약은 검증에서 이미 걸러짐: 넘는 것이 있으면 IllegalArgumentException)
     */
    public static String toApplySql(List<BackfillResult> results) {
        List<BackfillResult> acceptedResults =
                results.stream().filter(result -> result.status() == BackfillResult.Status.ACCEPTED).toList();
        StringBuilder applySql = new StringBuilder(String.format(HEADER_TEMPLATE, acceptedResults.size(),
                ArticleSummaryPolicy.MAXIMUM_SUMMARY_LENGTH, ArticleSummaryPolicy.MAXIMUM_ACTION_GUIDE_LENGTH));
        for (BackfillResult result : acceptedResults) {
            applySql.append(toCopyLine(result)).append('\n');
        }
        return applySql.append(COPY_END_MARK).append('\n').append(FOOTER).toString();
    }

    // 데이터 한 줄: article_id \t summary \t summary_key_points(JSON 배열 또는 \N) \t summary_action_guide(또는 \N)
    static String toCopyLine(BackfillResult result) {
        ArticleSummary summary = result.acceptedSummary();
        if (summary.summary().length() > ArticleSummaryPolicy.MAXIMUM_SUMMARY_LENGTH
                || (summary.actionGuide() != null
                && summary.actionGuide().length() > ArticleSummaryPolicy.MAXIMUM_ACTION_GUIDE_LENGTH)) {
            throw new IllegalArgumentException("summary exceeds column length : articleId=" + result.id());
        }
        return String.join(COPY_COLUMN_DELIMITER,
                Long.toString(result.id()),
                toCopyValue(summary.summary()),
                toCopyValue(SummaryKeyPointJson.toJson(summary.keyPoints())),
                toCopyValue(summary.actionGuide()));
    }

    /**
     * COPY 글 형식 값: null > \N, 역슬래시 > \\, 탭·줄바꿈·캐리지 리턴 > \t \n \r, NUL 은 뺌 (PostgreSQL 글에 넣을 수 없음)
     * 역슬래시를 모두 두 개로 바꾸므로 데이터 줄이 끝 표시(\.)나 \N 으로 읽히지 않음
     */
    static String toCopyValue(String text) {
        if (text == null) {
            return COPY_NULL_VALUE;
        }
        return text.replace("\u0000", "")
                .replace("\\", "\\\\")
                .replace("\t", "\\t")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }
}
