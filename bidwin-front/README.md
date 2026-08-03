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

### 6. API 연동은 정해진 방식이 없습니다

로그인 상태 공유나 공용 API 클라이언트 같은 공통 구조는 만들지 않기로 했습니다.
각자 담당 페이지에서 자유롭게 `fetch` 등으로 백엔드와 통신하면 됩니다.

다만 꼭 알아둬야 할 게 두 가지 있어요:

- 백엔드가 **세션 쿠키**로 로그인 상태를 관리합니다. `fetch` 호출에
  `credentials: 'include'`를 꼭 넣어야 로그인이 유지됩니다.
- 백엔드에 **CORS 설정이 아직 없습니다.** 로컬에서 프론트(5173)가 백엔드(8080)를
  직접 호출하면 브라우저가 응답을 막을 수 있어요. CORS를 추가하든 dev 서버
  프록시를 쓰든, 이 부분은 API 연동 시작 전에 정해야 합니다.

### 7. 배포(S3 + CloudFront) 관련

정적 호스팅이라 CloudFront에 커스텀 에러 응답(403/404 → `/index.html`, HTTP
200)이 설정되어 있어야 `/mypage`처럼 `/`가 아닌 경로로 바로 들어오거나
새로고침했을 때 정상 동작합니다. 코드에서 할 일은 없지만 배포 설정에서 빠뜨리지
않도록 확인이 필요합니다.

## AI(Claude)에게 작업 시킬 때 이렇게 말해주세요

### 화면/디자인 작업

`.claude/skills/design-new-page` 스킬이 새 화면을 만들 때 `DESIGN.md`, `reference/`,
기존 공통 컴포넌트를 자동으로 챙겨줍니다. 페이지 이름과 기능을 구체적으로 설명하면
충분해요.

> 예시: "로그인 페이지 만들어줘. 이메일/비밀번호 입력받고, 로그인 버튼 누르면 로그인
> 처리하고, 비밀번호 찾기 링크도 넣어줘."

### API 연동 작업

API 연동은 정해진 구조가 없으니(6번 참고), 프롬프트에 아래만 챙겨서 말해주면 됩니다:

- 어느 페이지인지
- 어떤 동작을 API로 연결할지 (로그인 처리, 목록 불러오기 등)
- "`fetch`에 `credentials: 'include'` 꼭 넣어줘"

> 예시: "로그인 페이지에 실제 로그인 API 연동해줘. `POST /api/v1/auth/login`에
> 이메일/비밀번호 보내고, `credentials: 'include'` 꼭 넣어줘."

### 새 공통 색상/컴포넌트가 필요할 때

"이런 색이 필요한데 `theme.css`에 없어", "이런 공통 컴포넌트가 필요해" 처럼 상황을
먼저 설명하면, `SKILL.md`에 이미 "공용 파일은 팀에 먼저 물어보고 고친다"는 규칙이
적혀 있어서 대부분 알아서 확인부터 하자고 제안합니다. 그래도 실제로 팀과 상의한
다음에 진행해주세요.

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
