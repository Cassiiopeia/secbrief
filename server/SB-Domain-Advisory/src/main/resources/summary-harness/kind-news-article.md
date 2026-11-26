# This item: a news article

sourceText is the short teaser that the news outlet put in its feed, not the full article.
It may stop in the middle of a sentence.

Cover, as far as the input says:
- what happened and who did it or who reported it
- who or what is affected (company, product, users, country)
- how it works or why it matters, in one sentence

Watch out:
- Say who claims what: "Example Labs 연구진이 ~라고 밝혔어요", "Example Corp가 ~를 확인했어요".
  Do not turn a claim, a warning or an estimate into an established fact.
- A teaser is short, so the summary is shorter still: 1 or 2 sentences that carry the news, not a retelling of every sentence.
- Do not add drama the input does not have (no "충격", "비상", "심각한" unless the input says so).
- A teaser rarely gives advice. actionGuide is null unless the input tells readers to do something specific.
- When sourceText is Korean, rebuild every sentence. Do not reuse any stretch longer than about 20 characters,
  and report quotations in your own words.

Example input:
{"title":"Attackers Abuse Example Sync Flaw to Steal Cloud Credentials","region":"OVERSEAS","kind":"NEWS","sourceText":"Researchers at Example Labs said on March 4 that a threat group is abusing a flaw in Example Sync to pull cloud access keys from developer laptops. The group, which the researchers track as Blue Heron, has hit at least a dozen software firms since January. Example Sync's maker said a fix is in testing and"}

Good output:
{"summary":"한 공격 그룹이 Example Sync의 결함을 악용해 개발자 노트북에서 클라우드 접근 키를 빼내고 있어요. Example Labs 연구진이 3월 4일에 밝힌 내용으로, 1월부터 소프트웨어 회사 여러 곳이 공격을 받았어요.","keyPoints":["Example Labs는 이 공격 그룹을 Blue Heron으로 추적해요.","공격 대상은 개발자 노트북의 클라우드 접근 키예요."],"actionGuide":null}

Bad output and why:
- "12곳 이상의 회사가 피해를 봤어요." The input says "a dozen", a vague amount, so no digits.
- "제조사는 곧 수정판을 배포할 예정이에요." The last input sentence is cut off, so its content is unknown.
- "Example Sync를 최신 버전으로 업데이트하세요." The teaser gives no advice.
- "개발 환경 보안이 얼마나 중요한지 보여 주는 사례예요." Your own comment, not a fact from the input.
