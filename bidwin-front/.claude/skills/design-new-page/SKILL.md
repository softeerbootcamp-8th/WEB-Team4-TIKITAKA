---
name: design-new-page
description: bidwin-front에서 새 화면/컴포넌트를 디자인하거나 와이어프레임을 구현할 때 사용. 기존 공통 컴포넌트와 DESIGN.md 토큰, reference/ 폴더의 실제 구현 사례를 참고해 급처 경매 서비스의 타임어택 톤에 맞는 화면을 만든다.
---

# 새 화면 디자인하기

bidwin-front에서 새로운 화면이나 컴포넌트를 디자인할 때 아래 순서를 따른다.

## 1. 공통 리소스부터 확인 — 새로 만들기 전에 재사용 가능한지 본다

아래는 이미 만들어져 있고 여러 페이지에서 같이 쓰는 것들이다. 비슷한 걸 새로
만들기 전에 먼저 확인하고, 있으면 그대로 가져다 쓴다.

- `src/components/ui/` — `Button`, `Badge`, `Card`, `TextInput`
- `src/components/layout/` — `TopNav` (모든 페이지 상단, `RootLayout`에 이미 연결됨)
- `src/components/feedback/` + `src/hooks/useToast.ts` — 전역 알림 (`useToast().showToast(...)`)
- `src/hooks/useCountdown.ts` — 마감 카운트다운 (남은 시간, 60초 이하 urgent 여부)
- `src/lib/format.ts` — `formatWon`, `formatClock`, `formatTimeOfDay`, `formatDeadline`

정말 없는 게 확실하고 여러 페이지에서 쓰일 공통 요소(예: 경매 카드)라면 새로
만들되, 위 폴더의 기존 네이밍·구조 패턴을 따르고 팀원에게 공유한다. 특정 페이지
전용 요소는 공통 폴더가 아니라 그 페이지 폴더 안에 둔다.

## 2. 디자인 토큰 확인 — `src/styles/theme.css` (매핑 완료) / `DESIGN.md` (원본 값)

`DESIGN.md`의 색상·타이포·spacing·radius 값은 이미 `src/styles/theme.css`에
Tailwind 유틸리티로 매핑되어 있다. 새로 매핑하지 말고 `bg-primary`, `text-ink`,
`rounded-pill`, `p-section` 같은 기존 유틸리티를 그대로 쓴다. 정말 값이 없을
때만 `DESIGN.md`의 체계(색상 토큰 → 시멘틱 색상)를 따라 `theme.css`에 추가한다.

## 3. `DESIGN.md`에서 bidwin에 해당하지 않는 부분

`DESIGN.md`는 Coinbase의 **공개 마케팅 랜딩 페이지**를 분석한 문서다. 색상/
타이포/spacing/radius 스케일과 버튼·카드·인풋 같은 범용 컴포넌트 스펙은 그대로
가져다 쓰지만, 아래 컴포넌트는 마케팅 사이트 전용이라 bidwin의 11개 페이지엔
해당하는 게 없다 — 억지로 끼워 넣지 않는다.

- `hero-band-dark`/`hero-band-light` (가입 유도 히어로 배너)
- `pricing-tier-card`/`pricing-tier-featured` (요금제 비교)
- `asset-row`/`price-up-cell`/`price-down-cell` (코인 시세 등락률 행 — 경매
  목록엔 등락률이 아니라 현재가·남은시간이 필요하므로 별도 컴포넌트로 새로
  설계한다)
- `footer-light`/`footer-link`/`legal-band`, `cta-band-dark`

## 4. 핵심 Do's/Don'ts (페이지 전체 톤 유지용 요약)

- 모든 CTA는 `rounded-pill`, 아이콘 플레이트는 `rounded-full`.
- 그림자는 `shadow-soft`/`shadow-card` 한 단계만 쓰고 새 tier를 만들지 않는다.
- `primary` 강조색은 화면당 1~2곳처럼 아주 드물게만 쓴다.
- 타임어택 감각(마감 임박, 실시간 경쟁)은 색을 요란하게 쓰는 게 아니라, 지정된
  포인트(카운트다운 `text-down`, live 배지 pulsing dot)에서만 드러낸다.

## 5. 구현 사례 참고 — `bidwin-front/reference/`

디자인 검증이 끝난 경매 상세 페이지 프로토타입이다. 이 폴더 자체는 실제 앱 코드가
아니므로 그대로 복사하지 말고, 아래 관점에서 참고만 한다.

- `components/` — 마크업 구조, 레이아웃, 인터랙션 상태(hover/active/disabled 등)
- `hooks/useAuction.js` — 막판 입찰 시 마감 60초 연장 같은 타임어택 비즈니스 로직 패턴
- `utils/format.js` — 가격/시간 포맷팅 규칙 (`src/lib/format.ts`로 이미 이식됨)
- `styles/tokens.css` — DESIGN.md 토큰이 실제 CSS 값으로 어떻게 매핑됐는지

## 6. 라우팅 규칙 — `src/routes/<path>/page.tsx` + `src/app/routes.ts`

라우트는 자동으로 스캔되지 않고, `src/app/routes.ts`에 사람이 목록으로 적어둔다
(React Router 공식 방식 그대로). 새 페이지를 추가할 때는:

1. `src/routes/<path>/page.tsx` 파일을 만든다 (동적 파라미터가 필요해도 폴더명은
   `detail`처럼 평범하게 짓는다 — 폴더명과 URL 경로는 무관하다).
2. `src/app/routes.ts` 배열에 `{ path: '<path>', lazy: () => import('../routes/<path>/page') }`
   한 줄을 추가한다.

이 한 줄 추가 외에는 `src/app/router.tsx`, `src/app/RootLayout.tsx`를 건드리지
않는다. 각자 자기 페이지 폴더(`src/routes/<path>/`) 안에서만 작업한다.

## 7. 실제 구현 시 지켜야 할 변환 규칙

- 평범한 JSX → TypeScript
- 플랫 구조 → route-based 폴더 구조
- 자주 쓰는 숫자/문자열은 하드코딩하지 말고 const로 분리
- mock 데이터/타이머 로직은 실제 백엔드 API 연동으로 교체

## 8. 와이어프레임을 함께 받은 경우

사용자가 참고용 와이어프레임 이미지를 준다면 그건 임시 시안이다. 화면에 맞지 않거나
어색한 요소가 있으면 위치를 재조정하거나 불필요한 기능은 빼도 된다.

## 서비스 톤

급처 물품 경매 사이트이므로, 타임어택(마감 임박, 실시간 입찰 경쟁) 감각이 드러나는
디자인을 우선한다.
