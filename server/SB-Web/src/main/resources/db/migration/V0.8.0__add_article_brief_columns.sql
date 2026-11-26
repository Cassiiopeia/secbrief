-- AI 요약을 브리핑 구조로 개편 (#67): 한 줄 결론 · 무슨 일 · 왜 중요 · 영향 대상 · 대응
-- 이전 구조 열(summary, summary_key_points, summary_action_guide)은 그대로 두고 브리핑에서 옮긴 값을 함께 저장
-- (summary = 한 줄 결론 + 무슨 일 + 왜 중요, summary_key_points = 영향 대상 줄, summary_action_guide = 대응을 이은 글)
-- (이전 앱 빌드, 배포 전환·되돌리기 중의 이전 서버 버전이 읽음). 기존 행은 새 열이 모두 null: 데이터 이전 없음

alter table article
    -- 한 줄 결론 (값이 있으면 브리핑 구조의 글, 이전 요약만 있는 행은 null)
    add column summary_one_line        varchar(200),
    -- 무슨 일 (1~2문장)
    add column summary_what_happened   varchar(600),
    -- 왜 중요 (입력에 영향이 적혀 있지 않으면 null)
    add column summary_why_it_matters  varchar(500),
    -- 영향 대상 (JSON 배열 [{target, detail}]: 대상 이름은 원문 글자 그대로, 조건의 숫자는 원문 숫자 그대로, 없으면 null)
    add column summary_affected        text,
    -- 대응 (JSON 문자열 배열: 원문에 적힌 조치만, 없으면 null)
    add column summary_actions         text;
