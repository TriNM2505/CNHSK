export type ReferenceKind = 'YOUTUBE_CHANNEL' | 'PODCAST' | 'BOOK'

export type PublicReference = {
  id: number
  kind: ReferenceKind
  title: string
  description: string | null
  url: string
  thumbnailUrl: string | null
  hskMin: number | null
  hskMax: number | null
  tags: string[]
  author?: string | null
  publisher?: string | null
  publishedYear?: number | null
  isAffiliate?: boolean
}

export type PublicReferencePage = {
  items: PublicReference[]
  page: number
  size: number
  total: number
}

export type ReferenceListParams = {
  kind: ReferenceKind
  hsk?: number
  tag?: string
  page?: number
  size?: number
}

export type ReportRequest = {
  reason: 'DEAD_LINK' | 'WRONG_CONTENT' | 'OTHER'
  note?: string | null
}
