package kr.suhsaechan.secbrief.advisory.refetch;

import java.util.Map;
import java.util.Set;
import kr.suhsaechan.secbrief.advisory.constant.SourceCode;

/**
 * 요약 입력을 다시 받는 곳 (출처별 1개): 피드에서 밀려난 글, 피드에 요약문이 없는 글의 공개 원문 위치
 * 받은 글은 요약 입력으로만 쓰고 요약이 끝나면 지움 (본문을 싣거나 보관하지 않음)
 */
public enum SourceTextRefetchMethod {
    // NVD: CVE ID 1건 조회 API 의 공개 설명
    NVD_CVE_API,
    // Ubuntu: 권고 주소 + .json 의 요약·설명
    UBUNTU_NOTICE_JSON,
    // Red Hat: 권고 CSAF 파일의 주제·상세
    REDHAT_CSAF_JSON,
    // Debian: 보안 공지 메일 보관 페이지 > 안 되면 보안 추적 페이지 (패키지·CVE·고친 버전)
    DEBIAN_ANNOUNCEMENT_PAGE,
    // KISA 보호나라 4곳: 게시글 페이지의 본문 칸 (표는 행마다 머리글과 짝지은 글)
    KISA_BOARD_VIEW_PAGE,
    // 기사·공지 페이지 본문의 문단 (BleepingComputer, Node.js)
    ARTICLE_PAGE_BODY,
    // 기사 페이지의 게시자 요약(meta description)만: 데일리시큐 (저작권 고지에 AI 활용 금지, 본문은 읽지 않음)
    ARTICLE_PAGE_DESCRIPTION,
    // 다시 받지 않음: 피드·카탈로그로 충분 (KEV, GitHub 권고), 기사 페이지가 봇 확인으로 막힘 (The Hacker News 403)
    NOT_SUPPORTED;

    // 출처 코드 > 다시 받는 곳 (없는 출처는 NOT_SUPPORTED)
    private static final Map<String, SourceTextRefetchMethod> METHOD_BY_SOURCE_CODE = Map.ofEntries(
            Map.entry(SourceCode.NVD, NVD_CVE_API),
            Map.entry(SourceCode.UBUNTU_USN, UBUNTU_NOTICE_JSON),
            Map.entry(SourceCode.REDHAT_RHSA, REDHAT_CSAF_JSON),
            Map.entry(SourceCode.DEBIAN_DSA, DEBIAN_ANNOUNCEMENT_PAGE),
            Map.entry(SourceCode.KISA, KISA_BOARD_VIEW_PAGE),
            Map.entry(SourceCode.KISA_VULNERABILITY, KISA_BOARD_VIEW_PAGE),
            Map.entry(SourceCode.KISA_REPORT, KISA_BOARD_VIEW_PAGE),
            Map.entry(SourceCode.KISA_ALERT_LEVEL, KISA_BOARD_VIEW_PAGE),
            Map.entry(SourceCode.BLEEPING_COMPUTER, ARTICLE_PAGE_BODY),
            Map.entry(SourceCode.NODEJS_SECURITY, ARTICLE_PAGE_BODY),
            Map.entry(SourceCode.DAILYSECU, ARTICLE_PAGE_DESCRIPTION));

    public static SourceTextRefetchMethod of(String sourceCode) {
        return METHOD_BY_SOURCE_CODE.getOrDefault(sourceCode, NOT_SUPPORTED);
    }

    /** 다시 받을 수 있는 출처 코드 전체 (서버 작업의 대상 조건) */
    public static Set<String> toSupportedSourceCodes() {
        return METHOD_BY_SOURCE_CODE.keySet();
    }
}
