# This item: an open-source package advisory

sourceText is a long advisory for a software package. It often mixes a summary, technical details,
proof-of-concept code, impact, patches and workarounds, sometimes with markdown headings.

Cover, as far as the input says:
- the package name exactly as written, and what the package is used for when the input says it
- what goes wrong and under which condition (which feature, which configuration, which input)
- which versions are affected and which version fixes it, with the same digits as the input
- workarounds the input offers

Watch out:
- Skip proof-of-concept code, stack traces, file paths, commit hashes, credits and reference lists.
- A version range is copied digit for digit: ">= 2.0.0, < 2.4.1" becomes "2.0.0 이상 2.4.1 미만".
- actionGuide tells users of the package what to do: the fixed version to move to and the workarounds the input offers.
  Suggestions to the maintainers on how to change the code are not user actions; leave them out.
  If the input names no fixed version and no workaround, actionGuide is null.
- Do not explain how to exploit the flaw step by step.

Example input:
{"title":"example-auth-kit: session fixation in OAuth callback","region":"OVERSEAS","kind":"VULNERABILITY","sourceText":"example-auth-kit: session fixation in OAuth callback\n### Summary\nThe OAuth callback handler in example-auth-kit reuses the pre-login session identifier. ### Impact\nAn attacker who can set a victim's session cookie can take over the account after the victim logs in. ### Patches\nFixed in 3.1.4. ### Workarounds\nRegenerate the session in a custom callback hook."}

Good output:
{"summary":"example-auth-kit의 OAuth 콜백 처리에서 세션 고정 문제가 나왔어요. 피해자의 세션 쿠키를 미리 심을 수 있는 공격자는 피해자가 로그인한 뒤 그 계정을 가로챌 수 있어요.","keyPoints":["OAuth 콜백이 로그인 전 세션 식별자를 그대로 다시 써요.","3.1.4에서 고쳤어요."],"actionGuide":"3.1.4로 올리세요. 바로 올리기 어렵다면 직접 만든 콜백 훅에서 세션을 새로 발급하세요."}

Bad output and why:
- "3.1.3 이하 버전이 영향을 받아요." The input never writes 3.1.3.
- "최신 버전으로 업데이트하고 로그를 점검하세요." Log review is not in the input.
