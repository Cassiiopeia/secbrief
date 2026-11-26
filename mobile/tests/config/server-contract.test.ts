/**
 * 서버 <-> 앱 값 계약: 서버 Java 원본(enum·상수)을 글자로 읽어 앱 상수와 비교
 * 대상 9개: 알림 종류, 알림함 종류, 대응 모아보기 구역·묶인 이유, 분류 태그 종류, 관심 소식 받는 방식, 출처 코드, 푸시 data 값, 방해 금지 기본값, 경보단계 값(단계·방향·해외 지표)
 * 분류 태그 값(분야·생태계·제품)은 서버 DB 라 여기서 비교하지 않음 (API 모양은 api-shape-contract.test)
 */
import * as fs from 'fs';
import * as path from 'path';

import {
  ALERT_LEVEL_DIRECTION_BY_API_NAME,
  CYBER_CRISIS_LEVEL_BY_API_NAME,
  INTERNATIONAL_INDICATOR_CODE_BY_API_NAME,
} from '@/data/mappers/alert-level-mapper';
import { ALERT_CATEGORIES } from '@/data/models/alert-category';
import { ALERT_DELIVERY_MODES } from '@/data/models/alert-interest';
import { CYBER_CRISIS_LEVELS, INTERNATIONAL_INDICATOR_CODES } from '@/data/models/alert-level-dashboard';
import { APP_NOTIFICATION_KINDS } from '@/data/models/app-notification';
import {
  CISA_KEV_SOURCE_CODE,
  KISA_SOURCE_CODES,
  LINUX_SOURCE_CODES,
  OPEN_SOURCE_SOURCE_CODES,
  RUNTIME_SOURCE_CODES,
} from '@/data/models/article-source-code';
import { CATALOG_TAG_KINDS } from '@/data/models/catalog-tag';
import { DEFAULT_QUIET_HOURS } from '@/data/models/quiet-hours';
import { RESPONSE_GUIDE_MATCH_REASONS, RESPONSE_GUIDE_SECTIONS } from '@/data/models/response-guide';

const SERVER_ROOT = path.join(__dirname, '../../../server');
const MEMBER_PACKAGE = 'SB-Domain-Member/src/main/java/kr/suhsaechan/secbrief/member';
const ADVISORY_PACKAGE = 'SB-Domain-Advisory/src/main/java/kr/suhsaechan/secbrief/advisory';
const APPLICATION_PACKAGE = 'SB-Application/src/main/java/kr/suhsaechan/secbrief/application';

const readServerSource = (relativePath: string): string => fs.readFileSync(path.join(SERVER_ROOT, relativePath), 'utf-8');

/** Java enum 파일 > 값 이름 목록 (선언 순서, 주석 줄 제외) */
function readJavaEnumNames(relativePath: string): string[] {
  const enumBody = /enum\s+\w+\s*\{([^;}]*)/.exec(readServerSource(relativePath))?.[1] ?? '';
  return enumBody
    .split('\n')
    .filter((line) => !line.trim().startsWith('//'))
    .join('\n')
    .split(',')
    .map((name) => name.trim())
    .filter((name) => name.length > 0);
}

/** Java 상수 파일 > 문자열 상수 값 (이름 > 값) */
function readJavaStringConstants(relativePath: string): Record<string, string> {
  const constants: Record<string, string> = {};
  for (const match of readServerSource(relativePath).matchAll(/static final String (\w+) = "([^"]*)";/g)) {
    constants[match[1]] = match[2];
  }
  return constants;
}

describe('서버 enum <-> 앱 상수 (같은 순서)', () => {
  test('알림 종류 AlertCategory', () => {
    expect([...ALERT_CATEGORIES]).toEqual(readJavaEnumNames(`${MEMBER_PACKAGE}/constant/AlertCategory.java`));
  });

  test('알림함 종류 NotificationKind', () => {
    expect([...APP_NOTIFICATION_KINDS]).toEqual(readJavaEnumNames(`${MEMBER_PACKAGE}/constant/NotificationKind.java`));
  });

  test('분류 태그 종류 CatalogTagKind (#93)', () => {
    expect([...CATALOG_TAG_KINDS]).toEqual(readJavaEnumNames(`${ADVISORY_PACKAGE}/constant/CatalogTagKind.java`));
  });

  test('관심 소식 받는 방식 AlertDeliveryMode (#93)', () => {
    expect([...ALERT_DELIVERY_MODES]).toEqual(readJavaEnumNames(`${MEMBER_PACKAGE}/constant/AlertDeliveryMode.java`));
  });

  test('대응 모아보기 구역 ResponseGuideSection', () => {
    expect([...RESPONSE_GUIDE_SECTIONS]).toEqual(readJavaEnumNames(`${ADVISORY_PACKAGE}/constant/ResponseGuideSection.java`));
  });

  test('대응 모아보기 묶인 이유 ResponseGuideMatchReason', () => {
    expect([...RESPONSE_GUIDE_MATCH_REASONS]).toEqual(
      readJavaEnumNames(`${ADVISORY_PACKAGE}/constant/ResponseGuideMatchReason.java`),
    );
  });
});

test('앱이 구분해 쓰는 출처 코드는 모두 서버 SourceCode 에 있다', () => {
  const serverSourceCodes = Object.values(readJavaStringConstants(`${ADVISORY_PACKAGE}/constant/SourceCode.java`));
  const appSourceCodes = [
    CISA_KEV_SOURCE_CODE,
    ...Object.values(KISA_SOURCE_CODES),
    ...Object.values(OPEN_SOURCE_SOURCE_CODES),
    ...Object.values(LINUX_SOURCE_CODES),
    ...Object.values(RUNTIME_SOURCE_CODES),
  ];
  expect(appSourceCodes.filter((sourceCode) => !serverSourceCodes.includes(sourceCode))).toEqual([]);
});

test('푸시 data 값: 서버 NotificationPushData <-> 앱 notification-target-route', () => {
  const serverPushData = readJavaStringConstants(`${APPLICATION_PACKAGE}/notification/NotificationPushData.java`);
  const appRouteSource = fs.readFileSync(
    path.join(__dirname, '../../src/features/notifications/notification-target-route.ts'),
    'utf-8',
  );
  // 앱 파일의 모듈 상수 값 (export 하지 않아 글자로 읽음)
  const readAppConstant = (name: string): string | undefined => new RegExp(`const ${name} = '([^']*)'`).exec(appRouteSource)?.[1];

  expect(readAppConstant('QUIET_HOURS_DIGEST_NOTIFICATION_KIND')).toBe(serverPushData.QUIET_HOURS_DIGEST_KIND);
  expect(readAppConstant('VULNERABILITY_TARGET_TAB')).toBe(serverPushData.TARGET_TAB_VULNERABILITY);
  // 키 이름 4개: 앱이 data 에서 꺼내는 이름
  for (const keyConstant of ['NOTIFICATION_ID_KEY', 'NOTIFICATION_KIND_KEY', 'ARTICLE_ID_KEY', 'TARGET_TAB_KEY']) {
    expect(appRouteSource).toContain(serverPushData[keyConstant]);
  }
});

test('방해 금지 기본값: 서버 QuietHours <-> 앱 DEFAULT_QUIET_HOURS', () => {
  const quietHoursSource = readServerSource(`${MEMBER_PACKAGE}/entity/QuietHours.java`);
  const toTimeOfDay = (constantName: string): string | undefined => {
    const match = new RegExp(`${constantName} = LocalTime\\.of\\((\\d+), (\\d+)\\)`).exec(quietHoursSource);
    return match ? `${match[1].padStart(2, '0')}:${match[2].padStart(2, '0')}` : undefined;
  };
  expect(DEFAULT_QUIET_HOURS.startTime).toBe(toTimeOfDay('DEFAULT_START_TIME'));
  expect(DEFAULT_QUIET_HOURS.endTime).toBe(toTimeOfDay('DEFAULT_END_TIME'));
  expect(DEFAULT_QUIET_HOURS.timeZone).toBe(readJavaStringConstants(`${MEMBER_PACKAGE}/entity/QuietHours.java`).DEFAULT_TIME_ZONE);
});

test('경보단계 값: 서버 CyberCrisisLevel·AlertLevelChangeDirection·InternationalAlertIndicator <-> 앱 매퍼 (같은 순서)', () => {
  // 생성자 인자가 있는 enum: 줄 앞의 대문자 이름( 만 읽음
  const readEnumNamesWithArguments = (relativePath: string): string[] =>
    [...readServerSource(relativePath).matchAll(/^\s{4}([A-Z_]+)\(/gm)].map((match) => match[1]);

  const serverLevels = readEnumNamesWithArguments(`${ADVISORY_PACKAGE}/constant/CyberCrisisLevel.java`);
  expect(Object.keys(CYBER_CRISIS_LEVEL_BY_API_NAME)).toEqual(serverLevels);
  expect(serverLevels.map((name) => CYBER_CRISIS_LEVEL_BY_API_NAME[name])).toEqual([...CYBER_CRISIS_LEVELS]);
  expect(Object.keys(ALERT_LEVEL_DIRECTION_BY_API_NAME)).toEqual(
    readJavaEnumNames(`${ADVISORY_PACKAGE}/constant/AlertLevelChangeDirection.java`),
  );
  const serverIndicators = readEnumNamesWithArguments(`${ADVISORY_PACKAGE}/indicator/InternationalAlertIndicator.java`);
  expect(serverIndicators.map((name) => INTERNATIONAL_INDICATOR_CODE_BY_API_NAME[name])).toEqual([...INTERNATIONAL_INDICATOR_CODES]);
});
