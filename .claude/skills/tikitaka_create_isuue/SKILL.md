---
name: draft-tikitaka-issue
description: Create a Korean Tikitaka issue title and body draft from a user's description of a feature they plan to implement. Use when the user asks to draft, write, or organize a Tikitaka development issue, feature issue, or implementation task in the team's prescribed issue template.
---

# Draft Tikitaka Issue

Transform the requested feature into a concise issue title and body. Return only the completed draft unless the user asks for explanation.

## Drafting rules

1. Create a concise Korean issue title that identifies the feature or task.
2. Put only the title on the first line as plain text. Do not add an issue type, tag, bracket, Markdown heading, or `제목:` label.
3. Summarize the feature's purpose under `어떤 작업인가요?`.
4. Analyze the request and list only the feature-level work needed under `상세 작업 내역`.
5. Keep related work together. Do not split the checklist into implementation details such as individual files, functions, endpoints, or test cases unless the user explicitly requests that detail.
6. Recommend one branch name in the form `feat/#{이슈번호}-{영문-kebab-case-요약}` under `추천 브랜치명`.
7. Use only lowercase ASCII letters, digits, and hyphens for the branch summary.
8. End the purpose sentence and every reference note in Korean declarative `~다.` style.
9. End every checklist item with a noun or noun phrase. Do not add a final period.
10. Never use the middle dot character, Unicode U+00B7.
11. Include `참고 사항` only when a decision, clarification, dependency, or design discussion is needed. Omit the entire section otherwise.
12. Preserve facts supplied by the user. Do not invent requirements or technical decisions.

## Output template

```markdown
{간결한 이슈 제목}

## 📝 어떤 작업인가요?

> {개발할 기능이나 해결할 문제의 목적을 간략히 설명한다.}

## 📌 상세 작업 내역

- [ ] {기능 단위 작업}
- [ ] {기능 단위 작업}

## 🌿 추천 브랜치명

`feat/#{이슈번호}-{영문-kebab-case-요약}`

## 💬 참고 사항 (선택)

> {결정하거나 논의할 내용을 적는다.}
```

Remove unused checklist rows. When no reference note is needed, remove the `참고 사항` heading and its blockquote.
