/**
 * 알림 막대 상태 (앱 전체 하나): 보여 줄 문구 1건. 새로 띄우면 앞 문구를 바꿔 끼움 (쌓지 않음)
 * 화면은 showSbToast 만 부르고, 그리기는 루트의 SbToastHost 한 곳
 */
export interface SbToastMessage {
  /** 같은 문구를 다시 띄워도 새로 보이게 하는 순번 */
  sequence: number;
  message: string;
}

type SbToastListener = (toastMessage: SbToastMessage | undefined) => void;

let currentToastMessage: SbToastMessage | undefined;
let lastToastSequence = 0;
const toastListeners = new Set<SbToastListener>();

/** 알림 막대 띄우기 (예: 저장했어요) */
export function showSbToast(message: string): void {
  lastToastSequence += 1;
  currentToastMessage = { sequence: lastToastSequence, message };
  toastListeners.forEach((listener) => listener(currentToastMessage));
}

/** 알림 막대 내리기 (그 순번이 아직 보이는 중일 때만: 뒤에 온 문구를 지우지 않게) */
export function dismissSbToast(sequence: number): void {
  if (currentToastMessage?.sequence !== sequence) return;
  currentToastMessage = undefined;
  toastListeners.forEach((listener) => listener(undefined));
}

/** 바뀔 때마다 알림 받기 > 해제 함수 */
export function subscribeSbToast(listener: SbToastListener): () => void {
  toastListeners.add(listener);
  return () => toastListeners.delete(listener);
}

export function readCurrentSbToast(): SbToastMessage | undefined {
  return currentToastMessage;
}
