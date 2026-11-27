# This item: a notice from the Korean national security agency (KISA)

sourceText is Korean. It may be a security notice, a vulnerability notice, a report or guide announcement,
or an alert level change.

Tables in sourceText are written one row per bracket: [header: value | header: value | ...].
Everything inside one bracket belongs to one table row. A product and a version are a pair only when they sit in the same bracket.

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
- Keep every product with the versions of its own row. Take target from the product cell of a row and detail from the
  affected-version cell of that same row. Never attach a version from another row or another product.
- When one product has several rows, either give each row its own item, or put that product once with only its own
  ranges in detail. When the table has more products than fit in affected, keep the first ones and leave the rest out.
- A fixed version in actions names the product it belongs to, as written in the same row. When many rows have different
  fixed versions, one action saying to update each product to the fixed version in the notice, without digits, is correct.
- Leave out scores, severity words and weakness IDs from a CVE table (CVSS, 심각도, CWE); the app shows them.
- When sourceText ends inside a bracket, that last row was cut off: ignore it.

Example input:
{"title":"Example 제품 보안 업데이트 권고","region":"DOMESTIC","kind":"ADVISORY","sourceText":"□ 개요 o Example社는 자사 제품에서 발생하는 취약점을 해결한 보안 업데이트 발표 o 영향받는 버전을 사용 중인 사용자는 해결 방안에 따라 최신 버전으로 업데이트 권고 □ 영향받는 제품 및 해결 방안 [제품명: Example Server | 영향받는 버전: 5.0.0 이상 5.2.3 미만 | 해결 버전: 5.2.3] [제품명: Example Agent | 영향받는 버전: 2.1.0 미만 | 해결 버전: 2.1.0]"}

Good output:
{"oneLine":"Example Server와 Example Agent 보안 업데이트가 나왔어요.","whatHappened":"Example이 자사 제품 두 가지에서 생기는 취약점을 고쳐 새 버전을 발표했어요.","whyItMatters":null,"affected":[{"target":"Example Server","detail":"5.0.0 이상 5.2.3 미만"},{"target":"Example Agent","detail":"2.1.0 미만"}],"actions":["Example Server는 5.2.3으로 올리세요.","Example Agent는 2.1.0으로 올리세요."]}

Bad output and why:
- whatHappened "영향받는 버전을 사용 중인 사용자는 해결 방안에 따라 최신 버전으로 업데이트를 권고해요." It reuses the input sentence and only changes the ending.
- affected [{"target":"Example Server","detail":"5.2.2 이하"}] The input never writes 5.2.2.
- affected [{"target":"Example Agent","detail":"5.0.0 이상 5.2.3 미만"}] That range is in the Example Server row, not the Example Agent row.
- actions ["Example Agent는 5.2.3으로 올리세요."] 5.2.3 is the fixed version of another product.
- whyItMatters "취약점을 방치하면 시스템이 위험해질 수 있어요." The notice does not state this risk.
- oneLine "Example社, 자사 제품 보안 업데이트 발표" A headline in the style of the notice, not a 해요체 sentence.
