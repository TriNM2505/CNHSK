import type {
  Comment,
  ModerationComment,
  ModerationPost,
  ModerationPostPage,
  OwnPost,
  OwnPostPage,
  PostDetail,
  PostSummaryPage,
  PostWrite,
  ReportCaseDetail,
  ReportCasePage,
} from './types'

const timestamp = '2026-10-30T03:00:00Z'
export const author = { id: 7, displayName: null }
export const postWrite: PostWrite = {
  title: 'Hỏi cách học chữ',
  content: 'Nội dung\n中文',
  category: 'QUESTION',
  submit: true,
}
export const ownPost: OwnPost = {
  title: postWrite.title,
  content: postWrite.content,
  category: postWrite.category,
  id: 42,
  status: 'PENDING',
  createdAt: timestamp,
  updatedAt: timestamp,
  reviewReason: null,
  publishedAt: null,
}
export const ownPostDraft: OwnPost = { ...ownPost, id: 46, title: 'Bản nháp mẫu', status: 'DRAFT', reviewReason: null, publishedAt: null }
export const postDetail: PostDetail = {
  id: 42,
  title: postWrite.title,
  content: postWrite.content,
  category: 'QUESTION',
  excerpt: postWrite.content,
  author,
  publishedAt: timestamp,
  commentCount: 2,
  likeCount: 5,
}
export const publishedPage: PostSummaryPage = {
  items: [
    {
      id: 42,
      title: postWrite.title,
      category: 'QUESTION',
      excerpt: postWrite.content,
      author,
      publishedAt: timestamp,
      commentCount: 2,
      likeCount: 5,
    },
  ],
  page: 0,
  size: 20,
  totalElements: 1,
}
export const ownPage: OwnPostPage = { items: [ownPost], page: 0, size: 20, totalElements: 1 }
export const ownPostRejected: OwnPost = { ...ownPost, id: 44, status: 'REJECTED', reviewReason: 'Vui lòng bổ sung nguồn trích dẫn.' }
export const ownPostHidden: OwnPost = { ...ownPost, id: 45, status: 'HIDDEN', reviewReason: 'Bài viết vi phạm quy định cộng đồng.' }
export const ownPostApproved: OwnPost = { ...ownPost, id: 43, status: 'APPROVED', reviewReason: null, publishedAt: timestamp }
export const ownPageWithStatuses: OwnPostPage = {
  items: [ownPostDraft, ownPost, ownPostApproved, ownPostRejected, ownPostHidden],
  page: 0,
  size: 20,
  totalElements: 5,
}
export const comment: Comment = {
  id: 11,
  postId: 42,
  parentId: null,
  depth: 0,
  status: 'VISIBLE',
  content: '你好',
  author,
  createdAt: timestamp,
  editedAt: null,
}
export const publicDiscussion: Comment[] = [
  { ...comment, id: 101, status: 'HIDDEN', content: null, author: null },
  { ...comment, id: 102, parentId: 101, depth: 1, status: 'DELETED', content: null, author: null },
  { ...comment, id: 103, parentId: 102, depth: 2, content: 'Trả lời vẫn còn\n中文' },
  { ...comment, id: 104, parentId: 103, depth: 3, content: '<img src=x onerror=alert(1)>' },
]
export const moderationPost: ModerationPost = {
  ...ownPost,
  author,
  reviewedBy: null,
  reviewedAt: null,
}
export const moderationPage: ModerationPostPage = {
  items: [
    {
      id: 42,
      title: postWrite.title,
      category: 'QUESTION',
      excerpt: postWrite.content,
      author,
      createdAt: timestamp,
      updatedAt: timestamp,
    },
  ],
  page: 0,
  size: 20,
  totalElements: 1,
}
export const moderationComment: ModerationComment = {
  id: 11,
  postId: 42,
  parentId: null,
  depth: 0,
  status: 'HIDDEN',
  content: 'Nội dung ẩn',
  author,
  createdAt: timestamp,
  moderatedBy: { id: 8, displayName: 'Kiểm duyệt viên' },
  moderationReason: 'Nội dung không phù hợp',
}
export const reportCase: ReportCaseDetail = {
  id: 9,
  status: 'PENDING',
  reportCount: 1,
  target: {
    kind: 'COMMENT',
    id: 11,
    content: 'Nội dung ẩn',
    status: 'HIDDEN',
    author,
    postId: 42,
    postTitle: postWrite.title,
  },
  reports: [
    {
      reporter: { id: 10, displayName: 'Người báo cáo' },
      reason: 'SPAM',
      detail: null,
      reportedAt: timestamp,
    },
  ],
  createdAt: timestamp,
  handledBy: null,
  handledAt: null,
  decisionNote: null,
}
export const reportPage: ReportCasePage = {
  items: [
    {
      id: 9,
      targetType: 'COMMENT',
      targetId: 11,
      reportCount: 1,
      latestReason: 'SPAM',
      createdAt: timestamp,
      targetPreview: 'Nội dung ẩn',
    },
  ],
  page: 0,
  size: 20,
  totalElements: 1,
}

export const moderationPostStates: ModerationPost[] = (['DRAFT', 'PENDING', 'APPROVED', 'REJECTED', 'HIDDEN'] as const).map((status) => ({
  ...moderationPost,
  status,
  reviewReason: status === 'REJECTED' || status === 'HIDDEN' ? 'Lý do kiểm duyệt mẫu.' : null,
}))
export const reportedPostCase: ReportCaseDetail = { ...reportCase, id: 10, target: { kind: 'POST', id: 42, title: postWrite.title, content: postWrite.content, category: 'QUESTION', status: 'APPROVED', author } }
export const handledReportCase: ReportCaseDetail = { ...reportCase, id: 12, status: 'ACTIONED', handledBy: { id: 8, displayName: 'Kiểm duyệt viên' }, handledAt: timestamp, decisionNote: 'Ghi chú xử lý mẫu.' }
