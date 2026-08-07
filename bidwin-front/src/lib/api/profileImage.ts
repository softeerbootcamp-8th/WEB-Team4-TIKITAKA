import { postJson } from './client'
import type { ApiResult } from './client'

const PROFILE_IMAGE_PRESIGN_PATH = '/api/v1/uploads/profile-images/presign'

export interface ProfileImagePresignResponse {
  presignedUrl: string
  objectKey: string
  signedHeaders: Record<string, string[]>
  expiresAt: string
}

export function requestProfileImagePresign(
  file: File,
): Promise<ApiResult<ProfileImagePresignResponse>> {
  return postJson<ProfileImagePresignResponse, {
    fileName: string
    contentType: string
    size: number
  }>(PROFILE_IMAGE_PRESIGN_PATH, {
    fileName: file.name,
    contentType: file.type,
    size: file.size,
  })
}

export async function uploadProfileImage(
  presign: ProfileImagePresignResponse,
  file: File,
): Promise<boolean> {
  const headers = new Headers()
  for (const [name, values] of Object.entries(presign.signedHeaders)) {
    if (values[0] !== undefined) headers.set(name, values[0])
  }

  try {
    const response = await fetch(presign.presignedUrl, {
      method: 'PUT',
      headers,
      body: file,
    })
    return response.ok
  } catch {
    return false
  }
}
