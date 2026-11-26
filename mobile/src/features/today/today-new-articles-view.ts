import type { Href } from 'expo-router';

import { toClockTime } from '@/core/format/briefing-date-formatter';
import type { NewArticleCounts, TagCount } from '@/data/models/new-article-counts';
import type { SbCountTileTone } from '@/design';

import type { TagCatalogIndex } from '../catalog/tag-catalog-view';
import { APP_ROUTES } from '../navigation/app-routes';
import { toNewsHref } from '../news/news-feed-filter';
import { type VulnerabilityFeedSelection, toVulnerabilityRouteParameters } from '../vulnerability/vulnerability-feed-selection';

/** 오늘 새 소식 타일 1개 표시 내용 */
export interface TodayNewArticleTileView {
  key: string;
  valueLabel: string;
  title: string;
  detail?: string;
  tone: SbCountTileTone;
  accessibilityLabel: string;
  href: Href;
}

/** 오늘 새 소식 띠 표시 내용: 머리 설명 + 타일들(건수 있는 것만) + 0건인 악용 중·KISA 한 줄 안내 (없으면 undefined) */
export interface TodayNewArticlesView {
  caption: string;
  tiles: TodayNewArticleTileView[];
  emptyNote?: string;
}

const SAMPLE_NAME_SEPARATOR = ', ';
const EXPLOITED_TITLE = '악용 중';
const KISA_TITLE = 'KISA 공지';
const EMPTY_TITLE_SEPARATOR = '·';

/**
 * 오늘 새 소식 건수 > 띠 (#93 시안 F): 건수 있는 타일만 (악용 중 > KISA 공지 > 분야 많은 순 > 생태계 많은 순 > 기사)
 * 0건인 악용 중·KISA 공지는 타일 대신 끝에 작은 한 줄 ("악용 중·KISA 공지 새 소식 없음"), 분야·생태계 이름은 카탈로그 (모르는 code 는 뺌)
 */
export function toTodayNewArticlesView(counts: NewArticleCounts, catalogIndex: TagCatalogIndex): TodayNewArticlesView {
  const tiles: TodayNewArticleTileView[] = [];
  const emptyTitles: string[] = [];
  if (counts.exploitedCount > 0) {
    tiles.push(toTile('exploited', counts.exploitedCount, EXPLOITED_TITLE, undefined, 'feature', toVulnerabilityHref({ segment: 'exploited' })));
  } else {
    emptyTitles.push(EXPLOITED_TITLE);
  }
  if (counts.kisaCount > 0) {
    tiles.push(toTile('kisa', counts.kisaCount, KISA_TITLE, undefined, 'neutral', toVulnerabilityHref({ sourceGroup: 'kisa', segment: 'all' })));
  } else {
    emptyTitles.push(KISA_TITLE);
  }
  tiles.push(
    ...toTagTiles(counts.fieldCounts, (code) => catalogIndex.findTag('FIELD', code)?.shortName, (code) =>
      toVulnerabilityHref({ sourceGroup: 'all', segment: 'all', fieldCode: code }),
    ),
    ...toTagTiles(counts.ecosystemCounts, (code) => catalogIndex.findTag('ECOSYSTEM', code)?.shortName, (code) =>
      toVulnerabilityHref({ sourceGroup: 'openSource', segment: 'all', ecosystemCode: code }),
    ),
  );
  if (counts.newsCount > 0) tiles.push(toTile('news', counts.newsCount, '기사', undefined, 'neutral', toNewsHref('all')));
  const collectedUntil = counts.lastCollectedAt === undefined ? '' : `${toClockTime(counts.lastCollectedAt)} 까지 `;
  return {
    caption: `${collectedUntil}모은 공개 소식 · 눌러서 봐요`,
    tiles,
    emptyNote: emptyTitles.length > 0 ? `${emptyTitles.join(EMPTY_TITLE_SEPARATOR)} 새 소식 없음` : undefined,
  };
}

function toTagTiles(tagCounts: TagCount[], toName: (code: string) => string | undefined, toHref: (code: string) => Href): TodayNewArticleTileView[] {
  return [...tagCounts]
    .filter((tagCount) => tagCount.count > 0)
    .sort((left, right) => right.count - left.count)
    .flatMap((tagCount) => {
      const name = toName(tagCount.code);
      if (name === undefined) return [];
      const detail = tagCount.sampleNames.length > 0 ? tagCount.sampleNames.join(SAMPLE_NAME_SEPARATOR) : undefined;
      return [toTile(tagCount.code, tagCount.count, name, detail, 'neutral', toHref(tagCount.code))];
    });
}

function toTile(key: string, count: number, title: string, detail: string | undefined, tone: SbCountTileTone, href: Href): TodayNewArticleTileView {
  return {
    key,
    valueLabel: `+${count}`,
    title,
    detail,
    tone,
    accessibilityLabel: [title, `오늘 ${count}건`, detail].filter((part) => part !== undefined).join(', '),
    href,
  };
}

// 취약점 탭을 그 분류·오늘로 여는 경로
function toVulnerabilityHref(selection: Partial<VulnerabilityFeedSelection>): Href {
  return { pathname: APP_ROUTES.vulnerability, params: toVulnerabilityRouteParameters({ ...selection, isTodayOnly: true }) };
}
