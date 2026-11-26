-- 값 보정 (#33): v0.5.0 배포 전환 중 이전 버전 서버가 새 KISA 출처를 먼저 수집해 생긴 값 + 기능 이전 기사의 중요도 하한
-- 스키마 변경 없음

-- 제목의 HTML 문자 참조 풀기 (KISA 피드가 따옴표·기호를 문자 참조로 줌, &amp; 는 맨 뒤에)
update article
   set title = replace(replace(replace(replace(replace(title, '&#39;', ''''), '&quot;', '"'), '&lt;', '<'), '&gt;', '>'), '&amp;', '&')
 where title like '%&#39;%' or title like '%&quot;%' or title like '%&lt;%' or title like '%&gt;%' or title like '%&amp;%';

-- KISA 경보단계 상향 공지: 긴급 + 중요도 HEADLINE (지난 공지라 알림은 보내지 않게 발송 처리 표시)
update article a
   set is_urgent = true, importance = 'HEADLINE', urgent_notified_at = coalesce(a.urgent_notified_at, now())
  from source s
 where s.id = a.source_id and s.code = 'KISA_ALERT_LEVEL' and a.title like '%상향%';

-- 중요도 규칙 하한 NOTABLE: KEV 등재, KISA 긴급 공지 (ArticleImportancePolicy 와 같은 규칙)
update article
   set importance = 'NOTABLE'
 where importance = 'ROUTINE' and is_kev = true;

update article a
   set importance = 'NOTABLE'
  from source s
 where s.id = a.source_id and s.code like 'KISA%' and a.title like '%긴급%' and a.importance = 'ROUTINE';
