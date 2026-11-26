package kr.suhsaechan.secbrief.member.push;

import java.util.List;
import java.util.Map;

/**
 * 푸시 발송 규약: 제공자(Expo 등)마다 구현체 1개
 * 발송 실패는 예외가 아니라 토큰별 결과로 돌려줌 (호출부가 재시도·기기 삭제를 정함)
 */
public interface PushSender {

    /** 발송 가능 여부 (꺼져 있으면 false: 호출하지 않고 건너뜀) */
    boolean isAvailable();

    /** 메시지마다 결과 1건 (입력과 같은 순서) */
    List<PushSendOutcome> send(List<PushMessage> pushMessages);

    /** 티켓 번호 > 접수증 (아직 접수증이 없는 티켓은 결과에 없음) */
    Map<String, PushReceipt> fetchReceipts(List<String> ticketIds);
}
