/**
 * 누름 래퍼 채우기 규칙: 안쪽 Animated.View 는 flexGrow 만 (flex 1 이면 Android 에서 0 높이로 접혀 탭바가 사라짐)
 */
import * as fs from 'fs';
import * as path from 'path';

const PRESSABLE_SOURCE_PATH = path.join(__dirname, '../../src/design/components/sb-pressable.tsx');

test('안쪽 Animated.View 는 flex 1 대신 fillContent(flexGrow) 를 쓴다', () => {
  const pressableSource = fs.readFileSync(PRESSABLE_SOURCE_PATH, 'utf-8');
  expect(pressableSource).toContain('<Animated.View style={[isFilling && styles.fillContent');
  expect(pressableSource).toMatch(/fillContent:\s*\{\s*flexGrow:\s*1\s*\}/);
});
