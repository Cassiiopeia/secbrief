package kr.suhsaechan.secbrief.common.config;

import lombok.Getter;

/**
 * 운영 중 바꾸는 설정의 단일 원본 (system_config.config_key = 이름): 묶음, 표시 이름, 설명, 값 규칙, 기본값, 운영 설정 키
 * 값을 읽는 순서: 설정 저장소(DB) > 운영 설정(propertyName 이 있고 값이 있을 때) > defaultValue
 */
@Getter
public enum ConfigKey {

    AI_PROVIDER_PRIORITY(ConfigGroup.AI_PROVIDER, "AI 제공자 우선순위",
            "앞에 적은 제공자부터 쓰고, 호출이 실패하면 다음 제공자로 넘어가요.",
            ConfigValueRule.text(100), "anthropic", "secbrief.ai.provider", true),
    // 저장 값: [{"id","nickname","apiKey","isEnabled"}] JSON 배열 (배열 순서 = 우선순위)
    ANTHROPIC_API_KEYS(ConfigGroup.AI_PROVIDER, "Anthropic API 키 목록",
            "키 여러 개를 우선순위대로 쓰고, 키에 문제가 생기면 다음 키로 넘어가요.",
            ConfigValueRule.secret(8000), "", null, true),

    AI_TITLE_BATCH_SIZE(ConfigGroup.AI_PROCESSING, "제목 분석 한 틱 건수",
            "AI 틱 한 번에 제목을 분석할 소식 수예요.",
            ConfigValueRule.integerBetween(1, 200), "20", "secbrief.ai.batch-size", false),
    AI_REQUEST_BATCH_SIZE(ConfigGroup.AI_PROCESSING, "제목 분석 호출당 건수",
            "AI 호출 한 번에 묶어 보낼 소식 수예요.",
            ConfigValueRule.integerBetween(1, 50), "10", "secbrief.ai.request-batch-size", false),
    SUMMARY_BATCH_SIZE(ConfigGroup.AI_PROCESSING, "요약 한 틱 건수",
            "요약 틱 한 번에 요약할 소식 수예요 (호출 한 번에 1건).",
            ConfigValueRule.integerBetween(1, 100), "10", "secbrief.summary.batch-size", false),
    SUMMARY_DAILY_LIMIT(ConfigGroup.AI_PROCESSING, "요약 하루 상한",
            "하루(서울 날짜)에 요약할 수 있는 최대 건수예요. 넘으면 남은 소식은 다음 날 요약해요.",
            ConfigValueRule.integerBetween(0, 100000), "300", "secbrief.summary.daily-limit", false),

    // -1: 예산 확인 끔
    AI_DAILY_BUDGET_USD(ConfigGroup.AI_COST, "하루 AI 예산 (USD)",
            "오늘(서울 날짜) 추정 비용이 이 값에 닿으면 AI 처리를 쉬어요. -1 이면 예산을 확인하지 않아요.",
            ConfigValueRule.decimalBetween("-1", "100000"), "-1", null, false),
    PRICE_ANTHROPIC_INPUT_PER_1M(ConfigGroup.AI_COST, "Anthropic 입력 단가 (USD / 100만 토큰)",
            "호출 기록의 추정 비용을 계산할 때 써요.",
            ConfigValueRule.decimalBetween("0", "1000"), "0.10", null, false),
    PRICE_ANTHROPIC_OUTPUT_PER_1M(ConfigGroup.AI_COST, "Anthropic 출력 단가 (USD / 100만 토큰)",
            "호출 기록의 추정 비용을 계산할 때 써요.",
            ConfigValueRule.decimalBetween("0", "1000"), "0.50", null, false),

    URGENT_ALERT_ENABLED(ConfigGroup.ALERT, "긴급 알림 보내기",
            "끄면 긴급 알림 발송을 쉬어요. 다시 켜면 그동안 쌓인 긴급 소식을 묶어서 알려요.",
            ConfigValueRule.toggle(), "true", null, false);

    private final ConfigGroup group;
    // 화면 표시 이름·설명 (해요체)
    private final String label;
    private final String description;
    private final ConfigValueRule valueRule;
    // 설정 저장소·운영 설정에 값이 없을 때 쓰는 값 (SECRET 은 빈 값 = 없음)
    private final String defaultValue;
    // 같은 값을 받는 운영 설정 키 (application.yml, 없으면 null)
    private final String propertyName;
    // 전용 화면(AI 키)에서만 바꾸는 설정: 일반 설정 화면은 읽기 전용으로 보여 줌
    private final boolean isManagedOnDedicatedScreen;

    ConfigKey(ConfigGroup group, String label, String description, ConfigValueRule valueRule, String defaultValue,
              String propertyName, boolean isManagedOnDedicatedScreen) {
        this.group = group;
        this.label = label;
        this.description = description;
        this.valueRule = valueRule;
        this.defaultValue = defaultValue;
        this.propertyName = propertyName;
        this.isManagedOnDedicatedScreen = isManagedOnDedicatedScreen;
    }

    public ConfigValueType getValueType() {
        return valueRule.valueType();
    }

    public boolean isSecret() {
        return valueRule.valueType() == ConfigValueType.SECRET;
    }
}
