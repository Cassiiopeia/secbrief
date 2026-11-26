package kr.suhsaechan.secbrief.advisory.embedding;

import java.util.ArrayList;
import java.util.List;
import kr.suhsaechan.secbrief.advisory.entity.Article;

/**
 * 기사 > 임베딩 입력 문장: 번역 제목(있으면) + 원문 제목 + 키워드 (줄바꿈으로 이음, 본문은 쓰지 않음)
 */
public final class ArticleEmbeddingText {

    private static final String LINE_SEPARATOR = "\n";
    private static final String KEYWORD_SEPARATOR = ", ";

    private ArticleEmbeddingText() {
    }

    public static String from(Article article) {
        List<String> lines = new ArrayList<>();
        if (article.getTranslatedTitle() != null) {
            lines.add(article.getTranslatedTitle());
        }
        lines.add(article.getTitle());
        List<String> keywords = article.getKeywords();
        if (!keywords.isEmpty()) {
            lines.add(String.join(KEYWORD_SEPARATOR, keywords));
        }
        return String.join(LINE_SEPARATOR, lines);
    }
}
