package kr.suhsaechan.secbrief.tool.summary;

import java.util.List;
import kr.suhsaechan.secbrief.advisory.summary.ArticleBrief;
import kr.suhsaechan.secbrief.advisory.summary.ArticleBriefJson;
import kr.suhsaechan.secbrief.advisory.summary.ArticleSummary;
import kr.suhsaechan.secbrief.advisory.summary.SummaryKeyPointJson;

/**
 * 운영 DB 적용 파일(SQL) 만들기: 통과한 브리핑 > 임시 표에 COPY 로 싣기 > article 을 UPDATE … FROM 으로 한 번에 갱신
 * 만든 글은 SQL 문장이 아니라 COPY 데이터 줄로만 들어감 (따옴표·$$ 가 SQL 로 읽히지 않음), 한 트랜잭션, 실은·갱신 건수 출력
 * 브리핑 열 5개와 이전 구조 열 3개를 함께 씀 (이전 구조 값은 서버와 같은 변환: ArticleBrief.toLegacySummary)
 * 갱신 조건: 적용 시점에 브리핑이 없는 행만 (summary_one_line 이 null: 그 사이 운영 서버가 브리핑을 쓴 행은 건드리지 않음)
 */
public final class SummaryApplyFileWriter {

    // COPY 글 형식: 열 구분, 없는 값, 데이터 끝 표시
    private static final String COPY_COLUMN_DELIMITER = "\t";
    private static final String COPY_NULL_VALUE = "\\N";
    private static final String COPY_END_MARK = "\\.";

    // 임시 표의 글 열은 길이 제한 없는 text: 길이는 article 열이 확인 (넘으면 UPDATE 가 실패하고 전체 롤백)
    private static final String HEADER_TEMPLATE = """
            -- secbrief 브리핑 적재 (#67): 로컬 도구(claude -p, 요약 하네스)가 만든 브리핑 %d건
            -- 갱신 대상: 적용 시점에 브리핑이 없는 행만 (summary_one_line is null)
            -- 실행: psql -d secbrief -f 이_파일 (또는 표준 입력으로 넘김). 마지막에 loaded_row_count, updated_row_count 출력
            -- 오류가 나면 전체 롤백 (ON_ERROR_STOP + 한 트랜잭션). 브리핑 열이 없는 DB(V0.8.0 이전)에서는 UPDATE 가 실패하고 롤백
            \\set ON_ERROR_STOP on
            begin;

            create temporary table brief_backfill (
                article_id              bigint primary key,
                summary_one_line        text not null,
                summary_what_happened   text not null,
                summary_why_it_matters  text,
                summary_affected        text,
                summary_actions         text,
                summary                 text not null,
                summary_key_points      text,
                summary_action_guide    text
            ) on commit drop;

            copy brief_backfill (article_id, summary_one_line, summary_what_happened, summary_why_it_matters, summary_affected,
                                 summary_actions, summary, summary_key_points, summary_action_guide) from stdin;
            """;

    private static final String FOOTER = """

            with updated as (
                update article a
                   set summary_one_line = b.summary_one_line,
                       summary_what_happened = b.summary_what_happened,
                       summary_why_it_matters = b.summary_why_it_matters,
                       summary_affected = b.summary_affected,
                       summary_actions = b.summary_actions,
                       summary = b.summary,
                       summary_key_points = b.summary_key_points,
                       summary_action_guide = b.summary_action_guide,
                       summary_status = 'DONE',
                       source_text = null,
                       updated_at = (now() at time zone 'Asia/Seoul')
                  from brief_backfill b
                 where a.id = b.article_id
                   and a.summary_one_line is null
                returning a.id
            )
            select (select count(*) from brief_backfill) as loaded_row_count,
                   count(*) as updated_row_count
              from updated;

            commit;
            """;

    private SummaryApplyFileWriter() {
    }

    /**
     * 통과한 결과만 실음
     */
    public static String toApplySql(List<BackfillResult> results) {
        List<BackfillResult> acceptedResults =
                results.stream().filter(result -> result.status() == BackfillResult.Status.ACCEPTED).toList();
        StringBuilder applySql = new StringBuilder(String.format(HEADER_TEMPLATE, acceptedResults.size()));
        for (BackfillResult result : acceptedResults) {
            applySql.append(toCopyLine(result)).append('\n');
        }
        return applySql.append(COPY_END_MARK).append('\n').append(FOOTER).toString();
    }

    // 데이터 한 줄: article_id, 브리핑 5개(한 줄 결론, 무슨 일, 왜 중요, 영향 대상 JSON, 대응 JSON), 이전 구조 3개 (없는 값은 \N)
    static String toCopyLine(BackfillResult result) {
        ArticleBrief brief = result.acceptedBrief();
        ArticleSummary legacySummary = brief.toLegacySummary();
        return String.join(COPY_COLUMN_DELIMITER,
                Long.toString(result.id()),
                toCopyValue(brief.oneLine()),
                toCopyValue(brief.whatHappened()),
                toCopyValue(brief.whyItMatters()),
                toCopyValue(ArticleBriefJson.toAffectedJson(brief.affected())),
                toCopyValue(ArticleBriefJson.toActionsJson(brief.actions())),
                toCopyValue(legacySummary.summary()),
                toCopyValue(SummaryKeyPointJson.toJson(legacySummary.keyPoints())),
                toCopyValue(legacySummary.actionGuide()));
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
