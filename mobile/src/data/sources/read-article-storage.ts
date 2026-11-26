import AsyncStorage from '@react-native-async-storage/async-storage';

// 기기 저장소 키: 읽은 기사 ID (JSON 배열, 최근에 읽은 것이 뒤. 기기에만 저장하고 서버로 보내지 않음)
const READ_ARTICLE_IDS_STORAGE_KEY = 'secbrief.readArticleIds';

/** 기기에 저장된 읽은 기사 ID (없음·읽기 실패·깨진 값 > 빈 목록) */
export async function readReadArticleIds(): Promise<string[]> {
  try {
    const storedText = await AsyncStorage.getItem(READ_ARTICLE_IDS_STORAGE_KEY);
    const storedValue: unknown = storedText === null ? [] : JSON.parse(storedText);
    return Array.isArray(storedValue) ? storedValue.filter((id): id is string => typeof id === 'string') : [];
  } catch {
    return [];
  }
}

/** 읽은 기사 ID 저장 */
export async function saveReadArticleIds(readArticleIds: string[]): Promise<void> {
  await AsyncStorage.setItem(READ_ARTICLE_IDS_STORAGE_KEY, JSON.stringify(readArticleIds));
}
