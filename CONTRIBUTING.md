# 브랜치 / 커밋 / PR 컨벤션

## 브랜치 전략

- `main` — 릴리즈
- `dev` — 개발 브랜치 병합

### 개발 브랜치

`{종류}/#{이슈번호}-{기능명}`

- 새로운 기능 개발 (이슈 #12): `feat/#12-bid-api`
- 버그 수정 (이슈 #15): `fix/#15-redis-lock-error`
- 설정 변경 (이슈 #3): `chore/#3-ci-cd-setup`

### 종류

| 타입 | 설명 |
| --- | --- |
| feat | 새로운 기능 추가 |
| fix | 버그 및 에러 수정 |
| refactor | 기능 변화 없이 코드 구조나 로직 개선 |
| chore | 빌드 업무, 패키지 매니저, 라이브러리 추가 등 |
| docs | README, Swagger 등 문서 수정 |

`main`, `dev`로 push하려는 브랜치명은 pre-commit 훅으로 형식이 강제됩니다 (`.husky/pre-commit`).

## 커밋 컨벤션

`{종류}: {설명} (#{이슈번호})` — `:` 앞에는 공백 없이, 뒤에는 공백 있게.

- `feat: 입찰 API 컨트롤러 생성 (#12)`
- `feat: 입찰가 갱신 비즈니스 로직 작성 (#12)`
- `fix: 테스트 중 발생한 Null 에러 수정 (#12)`

commit-msg 훅(commitlint)이 이 형식을 강제합니다. 최초 1회 `npm install` 실행 시 husky가 훅을 활성화합니다.

## PR 컨벤션

**제목:** `[타입] 작업 요약` (예: `[Feat] 실시간 입찰 갱신 API 구현`, `[Bug] 동시성 테스트 중 데드락 해결`, `[Release] v1.2.0`)

템플릿은 `.github/PULL_REQUEST_TEMPLATE.md`에 정의되어 있으며 PR 생성 시 자동으로 채워집니다.

- PR은 AI 제외 리뷰를 1명 이상 받아야 한다. 리뷰어는 최대한 빠르게 리뷰하여 어프루브해준다.
- 적절한 변경 줄수는 400~1000줄 사이 (테스트/문서/자동생성/락파일 제외). 필수는 아니고 감을 찾아가는 기준.
- PR은 반드시 라벨을 1개 이상 단다 (CI에서 강제: `.github/workflows/pr-lint.yml`).
- 하나의 PR은 이슈 5개 이하로 한다.

라벨 종류는 `.github/labels.yml`에서 관리하며 `main`에 push되면 자동으로 저장소에 동기화됩니다.

## 로컬 세팅

저장소 루트에서 최초 1회:

```bash
npm install
```

`husky`가 `.husky/` 아래 훅을 활성화합니다 (front/back 각 앱의 `npm install`과는 별개).
