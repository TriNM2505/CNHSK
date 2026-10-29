import { describe, expect, expectTypeOf, it } from 'vitest'

import type { ScreenCatalog } from '@/app/screenRegistry'
import * as comments from './api/comments'
import * as moderation from './api/moderation'
import * as posts from './api/posts'
import { reportContent } from './api/reports'
import { POST_CATEGORY_LABELS, POST_STATUS_LABELS } from './labels'
import { screens } from './screens'
import type * as Community from './types'

describe('community contract types (compile-time assertions; §13)', () => {
  it('FR007_FR016_FR018_FR034_FR046_enum domains match the approved YAML', () => {
    expectTypeOf<Community.PostCategory>().toEqualTypeOf<
      'EXPERIENCE' | 'QUESTION' | 'STUDY_BUDDY' | 'CULTURE' | 'JOB'
    >()
    expectTypeOf<Community.PostStatus>().toEqualTypeOf<
      'DRAFT' | 'PENDING' | 'APPROVED' | 'REJECTED' | 'HIDDEN'
    >()
    expectTypeOf<Community.CommentStatus>().toEqualTypeOf<'VISIBLE' | 'HIDDEN' | 'DELETED'>()
    expectTypeOf<Community.ReportTargetType>().toEqualTypeOf<'POST' | 'COMMENT'>()
    expectTypeOf<Community.ReportReason>().toEqualTypeOf<
      'SPAM' | 'OFFENSIVE' | 'HARASSMENT' | 'COPYRIGHT' | 'OTHER'
    >()
    expectTypeOf<Community.CaseStatus>().toEqualTypeOf<'PENDING' | 'ACTIONED' | 'REJECTED'>()
  })

  it('FR004_FR006_FR018_FR038_FR047_FR063_preserves writable fields, nullability and target discrimination', () => {
    expectTypeOf<keyof Community.PostWrite>().toEqualTypeOf<
      'title' | 'content' | 'category' | 'submit'
    >()
    expectTypeOf<Community.PostWrite['submit']>().toEqualTypeOf<boolean>()
    expectTypeOf<keyof Community.CommentWrite>().toEqualTypeOf<'content'>()
    expectTypeOf<Community.UserRef>().toEqualTypeOf<{ id: number; displayName: string | null }>()
    expectTypeOf<
      Extract<keyof Community.PostSummary, 'email' | 'passwordHash' | 'roles'>
    >().toEqualTypeOf<never>()
    expectTypeOf<Community.Comment['content']>().toEqualTypeOf<string | null>()
    expectTypeOf<Community.Comment['author']>().toEqualTypeOf<Community.UserRef | null>()
    expectTypeOf<Community.Comment['editedAt']>().toEqualTypeOf<string | null>()
    expectTypeOf<Community.Comment['parentId']>().toEqualTypeOf<number | null>()
    expectTypeOf<Community.ModerationComment['content']>().toEqualTypeOf<string>()
    expectTypeOf<Community.ModerationComment['author']>().toEqualTypeOf<Community.UserRef>()
    expectTypeOf<
      Community.ModerationComment['moderatedBy']
    >().toEqualTypeOf<Community.UserRef | null>()
    expectTypeOf<Community.ModerationComment['moderationReason']>().toEqualTypeOf<string | null>()
    expectTypeOf<Community.OwnPost['reviewReason']>().toEqualTypeOf<string | null | undefined>()
    expectTypeOf<Community.OwnPost['publishedAt']>().toEqualTypeOf<string | null | undefined>()
    expectTypeOf<Community.ModerationPost['reviewedBy']>().toEqualTypeOf<
      Community.UserRef | null | undefined
    >()
    expectTypeOf<Community.ReportWrite['detail']>().toEqualTypeOf<string | null | undefined>()
    expectTypeOf<Community.ReportReceived['received']>().toEqualTypeOf<true>()
    expectTypeOf<Community.ReportCaseDetail['target']>().toEqualTypeOf<
      Community.ReportedPost | Community.ReportedComment
    >()
    expectTypeOf<
      Extract<Community.ReportCaseDetail['target'], { kind: 'POST' }>
    >().toEqualTypeOf<Community.ReportedPost>()
    expectTypeOf<
      Extract<Community.ReportCaseDetail['target'], { kind: 'COMMENT' }>
    >().toEqualTypeOf<Community.ReportedComment>()
    expectTypeOf<Community.ReportEntry['detail']>().toEqualTypeOf<string | null>()
    expectTypeOf<Community.ReportCaseDetail['handledBy']>().toEqualTypeOf<
      Community.UserRef | null | undefined
    >()
    expectTypeOf<Community.ReportDecision['decision']>().toEqualTypeOf<'ACTION' | 'REJECT'>()
    expectTypeOf<Community.ReportDecision['commentAction']>().toEqualTypeOf<
      'HIDE' | 'DELETE' | undefined
    >()
    expectTypeOf<Community.CommentModeration['action']>().toEqualTypeOf<
      'HIDE' | 'DELETE' | 'RESTORE'
    >()
    expectTypeOf<Community.RejectWrite>().toEqualTypeOf<{ reason: string }>()
    expectTypeOf<Community.ErrorResponse>().toEqualTypeOf<{
      error_code: string
      message: string
      request_id: string
      retryAfterSeconds?: number
      details?: { field?: string; message?: string }[]
    }>()
  })

  it('FR012_FR016_FR039_FR046_preserves pagination and item schema names', () => {
    expectTypeOf<Community.PaginationParams>().toEqualTypeOf<{ page?: number; size?: number }>()
    expectTypeOf<Community.PublishedPostListParams['category']>().toEqualTypeOf<
      Community.PostCategory | undefined
    >()
    expectTypeOf<Community.MyPostListParams['status']>().toEqualTypeOf<
      Community.PostStatus | undefined
    >()
    expectTypeOf<Community.PostSummaryPage['items']>().toEqualTypeOf<Community.PostSummary[]>()
    expectTypeOf<Community.OwnPostPage['items']>().toEqualTypeOf<Community.OwnPost[]>()
    expectTypeOf<Community.ModerationPostPage['items']>().toEqualTypeOf<
      Community.ModerationPostSummary[]
    >()
    expectTypeOf<Community.ReportCasePage['items']>().toEqualTypeOf<Community.ReportCaseSummary[]>()
    expectTypeOf<Community.PostDetail>().toMatchTypeOf<
      Community.PostSummary & { content: string }
    >()
    expectTypeOf(screens).toMatchTypeOf<ScreenCatalog>()
  })

  it('FR001_all 24 wrappers expose the declared response types without runtime schema claims', () => {
    expectTypeOf(posts.listPublishedPosts).returns.toEqualTypeOf<
      Promise<Community.PostSummaryPage>
    >()
    expectTypeOf(posts.getPublishedPost).returns.toEqualTypeOf<Promise<Community.PostDetail>>()
    expectTypeOf(posts.createPost).returns.toEqualTypeOf<Promise<Community.OwnPost>>()
    expectTypeOf(posts.updatePost).returns.toEqualTypeOf<Promise<Community.OwnPost>>()
    expectTypeOf(posts.listMyPosts).returns.toEqualTypeOf<Promise<Community.OwnPostPage>>()
    expectTypeOf(posts.getMyPost).returns.toEqualTypeOf<Promise<Community.OwnPost>>()
    expectTypeOf(posts.getViewerState).returns.toEqualTypeOf<Promise<Community.ViewerState>>()
    expectTypeOf(posts.likePost).returns.toEqualTypeOf<Promise<Community.LikeState>>()
    expectTypeOf(posts.unlikePost).returns.toEqualTypeOf<Promise<Community.LikeState>>()
    expectTypeOf(comments.listPostComments).returns.toEqualTypeOf<Promise<Community.Comment[]>>()
    expectTypeOf(comments.createComment).returns.toEqualTypeOf<Promise<Community.Comment>>()
    expectTypeOf(comments.replyToComment).returns.toEqualTypeOf<Promise<Community.Comment>>()
    expectTypeOf(comments.updateComment).returns.toEqualTypeOf<Promise<Community.Comment>>()
    expectTypeOf(comments.deleteComment).returns.toEqualTypeOf<Promise<void>>()
    expectTypeOf(reportContent).returns.toEqualTypeOf<Promise<Community.ReportReceived>>()
    expectTypeOf(moderation.listPendingPosts).returns.toEqualTypeOf<
      Promise<Community.ModerationPostPage>
    >()
    expectTypeOf(moderation.getModerationPost).returns.toEqualTypeOf<
      Promise<Community.ModerationPost>
    >()
    expectTypeOf(moderation.approvePost).returns.toEqualTypeOf<Promise<Community.ModerationPost>>()
    expectTypeOf(moderation.rejectPost).returns.toEqualTypeOf<Promise<Community.ModerationPost>>()
    expectTypeOf(moderation.listPendingReportCases).returns.toEqualTypeOf<
      Promise<Community.ReportCasePage>
    >()
    expectTypeOf(moderation.getReportCase).returns.toEqualTypeOf<
      Promise<Community.ReportCaseDetail>
    >()
    expectTypeOf(moderation.decideReportCase).returns.toEqualTypeOf<
      Promise<Community.ReportCaseDetail>
    >()
    expectTypeOf(moderation.listPostCommentsForModeration).returns.toEqualTypeOf<
      Promise<Community.ModerationComment[]>
    >()
    expectTypeOf(moderation.moderateComment).returns.toEqualTypeOf<
      Promise<Community.ModerationComment>
    >()
  })
})

describe('community Vietnamese labels (FR-061, AT-49 foundation only)', () => {
  it('FR061_maps every category and status to its exact approved label', () => {
    expect(POST_CATEGORY_LABELS).toEqual({
      EXPERIENCE: 'Chia sẻ kinh nghiệm',
      QUESTION: 'Hỏi bài',
      STUDY_BUDDY: 'Tìm bạn học',
      CULTURE: 'Văn hoá',
      JOB: 'Việc làm',
    })
    expect(POST_STATUS_LABELS).toEqual({
      DRAFT: 'Bản nháp',
      PENDING: 'Chờ duyệt',
      APPROVED: 'Đã đăng',
      REJECTED: 'Bị từ chối',
      HIDDEN: 'Đã bị ẩn',
    })
  })
})
