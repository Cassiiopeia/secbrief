package kr.suhsaechan.secbrief.common.json;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 스프링 빈 밖(정적 파서·정책)에서 쓰는 JSON 변환기 단일 지점: 설정 없는 기본 ObjectMapper 1개 (스레드 안전)
 */
public final class JsonSupport {

    public static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private JsonSupport() {
    }
}
