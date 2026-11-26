/**
 * 상태 그림 자산 규격: 목록 파일과 폴더 일치, WebP, 크기 상한, 불러오는 곳은 한 곳
 */
import * as fs from 'fs';
import * as path from 'path';

const ILLUSTRATION_FOLDER = path.join(__dirname, '../../assets/illustrations');
const SOURCES_FILE = path.join(__dirname, '../../src/design/components/sb-state-illustration-sources.ts');
const SOURCE_ROOT = path.join(__dirname, '../../src');
// 그림 한 장 최대 크기 (40KB: 8장 합쳐도 앱 크기에 거의 영향 없음)
const MAXIMUM_ILLUSTRATION_BYTES = 40 * 1024;

const requiredFileNames = [...fs.readFileSync(SOURCES_FILE, 'utf-8').matchAll(/require\('@\/assets\/illustrations\/([^']+)'\)/g)].map(
  (match) => match[1],
);

describe('상태 그림 자산', () => {
  test('목록에 적힌 그림과 폴더의 그림이 같다 (빠진 파일·남는 파일 없음)', () => {
    expect([...requiredFileNames].sort()).toEqual(fs.readdirSync(ILLUSTRATION_FOLDER).sort());
  });

  test.each(requiredFileNames)('%s 는 WebP 이고 40KB 이하다', (fileName) => {
    const fileBytes = fs.readFileSync(path.join(ILLUSTRATION_FOLDER, fileName));
    expect(fileBytes.subarray(0, 4).toString('ascii')).toBe('RIFF');
    expect(fileBytes.subarray(8, 12).toString('ascii')).toBe('WEBP');
    expect(fileBytes.length).toBeLessThanOrEqual(MAXIMUM_ILLUSTRATION_BYTES);
  });

  test('그림 폴더는 sb-state-illustration-sources.ts 한 곳에서만 불러온다', () => {
    const requiringFiles = fs
      .readdirSync(SOURCE_ROOT, { withFileTypes: true, recursive: true })
      .filter((entry) => entry.isFile() && /\.tsx?$/.test(entry.name))
      .map((entry) => path.join(entry.parentPath, entry.name))
      .filter((filePath) => fs.readFileSync(filePath, 'utf-8').includes('assets/illustrations/'));
    expect(requiringFiles).toEqual([SOURCES_FILE]);
  });
});
