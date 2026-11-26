import { type PushDeviceRegistration, type PushDeviceSettings, toAlertCategories } from '../models/alert-category';
import { toQuietHours } from '../models/quiet-hours';
import { putApiData } from './secbrief-api-client';

// 서버 응답: 저장된 알림 종류 + 방해 금지 시간 (이 필드가 생기기 전 서버는 주지 않음)
interface PushDeviceApiResponse {
  enabledAlertCategories: string[];
  quietHours?: unknown;
}

/** 푸시 기기 등록·갱신: PUT /api/v1/push-devices > 서버에 저장된 알림 종류·방해 금지 시간 */
export async function savePushDevice(registration: PushDeviceRegistration): Promise<PushDeviceSettings> {
  const response = await putApiData<PushDeviceApiResponse>('/api/v1/push-devices', registration);
  return {
    enabledAlertCategories: toAlertCategories(response.enabledAlertCategories),
    quietHours: toQuietHours(response.quietHours),
  };
}
