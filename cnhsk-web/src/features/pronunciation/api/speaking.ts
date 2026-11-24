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

/** Visitors read the same material through the public path; only sending a recording needs an account. */
export function getSpeakingItems(
  hskLevel: number,
  kind: SpeakingKind,
  guest = false,
  count = 10,
): Promise<SpeakingItem[]> {
  const query = new URLSearchParams({ count: String(count), hskLevel: String(hskLevel), kind })
  const path = guest ? '/api/public/speaking/items' : '/api/speaking/items'
  return apiRequest<{ items: SpeakingItem[] }>(`${path}?${query}`).then((page) => page.items)
}

/** Uploads the recording straight to Cloudinary, then queues it for a teacher. */
export async function submitSpeaking(
  item: SpeakingItem,
  recording: Blob,
  onProgress?: (fraction: number) => void,
  taskId?: number,
): Promise<GradingRequest> {
  const signature = await apiRequest<UploadSignature>('/api/speaking/upload-signature', {
    method: 'POST',
  })
  const extension = recording.type.includes('mp4')
    ? 'm4a'
    : recording.type.includes('ogg')
      ? 'ogg'
      : 'webm'
  const file = new File([recording], `speaking.${extension}`, {
    type: recording.type || 'audio/webm',
  })
  const uploaded = await uploadToCloudinary(file, signature, onProgress)
  return apiRequest<GradingRequest>('/api/grading-requests/speaking', {
    body: {
      audioUrl: uploaded.url,
      hskLevel: item.hskLevel,
      meaning: item.meaning,
      pinyin: item.pinyin,
      publicId: uploaded.publicId,
      ...(taskId ? { taskId } : {}),
      text: item.text,
    },
    method: 'POST',
  })
}
