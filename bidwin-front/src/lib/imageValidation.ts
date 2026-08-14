const SUPPORTED_IMAGE_MIME_TYPES = ['image/jpeg', 'image/png', 'image/webp'] as const

function detectImageMimeType(bytes: Uint8Array): string | null {
  if (bytes[0] === 0xff && bytes[1] === 0xd8 && bytes[2] === 0xff) return 'image/jpeg'
  if (
    bytes[0] === 0x89 && bytes[1] === 0x50 && bytes[2] === 0x4e && bytes[3] === 0x47
    && bytes[4] === 0x0d && bytes[5] === 0x0a && bytes[6] === 0x1a && bytes[7] === 0x0a
  ) return 'image/png'
  if (
    String.fromCharCode(...bytes.slice(0, 4)) === 'RIFF'
    && String.fromCharCode(...bytes.slice(8, 12)) === 'WEBP'
  ) return 'image/webp'
  return null
}

async function isAuthenticImageFile(file: File): Promise<boolean> {
  const signature = new Uint8Array(await file.slice(0, 12).arrayBuffer())
  if (detectImageMimeType(signature) !== file.type) return false

  try {
    const image = await createImageBitmap(file)
    const hasMetadata = image.width > 0 && image.height > 0
    image.close()
    return hasMetadata
  } catch {
    return false
  }
}

export { SUPPORTED_IMAGE_MIME_TYPES, detectImageMimeType, isAuthenticImageFile }
