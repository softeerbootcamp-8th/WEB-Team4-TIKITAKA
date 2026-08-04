import { PROFILE_TEXT } from '../constants'

/*
 * 프로필 이미지가 없을 때는 닉네임 첫 글자로 아바타를 만든다.
 * 프로필 카드와 내 정보 드로어가 크기만 다르게 해서 같이 쓴다.
 */
function initialOf(nickname: string) {
  /* 이모지처럼 두 코드 유닛짜리 글자가 반쪽만 남지 않게 코드 포인트 단위로 자른다. */
  return Array.from(nickname)[0] ?? ''
}

function ProfileAvatar({
  nickname,
  imageUrl,
  className,
}: {
  nickname: string
  imageUrl?: string
  /** 크기와 글자 크기를 함께 넘긴다 (예: 'h-20 w-20 text-2xl') */
  className: string
}) {
  if (imageUrl) {
    return (
      <img
        src={imageUrl}
        alt={PROFILE_TEXT.avatarAlt}
        className={`shrink-0 rounded-full bg-surface-strong object-cover ${className}`}
      />
    )
  }

  return (
    <span
      aria-hidden
      className={`flex shrink-0 items-center justify-center rounded-full bg-surface-strong font-bold text-muted ${className}`}
    >
      {initialOf(nickname)}
    </span>
  )
}

export default ProfileAvatar
