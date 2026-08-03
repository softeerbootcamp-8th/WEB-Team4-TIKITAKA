import { postJson } from './client'
import type { ApiResult } from './client'

const AUCTION_IMAGE_API_PATH = {
  drafts: '/api/v1/uploads/auction-images/drafts',
  presign: '/api/v1/uploads/auction-images/presign',
}

const PUT_METHOD = 'PUT'

/* 백엔드 AuctionImageDraftResponse */
interface AuctionImageDraftResponse {
  draftId: string
}

/* 백엔드 AuctionImagePresignRequest와 1:1 대응 */
interface AuctionImagePresignRequest {
  fileName: string
  contentType: string
  size: number
}

interface AuctionImagePresignBatchRequest {
  draftId: string
  images: AuctionImagePresignRequest[]
}

/* 백엔드 AuctionImagePresignResponse. signedHeaders는 S3 PUT 업로드 시 그대로 실어 보내야 한다. */
interface AuctionImagePresignResponse {
  presignedUrl: string
  objectKey: string
  signedHeaders: Record<string, string[]>
  expiresAt: string
}

function requestAuctionImageDraft(): Promise<ApiResult<AuctionImageDraftResponse>> {
  return postJson<AuctionImageDraftResponse, Record<string, never>>(
    AUCTION_IMAGE_API_PATH.drafts,
    {},
  )
}

function requestAuctionImagePresign(
  request: AuctionImagePresignBatchRequest,
): Promise<ApiResult<AuctionImagePresignResponse[]>> {
  return postJson<AuctionImagePresignResponse[], AuctionImagePresignBatchRequest>(
    AUCTION_IMAGE_API_PATH.presign,
    request,
  )
}

/*
 * presign 응답의 objectKey/signedHeaders를 그대로 S3에 PUT한다.
 * 우리 서버가 아닌 스토리지로 직접 올리는 요청이라 쿠키·JSON 봉투를 쓰지 않는다.
 */
async function uploadImageToPresignedUrl(
  presign: AuctionImagePresignResponse,
  file: File,
): Promise<boolean> {
  const headers = new Headers()
  for (const [name, values] of Object.entries(presign.signedHeaders)) {
    if (values[0] !== undefined) headers.set(name, values[0])
  }

  try {
    const response = await fetch(presign.presignedUrl, {
      method: PUT_METHOD,
      headers,
      body: file,
    })
    return response.ok
  } catch {
    return false
  }
}

export {
  requestAuctionImageDraft,
  requestAuctionImagePresign,
  uploadImageToPresignedUrl,
}
export type {
  AuctionImagePresignBatchRequest,
  AuctionImagePresignRequest,
  AuctionImagePresignResponse,
}
