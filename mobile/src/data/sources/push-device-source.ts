import { type PushDeviceRegistration, type PushDeviceSettings, type SwitchableAlertCategory, toAlertCategories } from '../models/alert-category';
import { type AlertDigestSettings, type AlertInterests, toAlertDigestSettings, toAlertInterests } from '../models/alert-interest';
import { type AlertEstimate, toAlertEstimate } from '../models/alert-estimate';
import { toQuietHours } from '../models/quiet-hours';
import { postApiData, putApiData } from './secbrief-api-client';

// 서버 응답: 저장된 알림 종류 + 방해 금지 시간·관심 기술·받는 방식 (이 필드들이 생기기 전 서버는 주지 않음)
interface PushDeviceApiResponse {
  enabledAlertCategories: string[];
  quietHours?: unknown;
  interests?: unknown;
  digest?: unknown;
}

/** 푸시 기기 등록·갱신: PUT /api/v1/push-devices > 서버에 저장된 설정 */
export async function savePushDevice(registration: PushDeviceRegistration): Promise<PushDeviceSettings> {
  const response = await putApiData<PushDeviceApiResponse>('/api/v1/push-devices', registration);
  return {
    enabledAlertCategories: toAlertCategories(response.enabledAlertCategories),
    quietHours: toQuietHours(response.quietHours),
    interests: toAlertInterests(response.interests),
    digest: toAlertDigestSettings(response.digest),
  };
}

/** 알림 횟수 추정에 보내는 설정 (기기 등록 없이 계산만) */
export interface AlertEstimateRequest {
  enabledAlertCategories: SwitchableAlertCategory[];
  interests: AlertInterests;
  digest: AlertDigestSettings;
  timeZone: string;
}

/** 어제 기준 알림 횟수: POST /api/v1/push-devices/alert-estimate */
export async function fetchAlertEstimate(request: AlertEstimateRequest): Promise<AlertEstimate | undefined> {
  return toAlertEstimate(await postApiData<unknown>('/api/v1/push-devices/alert-estimate', request));
}
