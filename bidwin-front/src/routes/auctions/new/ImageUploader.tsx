import { useRef } from 'react'
import type { ChangeEvent } from 'react'
import { Loader2, Plus, X } from 'lucide-react'
import { ALLOWED_IMAGE_CONTENT_TYPES, MAX_IMAGE_COUNT, TEXT } from './constants'

type AuctionImageStatus = 'uploading' | 'done' | 'error'

interface AuctionImageItem {
  id: string
  file: File
  previewUrl: string
  status: AuctionImageStatus
  uploadId?: string
}

interface ImageUploaderProps {
  items: AuctionImageItem[]
  onAddFiles: (files: File[]) => void
  onRemove: (id: string) => void
  disabled?: boolean
}

const REMOVE_BUTTON_LABEL = '이미지 삭제'
const ADD_TILE_ICON_SIZE = 20
const STATUS_ICON_SIZE = 18

function ImageUploader({ items, onAddFiles, onRemove, disabled = false }: ImageUploaderProps) {
  const fileInputRef = useRef<HTMLInputElement>(null)
  const canAddMore = items.length < MAX_IMAGE_COUNT

  const handleFileChange = (event: ChangeEvent<HTMLInputElement>) => {
    const files = Array.from(event.target.files ?? [])
    event.target.value = ''
    if (files.length > 0) onAddFiles(files)
  }

  return (
    <div className="flex flex-col gap-xs">
      <span className="text-sm font-semibold text-body">{TEXT.imagesLabel}</span>
      <div className="flex flex-wrap gap-sm">
        {items.map((item) => (
          <div
            key={item.id}
            className="relative h-24 w-24 overflow-hidden rounded-md border border-hairline-soft bg-surface-soft"
          >
            <img
              src={item.previewUrl}
              alt=""
              className="h-full w-full object-cover"
            />
            {item.status === 'uploading' && (
              <div className="absolute inset-0 flex items-center justify-center bg-surface-dark/40">
                <Loader2
                  size={STATUS_ICON_SIZE}
                  className="animate-spin text-on-dark"
                />
              </div>
            )}
            {item.status === 'error' && (
              <div className="absolute inset-0 flex items-center justify-center bg-down/70 text-xs font-semibold text-on-dark">
                실패
              </div>
            )}
            <button
              type="button"
              onClick={() => onRemove(item.id)}
              aria-label={REMOVE_BUTTON_LABEL}
              className="absolute right-1 top-1 flex h-5 w-5 items-center justify-center rounded-full bg-surface-dark/70 text-on-dark hover:bg-surface-dark"
            >
              <X size={12} />
            </button>
          </div>
        ))}

        {canAddMore && (
          <button
            type="button"
            disabled={disabled}
            onClick={() => fileInputRef.current?.click()}
            className="flex h-24 w-24 flex-col items-center justify-center gap-xxs rounded-md border border-dashed border-hairline text-muted hover:border-primary hover:text-primary disabled:cursor-not-allowed disabled:opacity-60"
          >
            <Plus size={ADD_TILE_ICON_SIZE} />
            <span className="text-xs font-medium">{items.length}/{MAX_IMAGE_COUNT}</span>
          </button>
        )}
      </div>

      <input
        ref={fileInputRef}
        type="file"
        multiple
        accept={ALLOWED_IMAGE_CONTENT_TYPES.join(',')}
        onChange={handleFileChange}
        className="hidden"
      />
      <p className="text-sm text-muted">{TEXT.imagesHelper}</p>
    </div>
  )
}

export default ImageUploader
export type { AuctionImageItem, AuctionImageStatus, ImageUploaderProps }
