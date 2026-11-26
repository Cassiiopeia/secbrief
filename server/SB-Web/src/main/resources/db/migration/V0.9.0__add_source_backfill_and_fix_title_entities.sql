-- KISA 지난 글 채우기 표시 + 남은 제목 문자 참조 풀기 (#96)

-- 지난 글 1회 채우기를 마친 시각 (null: 아직 안 함, 채우기는 KISA 게시판 4곳만)
-- 이전 버전 서버는 이 열을 읽지 않음 (배포 전환 중 같이 떠 있어도 영향 없음)
alter table source add column backfill_completed_at timestamptz;

-- 제목의 HTML 문자 참조 풀기: V0.5.1 이 &#39; 만 풀어 0 이 붙은 &#039; 가 남음 (기사 800, KISA 경보단계)
-- 수집 단계는 이미 모든 문자 참조를 풂 (ArticleNormalizer), &amp; 는 맨 뒤에
update article
   set title = replace(replace(replace(replace(replace(replace(title,
               '&#039;', ''''), '&#39;', ''''), '&quot;', '"'), '&lt;', '<'), '&gt;', '>'), '&amp;', '&')
 where title like '%&#039;%' or title like '%&#39;%' or title like '%&quot;%'
    or title like '%&lt;%' or title like '%&gt;%' or title like '%&amp;%';
