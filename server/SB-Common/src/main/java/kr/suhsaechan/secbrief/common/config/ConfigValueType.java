package kr.suhsaechan.secbrief.common.config;

/**
 * 설정 값 종류: 검증 방식과 관리자 화면 입력 형태를 정함
 */
public enum ConfigValueType {
    // 정수 (최소·최대 범위)
    INT,
    // 소수 (최소·최대 범위, 소수점 6자리까지)
    DECIMAL,
    // true / false
    BOOLEAN,
    // 글자 (최대 길이)
    STRING,
    // 허용 값 중 하나
    ENUM,
    // 비밀값: 암호화해 저장, 화면·이력에는 값을 내보내지 않음
    SECRET
}
