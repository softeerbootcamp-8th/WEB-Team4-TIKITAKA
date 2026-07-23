# Design Reference (프로토타입 참고용)

이 폴더는 실제 앱 소스가 아닙니다. `front-demo/auction-app`에서 디자인 검증이 끝난
프로토타입을 그대로 옮겨온 참고 자료로, **여기 코드를 그대로 src/에 옮기지 말고**
아래 규칙에 맞춰 새로 구현하는 데 참고만 하세요.

- CSS Modules → Tailwind (색상은 `DESIGN.md` 토큰을 시멘틱 컬러로 매핑해서 사용)
- 평범한 JSX → TypeScript
- 플랫 구조 → route-based 폴더 구조
- `hooks/useAuction.js`의 mock 데이터/타이머 로직은 실제 백엔드 API 연동으로 교체

`components/`의 마크업·인터랙션, `hooks/`의 타임어택 연장 로직, `utils/format.js`의
포맷 규칙, `styles/tokens.css`의 색상·타이포·spacing 수치를 기준으로 구현하면 됩니다.
