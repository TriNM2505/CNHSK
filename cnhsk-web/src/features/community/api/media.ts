import { apiRequest } from '@/shared/api'

import type { MediaItem } from '../types'

export type UploadSignature = {
  cloudName: string
  apiKey: string
  folder: string
  timestamp: number
  signature: string
  uploadUrl: string
}

/** Cloudinary's free plan accepts images up to 10 MB and videos up to 100 MB. */
export const MAX_IMAGE_BYTES = 10 * 1024 * 1024
export const MAX_VIDEO_BYTES = 100 * 1024 * 1024
export const MAX_MEDIA_ITEMS = 4

export function getUploadSignature(): Promise<UploadSignature> {
  return apiRequest<UploadSignature>('/api/community/media/signature', { method: 'POST' })
}

/** Why a file cannot be attached, or undefined when it can. */
export function rejectFile(file: File): string | undefined {
  const isVideo = file.type.startsWith('video/')
  if (!isVideo && !file.type.startsWith('image/')) return `${file.name}: chỉ nhận ảnh hoặc video.`
  if (isVideo && file.size > MAX_VIDEO_BYTES) return `${file.name}: video tối đa 100 MB.`
  if (!isVideo && file.size > MAX_IMAGE_BYTES) return `${file.name}: ảnh tối đa 10 MB.`
  return undefined
}

/**
 * Sends one file straight from the browser to Cloudinary with a server-made signature; the server never sees the
 * bytes. Progress goes from 0 to 1.
 */
export function uploadToCloudinary(
  file: File,
  signature: UploadSignature,
  onProgress?: (fraction: number) => void,
): Promise<MediaItem> {
  const form = new FormData()
  form.append('file', file)
  form.append('api_key', signature.apiKey)
  form.append('timestamp', String(signature.timestamp))
  form.append('folder', signature.folder)
  form.append('signature', signature.signature)

  return new Promise((resolve, reject) => {
    const request = new XMLHttpRequest()
    request.open('POST', signature.uploadUrl)
    request.upload.onprogress = (event) => {
      if (event.lengthComputable) onProgress?.(event.loaded / event.total)
    }
    request.onerror = () => reject(new Error(`Không tải được ${file.name} lên.`))
    request.onload = () => {
      try {
        const body = JSON.parse(request.responseText) as {
          error?: { message?: string }
          public_id?: string
          resource_type?: string
          secure_url?: string
        }
        if (request.status >= 400 || !body.secure_url || !body.public_id) {
          reject(new Error(body.error?.message ?? `Không tải được ${file.name} lên.`))
          return
        }
        resolve({
          kind: body.resource_type === 'video' ? 'VIDEO' : 'IMAGE',
          publicId: body.public_id,
          url: body.secure_url,
        })
      } catch {
        reject(new Error(`Không tải được ${file.name} lên.`))
      }
    }
    request.send(form)
  })
}
