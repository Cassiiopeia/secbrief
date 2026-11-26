# This item: an event notice

The item announces a conference, seminar, webinar, training course, contest or call for papers.
sourceText is the organiser's or the news outlet's short description.

Cover, as far as the input says:
- what the event is and who hosts it
- when and where it takes place, or that it is online
- what the sessions cover and who it is for
- how to take part, when the input says it (registration, submission deadline, fee)

Watch out:
- Dates, times, places and prices are copied exactly. If the input gives no date or place, do not mention one.
- Promotional claims stay claims: "주최 측은 ~를 다룬다고 소개했어요". Do not present them as proven facts.
- A webinar teaser often opens with a threat story. The item is still an event:
  say first that it is an event (who, when, where), then what the sessions present or reveal. Keep the background story to one clause.
- actionGuide is for taking part (register, submit by a date) and only when the input says how. Otherwise null.

Example input:
{"title":"Webinar: Closing the Gaps in Example Pipeline Security","region":"OVERSEAS","kind":"NEWS","sourceText":"Build pipelines have become a favourite target, and signed releases alone no longer stop tampering. In this webinar, engineers from Example Security walk through real pipeline compromises and show how to verify every build step. Register to save your seat."}

Good output:
{"summary":"Example Security가 빌드 파이프라인 보안을 다루는 웨비나를 열어요. 엔지니어들이 실제 파이프라인 침해 사례를 짚고, 빌드 단계마다 검증하는 방법을 보여 준다고 소개했어요.","keyPoints":["주최 측은 서명한 릴리스만으로는 변조를 막기 어렵다고 설명해요."],"actionGuide":"참석하려면 미리 등록하세요."}

Bad output and why:
- "웨비나는 다음 주에 열려요." The input gives no date.
- "서명한 릴리스로는 변조를 막을 수 없어요." This is the organiser's pitch, stated as a fact and made stronger than the input.
