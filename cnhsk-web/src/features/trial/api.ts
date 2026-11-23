import { apiRequest } from '@/shared/api'

export type TrialFeature = 'VOCAB' | 'GRAMMAR' | 'LISTENING' | 'HANZI'

/** `answer` is the index of the right option; a trial is marked in the browser and never stored. */
export type TrialQuestion = {
  prompt: string
  pinyin: string | null
  audioUrl: string | null
  options: string[]
  answer: number
  explanation: string
}

export type TrialSet = { feature: TrialFeature; hskLevel: number; questions: TrialQuestion[] }

export function getTrial(feature: TrialFeature, hskLevel: number): Promise<TrialSet> {
  const query = new URLSearchParams({ feature, hskLevel: String(hskLevel) })
  return apiRequest<TrialSet>(`/api/public/learning/trial?${query}`)
}
