## 페이지 라우트

| # | 페이지 | URL | 폴더 (`src/routes/`) | 컴포넌트 | 담당 |
|---|---|---|---|---|---|
| 1 | 로그인 | `/login` | `login/page.tsx` | `LoginPage` | geunseong kim |
| 2 | 회원가입 | `/signup` | `signup/page.tsx` | `SignupPage` | geunseong kim |
| 3 | 경매 목록 (검색 결과) | `/auctions` | `auctions/page.tsx` | `AuctionListPage` | geunseong kim |
| 4 | 마이페이지 | `/mypage` | `mypage/page.tsx` | `MyPage` | geunseong kim |
| 5 | 이메일 인증 | `/email-verification` (`?token=` 있으면 확인 단계) | `email-verification/page.tsx` | `EmailVerificationPage` | CoooooooodinG |
| 6 | 비밀번호 재설정 (로그인 화면) | `/password-reset` (`?token=` 있으면 새 비번 입력 단계) | `password-reset/page.tsx` | `PasswordResetRequestPage` | CoooooooodinG |
| 7 | 비밀번호 재설정 (마이페이지) | `/mypage/password` | `mypage/password/page.tsx` | `MyPagePasswordResetPage` | CoooooooodinG |
| 8 | 경매 등록 | `/auctions/new` | `auctions/new/page.tsx` | `AuctionRegisterPage` | CoooooooodinG |
| 9 | 메인 (인기순 5개) | `/` | `page.tsx` | `HomePage` | 노승억 |
| 10 | 경매 상세 (입찰, 입찰 기록) | `/auctions/:auctionId` | `auctions/detail/page.tsx` | `AuctionDetailPage` | 노승억 |
| 11 | 내 기록 상세조회 | `/mypage/history` | `mypage/history/page.tsx` | `MyRecordsPage` | 노승억 |

일치하는 라우트가 없으면 `src/app/NotFoundPage.tsx`로 간다 (`src/app/router.tsx`의 `*` 라우트).

**새 페이지 추가하는 법**은 `.claude/skills/design-new-page/SKILL.md`의 "라우팅 규칙" 참고.

## 시작 전에 꼭 읽어주세요

### 1. 새 페이지 추가는 이 2단계만

1. `src/routes/<path>/page.tsx` 생성
2. `src/app/routes.ts` 배열에 한 줄 추가

이 외에 `src/app/router.tsx`, `src/app/RootLayout.tsx`는 건드릴 필요 없습니다.
동적 경로(`:auctionId` 같은 것)가 필요해도 폴더명은 `detail`처럼 평범하게 짓고,
`useParams()`로 값을 읽으면 됩니다 (`auctions/detail/page.tsx` 참고).

### 2. 색은 무조건 `src/styles/theme.css`의 시멘틱 유틸리티만

`bg-primary`, `text-ink`, `text-body`, `bg-surface-strong`, `rounded-pill` 처럼
이미 정의된 것만 쓰고, `bg-blue-500`같은 Tailwind 기본 팔레트나 hex 하드코딩은
쓰지 않습니다. **쓰고 싶은 색/spacing/radius가 없으면 직접 `theme.css`를 고치지
말고, 먼저 팀에 얘기해주세요** — 다 같이 쓰는 파일이라 서로 다른 값을 추가하면
바로 충돌납니다.

### 3. UI 새로 짜기 전에 이미 있는지 확인

- `src/components/ui/` — `Button`, `Badge`, `Card`, `TextInput`
- `src/components/layout/` — `TopNav` (모든 페이지에 이미 붙어 있음)
- `src/hooks/useToast.ts` — `useToast().showToast('메시지')`
- `src/hooks/useCountdown.ts` — 마감 카운트다운
- `src/lib/format.ts` — `formatWon`, `formatClock`, `formatTimeOfDay`, `formatDeadline`

똑같이 생긴 버튼/카드를 페이지마다 새로 만들지 말고 이걸 재사용해주세요. 여러
페이지에서 쓸 게 확실한 새 컴포넌트(예: 경매 카드)가 필요하면 그때도 먼저
팀에 공유하고 만듭니다.

### 4. `DESIGN.md`에서 무시해도 되는 부분

`DESIGN.md`는 Coinbase 마케팅 랜딩 페이지 분석 문서라, 색상/타이포/spacing/
radius 값과 버튼·카드·인풋 스펙은 그대로 쓰지만 `hero-band`, `pricing-tier`,
`asset-row`, `footer-light`, `legal-band`, `cta-band-dark` 같은 마케팅 전용
컴포넌트는 bidwin 11개 페이지 어디에도 해당 안 되니 무시하면 됩니다.

### 5. 이메일 인증 / 비밀번호 재설정은 페이지가 1개씩 뿐, 상태가 2개

`/email-verification`, `/password-reset`은 URL이 하나인데 상태가 두 가지입니다:

- `?token=` 없음 → 이메일 입력해서 발송 요청
- `?token=` 있음 (메일 링크로 들어온 경우) → 그 자리에서 바로 인증/재설정 처리

`useSearchParams()`로 `token` 유무를 분기해서 구현해야 합니다. 별도 경로를
새로 만들 필요는 없습니다.

### 6. 아직 없는 것 — API 연동 시작 전에 먼저 얘기해주세요

백엔드 연동(로그인 상태 공유, API 호출 방식, 인증 필요한 페이지 접근 제한,
환경변수)은 아직 안 만들어져 있습니다. 각자 페이지에서 `fetch`를 바로 쓰기
시작하기 전에, 공용 API 클라이언트/인증 상태를 먼저 만들지 상의해주세요 —
안 그러면 세 명이 각자 다른 방식으로 만들게 됩니다.

### 7. 배포(S3 + CloudFront) 관련

정적 호스팅이라 CloudFront에 커스텀 에러 응답(403/404 → `/index.html`, HTTP
200)이 설정되어 있어야 `/mypage`처럼 `/`가 아닌 경로로 바로 들어오거나
새로고침했을 때 정상 동작합니다. 코드에서 할 일은 없지만 배포 설정에서 빠뜨리지
않도록 확인이 필요합니다.

# React + TypeScript + Vite

This template provides a minimal setup to get React working in Vite with HMR and some Oxlint rules.

Currently, two official plugins are available:

- [@vitejs/plugin-react](https://github.com/vitejs/vite-plugin-react/blob/main/packages/plugin-react) uses [Oxc](https://oxc.rs)
- [@vitejs/plugin-react-swc](https://github.com/vitejs/vite-plugin-react/blob/main/packages/plugin-react-swc) uses [SWC](https://swc.rs/)

## React Compiler

The React Compiler is enabled on this template. See [this documentation](https://react.dev/learn/react-compiler) for more information.

Note: This will impact Vite dev & build performances.

## Expanding the Oxlint configuration

If you are developing a production application, we recommend enabling type-aware lint rules by installing `oxlint-tsgolint` and editing `.oxlintrc.json`:

```json
{
  "$schema": "./node_modules/oxlint/configuration_schema.json",
  "plugins": ["react", "typescript", "oxc"],
  "options": {
    "typeAware": true
  },
  "rules": {
    "react/rules-of-hooks": "error",
    "react/only-export-components": ["warn", { "allowConstantExport": true }]
  }
}
```

See the [Oxlint rules documentation](https://oxc.rs/docs/guide/usage/linter/rules) for the full list of rules and categories.
