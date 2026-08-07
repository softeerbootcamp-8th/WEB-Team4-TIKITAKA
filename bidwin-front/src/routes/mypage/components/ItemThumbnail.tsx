import { ImageIcon } from 'lucide-react'

/*
 * 마이페이지 물품 썸네일. 이미지가 없는 응답에는 자리표시자를 그린다.
 */
const PLACEHOLDER_ICON_SIZE = 20

function ItemThumbnail({
  thumbnailUrl,
  className,
}: {
  thumbnailUrl?: string | null
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
