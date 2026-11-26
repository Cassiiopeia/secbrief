# This item: a news article

sourceText is the short teaser that the news outlet put in its feed, not the full article.
It may stop in the middle of a sentence.

Where the facts go:
- whatHappened: what happened and who did it or who reported it, with the date when the input gives one
- whyItMatters: the scale or consequence the input states (how many victims, what was taken, what is at risk); null when the teaser gives none
- affected: named products, companies or organisations that were hit or are exposed; never the attacker or the researcher.
  A company that only announces a product, a deal, a report or a policy is not affected: empty array for such news.
- actions: almost always an empty array. A teaser rarely tells readers to do something specific.

Watch out:
- Say who claims what: "Example Labs 연구진이 ~라고 밝혔어요", "Example Corp가 ~를 확인했어요".
  Do not turn a claim, a warning or an estimate into an established fact.
- A teaser is short, so the briefing is shorter still: carry the news, do not retell every sentence.
- Do not add drama the input does not have (no "충격", "비상", "심각한" unless the input says so).
- A CVE ID that the teaser gives may appear once, inside a whatHappened sentence that says something else too.
- When sourceText is Korean, rebuild every sentence. Do not reuse any stretch longer than about 20 characters,
  and report quotations in your own words.

Example input:
{"title":"Attackers Abuse Example Sync Flaw to Steal Cloud Credentials","region":"OVERSEAS","kind":"NEWS","sourceText":"Researchers at Example Labs said on March 4 that a threat group is abusing a flaw in Example Sync to pull cloud access keys from developer laptops. The group, which the researchers track as Blue Heron, has hit at least a dozen software firms since January. Example Sync's maker said a fix is in testing and"}

Good output:
{"oneLine":"Example Sync 결함으로 개발자의 클라우드 키가 털리고 있어요.","whatHappened":"한 공격 그룹이 Example Sync의 결함을 악용해 개발자 노트북에서 클라우드 접근 키를 빼내고 있어요. Example Labs 연구진이 3월 4일에 밝힌 내용이에요.","whyItMatters":"1월부터 소프트웨어 회사 여러 곳이 공격을 받았어요.","affected":[{"target":"Example Sync","detail":null}],"actions":[]}

Bad output and why:
- whyItMatters "12곳 이상의 회사가 피해를 봤어요." The input says "a dozen", a vague amount, so no digits.
- whyItMatters "개발 환경 보안이 얼마나 중요한지 보여 주는 사례예요." Your own comment, not a fact from the input.
- affected [{"target":"Blue Heron","detail":null}] Blue Heron is the attacker, not an affected target.
- affected [{"target":"소프트웨어 회사","detail":"12곳"}] Not a name written in the input, and a number the input does not give.
- actions ["Example Sync를 최신 버전으로 업데이트하세요."] The teaser gives no advice.
- whatHappened "제조사는 곧 수정판을 배포할 예정이에요." The last input sentence is cut off, so its content is unknown.
- oneLine "한 공격 그룹이 Example Sync의 결함을 악용해 클라우드 접근 키를 빼내고 있어요." A copy of the first whatHappened sentence, and too long for the card.
