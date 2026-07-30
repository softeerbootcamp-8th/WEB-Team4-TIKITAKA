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
