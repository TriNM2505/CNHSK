export type PostCategory = 'EXPERIENCE' | 'QUESTION' | 'STUDY_BUDDY' | 'CULTURE' | 'JOB'
export type PostStatus = 'DRAFT' | 'PENDING' | 'APPROVED' | 'REJECTED' | 'HIDDEN'
export type CommentStatus = 'VISIBLE' | 'HIDDEN' | 'DELETED'
export type ReportTargetType = 'POST' | 'COMMENT'
export type ReportReason = 'SPAM' | 'OFFENSIVE' | 'HARASSMENT' | 'COPYRIGHT' | 'OTHER'
export type CaseStatus = 'PENDING' | 'ACTIONED' | 'REJECTED'

export interface ErrorResponse {
  error_code: string
  message: string
  request_id: string
  retryAfterSeconds?: number
  details?: { field?: string; message?: string }[]
}

export interface UserRef {
  id: number
  displayName: string | null
}

export interface PostSummary {
  id: number
  title: string
  category: PostCategory
  excerpt: string
  author: UserRef
  publishedAt: string
  commentCount: number
  likeCount: number
}

export interface PostDetail extends PostSummary {
  content: string
}

export interface PostSummaryPage {
  items: PostSummary[]
  page: number
  size: number
  totalElements: number
}

export interface PostWrite {
  title: string
  content: string
  category: PostCategory
  submit: boolean
}

export interface OwnPost {
  id: number
  title: string
  content: string
  category: PostCategory
  status: PostStatus
  reviewReason?: string | null
  publishedAt?: string | null
  createdAt: string
  updatedAt: string
}

export interface OwnPostPage {
  items: OwnPost[]
  page: number
  size: number
  totalElements: number
}

export interface ViewerState {
  liked: boolean
  reported: boolean
}

export interface LikeState {
  liked: boolean
  likeCount: number
}

export interface CommentWrite {
  content: string
}

export interface Comment {
  id: number
  postId: number
  parentId: number | null
  depth: number
  status: CommentStatus
  content: string | null
  author: UserRef | null
  createdAt: string
  editedAt: string | null
}

export interface ReportWrite {
  targetType: ReportTargetType
  targetId: number
  reason: ReportReason
  detail?: string | null
}

export interface ReportReceived {
  received: true
}

export interface ModerationPostSummary {
  id: number
  title: string
  category: PostCategory
  excerpt: string
  author: UserRef
  createdAt: string
  updatedAt: string
}

export interface ModerationPostPage {
  items: ModerationPostSummary[]
  page: number
  size: number
  totalElements: number
}

export interface ModerationPost {
  id: number
  title: string
  content: string
  category: PostCategory
  status: PostStatus
  author: UserRef
  reviewReason?: string | null
  reviewedBy?: UserRef | null
  reviewedAt?: string | null
  publishedAt?: string | null
  createdAt: string
  updatedAt: string
}

export interface RejectWrite {
  reason: string
}

export interface ReportCaseSummary {
  id: number
  targetType: ReportTargetType
  targetId: number
  reportCount: number
  latestReason: ReportReason
  createdAt: string
  targetPreview: string
}

export interface ReportCasePage {
  items: ReportCaseSummary[]
  page: number
  size: number
  totalElements: number
}

export interface ReportedPost {
  kind: 'POST'
  id: number
  title: string
  content: string
  category: PostCategory
  status: PostStatus
  author: UserRef
}

export interface ReportedComment {
  kind: 'COMMENT'
  id: number
  content: string
  status: CommentStatus
  author: UserRef
  postId: number
  postTitle: string
}

export interface ReportEntry {
  reporter: UserRef
  reason: ReportReason
  detail: string | null
  reportedAt: string
}

export interface ReportCaseDetail {
  id: number
  status: CaseStatus
  reportCount: number
  target: ReportedPost | ReportedComment
  reports: ReportEntry[]
  handledBy?: UserRef | null
  handledAt?: string | null
  decisionNote?: string | null
  createdAt: string
}

export interface ReportDecision {
  decision: 'ACTION' | 'REJECT'
  note: string
  commentAction?: 'HIDE' | 'DELETE'
}

export interface CommentModeration {
  action: 'HIDE' | 'DELETE' | 'RESTORE'
  reason: string
}

export interface ModerationComment {
  id: number
  postId: number
  parentId: number | null
  depth: number
  status: CommentStatus
  content: string
  author: UserRef
  createdAt: string
  moderatedBy: UserRef | null
  moderationReason: string | null
}

export interface PaginationParams {
  page?: number
  size?: number
}

export interface PublishedPostListParams extends PaginationParams {
  category?: PostCategory
}

export interface MyPostListParams extends PaginationParams {
  status?: PostStatus
}
