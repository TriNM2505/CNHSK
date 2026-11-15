export type ContestStatus = 'DRAFT' | 'PUBLISHED' | 'CANCELLED'
export type ScheduleState = 'UPCOMING' | 'ONGOING' | 'ENDED'
export type Prize = { fromRank: number; toRank: number; description: string }
export type ContestWrite = {
  name: string
  description?: string | null
  examId?: number | null
  startsAt: string
  endsAt: string
  maxParticipants?: number | null
  prizes?: Prize[]
}
export type AdminContest = {
  id: number
  name: string
  description: string | null
  examId: number | null
  examTitle: string | null
  startsAt: string
  endsAt: string
  maxParticipants: number | null
  prizes: Prize[]
  status: ContestStatus
  state: ScheduleState
  rankingPublishedAt: string | null
  participantCount: number
}
export type Page<T> = { items: T[]; page: number; size: number; totalElements: number }
export type AdminContestPage = Page<AdminContest>
export type ContestListParams = { status?: ContestStatus; page?: number; size?: number }
export type Participant = {
  userId: number
  displayName: string | null
  status: string
  score: number | null
  maxScore: number | null
  durationMs: number | null
  submittedAt: string | null
  suspicious: boolean
  cheatEventCount: number
}
export type ParticipantDecision = { excluded: boolean; note: string }
export type Winner = {
  rank: number
  userId: number
  displayName: string | null
  email: string
  score: number
  durationMs: number | null
  prize: string
}
// GET /api/admin/exams: contract of spec 023, kept separate from learner exams.
export type AdminExamSummary = {
  id: number
  code: string
  title: string
  hskLevel: number
  status: 'DRAFT' | 'PUBLISHED' | 'ARCHIVED'
  publishedAt: string | null
  questionCount: number
  inUse: boolean
}
export type ExamListParams = { q?: string; page?: number; size?: number }
export type ContestAction = 'publish' | 'cancel' | 'ranking'
