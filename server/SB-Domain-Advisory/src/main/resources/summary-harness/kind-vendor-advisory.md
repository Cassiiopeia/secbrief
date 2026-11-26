# This item: an official advisory from an operating system or runtime vendor

sourceText is a short, formulaic notice: which package got a security update, for which product release,
and sometimes a severity rating or a list of the fixed flaws.

Cover, as far as the input says:
- which package or component is updated and for which product or release, exactly as written
- the vendor's severity rating when the input gives one, in the vendor's own word ("Important", "Critical")
- what the fixed flaws allow, when the input describes them

Watch out:
- Skip boilerplate that tells nothing about this item, such as where CVSS scores can be found
  or how the rating scale works.
- Formulaic input holds few facts. One or two sentences are enough, and keyPoints may be an empty array.
  Do not add who should care or what to check.
- When the input lists several fixed flaws, group them by what they allow instead of walking through every CVE.
  Put CVE IDs and release names in keyPoints.
- Long product names must stay exact. When there are several, name each one in its own short Korean clause
  instead of copying the input's list sentence.
- When the input says an update or a fixed package is available, actionGuide may tell the reader to apply that update,
  naming the package and the product as the input does. If the input offers no update or mitigation, actionGuide is null.

Example input:
{"title":"EXSA-0000:0001: Important: example-httpd security update","region":"OVERSEAS","kind":"ADVISORY","sourceText":"An update for example-httpd is now available for Example Linux 9 and Example Linux 9 Extended Support. Example Product Security has rated this update as having a security impact of Important. A severity score, which gives a detailed rating, is available for each vulnerability from the links in the References section."}

Good output:
{"summary":"Example Linux 9와 같은 제품의 Extended Support 판에 example-httpd 보안 업데이트가 나왔어요. Example Product Security는 이 업데이트의 보안 영향을 Important로 평가했어요.","keyPoints":[],"actionGuide":"Example Linux 9를 쓰고 있다면 example-httpd 업데이트를 적용하세요."}

Bad output and why:
- "원격 코드 실행 취약점을 고쳤어요." The input does not say which flaw was fixed.
- "CVSS 점수는 참고 자료에서 확인할 수 있어요." Boilerplate, not a fact about this item.
- "Example Linux 9 서버 운영자가 확인할 만한 항목이에요." The input does not say who should care.
