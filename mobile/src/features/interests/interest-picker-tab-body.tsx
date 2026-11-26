import { useState } from 'react';

import type { AlertInterests } from '@/data/models/alert-interest';
import { SbInlineNotice, SbPickChips, SbPickRow, SbSearchField, SbSectionHeader } from '@/design';

import type { TagCatalogIndex } from '../catalog/tag-catalog-view';
import {
  type InterestPickerTab,
  searchProducts,
  toPickedProductsOutsideGroups,
  toProductGroupSections,
} from './interest-view-state';

interface InterestPickerTabBodyProps {
  tab: InterestPickerTab;
  interests: AlertInterests;
  catalogIndex: TagCatalogIndex;
  onToggled: (tab: InterestPickerTab, code: string) => void;
}

// 제품 검색어 최대 길이
const PRODUCT_QUERY_MAXIMUM_LENGTH = 40;
// 분야·생태계 행 설명 (넓은 묶음이라 그중 주요만 알림)
const BROAD_TAG_ROW_DETAIL = '그중 주요 소식만 알려 드려요';

/**
 * 관심 기술 칸 본문 (#93 시안 A②): 제품은 검색 + 인기 묶음 칩(검색으로 고른 것은 위에 따로), 분야·생태계는 받기·안 받기 행
 * 목록·이름·묶음은 서버 카탈로그 그대로 (카탈로그를 못 받았으면 안내만)
 */
export function InterestPickerTabBody({ tab, interests, catalogIndex, onToggled }: InterestPickerTabBodyProps) {
  const [productQuery, setProductQuery] = useState('');
  if (tab === 'field' || tab === 'ecosystem') {
    const tags = tab === 'field' ? catalogIndex.fields : catalogIndex.ecosystems;
    const pickedCodes = tab === 'field' ? interests.fieldCodes : interests.ecosystemCodes;
    return (
      <>
        <SbInlineNotice
          message={
            tab === 'field'
              ? '분야는 넓어서 그중 주요 소식(널리 쓰는 제품, 악용 중, CVSS 9.0 이상이면서 AI 가 중요하다고 본 것)만 알려 드려요.'
              : '오픈소스 패키지를 쓰는 언어별로 골라요. GitHub 권고에 적힌 생태계 그대로 나눠요.'
          }
        />
        {tags.length === 0 && <SbInlineNotice message="목록을 불러오지 못했어요. 잠시 후 다시 열어 주세요." />}
        {tags.map((tag) => (
          <SbPickRow
            key={tag.code}
            title={tag.name}
            detail={BROAD_TAG_ROW_DETAIL}
            isPicked={pickedCodes.includes(tag.code)}
            pickedLabel="받기"
            unpickedLabel="안 받기"
            onToggled={() => onToggled(tab, tag.code)}
          />
        ))}
      </>
    );
  }

  const searchResults = searchProducts(catalogIndex, productQuery);
  const pickedOutsideGroups = toPickedProductsOutsideGroups(interests, catalogIndex);
  const onProductToggled = (code: string) => onToggled('product', code);
  return (
    <>
      <SbSearchField
        value={productQuery}
        placeholder="제품·회사·패키지 이름"
        accessibilityLabel="제품 이름 찾기"
        clearAccessibilityLabel="찾는 글자 지우기"
        maximumLength={PRODUCT_QUERY_MAXIMUM_LENGTH}
        onChangeText={setProductQuery}
      />
      {productQuery.trim().length > 0 ? (
        <>
          <SbSectionHeader title={`검색 결과 ${searchResults.length}개`} />
          {searchResults.length === 0 ? (
            <SbInlineNotice message={`"${productQuery.trim()}" 은 아직 목록에 없어요. 이름을 다르게 찾아보세요.`} />
          ) : (
            <SbPickChips options={searchResults} pickedValues={interests.productCodes} onToggled={onProductToggled} />
          )}
        </>
      ) : (
        <>
          {pickedOutsideGroups.length > 0 && (
            <>
              <SbSectionHeader title="검색으로 고른 것" />
              <SbPickChips options={pickedOutsideGroups} pickedValues={interests.productCodes} onToggled={onProductToggled} />
            </>
          )}
          {toProductGroupSections(catalogIndex).map((section) => (
            <InterestProductGroup key={section.groupCode} title={section.title}>
              <SbPickChips options={section.options} pickedValues={interests.productCodes} onToggled={onProductToggled} />
            </InterestProductGroup>
          ))}
        </>
      )}
    </>
  );
}

function InterestProductGroup({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <>
      <SbSectionHeader title={title} />
      {children}
    </>
  );
}
