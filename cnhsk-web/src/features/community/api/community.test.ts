import { beforeEach, describe, expect, it, vi } from 'vitest'

import { apiRequest, ApiError } from '@/shared/api'
import {
  comment,
  moderationComment,
  moderationPage,
  moderationPost,
  ownPage,
  ownPost,
  postDetail,
  postWrite,
  publishedPage,
  reportCase,
  reportPage,
} from '../test-fixtures'
import * as comments from './comments'
import * as moderation from './moderation'
import * as posts from './posts'
import { reportContent } from './reports'

vi.mock('@/shared/api', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/shared/api')>()),
  apiRequest: vi.fn(),
}))

const request = vi.mocked(apiRequest)
const signal = new AbortController().signal
const commentWrite = { content: '中文\nBình luận' }
const reportWrite = {
  targetType: 'COMMENT',
  targetId: 11,
  reason: 'OTHER',
  detail: 'Chi tiết',
} as const
const decision = { decision: 'ACTION', note: 'Vi phạm quy định', commentAction: 'HIDE' } as const
const commentModeration = { action: 'RESTORE', reason: 'Đã kiểm tra' } as const

const operations = [
  {
    name: 'FR012_listPublishedPosts',
    invoke: () => posts.listPublishedPosts({ category: 'QUESTION', page: 2, size: 7 }, signal),
    path: '/api/public/community/posts?category=QUESTION&page=2&size=7',
    init: { signal },
    response: publishedPage,
  },
  {
    name: 'FR015_getPublishedPost',
    invoke: () => posts.getPublishedPost(42, signal),
    path: '/api/public/community/posts/42',
    init: { signal },
    response: postDetail,
  },
  {
    name: 'FR009_createPost',
    invoke: () => posts.createPost(postWrite),
    path: '/api/community/posts',
    init: { method: 'POST', body: postWrite },
    response: ownPost,
  },
  {
    name: 'FR010_updatePost',
    invoke: () => posts.updatePost(42, postWrite),
    path: '/api/community/posts/42',
    init: { method: 'PUT', body: postWrite },
    response: ownPost,
  },
  {
    name: 'FR016_listMyPosts',
    invoke: () => posts.listMyPosts({ status: 'REJECTED', page: 1, size: 9 }, signal),
    path: '/api/community/posts/mine?status=REJECTED&page=1&size=9',
    init: { signal },
    response: ownPage,
  },
  {
    name: 'FR016_getMyPost',
    invoke: () => posts.getMyPost(42, signal),
    path: '/api/community/posts/mine/42',
    init: { signal },
    response: ownPost,
  },
  {
    name: 'FR056_getViewerState',
    invoke: () => posts.getViewerState(42, signal),
    path: '/api/community/posts/42/viewer-state',
    init: { signal },
    response: { liked: true, reported: false },
  },
  {
    name: 'FR028_likePost',
    invoke: () => posts.likePost(42),
    path: '/api/community/posts/42/like',
    init: { method: 'POST' },
    response: { liked: true, likeCount: 6 },
  },
  {
    name: 'FR029_unlikePost',
    invoke: () => posts.unlikePost(42),
    path: '/api/community/posts/42/like',
    init: { method: 'DELETE' },
    response: { liked: false, likeCount: 5 },
  },
  {
    name: 'FR018_listPostComments',
    invoke: () => comments.listPostComments(42, signal),
    path: '/api/public/community/posts/42/comments',
    init: { signal },
    response: [comment],
  },
  {
    name: 'FR017_createComment',
    invoke: () => comments.createComment(42, commentWrite),
    path: '/api/community/posts/42/comments',
    init: { method: 'POST', body: commentWrite },
    response: comment,
  },
  {
    name: 'FR020_replyToComment',
    invoke: () => comments.replyToComment(11, commentWrite),
    path: '/api/community/comments/11/replies',
    init: { method: 'POST', body: commentWrite },
    response: comment,
  },
  {
    name: 'FR024_updateComment',
    invoke: () => comments.updateComment(11, commentWrite),
    path: '/api/community/comments/11',
    init: { method: 'PUT', body: commentWrite },
    response: comment,
  },
  {
    name: 'FR026_deleteComment',
    invoke: () => comments.deleteComment(11),
    path: '/api/community/comments/11',
    init: { method: 'DELETE' },
    response: undefined,
  },
  {
    name: 'FR032_reportContent',
    invoke: () => reportContent(reportWrite),
    path: '/api/community/reports',
    init: { method: 'POST', body: reportWrite },
    response: { received: true },
  },
  {
    name: 'FR039_listPendingPosts',
    invoke: () => moderation.listPendingPosts({ page: 3, size: 4 }, signal),
    path: '/api/community/moderation/posts?page=3&size=4',
    init: { signal },
    response: moderationPage,
  },
  {
    name: 'FR040_getModerationPost',
    invoke: () => moderation.getModerationPost(42, signal),
    path: '/api/community/moderation/posts/42',
    init: { signal },
    response: moderationPost,
  },
  {
    name: 'FR041_approvePost',
    invoke: () => moderation.approvePost(42),
    path: '/api/community/moderation/posts/42/approve',
    init: { method: 'PATCH' },
    response: moderationPost,
  },
  {
    name: 'FR043_rejectPost',
    invoke: () => moderation.rejectPost(42, { reason: 'Trùng nội dung' }),
    path: '/api/community/moderation/posts/42/reject',
    init: { method: 'PATCH', body: { reason: 'Trùng nội dung' } },
    response: moderationPost,
  },
  {
    name: 'FR046_listPendingReportCases',
    invoke: () => moderation.listPendingReportCases({ page: 1, size: 8 }, signal),
    path: '/api/community/moderation/reports?page=1&size=8',
    init: { signal },
    response: reportPage,
  },
  {
    name: 'FR047_getReportCase',
    invoke: () => moderation.getReportCase(9, signal),
    path: '/api/community/moderation/reports/9',
    init: { signal },
    response: reportCase,
  },
  {
    name: 'FR048_decideReportCase',
    invoke: () => moderation.decideReportCase(9, decision),
    path: '/api/community/moderation/reports/9',
    init: { method: 'PATCH', body: decision },
    response: reportCase,
  },
  {
    name: 'FR063_listPostCommentsForModeration',
    invoke: () => moderation.listPostCommentsForModeration(42, signal),
    path: '/api/community/moderation/posts/42/comments',
    init: { signal },
    response: [moderationComment],
  },
  {
    name: 'FR052_moderateComment',
    invoke: () => moderation.moderateComment(11, commentModeration),
    path: '/api/community/moderation/comments/11',
    init: { method: 'PATCH', body: commentModeration },
    response: moderationComment,
  },
]

describe('community OpenAPI wrappers (§13; AT-01–47 and AT-51 transport only)', () => {
  beforeEach(() => {
    request.mockReset()
  })

  it.each(operations)(
    '$name sends the exact contract and preserves the response',
    async ({ invoke, path, init, response }) => {
      request.mockResolvedValue(response)
      await expect(invoke()).resolves.toBe(response)
      expect(request).toHaveBeenCalledTimes(1)
      expect(request).toHaveBeenCalledWith(path, init)
    },
  )

  it.each(operations)('$name propagates client rejection unchanged', async ({ invoke }) => {
    const error = new ApiError({
      status: 403,
      errorCode: 'FORBIDDEN',
      message: 'Không có quyền',
      requestId: 'req-test',
    })
    request.mockRejectedValue(error)
    await expect(invoke()).rejects.toBe(error)
    expect(request).toHaveBeenCalledOnce()
  })

  it('FR012_FR016_FR039_FR046_default pagination is explicit and invalid input reaches server validation', async () => {
    request.mockResolvedValue(undefined)
    await posts.listPublishedPosts()
    await posts.listMyPosts()
    await moderation.listPendingPosts()
    await moderation.listPendingReportCases()
    expect(request.mock.calls.map(([path]) => path)).toEqual([
      '/api/public/community/posts?page=0&size=20',
      '/api/community/posts/mine?page=0&size=20',
      '/api/community/moderation/posts?page=0&size=20',
      '/api/community/moderation/reports?page=0&size=20',
    ])
    request.mockClear()
    await posts.listPublishedPosts({ page: -1, size: 51 })
    await posts.listMyPosts({ page: 99, size: 0 })
    await moderation.listPendingPosts({ page: -2, size: 51 })
    await moderation.listPendingReportCases({ page: 1, size: 0 })
    expect(request.mock.calls.map(([path]) => path)).toEqual([
      '/api/public/community/posts?page=-1&size=51',
      '/api/community/posts/mine?page=99&size=0',
      '/api/community/moderation/posts?page=-2&size=51',
      '/api/community/moderation/reports?page=1&size=0',
    ])
  })
})
