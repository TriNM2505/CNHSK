import type { PublicReference, ReferenceKind } from '../types'

export type ReferenceStatus = 'DRAFT' | 'PUBLISHED' | 'HIDDEN'
export type ReferenceAction = 'publish' | 'hide' | 'unhide'
export type AdminReference = PublicReference & {
  status: ReferenceStatus
  sortOrder: number
  reportCount: number
  lastReportedAt: string | null
  publishedAt: string | null
  updatedAt: string
}
export type AdminReferencePage = {
  items: AdminReference[]
  page: number
  size: number
  total: number
}
export type AdminListParams = {
  kind?: ReferenceKind
  status?: ReferenceStatus
  reported?: boolean
  page?: number
  size?: number
}
export type ReferenceInput = {
  kind: ReferenceKind
  title: string
  description: string | null
  url: string
  thumbnailUrl: string | null
  hskMin: number | null
  hskMax: number | null
  tags: string[]
  sortOrder: number
  author: string | null
  publisher: string | null
  publishedYear: number | null
  isAffiliate: boolean
}
