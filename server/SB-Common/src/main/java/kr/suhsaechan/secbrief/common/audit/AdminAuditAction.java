package kr.suhsaechan.secbrief.common.audit;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 관리자 동작 종류 (admin_audit_log.action 값 = 이름): 화면 표시 이름, 대상 종류
 */
@Getter
@RequiredArgsConstructor
public enum AdminAuditAction {
    LOGIN_SUCCESS("로그인", "ADMIN"),
    LOGIN_LOCKED("로그인 잠금", "ADMIN"),
    PASSWORD_CHANGE("비밀번호 변경", "ADMIN"),
    CONFIG_UPDATE("설정 변경", "CONFIG"),
    CONFIG_RESET("설정 기본값 복원", "CONFIG"),
    AI_KEY_ADD("AI 키 추가", "AI_KEY"),
    AI_KEY_REMOVE("AI 키 삭제", "AI_KEY"),
    AI_KEY_MOVE("AI 키 우선순위 변경", "AI_KEY"),
    AI_KEY_TOGGLE("AI 키 켜기·끄기", "AI_KEY"),
    AI_KEY_REPLACE("AI 키 값 교체", "AI_KEY"),
    AI_KEY_RENAME("AI 키 별명 변경", "AI_KEY"),
    SOURCE_UPDATE("출처 설정 변경", "SOURCE"),
    NOTICE_CREATE("공지사항 올리기", "NOTICE"),
    NOTICE_UPDATE("공지사항 고치기", "NOTICE"),
    NOTICE_PIN("공지사항 고정 바꾸기", "NOTICE"),
    NOTICE_DELETE("공지사항 지우기", "NOTICE");

    private final String label;
    private final String targetType;
}
