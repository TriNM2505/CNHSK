import type { ScreenId } from '@/app/screenRegistry'

export type ChatTurn = { role: 'user' | 'assistant'; text: string }

export type AskRequest = {
  question: string
  history?: ChatTurn[]
  pageContext?: ScreenId | null
}

export type AskResult = { answer: string; outOfScope: boolean }
