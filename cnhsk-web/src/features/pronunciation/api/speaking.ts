import { uploadToCloudinary, type UploadSignature } from '@/features/community/api/media'
import type { GradingRequest } from '@/features/grading/types'
import { apiRequest } from '@/shared/api'

export type SpeakingKind = 'WORD' | 'SENTENCE'

/** One word or sentence to read aloud; without `audioUrl` the browser's Chinese voice reads the model. */
export type SpeakingItem = {
  kind: SpeakingKind
  text: string
  pinyin: string | null
  meaning: string | null
  audioUrl: string | null
  hskLevel: number
}

export function getSpeakingItems(hskLevel: number, kind: SpeakingKind, count = 10): Promise<SpeakingItem[]> {
  const query = new URLSearchParams({ count: String(count), hskLevel: String(hskLevel), kind })
  return apiRequest<{ items: SpeakingItem[] }>(`/api/speaking/items?${query}`).then((page) => page.items)
}

/** Uploads the recording straight to Cloudinary, then queues it for a teacher. */
export async function submitSpeaking(
  item: SpeakingItem,
  recording: Blob,
  onProgress?: (fraction: number) => void,
): Promise<GradingRequest> {
  const signature = await apiRequest<UploadSignature>('/api/speaking/upload-signature', { method: 'POST' })
  const extension = recording.type.includes('mp4') ? 'm4a' : recording.type.includes('ogg') ? 'ogg' : 'webm'
  const file = new File([recording], `speaking.${extension}`, { type: recording.type || 'audio/webm' })
  const uploaded = await uploadToCloudinary(file, signature, onProgress)
  return apiRequest<GradingRequest>('/api/grading-requests/speaking', {
    body: {
      audioUrl: uploaded.url,
      hskLevel: item.hskLevel,
      meaning: item.meaning,
      pinyin: item.pinyin,
      publicId: uploaded.publicId,
      text: item.text,
    },
    method: 'POST',
  })
}
