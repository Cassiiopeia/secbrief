# This item: an open-source package advisory

sourceText is a long advisory for a software package. It often mixes a summary, technical details,
proof-of-concept code, impact, patches and workarounds, sometimes with markdown headings.

Where the facts go:
- whatHappened: what goes wrong and under which condition (which feature, which configuration, which input)
- whyItMatters: the impact the advisory states (what an attacker can do, who is exposed)
- affected: the package name exactly as written as target, the affected version range as detail
- actions: the fixed version to move to, and each workaround the input offers as its own item

Watch out:
- Skip proof-of-concept code, stack traces, file paths, commit hashes, credits and reference lists.
- A version range is copied digit for digit: ">= 2.0.0, < 2.4.1" becomes "2.0.0 이상 2.4.1 미만".
- actions tell users of the package what to do. Suggestions to the maintainers on how to change the code are not user actions; leave them out.
  If the input names no fixed version and no workaround, actions is an empty array.
- Do not explain how to exploit the flaw step by step.
- A long advisory still gets a short briefing. Pick the condition and the impact; leave the internals out.

Example input:
{"title":"example-auth-kit: session fixation in OAuth callback","region":"OVERSEAS","kind":"VULNERABILITY","sourceText":"example-auth-kit: session fixation in OAuth callback\n### Summary\nThe OAuth callback handler in example-auth-kit reuses the pre-login session identifier. ### Impact\nAn attacker who can set a victim's session cookie can take over the account after the victim logs in. ### Patches\nVersions >= 3.0.0, < 3.1.4 are affected. Fixed in 3.1.4. ### Workarounds\nRegenerate the session in a custom callback hook."}

Good output:
{"oneLine":"example-auth-kit에서 로그인한 계정을 가로챌 수 있어요.","whatHappened":"example-auth-kit의 OAuth 콜백 처리기가 로그인 전 세션 식별자를 그대로 다시 써서 세션 고정 문제가 생겨요.","whyItMatters":"피해자의 세션 쿠키를 미리 심을 수 있는 공격자는 피해자가 로그인한 뒤 그 계정을 가로챌 수 있어요.","affected":[{"target":"example-auth-kit","detail":"3.0.0 이상 3.1.4 미만"}],"actions":["3.1.4로 올리세요.","바로 올리기 어렵다면 직접 만든 콜백 훅에서 세션을 새로 발급하세요."]}

Bad output and why:
- affected [{"target":"example-auth-kit","detail":"3.1.3 이하"}] The input never writes 3.1.3.
- affected [{"target":"OAuth 콜백 처리기","detail":null}] A component described in the text, not the package the reader installs.
- actions ["최신 버전으로 업데이트하고 로그를 점검하세요."] Two actions in one item, and log review is not in the input.
- oneLine "example-auth-kit 3.0.0 이상 3.1.4 미만에서 세션 고정 취약점이 발견돼 3.1.4에서 고쳤어요." Version ranges and a second fact in the headline.
