import { Camera } from 'lucide-react'
import { useRef, useState } from 'react'
import type { ChangeEvent } from 'react'
import Button from '../../../components/ui/Button'
import { useToast } from '../../../hooks/useToast'
import { MY_INFO_TEXT, PROFILE_IMAGE_ACCEPT, PROFILE_IMAGE_MAX_BYTES } from '../constants'
import ProfileAvatar from './ProfileAvatar'

const AVATAR_CLASS = 'h-16 w-16 text-xl'
const CAMERA_ICON_SIZE = 16
const IMAGE_MIME_PREFIX = 'image/'

/*
 * 프로필 이미지 변경. 고른 파일은 위쪽에서 미리보기 URL로 바꿔 프로필 카드까지 함께 갱신한다.
 * 파일 형식·용량은 업로드 요청을 보내기 전에 여기서 먼저 거른다.
 */
function ProfileImageField({
  nickname,
  imageUrl,
  onChangeImage,
}: {
  nickname: string
  imageUrl?: string
  /** null이면 기본 이미지로 되돌린다는 뜻 */
  onChangeImage: (file: File | null) => void
}) {
  const { showToast } = useToast()
  const inputRef = useRef<HTMLInputElement>(null)
  const [error, setError] = useState('')

  function handleFileChange(event: ChangeEvent<HTMLInputElement>) {
    const file = event.target.files?.[0]
    /* 같은 파일을 다시 골라도 change가 일어나도록 입력값을 비워 둔다. */
    event.target.value = ''
    if (!file) return

    if (!file.type.startsWith(IMAGE_MIME_PREFIX)) {
      setError(MY_INFO_TEXT.imageNotSupported)
      return
    }
    if (file.size > PROFILE_IMAGE_MAX_BYTES) {
      setError(MY_INFO_TEXT.imageTooLarge)
      return
    }

    setError('')
    onChangeImage(file)
    showToast(MY_INFO_TEXT.imageSelected)
  }

  function handleReset() {
    setError('')
    onChangeImage(null)
    showToast(MY_INFO_TEXT.imageResetDone)
  }

  return (
    <div className="flex items-center gap-base">
      <ProfileAvatar nickname={nickname} imageUrl={imageUrl} className={AVATAR_CLASS} />

      <div className="flex min-w-0 flex-1 flex-col gap-xs">
        <div className="flex flex-wrap gap-xs">
          <Button variant="secondary" onClick={() => inputRef.current?.click()}>
            <Camera size={CAMERA_ICON_SIZE} />
            {MY_INFO_TEXT.imageChange}
          </Button>
          {imageUrl && (
            <Button variant="tertiary" onClick={handleReset}>
              {MY_INFO_TEXT.imageReset}
            </Button>
          )}
        </div>
        <p className={`text-xs ${error ? 'text-down' : 'text-muted'}`}>
          {error || MY_INFO_TEXT.imageHint}
        </p>
      </div>

      <input
        ref={inputRef}
        type="file"
        accept={PROFILE_IMAGE_ACCEPT}
        onChange={handleFileChange}
        className="hidden"
        aria-hidden
        tabIndex={-1}
      />
    </div>
  )
}

export default ProfileImageField
