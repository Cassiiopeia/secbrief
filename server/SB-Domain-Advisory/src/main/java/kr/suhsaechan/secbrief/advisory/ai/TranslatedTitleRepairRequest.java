package kr.suhsaechan.secbrief.advisory.ai;

import java.util.List;

/**
 * 번역 제목 고쳐 쓰기 요청 1건: 원문 제목, 거부된 번역, 원문에 없는 한자 목록 (거부 사유로 그대로 알려 줌)
 */
public record TranslatedTitleRepairRequest(String title, String rejectedTranslatedTitle, List<String> hanjaNotInOriginal) {
}
