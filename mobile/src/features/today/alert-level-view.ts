import { toCompactDateLabel } from '@/core/format/briefing-date-formatter';
import { KISA_SOURCE_CODES } from '@/data/models/article-source-code';
import type { BriefingItem } from '@/data/models/briefing-item';

import { type ArticleRowView, toArticleRowView } from '../articles/article-row-view';

/** 오늘 탭 위쪽 KISA 경보단계 띠 표시 내용 */
export interface AlertLevelView {
  capsLabel: string;
  /** 원문 제목 그대로 (단계 값을 따로 지어내지 않음) */
  title: string;
  accessibilityLabel: string;
  /** 누르면 열 기사 (상세 화면이 먼저 그릴 표시 내용) */
  article: ArticleRowView;
}

const ALERT_LEVEL_CAPTION = 'KISA 경보단계';

/**
 * 소식들 > 경보단계 띠: 출처가 KISA 경보단계인 가장 새 소식 1건 (받은 순서 = 최신순)
 * 출처 코드가 없는 소식(이전 서버)은 경보단계인지 알 수 없어 띠를 만들지 않음
 */
export function toAlertLevelView(items: BriefingItem[], now: Date): AlertLevelView | undefined {
  const alertLevelItem = items.find((item) => item.sourceCode === KISA_SOURCE_CODES.alertLevel);
  if (alertLevelItem === undefined) return undefined;
  const capsLabel = `${ALERT_LEVEL_CAPTION} · ${toCompactDateLabel(alertLevelItem.publishedAt, now)}`;
  const article = toArticleRowView(alertLevelItem, now);
  return { capsLabel, title: article.title, accessibilityLabel: `${capsLabel}, ${article.title}, 자세히 보기`, article };
}
