/**
 * 빌드 워크플로 3개가 같은 운영 API 주소로 앱을 묶는지 + 앱 링크 도메인(app.json)·서버 공유 주소가 같은 주소인지 (주소 단일 원본 확인)
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

describe('공유 링크 도메인은 운영 API 주소와 같다 (#94)', () => {
  const productionHost = new URL(PRODUCTION_API_BASE_URL).host;
  const expoConfig = JSON.parse(fs.readFileSync(path.join(__dirname, '../../app.json'), 'utf-8')).expo;

  test('iOS 유니버설 링크 도메인', () => {
    expect(expoConfig.ios.associatedDomains).toEqual([`applinks:${productionHost}`]);
  });
  test('Android 앱 링크: 확인(autoVerify) 켬, https + 운영 호스트 + /a/', () => {
    expect(expoConfig.android.intentFilters).toEqual([
      expect.objectContaining({ autoVerify: true, data: [{ scheme: 'https', host: productionHost, pathPrefix: '/a/' }] }),
    ]);
  });
  test('서버 공유 미리보기 공개 주소 (application.yml secbrief.share-link.public-base-url)', () => {
    const serverConfigText = fs.readFileSync(
      path.join(__dirname, '../../../server/SB-Web/src/main/resources/application.yml'),
      'utf-8',
    );
    expect(serverConfigText).toContain(`public-base-url: ${PRODUCTION_API_BASE_URL}`);
  });
});
