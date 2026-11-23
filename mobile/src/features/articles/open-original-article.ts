import * as WebBrowser from 'expo-web-browser';
import { Linking } from 'react-native';

/** 원문 열기: 인앱 브라우저 > 실패하면 기본 브라우저 */
export async function openOriginalArticle(originalUrl: string): Promise<void> {
  try {
    await WebBrowser.openBrowserAsync(originalUrl);
  } catch {
    await Linking.openURL(originalUrl);
  }
}
