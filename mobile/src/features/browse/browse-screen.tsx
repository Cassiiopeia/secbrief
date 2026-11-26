import { useLocalSearchParams } from 'expo-router';

import { SbEmptyState, SbScreen, SbScreenHeader } from '@/design';

import { ArticleInfiniteList } from '../articles/article-infinite-list';
import { useArticlePages } from '../articles/use-article-pages';
import { usePullRefresh } from '../articles/use-pull-refresh';
import { goBackOrHome } from '../navigation/go-back-or-home';
import { type BrowseTarget, toBrowseTarget } from './browse-target';

const BACK_ACCESSIBILITY_LABEL = '뒤로';

/** 모아 보기 화면 (탭 밖): 주제·출처·키워드 한 묶음의 소식 목록. 모르는 묶음이면 안내만 */
export function BrowseScreen() {
  const { browseKey } = useLocalSearchParams<{ browseKey: string }>();
  const browseTarget = toBrowseTarget(browseKey ?? '');
  return (
    <SbScreen>
      <SbScreenHeader title={browseTarget?.title} backAccessibilityLabel={BACK_ACCESSIBILITY_LABEL} onBackPress={goBackOrHome} />
      {browseTarget === undefined ? (
        <SbEmptyState title="찾는 소식 묶음이 없어요" description="이전 화면에서 다시 골라 주세요." />
      ) : (
        <BrowseArticleList browseKey={browseKey ?? ''} browseTarget={browseTarget} />
      )}
    </SbScreen>
  );
}

/** 한 묶음의 무한 목록 (묶음 키별 캐시). 찾는 목록이라 조밀한 행으로만 보여 줌 */
function BrowseArticleList({ browseKey, browseTarget }: { browseKey: string; browseTarget: BrowseTarget }) {
  const articlePagesQuery = useArticlePages(['articles', 'browse', browseKey], browseTarget.filter);
  const { isPullRefreshing, onPullRefresh } = usePullRefresh(articlePagesQuery.refetch);
  return (
    <ArticleInfiniteList
      articlePagesQuery={articlePagesQuery}
      listPurpose="lookup"
      skeletonVariant="articleRows"
      emptyTitle="아직 모은 소식이 없어요"
      emptyDescription={`${browseTarget.title} 소식이 올라오면 여기에 보여 드릴게요.`}
      isPullRefreshing={isPullRefreshing}
      onPullRefresh={onPullRefresh}
    />
  );
}
