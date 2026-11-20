import type { TodayBriefing } from '../models/today-briefing';

// 로딩 화면이 보이도록 두는 예시 지연 (단위: 밀리초)
const SAMPLE_DELAY_MILLISECONDS = 600;

// 예시 기준일의 특정 시각 (시안의 날짜 2026-11-20)
const toSampleDateTime = (hour = 0, minute = 0): Date => new Date(2026, 10, 20, hour, minute);

/**
 * 오늘 브리핑 예시 소스: 서버 API 가 생기기 전까지 쓰는 고정 예시 데이터 (시안과 같은 내용)
 */
export async function fetchSampleTodayBriefing(): Promise<TodayBriefing> {
  await new Promise((resolve) => setTimeout(resolve, SAMPLE_DELAY_MILLISECONDS));
  return {
    date: toSampleDateTime(),
    readCount: 2,
    totalCount: 5,
    streakDays: 12,
    urgentItem: {
      id: 'sample-urgent-tomcat',
      title: 'Tomcat 원격 코드 실행 취약점, 실제 공격에 쓰이는 중',
      sourceName: 'KISA',
      publishedAt: toSampleDateTime(6, 30),
      topic: 'vulnerability',
      cvssScore: 9.8,
      productName: 'Tomcat',
      isMyStackMatch: true,
    },
    topTrend: { name: '랜섬웨어', weeklyCount: 41, changePercent: 38 },
    streamItems: [
      {
        id: 'sample-postgresql',
        title: 'PostgreSQL psql SQL 삽입 취약점, 14.16 이상으로 업데이트 권고',
        sourceName: 'KISA',
        publishedAt: toSampleDateTime(9, 12),
        topic: 'database',
        cvssScore: 8.1,
        productName: 'PostgreSQL',
        isMyStackMatch: true,
      },
      {
        id: 'sample-privacy-fine',
        title: '유출 통지 늦은 사업자에 과징금, 이번 주 처분 요약',
        sourceName: '개인정보위',
        publishedAt: toSampleDateTime(8, 40),
        topic: 'privacy',
        isMyStackMatch: false,
      },
      {
        id: 'sample-openssh',
        title: "OpenSSH 'regreSSHion', 인증 없이 root 권한 탈취",
        sourceName: '해외',
        publishedAt: toSampleDateTime(7, 55),
        topic: 'remoteAccess',
        cvssScore: 8.1,
        productName: 'OpenSSH',
        isMyStackMatch: false,
      },
    ],
  };
}
