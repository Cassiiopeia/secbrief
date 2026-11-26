/**
 * 알림 막대 상태: 새 문구가 앞 문구를 바꿔 끼움, 지난 순번 내리기는 무시
 */
import { dismissSbToast, readCurrentSbToast, showSbToast, subscribeSbToast } from '../../src/design/components/sb-toast-store';

describe('알림 막대 상태', () => {
  test('띄우면 구독자에게 알리고, 같은 순번으로 내리면 비워짐', () => {
    const receivedMessages: (string | undefined)[] = [];
    const unsubscribe = subscribeSbToast((toastMessage) => receivedMessages.push(toastMessage?.message));
    showSbToast('알림 설정을 저장했어요');
    const shown = readCurrentSbToast();
    expect(shown?.message).toBe('알림 설정을 저장했어요');
    dismissSbToast(shown!.sequence);
    expect(readCurrentSbToast()).toBeUndefined();
    expect(receivedMessages).toEqual(['알림 설정을 저장했어요', undefined]);
    unsubscribe();
  });

  test('새 문구가 뜬 뒤 앞 문구의 내리기 예약은 새 문구를 지우지 않음', () => {
    showSbToast('첫 문구');
    const firstSequence = readCurrentSbToast()!.sequence;
    showSbToast('둘째 문구');
    dismissSbToast(firstSequence);
    expect(readCurrentSbToast()?.message).toBe('둘째 문구');
  });

  test('같은 문구를 다시 띄우면 순번이 달라 새로 보임', () => {
    showSbToast('방해 금지 시간을 바꿨어요');
    const firstSequence = readCurrentSbToast()!.sequence;
    showSbToast('방해 금지 시간을 바꿨어요');
    expect(readCurrentSbToast()!.sequence).toBeGreaterThan(firstSequence);
  });

  test('구독 해제 뒤에는 알리지 않음', () => {
    const listener = jest.fn();
    const unsubscribe = subscribeSbToast(listener);
    unsubscribe();
    showSbToast('알림 설정을 저장했어요');
    expect(listener).not.toHaveBeenCalled();
  });
});
