import { ImageIcon } from 'lucide-react'

/*
 * 마이페이지의 물품 썸네일. 이미지 API 연동 전이라 비어 있는 경우가 흔해서,
 * 자리표시자를 컴포넌트 안에 같이 두고 크기만 밖에서 정한다.
 */
const PLACEHOLDER_ICON_SIZE = 20

function ItemThumbnail({
  thumbnailUrl,
  className,
}: {
  thumbnailUrl?: string
  className: string
}) {
  if (thumbnailUrl) {
    return (
      <img src={thumbnailUrl} alt="" className={`shrink-0 rounded-lg object-cover ${className}`} />
    )
  }

  return (
    <span
      /* 흰 카드 위에도, 회색 타일 위에도 자리표시자가 보이도록 한 단계 진한 면을 쓴다. */
      className={`flex shrink-0 items-center justify-center rounded-lg bg-surface-strong text-muted-soft ${className}`}
    >
      <ImageIcon size={PLACEHOLDER_ICON_SIZE} />
    </span>
  )
}

export default ItemThumbnail
