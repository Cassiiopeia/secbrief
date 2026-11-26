# This item: a notice from the Korean national security agency (KISA)

sourceText is Korean. It may be a security notice, a vulnerability notice, a report or guide announcement,
or an alert level change.

Where the facts go:
- whatHappened: which product, service or kind of organisation the notice is about and what the notice says
  (for a report or guide: what it contains; for an alert level change: the old level and the new level in the agency's own words, and when it takes effect)
- whyItMatters: the risk the notice states; null when it states none
- affected: each product as target, its affected versions as detail, with the same digits as the input
- actions: the agency's recommended actions, one per item, including the fixed version when the input gives one

Watch out:
- The input is already Korean, so copying is the main risk. Do not reuse any stretch longer than about 20 characters.
  Rebuild every sentence: change the subject, the order and the verbs.
- Official notices use formal written Korean ("~하시기 바랍니다", "~을 권고함"). Rewrite into 해요체.
- Copy version numbers exactly, but say the action in your own words.

Example input:
{"title":"Example 제품 보안 업데이트 권고","region":"DOMESTIC","kind":"ADVISORY","sourceText":"Example社는 자사 제품에서 발생하는 취약점을 해결한 보안 업데이트를 발표하였음. 영향받는 버전을 사용 중인 이용자는 해결 버전으로 업데이트할 것을 권고함. 영향받는 제품: Example Server 5.0.0 이상 5.2.3 미만. 해결 버전: 5.2.3."}

Good output:
{"oneLine":"Example Server 보안 업데이트가 나왔어요.","whatHappened":"Example이 자사 제품에서 생기는 취약점을 고쳐 새 버전을 발표했어요.","whyItMatters":null,"affected":[{"target":"Example Server","detail":"5.0.0 이상 5.2.3 미만"}],"actions":["영향받는 버전을 쓰고 있다면 5.2.3으로 올리세요."]}

Bad output and why:
- whatHappened "영향받는 버전을 사용 중인 이용자는 해결 버전으로 업데이트할 것을 권고해요." It reuses the input sentence and only changes the ending.
- affected [{"target":"Example Server","detail":"5.2.2 이하"}] The input never writes 5.2.2.
- whyItMatters "취약점을 방치하면 시스템이 위험해질 수 있어요." The notice does not state this risk.
- oneLine "Example社, 자사 제품 보안 업데이트 발표" A headline in the style of the notice, not a 해요체 sentence.
