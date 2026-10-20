export type PronunciationStageState = 'LOCKED' | 'AVAILABLE' | 'COMPLETED'

export interface PronunciationStageSummary {
  id: number
  stageIndex: number
  name: string
  description?: string | null
  targetSounds: string[]
  passPercent: number
  unitCount: number
  playable: boolean
  state: PronunciationStageState
  bestAccuracy?: number | null
  activeSessionId?: number | null
  activeAnswered?: number | null
}

export interface PronunciationFinishExtra extends Record<string, unknown> {
  stageIndex: number
  accuracy: number | null
  bestAccuracy: number | null
  passPercent: number
  passed: boolean
  unlockedNextStage: boolean
  nextStageIndex: number | null
  needed: number | null
  completedAllStages: boolean
  noScore: boolean
}
