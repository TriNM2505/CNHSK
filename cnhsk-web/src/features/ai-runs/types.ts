export type RunStatus = 'RUNNING' | 'COMPLETED' | 'FAILED'
export type RunError =
  'INVALID_OUTPUT' | 'BUDGET_EXCEEDED' | 'PROVIDER_ERROR' | 'NO_VALID_QUESTION' | 'STALE'
export type AiRun = {
  id: number
  kid: string
  status: RunStatus
  createdAt: string
  completedAt: string | null
  questionCount: number
  droppedCount: number
  inputTokens: number
  outputTokens: number
  error: RunError | null
}
export type RunPage = {
  enabled: boolean
  monthTokens: number
  items: AiRun[]
  page: number
  size: number
  totalElements: number
}
