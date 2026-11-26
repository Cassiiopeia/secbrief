# This item: a notice from the Korean national security agency (KISA)

sourceText is Korean. It may be a security notice, a vulnerability notice, a report or guide announcement,
or an alert level change.

Cover, as far as the input says:
- which product, service or kind of organisation the notice is about
- what the risk is, or what the report or guide contains
- affected versions and fixed versions, with the same digits as the input
- for an alert level change: the old level and the new level in the agency's own words, and when it takes effect

Watch out:
- The input is already Korean, so copying is the main risk. Do not reuse any stretch longer than about 20 characters.
  Rebuild every sentence: change the subject, the order and the verbs.
- Official notices use formal written Korean ("~하시기 바랍니다", "~을 권고함"). Rewrite into 해요체.
- actionGuide holds the agency's recommended actions only. Copy version numbers exactly, but say the action in your own words.

Example input:
{"title":"Example 제품 보안 업데이트 권고","region":"DOMESTIC","kind":"ADVISORY","sourceText":"Example社는 자사 제품에서 발생하는 취약점을 해결한 보안 업데이트를 발표하였음. 영향받는 버전을 사용 중인 이용자는 해결 버전으로 업데이트할 것을 권고함. 영향받는 제품: Example Server 5.0.0 이상 5.2.3 미만. 해결 버전: 5.2.3."}

Good output:
{"summary":"Example이 자사 제품의 취약점을 고친 보안 업데이트를 내놨어요. Example Server의 일부 버전이 대상이에요.","keyPoints":["Example Server 5.0.0 이상 5.2.3 미만이 영향을 받아요.","5.2.3에서 문제를 고쳤어요."],"actionGuide":"영향받는 버전을 쓰고 있다면 5.2.3으로 올리세요."}

Bad output and why:
- "영향받는 버전을 사용 중인 이용자는 해결 버전으로 업데이트할 것을 권고해요." It reuses the input sentence and only changes the ending.
- "5.2.2 이하 버전이 대상이에요." The input never writes 5.2.2.
