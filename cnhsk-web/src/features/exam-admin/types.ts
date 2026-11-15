export type ExamStatus = 'DRAFT' | 'PUBLISHED' | 'ARCHIVED'
export type SectionType = 'LISTENING' | 'READING' | 'WRITING'
export type PublicationError = {
  code: string
  message: string
  sectionIndex?: number | null
  questionId?: number | null
}
export type ExamSection = { name: string; type: SectionType; durationMin: number }
export type ExamQuestionWrite = { questionId: number; sectionIndex: number; points: number }
export type ExamWrite = {
  code: string
  title: string
  hskLevel: number
  totalScore: number
  sections: ExamSection[]
  questions: ExamQuestionWrite[]
}
export type ExamQuestion = ExamQuestionWrite & {
  orderIndex: number
  code: string | null
  qtype: string
  status: string
  kid: string | null
  hasAudio: boolean
}
export type ExamDetail = Omit<ExamWrite, 'sections' | 'questions'> & {
  id: number
  status: ExamStatus
  publishedAt: string | null
  etag: string
  sections: { name: string; type: SectionType | null; durationMin: number | null }[]
  questions: ExamQuestion[]
  publicationErrors: PublicationError[]
}
export type AdminExam = {
  id: number
  code: string
  title: string
  hskLevel: number
  status: ExamStatus
  publishedAt: string | null
  questionCount: number
  inUse: boolean
}
export type ExamPage = { items: AdminExam[]; page: number; size: number; totalElements: number }
export type ExamFilters = {
  status?: ExamStatus
  hsk?: number
  q?: string
  page?: number
  size?: number
}
export type DraftSection = {
  key: string
  name: string
  type: SectionType | ''
  durationMin: string
}
export type DraftQuestion = Omit<ExamQuestion, 'sectionIndex' | 'orderIndex' | 'points'> & {
  sectionKey: string
  points: string
}
export type ExamForm = {
  code: string
  title: string
  hskLevel: string
  totalScore: string
  sections: DraftSection[]
  questions: DraftQuestion[]
}
