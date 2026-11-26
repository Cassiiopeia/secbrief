package kr.suhsaechan.secbrief.advisory.ai;

/**
 * 관리자 화면용 제공자 1개: 코드, 호출 가능 여부(키·주소 설정됨), 우선순위 (1부터, 목록에 없으면 0)
 */
public record AiProviderView(String providerCode, boolean isAvailable, int priority) {

    public boolean isInPriority() {
        return priority > 0;
    }
}
