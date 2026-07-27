---
name: draft-tikitaka-pr
description: Create a detailed Korean Tikitaka pull request title and body draft for the current Git branch by inspecting its GitHub commits, changes, related issue, incomplete work, and verification evidence with gh. Use when the user asks to draft, write, or organize a Tikitaka PR or pull request for the current branch in the team's prescribed format.
---

# Draft Tikitaka PR

Return only the completed title followed by the Markdown body unless the user asks for explanation. Do not create or update a pull request unless explicitly requested.

## Gather evidence

1. Confirm the current branch and GitHub repository.
2. Resolve the base branch from the current pull request when one exists, otherwise use the repository default branch.
3. Use `gh pr view` for an existing pull request or the GitHub compare API through `gh api` to inspect every commit and changed file added between the base and current branch. Read the relevant local code when the summary or patch is insufficient.
4. Extract each `#[0-9]+` issue reference from the current branch name, commits, or existing pull request. Use `gh issue view` to inspect each issue title, body, checklist, state, and relevant comments. If no issue reference exists, ask the user for one instead of inventing it.
5. Compare the issue requirements with the branch changes. Include evidence-backed unfinished work in the first section. Do not treat a stale unchecked box as unfinished when the branch implements it.
6. Inspect changed tests and the repository's existing verification commands. Run the smallest relevant safe check when practical. Report only checks actually performed and their result. If no code-backed verification was performed, use `직접 실행하며 동작 확인`.

## Drafting rules

1. Write one concise Korean title in the form `[Type] 변경 요약`. Infer `Type` from the primary change and follow the repository's casing, such as `[Feat]`, `[Fix]`, `[Refactor]`, `[Test]`, `[Docs]`, or `[Chore]`.
2. Explain the overall change and motivation in a short opening paragraph under `작업 배경 및 핵심 로직`.
3. Add descriptive `###` subsections for distinct implementation topics when they help a reviewer trace the change. Omit subsections for a small, single-topic change.
4. Use paragraphs for reasoning, bullet lists for facts, and fenced `text` blocks for flows, formats, or before-and-after values.
5. Describe each component's responsibility, important policy or parameter choices, and the reason for non-obvious design decisions when supported by the code or issue.
6. Put verified checks under `검수 결과` as completed task-list items. Introduce them with one short result summary when useful.
7. Add `참고 사항` only for known follow-up work, environment limitations, migrations, compatibility concerns, or reviewer caveats.
8. Preserve repository evidence. Do not invent changes, motivations, links, test results, or unfinished work.
9. List every resolved issue reference as `* Resolves #x`.

## Output template

````markdown
[Type] {변경 핵심을 담은 PR 제목}

## 💡 작업 배경 및 핵심 로직 (What & Why)

{무엇을 변경했고 왜 변경했는지 설명하는 짧은 문단}

### {구현 주제}

{구현 방식과 설계 이유}

* {검토에 필요한 구체적인 변경 사항}

```text
{처리 흐름, 저장 형식, 또는 변경 전후 값}
```

## 🧪 검수 결과 (Proof)

{실제로 수행한 검증의 요약}

* [x] {실제로 확인한 동작 또는 통과한 검사}

## ⚠️ 참고 사항

* {확인된 후속 작업 또는 제약}

## 🔗 연관 이슈

* Resolves #{이슈 번호}
````

Remove unused subsection, code block, summary, and `참고 사항`. Keep the remaining top-level headings in this order.
