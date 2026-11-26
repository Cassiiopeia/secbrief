package kr.suhsaechan.secbrief.tool.summary;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import kr.suhsaechan.secbrief.advisory.summary.AffectedTarget;
import kr.suhsaechan.secbrief.advisory.summary.ArticleBrief;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.containers.Container;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.MountableFile;

/**
 * 적용 파일: 값 이스케이프(따옴표·역슬래시·줄바꿈·탭·$$·끝 표시), 통과분만 실음, 한 트랜잭션, 브리핑 열 5개 + 이전 구조 열 3개
 * 실제 PostgreSQL 에 psql 로 적용해 글이 그대로 들어가는지, 브리핑이 없는 행만 바뀌는지(이전 요약이 DONE 인 행 포함), 건수가 나오는지 확인
 */
class SummaryApplyFileWriterTest {

    // SQL 로 읽히면 위험한 글: 작은따옴표·큰따옴표, 역슬래시, 줄바꿈·탭, 달러 인용, COPY 끝 표시·null 표시, 문장 끝내기
    private static final String TRICKY_WHAT_HAPPENED = "It's \"요약\"이에요. 경로는 C:\\temp\\new 예요.\n둘째 줄\t탭 $$ $tag$ ; drop table article; -- \\. \\N";
    private static final String TRICKY_ACTION = "\\.\n'; update article set summary = 'x'; --";
    private static final ArticleBrief TRICKY_BRIEF = new ArticleBrief("한 줄 '결론'이에요.", TRICKY_WHAT_HAPPENED, "왜 \"중요\"한지예요.",
            List.of(new AffectedTarget("Example 'VPN'", "2.4.0\\이전"), new AffectedTarget("$$Hub$$", null)),
            List.of(TRICKY_ACTION, "둘째 대응이에요."));

    @Test
    void escapesBackslashTabNewlineAndKeepsQuotesAndDollarsAsData() {
        assertThat(SummaryApplyFileWriter.toCopyValue("a\\b\tc\nd\re")).isEqualTo("a\\\\b\\tc\\nd\\re");
        assertThat(SummaryApplyFileWriter.toCopyValue("It's \"x\" $$ ;")).isEqualTo("It's \"x\" $$ ;");
        assertThat(SummaryApplyFileWriter.toCopyValue("\\.")).isEqualTo("\\\\.");
        assertThat(SummaryApplyFileWriter.toCopyValue("\\N")).isEqualTo("\\\\N");
        assertThat(SummaryApplyFileWriter.toCopyValue("a\u0000b")).isEqualTo("ab");
        assertThat(SummaryApplyFileWriter.toCopyValue(null)).isEqualTo("\\N");
    }

    @Test
    void writesOneDataLinePerAcceptedResultWithBriefAndLegacyColumnsInsideOneTransaction() {
        String applySql = SummaryApplyFileWriter.toApplySql(List.of(
                accepted(7L, new ArticleBrief("한 줄이에요.", "무슨 일이에요.", "왜 중요한지예요.",
                        List.of(new AffectedTarget("Example VPN", "2.4.0 이전")), List.of("패치를 \"적용\"하세요."))),
                accepted(6L, new ArticleBrief("빈 칸이 많은 한 줄이에요.", "무슨 일만 있어요.", null, List.of(), List.of())),
                new BackfillResult(8L, "NVD", BackfillResult.Status.REJECTED, null, 3, 6, false, "whatHappened copies source text"),
                BackfillResult.withoutBrief(BackfillTestData.newsTarget(9L, null), BackfillResult.Status.NOT_RECOVERABLE)));

        assertThat(applySql).contains("브리핑 2건");
        assertThat(applySql).contains("\\set ON_ERROR_STOP on\nbegin;");
        // 열 순서: id, 한 줄 결론, 무슨 일, 왜 중요, 영향 대상 JSON, 대응 JSON, 이전 요약, 이전 핵심 정리 JSON, 이전 대응 안내
        assertThat(applySql).contains("7\t한 줄이에요.\t무슨 일이에요.\t왜 중요한지예요.\t"
                + "[{\"target\":\"Example VPN\",\"detail\":\"2.4.0 이전\"}]\t[\"패치를 \\\\\"적용\\\\\"하세요.\"]\t"
                + "한 줄이에요. 무슨 일이에요. 왜 중요한지예요.\t[\"Example VPN: 2.4.0 이전\"]\t패치를 \"적용\"하세요.\n");
        assertThat(applySql).contains("6\t빈 칸이 많은 한 줄이에요.\t무슨 일만 있어요.\t\\N\t\\N\t\\N\t빈 칸이 많은 한 줄이에요. 무슨 일만 있어요.\t\\N\t\\N\n\\.\n");
        // 갱신 조건: 적용 시점에 브리핑이 없는 행만
        assertThat(applySql).contains("where a.id = b.article_id\n       and a.summary_one_line is null");
        assertThat(applySql).contains("summary_status = 'DONE'").contains("source_text = null")
                .contains("loaded_row_count").contains("updated_row_count");
        assertThat(applySql).doesNotContain("\n8\t").doesNotContain("\n9\t");
        assertThat(applySql.strip()).endsWith("commit;");
    }

    @Test
    void appliesTrickyTextExactlyAndOnlyToRowsWithoutBrief(@TempDir Path temporaryDirectory) throws Exception {
        String applySql = SummaryApplyFileWriter.toApplySql(List.of(
                accepted(1L, TRICKY_BRIEF),
                accepted(2L, briefOf("이전 요약만 있던 행의 한 줄이에요.")),
                accepted(3L, briefOf("실패했던 행의 한 줄이에요.")),
                accepted(5L, briefOf("이미 브리핑이 있는 행에 쓰려는 한 줄이에요.")),
                accepted(99L, briefOf("없는 행의 한 줄이에요."))));
        Path applyFile = temporaryDirectory.resolve("apply.sql");
        Files.writeString(applyFile, applySql, UTF_8);

        try (PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")) {
            postgres.start();
            try (Connection connection = DriverManager.getConnection(
                    postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())) {
                createArticleTable(connection);
                insertArticle(connection, 1L, "PENDING", "source one", null, null);
                insertArticle(connection, 2L, "DONE", null, "이전 구조로 쓴 요약이에요.", null);
                insertArticle(connection, 3L, "FAILED", null, null, null);
                insertArticle(connection, 4L, "NONE", null, null, null);
                insertArticle(connection, 5L, "DONE", null, "운영 서버가 먼저 쓴 무슨 일이에요.", "운영 서버가 먼저 쓴 한 줄이에요.");

                postgres.copyFileToContainer(MountableFile.forHostPath(applyFile), "/tmp/apply.sql");
                Container.ExecResult psqlResult = postgres.execInContainer(
                        "psql", "-U", postgres.getUsername(), "-d", postgres.getDatabaseName(), "-f", "/tmp/apply.sql");

                // 실은 5건 중 브리핑이 없는 3건만 갱신 (5번은 이미 브리핑이 있음, 99번은 없는 행)
                assertThat(psqlResult.getExitCode()).as(psqlResult.getStderr()).isZero();
                assertThat(psqlResult.getStdout()).contains("loaded_row_count").contains("updated_row_count")
                        .containsPattern("\\s5 \\|\\s+3\\s");
                // 1번: 글자까지 같은 브리핑 + 서버와 같은 변환으로 만든 이전 구조 값
                assertThat(readArticle(connection, 1L)).containsExactly(
                        "한 줄 '결론'이에요.", TRICKY_WHAT_HAPPENED, "왜 \"중요\"한지예요.",
                        "[{\"target\":\"Example 'VPN'\",\"detail\":\"2.4.0\\\\이전\"},{\"target\":\"$$Hub$$\",\"detail\":null}]",
                        "[\"\\\\.\\n'; update article set summary = 'x'; --\",\"둘째 대응이에요.\"]",
                        "한 줄 '결론'이에요. " + TRICKY_WHAT_HAPPENED + " 왜 \"중요\"한지예요.",
                        "[\"Example 'VPN': 2.4.0\\\\이전\",\"$$Hub$$\"]",
                        TRICKY_ACTION + " 둘째 대응이에요.", "DONE", null);
                // 2번: 이전 요약이 DONE 이던 행도 브리핑으로 바뀜 (이전 구조 값도 브리핑에서 옮긴 값으로)
                assertThat(readArticle(connection, 2L)).containsExactly("이전 요약만 있던 행의 한 줄이에요.", "무슨 일이에요.", null,
                        null, null, "이전 요약만 있던 행의 한 줄이에요. 무슨 일이에요.", null, null, "DONE", null);
                assertThat(readArticle(connection, 3L)).startsWith("실패했던 행의 한 줄이에요.").contains("DONE");
                assertThat(readArticle(connection, 4L))
                        .containsExactly(null, null, null, null, null, null, null, null, "NONE", null);
                assertThat(readArticle(connection, 5L)).startsWith("운영 서버가 먼저 쓴 한 줄이에요.", null, null, null, null,
                        "운영 서버가 먼저 쓴 무슨 일이에요.");
                try (ResultSet tableCount = connection.createStatement().executeQuery("select count(*) from article")) {
                    tableCount.next();
                    assertThat(tableCount.getInt(1)).isEqualTo(5);
                }
            }
        }
    }

    @Test
    void rollsBackEverythingWhenBriefColumnsDoNotExistYet(@TempDir Path temporaryDirectory) throws Exception {
        // 브리핑 열이 없는 DB (V0.8.0 배포 전)에 실수로 적용해도 아무것도 바뀌지 않아야 함
        Path applyFile = temporaryDirectory.resolve("apply.sql");
        Files.writeString(applyFile, SummaryApplyFileWriter.toApplySql(List.of(accepted(1L, briefOf("한 줄이에요.")))), UTF_8);

        try (PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")) {
            postgres.start();
            try (Connection connection = DriverManager.getConnection(
                    postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
                 Statement statement = connection.createStatement()) {
                statement.execute("create table article (id bigint primary key, source_text text, summary varchar(1500), "
                        + "summary_key_points text, summary_action_guide varchar(800), summary_status varchar(16) not null, "
                        + "updated_at timestamp not null default now())");
                statement.execute("insert into article (id, summary, summary_status) values (1, '이전 구조로 쓴 요약이에요.', 'DONE')");

                postgres.copyFileToContainer(MountableFile.forHostPath(applyFile), "/tmp/apply.sql");
                Container.ExecResult psqlResult = postgres.execInContainer(
                        "psql", "-U", postgres.getUsername(), "-d", postgres.getDatabaseName(), "-f", "/tmp/apply.sql");

                assertThat(psqlResult.getExitCode()).isNotZero();
                try (ResultSet row = statement.executeQuery("select summary, summary_status from article where id = 1")) {
                    row.next();
                    assertThat(row.getString(1)).isEqualTo("이전 구조로 쓴 요약이에요.");
                    assertThat(row.getString(2)).isEqualTo("DONE");
                }
            }
        }
    }

    private static ArticleBrief briefOf(String oneLine) {
        return new ArticleBrief(oneLine, "무슨 일이에요.", null, List.of(), List.of());
    }

    private static BackfillResult accepted(long id, ArticleBrief brief) {
        return new BackfillResult(id, "THE_HACKER_NEWS", BackfillResult.Status.ACCEPTED, brief, 1, 1, false, null);
    }

    // 적용 파일이 건드리는 열만 가진 article 표 (운영 스키마 V0.5.0·V0.8.0 의 같은 열·같은 길이)
    private static void createArticleTable(Connection connection) throws Exception {
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    create table article (
                        id                      bigint primary key,
                        source_text             text,
                        summary                 varchar(1500),
                        summary_key_points      text,
                        summary_action_guide    varchar(800),
                        summary_one_line        varchar(200),
                        summary_what_happened   varchar(600),
                        summary_why_it_matters  varchar(500),
                        summary_affected        text,
                        summary_actions         text,
                        summary_status          varchar(16) not null default 'NONE',
                        updated_at              timestamp   not null default (now() at time zone 'Asia/Seoul')
                    )""");
        }
    }

    private static void insertArticle(Connection connection, long id, String summaryStatus, String sourceText,
                                      String summary, String oneLine) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "insert into article (id, summary_status, source_text, summary, summary_one_line) values (?, ?, ?, ?, ?)")) {
            statement.setLong(1, id);
            statement.setString(2, summaryStatus);
            statement.setString(3, sourceText);
            statement.setString(4, summary);
            statement.setString(5, oneLine);
            statement.executeUpdate();
        }
    }

    // 한 줄 결론, 무슨 일, 왜 중요, 영향 대상, 대응, 이전 요약, 이전 핵심 정리, 이전 대응 안내, 상태, 요약 입력
    private static List<String> readArticle(Connection connection, long id) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "select summary_one_line, summary_what_happened, summary_why_it_matters, summary_affected, summary_actions, "
                        + "summary, summary_key_points, summary_action_guide, summary_status, source_text from article where id = ?")) {
            statement.setLong(1, id);
            try (ResultSet row = statement.executeQuery()) {
                row.next();
                List<String> values = new java.util.ArrayList<>();
                for (int columnIndex = 1; columnIndex <= 10; columnIndex++) {
                    values.add(row.getString(columnIndex));
                }
                return values;
            }
        }
    }
}
