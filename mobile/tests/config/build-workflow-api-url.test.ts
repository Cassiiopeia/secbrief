/**
 * 빌드 워크플로 3개가 같은 운영 API 주소로 앱을 묶는지 (주소 단일 원본 확인)
 */
import * as fs from 'fs';
import * as path from 'path';

const WORKFLOW_FILE_NAMES = [
  'PROJECT-REACT-NATIVE-EXPO-ANDROID-PLAYSTORE-CICD.yaml',
  'PROJECT-REACT-NATIVE-EXPO-ANDROID-TEST-APK.yaml',
  'PROJECT-REACT-NATIVE-EXPO-IOS-TESTFLIGHT.yaml',
];
const PRODUCTION_API_BASE_URL = 'https://api.secbrief.suhsaechan.kr';

test.each(WORKFLOW_FILE_NAMES)('%s 는 운영 API 주소로 빌드한다', (fileName) => {
  const workflowText = fs.readFileSync(path.join(__dirname, '../../../.github/workflows', fileName), 'utf-8');
  expect(workflowText).toContain(`EXPO_PUBLIC_API_BASE_URL: "${PRODUCTION_API_BASE_URL}"`);
});
