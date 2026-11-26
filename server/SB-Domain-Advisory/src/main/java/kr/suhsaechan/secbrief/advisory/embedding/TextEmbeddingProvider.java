package kr.suhsaechan.secbrief.advisory.embedding;

import java.util.List;

/**
 * 문장 임베딩 규약: 문장 여러 건 > 벡터 여러 건 (요청과 같은 순서·같은 길이)
 * 구현체는 Ollama 1개 (Anthropic 은 임베딩 API 를 제공하지 않아 만들지 않음)
 */
public interface TextEmbeddingProvider {

    /** 임베딩 가능 여부 (꺼져 있거나 주소·모델이 없으면 false: 호출하지 않고 글자 일치 검색으로 동작) */
    boolean isAvailable();

    /** 임베딩 모델 이름 (저장한 벡터가 어느 모델 것인지 구분: 모델을 바꾸면 다시 만듦) */
    String getModelName();

    /**
     * 예외(CustomException(AI_API_ERROR))는 제공자 호출 실패·읽을 수 없는 응답 (문장은 메시지에 넣지 않음)
     */
    List<float[]> embed(List<String> texts);
}
