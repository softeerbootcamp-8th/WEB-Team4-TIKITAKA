/*
 * 회원가입에서 받아야 하는 필수 동의 항목. 원문은 노션 문서로 관리하므로 링크만 들고 있고,
 * 항목이 늘어나면 이 배열에만 추가하면 화면과 검증이 함께 따라온다.
 */
export const AGREEMENT_ITEMS = [
  {
    id: 'terms',
    label: '이용약관에 동의합니다.',
    linkLabel: '이용약관 전문 보기',
    url: 'https://softeer04.notion.site/3bf6b34e4aac80eead44e47d62b8870b?source=copy_link',
  },
  {
    id: 'privacy',
    label: '개인정보 처리방침에 동의합니다.',
    linkLabel: '개인정보 처리방침 전문 보기',
    url: 'https://softeer04.notion.site/3bf6b34e4aac806b90e7e85a112233af?source=copy_link',
  },
] as const

export type AgreementId = (typeof AGREEMENT_ITEMS)[number]['id']
export type AgreementState = Record<AgreementId, boolean>

export const INITIAL_AGREEMENT_STATE: AgreementState = {
  terms: false,
  privacy: false,
}

export function isEveryAgreementAccepted(state: AgreementState) {
  return AGREEMENT_ITEMS.every((item) => state[item.id])
}

export function acceptEveryAgreement(accepted: boolean): AgreementState {
  return { terms: accepted, privacy: accepted }
}
