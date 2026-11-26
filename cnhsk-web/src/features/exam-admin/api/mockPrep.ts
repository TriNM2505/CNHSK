import { uploadToCloudinary, type UploadSignature } from '@/features/community/api/media'
import { apiRequest } from '@/shared/api'

export type MockPartCount = {
  part: string
  section: number
  skill: string
  qtype: string
  picture: 'NONE' | 'STEM' | 'OPTIONS'
  approved: number
  pending: number
  needed: number
  /** Options per question; 0 for ordering and fill-in parts. */
  options: number
  /** How to write this part's import rows. */
  guide: string
}
export type MockPaperState = { id: number; code: string; status: string; questions: number }
export type MockPool = {
  level: number
  questionsPerPaper: number
  parts: MockPartCount[]
  papers: MockPaperState[]
}
export type WordImage = {
  lexemeId: number
  text: string
  pinyin?: string | null
  meaningVi: string
  imageUrl?: string | null
}

export function getMockPool(level: number, signal?: AbortSignal) {
  return apiRequest<MockPool>(`/api/admin/mock-prep/pool?level=${level}`, { signal })
}

export function buildMockPapers(level: number) {
  return apiRequest<{ built: string[]; kept: string[] }>('/api/admin/mock-prep/papers', {
    method: 'POST',
    body: { level },
  })
}

export function getWordImages(level: number, signal?: AbortSignal) {
  return apiRequest<WordImage[]>(`/api/admin/mock-prep/word-images?level=${level}`, { signal })
}

/** Uploads the picture straight to the word-picture folder, then attaches it to the word. */
export async function uploadWordImage(lexemeId: number, file: File) {
  const signature = await apiRequest<UploadSignature>('/api/admin/mock-prep/word-images/signature', {
    method: 'POST',
  })
  const uploaded = await uploadToCloudinary(file, signature)
  return apiRequest<WordImage>(`/api/admin/mock-prep/word-images/${lexemeId}`, {
    method: 'PUT',
    body: { imageUrl: uploaded.url, publicId: uploaded.publicId },
  })
}

export function removeWordImage(lexemeId: number) {
  return apiRequest<void>(`/api/admin/mock-prep/word-images/${lexemeId}`, { method: 'DELETE' })
}
