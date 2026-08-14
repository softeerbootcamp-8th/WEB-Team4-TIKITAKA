import { Camera } from 'lucide-react'
import { useRef, useState } from 'react'
import type { ChangeEvent } from 'react'
import Button from '../../../components/ui/Button'
import { useToast } from '../../../hooks/useToast'
import {
  requestProfileImageReset,
  requestProfileImageUpdate,
} from '../../../lib/api/mypage'
import {
  requestProfileImagePresign,
  uploadProfileImage,
} from '../../../lib/api/profileImage'
import { isAuthenticImageFile } from '../../../lib/imageValidation'
import { MY_INFO_TEXT, PROFILE_IMAGE_ACCEPT, PROFILE_IMAGE_MAX_BYTES } from '../constants'
import ProfileAvatar from './ProfileAvatar'

const AVATAR_CLASS = 'h-16 w-16 text-xl'
const CAMERA_ICON_SIZE = 16

function ProfileImageField({
  nickname,
  imageUrl,
  onChangeImage,
}: {
  nickname: string
  imageUrl?: string | null
  onChangeImage: (profileImageUrl: string | null) => void
}) {
  const { showToast } = useToast()
  const inputRef = useRef<HTMLInputElement>(null)
  const [error, setError] = useState('')
  const [isSubmitting, setIsSubmitting] = useState(false)

  async function handleFileChange(event: ChangeEvent<HTMLInputElement>) {
    const file = event.target.files?.[0]
    event.target.value = ''
    if (!file || isSubmitting) return

    if (file.size > PROFILE_IMAGE_MAX_BYTES) {
      setError(MY_INFO_TEXT.imageTooLarge)
      return
    }
    if (!(await isAuthenticImageFile(file))) {
      setError(MY_INFO_TEXT.imageNotSupported)
      return
    }

    setError('')
    setIsSubmitting(true)
    const presignResult = await requestProfileImagePresign(file)
    if (!presignResult.ok) {
      setIsSubmitting(false)
      setError(presignResult.message)
      return
    }

    const uploaded = await uploadProfileImage(presignResult.data, file)
    if (!uploaded) {
      setIsSubmitting(false)
      setError(MY_INFO_TEXT.imageUploadFailed)
      return
    }

    const updateResult = await requestProfileImageUpdate(presignResult.data.objectKey)
    setIsSubmitting(false)
    if (!updateResult.ok) {
      setError(updateResult.message)
      return
    }

    onChangeImage(updateResult.data.profileImageUrl)
    showToast(MY_INFO_TEXT.imageSelected)
  }

  async function handleReset() {
    if (isSubmitting) return
    setError('')
    setIsSubmitting(true)
    const result = await requestProfileImageReset()
    setIsSubmitting(false)
    if (!result.ok) {
      setError(result.message)
      return
    }
    onChangeImage(result.data.profileImageUrl)
    showToast(MY_INFO_TEXT.imageResetDone)
  }

  return (
    <div className="flex items-center gap-base">
      <ProfileAvatar nickname={nickname} imageUrl={imageUrl} className={AVATAR_CLASS} />

      <div className="flex min-w-0 flex-1 flex-col gap-xs">
        <div className="flex flex-wrap gap-xs">
          <Button
            variant="secondary"
            onClick={() => inputRef.current?.click()}
            disabled={isSubmitting}
          >
            <Camera size={CAMERA_ICON_SIZE} />
            {isSubmitting ? '처리 중…' : MY_INFO_TEXT.imageChange}
          </Button>
          {imageUrl && (
            <Button variant="tertiary" onClick={handleReset} disabled={isSubmitting}>
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
