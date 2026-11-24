import { apiRequest } from '@/shared/api'

export type LearningTaskKind = 'SPEAKING' | 'WRITING'

/** A speaking or writing task of the competency tree, tied to one communicative node (NL). */
export type LearningTask = {
  id: number
  code: string
  kind: LearningTaskKind
  hskLevel: number
  nlKid: string
  titleVi: string
  promptVi: string
  promptZh: string | null
  minChars: number | null
  maxSeconds: number | null
}

/** Public, so visitors can see what each level asks for; sending still needs an account. */
export function getLearningTasks(
  kind: LearningTaskKind,
  hskLevel: number,
  signal?: AbortSignal,
): Promise<LearningTask[]> {
  const query = new URLSearchParams({ hskLevel: String(hskLevel), kind })
  return apiRequest<LearningTask[]>(`/api/public/learning/tasks?${query}`, { signal })
}
