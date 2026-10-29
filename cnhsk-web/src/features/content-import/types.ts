export type ImportDataType =
  'LEXEME' | 'TOPIC' | 'TOPIC_ITEM' | 'GRAMMAR' | 'GRAMMAR_EXERCISE' | 'PRON_STAGE' | 'PRON_UNIT'

export type ImportRunStatus = 'PENDING' | 'RUNNING' | 'COMPLETED' | 'FAILED' | 'EXPIRED'
export type ImportRowAction = 'CREATE' | 'UPDATE' | 'UNCHANGED'

export interface ImportTemplateColumn {
  name: string
  required: boolean
  maxLength?: number | null
  description?: string
  example?: string
}

export interface ImportTemplate {
  dataType: ImportDataType
  label: string
  naturalKey: string[]
  dependsOn: ImportDataType[]
  columns: ImportTemplateColumn[]
}

export interface ImportTotals {
  total: number
  valid: number
  invalid: number
  create: number
  update: number
  unchanged: number
}

export interface ImportResult {
  total: number
  valid: number
  invalid: number
  created: number
  updated: number
  unchanged: number
  failed: number
}

export interface ImportRowError {
  line: number
  column?: string | null
  value?: string | null
  code: string
  message: string
}

export interface ImportPreviewRow {
  line: number
  action: ImportRowAction
  values: Record<string, string | null>
}

export interface ImportPreview {
  runId: number
  dataType: ImportDataType
  fileName: string
  status: 'PENDING'
  totals: ImportTotals
  alreadyImported: boolean
  expiresAt: string
  preview: ImportPreviewRow[]
  errors: ImportRowError[]
  errorCount: number
}

export interface ImportRun {
  runId: number
  /** Null only for FAILED / FILE_TOO_LARGE before multipart metadata was read. */
  dataType: ImportDataType | null
  fileName: string | null
  status: ImportRunStatus
  createdAt: string
  completedAt?: string | null
  createdBy: { id: number; displayName: string }
  totals?: ImportTotals | null
  result?: ImportResult | null
  errorCount: number
  failureReason?: string | null
}

export interface ImportRunPage {
  items: ImportRun[]
  page: number
  size: number
  totalElements: number
}

export interface ImportErrorPage {
  items: ImportRowError[]
  page: number
  size: number
  totalElements: number
}

export interface ConfirmImportRequest {
  acknowledgeRerun: boolean
}
