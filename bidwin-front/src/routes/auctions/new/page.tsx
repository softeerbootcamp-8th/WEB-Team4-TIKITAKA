import { useEffect, useState } from 'react'
import type { ChangeEvent, FormEvent } from 'react'
import { useNavigate } from 'react-router-dom'
import Button from '../../../components/ui/Button'
import Card from '../../../components/ui/Card'
import SegmentedControl from '../../../components/ui/SegmentedControl'
import Select from '../../../components/ui/Select'
import TextInput from '../../../components/ui/TextInput'
import Textarea from '../../../components/ui/Textarea'
import { useToast } from '../../../hooks/useToast'
import {
  calculateFileChecksumSha256,
  requestAuctionImageDraft,
  requestAuctionImagePresign,
  uploadImageToPresignedUrl,
} from '../../../lib/api/auctionImage'
import type { AuctionImagePresignResponse } from '../../../lib/api/auctionImage'
import { requestAuctionCreate } from '../../../lib/api/auctions'
import type { AuctionCategory } from '../../../lib/api/auctions'
import ImageUploader from './ImageUploader'
import type { AuctionImageItem } from './ImageUploader'
import {
  ALLOWED_IMAGE_CONTENT_TYPES,
  AUCTION_DURATION_OPTIONS,
  AUCTION_TYPE_OPTIONS,
  CATEGORY_OPTIONS,
  ERROR_MESSAGE,
  MAX_IMAGE_COUNT,
  MAX_IMAGE_SIZE_BYTES,
  TEXT,
  TRADE_TYPE_OPTIONS,
} from './constants'
import { validateAuctionFields } from './validation'
import type { AuctionFormFields } from './validation'

const ROUTE = {
  auctionList: '/auctions',
  home: '/',
}

type AuctionType = (typeof AUCTION_TYPE_OPTIONS)[number]['value']
type AuctionDurationMinutes = (typeof AUCTION_DURATION_OPTIONS)[number]['value']
type TradeType = (typeof TRADE_TYPE_OPTIONS)[number]['value']

let nextImageItemId = 0
function createImageItemId() {
  nextImageItemId += 1
  return `auction-image-${nextImageItemId}`
}

const NON_DIGIT_PATTERN = /\D/g

/* 원 단위 입력값을 1,000 같은 천 단위 구분 표기로 보여준다. 상태에는 숫자만 남긴다. */
function formatPriceDigits(digits: string) {
  if (digits === '') return ''
  return Number(digits).toLocaleString('ko-KR')
}

function AuctionRegisterPage() {
  const { showToast } = useToast()
  const navigate = useNavigate()

  const [draftId, setDraftId] = useState<string | null>(null)

  const [title, setTitle] = useState('')
  const [description, setDescription] = useState('')
  const [category, setCategory] = useState('')
  const [contact, setContact] = useState('')
  const [durationMinutes, setDurationMinutes] = useState<AuctionDurationMinutes>(
    AUCTION_DURATION_OPTIONS[0].value,
  )
  const [auctionType, setAuctionType] = useState<AuctionType>('DOWN')
  const [tradeType, setTradeType] = useState<TradeType>(TRADE_TYPE_OPTIONS[0].value)
  const [startPrice, setStartPrice] = useState('')
  const [buyNowPrice, setBuyNowPrice] = useState('')
  const [minimumPrice, setMinimumPrice] = useState('')
  const [dropPrice, setDropPrice] = useState('')
  const [priceDropInterval, setPriceDropInterval] = useState('')

  const [images, setImages] = useState<AuctionImageItem[]>([])
  const [error, setError] = useState<string | null>(null)
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [isSubmitted, setIsSubmitted] = useState(false)

  useEffect(() => {
    requestAuctionImageDraft().then((result) => {
      if (result.ok) setDraftId(result.data.draftId)
      else setError(ERROR_MESSAGE.draftInitFailed)
    })
  }, [])

  const updateImageItem = (id: string, patch: Partial<AuctionImageItem>) => {
    setImages((prev) =>
      prev.map((item) => (item.id === id ? { ...item, ...patch } : item)),
    )
  }

  const handleAddFiles = async (files: File[]) => {
    if (draftId === null) {
      setError(ERROR_MESSAGE.draftInitFailed)
      return
    }

    const remainingSlots = MAX_IMAGE_COUNT - images.length
    if (remainingSlots <= 0) {
      setError(ERROR_MESSAGE.tooManyImages)
      return
    }

    const candidates = files.slice(0, remainingSlots)
    const validFiles: File[] = []
    for (const file of candidates) {
      if (file.size > MAX_IMAGE_SIZE_BYTES) {
        setError(ERROR_MESSAGE.imageTooLarge)
        continue
      }
      if (!ALLOWED_IMAGE_CONTENT_TYPES.includes(file.type)) {
        setError(ERROR_MESSAGE.unsupportedImageType)
        continue
      }
      validFiles.push(file)
    }
    if (validFiles.length === 0) return

    const newItems: AuctionImageItem[] = validFiles.map((file) => ({
      id: createImageItemId(),
      file,
      previewUrl: URL.createObjectURL(file),
      status: 'uploading',
    }))
    setImages((prev) => [...prev, ...newItems])
    setError(null)

    let presignResult
    try {
      presignResult = await requestAuctionImagePresign({
        draftId,
        images: await Promise.all(
          validFiles.map(async (file) => ({
            fileName: file.name,
            contentType: file.type,
            size: file.size,
            checksumSha256: await calculateFileChecksumSha256(file),
          })),
        ),
      })
    } catch {
      newItems.forEach((item) => updateImageItem(item.id, { status: 'error' }))
      setError(ERROR_MESSAGE.imageUploadFailed)
      return
    }

    if (!presignResult.ok) {
      newItems.forEach((item) => updateImageItem(item.id, { status: 'error' }))
      setError(presignResult.message)
      return
    }

    await Promise.all(
      newItems.map(async (item, index) => {
        const presign = presignResult.data[index] as
          | AuctionImagePresignResponse
          | undefined
        if (!presign) {
          updateImageItem(item.id, { status: 'error' })
          return
        }
        const uploaded = await uploadImageToPresignedUrl(presign, item.file)
        updateImageItem(item.id, {
          status: uploaded ? 'done' : 'error',
          uploadId: uploaded ? presign.uploadId : undefined,
        })
      }),
    )
  }

  const handleRemoveImage = (id: string) => {
    setImages((prev) => {
      const target = prev.find((item) => item.id === id)
      if (target) URL.revokeObjectURL(target.previewUrl)
      return prev.filter((item) => item.id !== id)
    })
  }

  const handleFieldChange =
    (setField: (value: string) => void) => (event: ChangeEvent<HTMLInputElement>) => {
      setField(event.target.value)
      setError(null)
    }

  /* 콤마·원 등 표시용 문자를 지우고 숫자만 상태에 남긴다. */
  const handlePriceChange =
    (setField: (value: string) => void) => (event: ChangeEvent<HTMLInputElement>) => {
      setField(event.target.value.replace(NON_DIGIT_PATTERN, ''))
      setError(null)
    }

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    if (isSubmitting) return

    const fields: AuctionFormFields = {
      title: title.trim(),
      description: description.trim(),
      category,
      contact: contact.trim(),
      auctionType,
      tradeType,
      startPrice,
      buyNowPrice,
      minimumPrice,
      dropPrice,
      priceDropInterval,
    }

    const fieldError = validateAuctionFields(fields)
    if (fieldError) {
      setError(fieldError)
      return
    }
    if (images.length === 0) {
      setError(ERROR_MESSAGE.noImages)
      return
    }
    if (images.some((item) => item.status === 'uploading')) {
      setError(ERROR_MESSAGE.imagesUploading)
      return
    }
    if (images.some((item) => item.status === 'error')) {
      setError(ERROR_MESSAGE.imageUploadFailed)
      return
    }
    setError(null)

    setIsSubmitting(true)
    const imageUploadIds = images.flatMap((image) => image.uploadId ? [image.uploadId] : [])
    if (draftId === null || imageUploadIds.length !== images.length) {
      setIsSubmitting(false)
      setError(ERROR_MESSAGE.imageUploadFailed)
      return
    }

    const result = await requestAuctionCreate({
      draftId,
      title: fields.title,
      description: fields.description,
      category: fields.category as AuctionCategory,
      contact: fields.contact,
      auctionType,
      tradeType,
      durationMinutes: Number(durationMinutes),
      startPrice: Number(startPrice),
      buyNowPrice: auctionType === 'UP' && buyNowPrice ? Number(buyNowPrice) : null,
      minimumPrice: auctionType === 'DOWN' ? Number(minimumPrice) : null,
      dropPrice: auctionType === 'DOWN' ? Number(dropPrice) : null,
      priceDropInterval: auctionType === 'DOWN' ? Number(priceDropInterval) : null,
      imageUploadIds,
    })
    setIsSubmitting(false)
    if (!result.ok) {
      setError(result.message)
      return
    }

    showToast(TEXT.submitSuccessToast)
    setIsSubmitted(true)
  }

  if (isSubmitted) {
    return (
      <main className="flex min-h-dvh items-center justify-center px-lg">
        <Card className="flex w-full max-w-[420px] flex-col items-center gap-base text-center">
          <h1 className="text-xl font-bold text-ink">{TEXT.completeTitle}</h1>
          <p className="text-sm text-body">{TEXT.completeDescription}</p>
          <div className="flex w-full flex-col gap-xs">
            <Button className="w-full" onClick={() => navigate(ROUTE.auctionList)}>
              {TEXT.goToList}
            </Button>
            <Button
              variant="secondary"
              className="w-full"
              onClick={() => navigate(ROUTE.home)}
            >
              {TEXT.goToHome}
            </Button>
          </div>
        </Card>
      </main>
    )
  }

  return (
    <main className="mx-auto w-full max-w-[640px] px-lg py-xl">
      <div className="mb-lg flex flex-col gap-xxs">
        <h1 className="text-2xl font-bold text-ink">{TEXT.pageTitle}</h1>
        <p className="text-sm text-body">{TEXT.pageSubtitle}</p>
      </div>

      <form onSubmit={handleSubmit} noValidate className="flex flex-col gap-lg">
        <Card className="flex flex-col gap-base">
          <TextInput
            label={TEXT.titleLabel}
            value={title}
            onChange={handleFieldChange(setTitle)}
            placeholder={TEXT.titlePlaceholder}
            maxLength={30}
          />
          <Textarea
            label={TEXT.descriptionLabel}
            value={description}
            onChange={(event) => {
              setDescription(event.target.value)
              setError(null)
            }}
            placeholder={TEXT.descriptionPlaceholder}
          />
          <Select
            label={TEXT.categoryLabel}
            value={category}
            onChange={(event) => {
              setCategory(event.target.value)
              setError(null)
            }}
            options={[...CATEGORY_OPTIONS]}
            placeholder={TEXT.categoryPlaceholder}
          />
          <TextInput
            label={TEXT.contactLabel}
            value={contact}
            onChange={handleFieldChange(setContact)}
            placeholder={TEXT.contactPlaceholder}
            maxLength={100}
          />
        </Card>

        <Card className="flex flex-col gap-base">
          <ImageUploader
            items={images}
            onAddFiles={handleAddFiles}
            onRemove={handleRemoveImage}
            disabled={draftId === null}
          />
        </Card>

        <Card className="flex flex-col gap-base">
          <SegmentedControl
            label={TEXT.auctionTypeLabel}
            options={AUCTION_TYPE_OPTIONS}
            value={auctionType}
            onChange={setAuctionType}
          />
          <SegmentedControl
            label={TEXT.durationLabel}
            options={AUCTION_DURATION_OPTIONS}
            value={durationMinutes}
            onChange={setDurationMinutes}
          />
          <SegmentedControl
            label={TEXT.tradeTypeLabel}
            options={TRADE_TYPE_OPTIONS}
            value={tradeType}
            onChange={setTradeType}
          />
          <TextInput
            label={TEXT.startPriceLabel}
            type="text"
            inputMode="numeric"
            suffix="원"
            value={formatPriceDigits(startPrice)}
            onChange={handlePriceChange(setStartPrice)}
          />

          {auctionType === 'UP' ? (
            <TextInput
              label={TEXT.buyNowPriceLabel}
              type="text"
              inputMode="numeric"
              suffix="원"
              value={formatPriceDigits(buyNowPrice)}
              onChange={handlePriceChange(setBuyNowPrice)}
            />
          ) : (
            <>
              <TextInput
                label={TEXT.minimumPriceLabel}
                type="text"
                inputMode="numeric"
                suffix="원"
                value={formatPriceDigits(minimumPrice)}
                onChange={handlePriceChange(setMinimumPrice)}
              />
              <TextInput
                label={TEXT.dropPriceLabel}
                type="text"
                inputMode="numeric"
                suffix="원"
                value={formatPriceDigits(dropPrice)}
                onChange={handlePriceChange(setDropPrice)}
              />
              <TextInput
                label={TEXT.priceDropIntervalLabel}
                type="number"
                inputMode="numeric"
                min={1}
                suffix="분"
                value={priceDropInterval}
                onChange={handleFieldChange(setPriceDropInterval)}
                placeholder={TEXT.priceDropIntervalPlaceholder}
              />
            </>
          )}
        </Card>

        {error && (
          <p role="alert" className="rounded-sm bg-down-tint px-base py-sm text-sm font-medium text-down">
            {error}
          </p>
        )}

        <Button type="submit" size="lg" className="w-full" disabled={isSubmitting}>
          {isSubmitting ? TEXT.submitting : TEXT.submit}
        </Button>
      </form>
    </main>
  )
}

export default AuctionRegisterPage
