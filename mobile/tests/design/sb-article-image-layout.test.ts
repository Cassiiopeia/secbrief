/**
 * 소식 그림 규칙: 실제 대표 이미지만 보임 (없음·실패면 글만), 이미지 없으면 제목 줄 제한 없음, 기사 자리에 커버 그림 금지
 */
import * as fs from 'fs';
import * as path from 'path';

import { toSbArticleTitleMaximumLines, toSbShownArticleImageUrl } from '../../src/design/components/sb-article-image-layout';

const SOURCE_ROOT = path.join(__dirname, '../../src');
// 기사 한 건을 그림과 함께 보여 주는 부품·화면 (커버 그림 대신 글만 있는 배치로)
const ARTICLE_IMAGE_SOURCE_PATHS = [
  'design/components/sb-article-image.tsx',
  'design/components/sb-article-row.tsx',
  'design/components/sb-headline-card.tsx',
  'design/components/sb-stream-rail.tsx',
  'design/components/sb-urgent-tile.tsx',
  'features/article-detail/article-detail-screen.tsx',
];

test('보일 이미지: 주소가 없거나 불러오기 실패한 주소면 없음, 주소가 바뀌면 다시 보임', () => {
  expect(toSbShownArticleImageUrl(undefined, undefined)).toBeUndefined();
  expect(toSbShownArticleImageUrl('https://example.com/a.webp', undefined)).toBe('https://example.com/a.webp');
  expect(toSbShownArticleImageUrl('https://example.com/a.webp', 'https://example.com/a.webp')).toBeUndefined();
  expect(toSbShownArticleImageUrl('https://example.com/b.webp', 'https://example.com/a.webp')).toBe('https://example.com/b.webp');
});

test('제목 줄 수: 이미지가 있으면 배치 제한, 없으면 제한 없음 (제목 전체)', () => {
  expect(toSbArticleTitleMaximumLines(true, 3)).toBe(3);
  expect(toSbArticleTitleMaximumLines(false, 3)).toBeUndefined();
});

test.each(ARTICLE_IMAGE_SOURCE_PATHS)('%s: 기사 그림 자리에 커버 그림을 쓰지 않음', (relativePath) => {
  const source = fs.readFileSync(path.join(SOURCE_ROOT, relativePath), 'utf-8');
  expect(source).not.toMatch(/SbCoverArt|sb-cover-art|coverKind/);
});
