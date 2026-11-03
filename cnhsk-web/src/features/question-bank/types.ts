export type KnowledgeDomain = 'NA' | 'CH' | 'TV' | 'NP' | 'NL' | 'VB'
export type SkillType = 'LISTENING' | 'READING' | 'WRITING' | 'VOCAB' | 'GRAMMAR' | 'CHARACTER'
export type QuestionType =
  | 'MULTIPLE_CHOICE'
  | 'TRUE_FALSE'
  | 'IMAGE_MATCH'
  | 'SENTENCE_MATCH'
  | 'FILL_BLANK'
  | 'SENTENCE_ORDER'
  | 'ESSAY'
export type QuestionStatus = 'PENDING_REVIEW' | 'APPROVED' | 'REJECTED' | 'ARCHIVED'
export type QuestionSource = 'IMPORT' | 'AI' | 'MANUAL'
export type LabelRole = 'PRIMARY' | 'SECONDARY'

export type KnowledgeTreeParams = {
  domain?: KnowledgeDomain
  hskLevel?: number
}

export type KnowledgeNode = {
  kid: string
  domain: KnowledgeDomain
  hskLevel: number
  nameVi: string
  nameZh: string
  canDo?: string | null
  skillType?: SkillType | null
  prereqKids: string[]
  supportKids: string[]
}

export type KnowledgeTree = {
  nodes: KnowledgeNode[]
}

export type KnowledgeNodeDetail = KnowledgeNode & {
  dependentKids: string[]
  topics: { id: number; code: string; name: string }[]
  lexemeCount: number
  approvedQuestionCount: number
}

export type QuestionListParams = {
  hskLevel?: number
  qtype?: QuestionType
  status?: QuestionStatus
  kid?: string
  q?: string
  page?: number
  size?: number
}

export type QuestionSummary = {
  id: number
  code?: string | null
  qtype: QuestionType
  hskLevel: number
  difficulty: number
  stemSummary: string
  source: QuestionSource
  status: QuestionStatus
  primaryKid?: string | null
  secondaryKids: string[]
  used: boolean
}

export type QuestionPage = {
  items: QuestionSummary[]
  page: number
  size: number
  totalElements: number
}

export type QuestionOption = {
  key: string
  text: string
  isCorrect: boolean
  errorKid?: string | null
  errorNote?: string | null
}

export type QuestionLabel = {
  kid: string
  nameVi: string
  role: LabelRole
}

export type QuestionDetail = {
  id: number
  code?: string | null
  qtype: QuestionType
  hskLevel: number
  difficulty: number
  stem: string
  options: QuestionOption[]
  correctKey?: string | null
  explanation?: string | null
  audioUrl?: string | null
  imageUrl?: string | null
  source: QuestionSource
  status: QuestionStatus
  reviewReason?: string | null
  labels: QuestionLabel[]
  used: boolean
}

export type QuestionUpdate = {
  stem: string
  options: QuestionOption[]
  correctKey?: string | null
  explanation: string
  hskLevel: number
  difficulty: number
  audioUrl?: string | null
  imageUrl?: string | null
  primaryKid: string
  secondaryKids: string[]
}
