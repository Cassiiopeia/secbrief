import { type AlertCategory, type PushDeviceRegistration, toAlertCategories } from '../models/alert-category';
import { putApiData } from './secbrief-api-client';

// 서버 응답: 저장된 알림 종류
interface PushDeviceApiResponse {
  enabledAlertCategories: string[];
}

/** 푸시 기기 등록·갱신: PUT /api/v1/push-devices > 서버에 저장된 알림 종류 */
export async function savePushDevice(registration: PushDeviceRegistration): Promise<AlertCategory[]> {
  const response = await putApiData<PushDeviceApiResponse>('/api/v1/push-devices', registration);
  return toAlertCategories(response.enabledAlertCategories);
}
