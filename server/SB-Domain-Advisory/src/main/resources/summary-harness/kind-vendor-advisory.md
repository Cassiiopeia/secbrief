# This item: an official advisory from an operating system or runtime vendor

sourceText is a short, formulaic notice: which package got a security update, for which product release,
and sometimes a severity rating or a list of the fixed flaws.

Where the facts go:
- oneLine: the package and, when the input gives one, the vendor's severity rating in the vendor's own word ("Important", "Critical")
- whatHappened: which product or product line the update is for and who rated it, and what the flaws are when the input describes them.
  The team that rates an update is not the one that released it: do not write that a security team "released" the update
- whyItMatters: what the flaws allow, when the input says so. null for a formulaic notice:
  a rating that oneLine already carries is not told again here
- affected: each product or release the update is for, as its own item with the exact name; detail is null unless the input gives a version or a condition
- actions: applying the update, when the input says an update or a fixed package is available

Watch out:
- Skip boilerplate that tells nothing about this item, such as where CVSS scores can be found or how the rating scale works.
- Formulaic input holds few facts. One short sentence is enough for whatHappened. Do not add who should care or what to check.
- When the input lists several fixed flaws, group them by what they allow instead of walking through every CVE. Leave the CVE IDs out.
- Long product names must stay exact. Put each one in affected as its own target instead of listing them in a sentence.
- When an issue applies only to some releases, name the package as target and put the releases in detail, as the input writes them.
- Say that an update is available only when the input says so. A notice that only describes flaws is told as flaws found in the package,
  and its actions is an empty array.
- When a release restriction covers only some of the listed flaws, leave detail null instead of attaching it to the whole package.

Example input:
{"title":"EXSA-0000:0001: Important: example-httpd security update","region":"OVERSEAS","kind":"ADVISORY","sourceText":"An update for example-httpd is now available for Example Linux 9 and Example Linux 9 Extended Support. Example Product Security has rated this update as having a security impact of Important. A severity score, which gives a detailed rating, is available for each vulnerability from the links in the References section."}

Good output:
{"oneLine":"example-httpd에 Important 등급 보안 업데이트가 나왔어요.","whatHappened":"Example Linux 9와 그 Extended Support 판에 쓰는 example-httpd의 수정판이에요. 보안 영향 등급은 Example Product Security가 매겼어요.","whyItMatters":null,"affected":[{"target":"Example Linux 9","detail":null},{"target":"Example Linux 9 Extended Support","detail":null}],"actions":["example-httpd 업데이트를 적용하세요."]}

Bad output and why:
- whatHappened "원격 코드 실행 취약점을 고쳤어요." The input does not say which flaw was fixed.
- whyItMatters "CVSS 점수는 참고 자료에서 확인할 수 있어요." Boilerplate, not a fact about this item.
- whyItMatters "Example Linux 9 서버 운영자가 확인할 만한 항목이에요." The input does not say who should care.
- whyItMatters "보안 영향은 Important 등급이에요." oneLine already says so. One fact, one field.
- whatHappened "Example Product Security가 example-httpd 업데이트를 내놨어요." The input says that team rated the update, not that it released it.
- affected [{"target":"Example Linux 9 및 Extended Support","detail":null}] Two targets merged into a name the input does not write.
- oneLine "EXSA-0000:0001 Important 등급 example-httpd 보안 업데이트" An advisory ID in the headline, and not a sentence.
