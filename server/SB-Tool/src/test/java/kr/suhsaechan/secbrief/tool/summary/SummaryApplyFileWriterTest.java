package kr.suhsaechan.secbrief.tool.summary;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import kr.suhsaechan.secbrief.advisory.summary.ArticleSummary;
import kr.suhsaechan.secbrief.advisory.summary.ArticleSummaryPolicy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.containers.Container;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.MountableFile;

/**
 * 적용 파일: 값 이스케이프(따옴표·역슬래시·줄바꿈·탭·$$·끝 표시), 통과분만 실음, 한 트랜잭션
 * 실제 PostgreSQL 에 psql 로 적용해 글이 그대로 들어가는지, DONE 이 아닌 행만 바뀌는지, 갱신 건수가 나오는지 확인
 */
class SummaryApplyFileWriterTest {

    // SQL 로 읽히면 위험한 글: 작은따옴표·큰따옴표, 역슬래시, 줄바꿈·탭, 달러 인용, COPY 끝 표시·null 표시, 문장 끝내기
    private static final String TRICKY_SUMMARY = "It's \"요약\"이에요. 경로는 C:\\temp\\new 예요.\n둘째 줄\t탭 $$ $tag$ ; drop table article; -- \\. \\N";
    private static final String TRICKY_ACTION_GUIDE = "\\.\n'; update article set summary = 'x'; --";

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
    void writesOneDataLinePerAcceptedResultInsideOneTransaction() {
        String applySql = SummaryApplyFileWriter.toApplySql(List.of(
                accepted(7L, new ArticleSummary("요약이에요.", List.of("핵심 \"하나\"예요."), null)),
                new BackfillResult(8L, "NVD", BackfillResult.Status.REJECTED, null, 3, 6, false, "summary copies source text"),
                BackfillResult.withoutSummary(BackfillTestData.newsTarget(9L, null), BackfillResult.Status.NOT_RECOVERABLE)));

        assertThat(applySql).contains("요약 1건");
        assertThat(applySql).contains("\\set ON_ERROR_STOP on\nbegin;");
        assertThat(applySql).contains("7\t요약이에요.\t[\"핵심 \\\\\"하나\\\\\"예요.\"]\t\\N\n\\.\n");
        assertThat(applySql).contains("where a.id = b.article_id\n       and a.summary_status <> 'DONE'");
        assertThat(applySql).contains("summary_status = 'DONE'").contains("source_text = null");
        assertThat(applySql).doesNotContain("\n8\t").doesNotContain("\n9\t");
        assertThat(applySql.strip()).endsWith("commit;");
    }

    @Test
    void refusesSummaryLongerThanColumn() {
        BackfillResult tooLong = accepted(1L,
                new ArticleSummary("가".repeat(ArticleSummaryPolicy.MAXIMUM_SUMMARY_LENGTH + 1), List.of(), null));

        assertThatThrownBy(() -> SummaryApplyFileWriter.toApplySql(List.of(tooLong))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void appliesTrickyTextExactlyAndOnlyToRowsThatAreNotDone(@TempDir Path temporaryDirectory) throws Exception {
        String applySql = SummaryApplyFileWriter.toApplySql(List.of(
                accepted(1L, new ArticleSummary(TRICKY_SUMMARY, List.of("첫째 'key'\n\\point", "$$둘째$$"), TRICKY_ACTION_GUIDE)),
                accepted(2L, new ArticleSummary("이미 끝난 행에 쓰려는 요약이에요.", List.of(), null)),
                accepted(3L, new ArticleSummary("실패했던 행의 요약이에요.", List.of(), null)),
                accepted(99L, new ArticleSummary("없는 행의 요약이에요.", List.of(), null))));
        Path applyFile = temporaryDirectory.resolve("apply.sql");
        Files.writeString(applyFile, applySql, UTF_8);

        try (PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")) {
            postgres.start();
            try (Connection connection = DriverManager.getConnection(
                    postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())) {
                createArticleTable(connection);
                insertArticle(connection, 1L, "PENDING", "source one", null);
                insertArticle(connection, 2L, "DONE", null, "운영 서버가 먼저 쓴 요약이에요.");
                insertArticle(connection, 3L, "FAILED", null, null);
                insertArticle(connection, 4L, "NONE", null, null);

                postgres.copyFileToContainer(MountableFile.forHostPath(applyFile), "/tmp/apply.sql");
                Container.ExecResult psqlResult = postgres.execInContainer(
                        "psql", "-U", postgres.getUsername(), "-d", postgres.getDatabaseName(), "-f", "/tmp/apply.sql");

                // 실은 4건 중 DONE 이 아닌 2건만 갱신 (2번은 이미 DONE, 99번은 없는 행)
                assertThat(psqlResult.getExitCode()).isZero();
                assertThat(psqlResult.getStdout()).contains("loaded_row_count").contains("updated_row_count")
                        .containsPattern("\\s4 \\|\\s+2\\s");
                assertThat(readArticle(connection, 1L)).containsExactly(TRICKY_SUMMARY,
                        "[\"첫째 'key'\\n\\\\point\",\"$$둘째$$\"]", TRICKY_ACTION_GUIDE, "DONE", null);
                assertThat(readArticle(connection, 2L)).containsExactly("운영 서버가 먼저 쓴 요약이에요.", null, null, "DONE", null);
                assertThat(readArticle(connection, 3L)).containsExactly("실패했던 행의 요약이에요.", null, null, "DONE", null);
                assertThat(readArticle(connection, 4L)).containsExactly(null, null, null, "NONE", null);
                try (ResultSet tableCount = connection.createStatement().executeQuery("select count(*) from article")) {
                    tableCount.next();
                    assertThat(tableCount.getInt(1)).isEqualTo(4);
                }
            }
        }
    }

    private static BackfillResult accepted(long id, ArticleSummary summary) {
        return new BackfillResult(id, "THE_HACKER_NEWS", BackfillResult.Status.ACCEPTED, summary, 1, 1, false, null);
    }

    // 적용 파일이 건드리는 열만 가진 article 표 (운영 스키마 V0.3.0·V0.5.0 의 같은 열)
    private static void createArticleTable(Connection connection) throws Exception {
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    create table article (
                        id                    bigint primary key,
                        source_text           text,
                        summary               varchar(1500),
                        summary_key_points    text,
                        summary_action_guide  varchar(800),
                        summary_status        varchar(16) not null default 'NONE',
                        updated_at            timestamp   not null default (now() at time zone 'Asia/Seoul')
                    )""");
        }
    }

    private static void insertArticle(Connection connection, long id, String summaryStatus, String sourceText,
                                      String summary) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "insert into article (id, summary_status, source_text, summary) values (?, ?, ?, ?)")) {
            statement.setLong(1, id);
            statement.setString(2, summaryStatus);
            statement.setString(3, sourceText);
            statement.setString(4, summary);
            statement.executeUpdate();
        }
    }

    // summary, summary_key_points, summary_action_guide, summary_status, source_text
    private static List<String> readArticle(Connection connection, long id) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "select summary, summary_key_points, summary_action_guide, summary_status, source_text from article where id = ?")) {
            statement.setLong(1, id);
            try (ResultSet row = statement.executeQuery()) {
                row.next();
                return java.util.Arrays.asList(row.getString(1), row.getString(2), row.getString(3), row.getString(4), row.getString(5));
            }
        }
    }
}
