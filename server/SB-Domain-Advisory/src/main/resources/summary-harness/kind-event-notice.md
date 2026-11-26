# This item: an event notice

The item announces a conference, seminar, webinar, training course, contest or call for papers.
sourceText is the organiser's or the news outlet's short description.

Where the facts go:
- oneLine: who hosts which event (and the date, when the input gives one)
- whatHappened: when and where it takes place (or that it is online) and what the sessions cover; do not announce the event a second time
- whyItMatters: what the organiser says attendees get or why the topic matters, kept as the organiser's claim; null when the input says neither
- affected: an empty array. An event has no affected targets.
- actions: how to take part, when the input says it (register, submit by a date, fee). Otherwise an empty array.

Watch out:
- Dates, times, places and prices are copied exactly. If the input gives no date or place, do not mention one.
- Promotional claims stay claims: "주최 측은 ~를 다룬다고 소개했어요". Do not present them as proven facts.
- A webinar teaser often opens with a threat story. The item is still an event:
  oneLine says that it is an event and who hosts it. Keep the background story to one clause of whatHappened.

Example input:
{"title":"Webinar: Closing the Gaps in Example Pipeline Security","region":"OVERSEAS","kind":"NEWS","sourceText":"Build pipelines have become a favourite target, and signed releases alone no longer stop tampering. In this webinar, engineers from Example Security walk through real pipeline compromises and show how to verify every build step. Register to save your seat."}

Good output:
{"oneLine":"Example Security가 빌드 파이프라인 보안 웨비나를 열어요.","whatHappened":"엔지니어들이 실제 파이프라인 침해 사례를 짚고 빌드 단계마다 검증하는 방법을 보여 준다고 소개했어요.","whyItMatters":"주최 측은 서명한 릴리스만으로는 변조를 막기 어려워졌다고 설명해요.","affected":[],"actions":["참석하려면 미리 등록하세요."]}

Bad output and why:
- whatHappened "웨비나는 다음 주에 열려요." The input gives no date.
- whyItMatters "서명한 릴리스로는 변조를 막을 수 없어요." The organiser's pitch, stated as a fact and made stronger than the input.
- affected [{"target":"Example Security","detail":null}] The host of an event is not an affected target.
- oneLine "빌드 파이프라인이 공격자의 주요 표적이 됐어요." The background story instead of the event.
- oneLine "Example Security, 빌드 파이프라인 보안 웨비나 개최" A newspaper headline, not a 해요체 sentence.
