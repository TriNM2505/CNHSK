import { useState } from 'react'
import { Trophy } from 'lucide-react'
import type { PreviewState } from '@/app/preview/types'
import { AuthContext, type AuthContextValue } from '@/shared/auth/AuthContext'
import { LearnerLayout } from '@/shared/layout'
import { Button, EmptyState, ErrorState, Skeleton } from '@/shared/ui'
import { quizResult } from '../test-fixtures'
import type { QuizResult, TopicRankEntry } from '../types'
import { QuizResultView } from './QuizResultView'
import { QuizRankingList } from './QuizResultParts'

const previewAuth: AuthContextValue = {
  user: {
    id: 1,
    displayName: 'Học viên xem trước',
    email: '',
    emailVerified: true,
    roles: ['LEARNER'],
  },
  status: 'authenticated',
  error: null,
  reloadCurrentUser: async () => undefined,
  setAuthenticatedUser: () => undefined,
  signOut: async () => undefined,
}

const sampleResult: QuizResult = {
  ...quizResult,
  score: 75,
  correctCount: 3,
  wrongCount: 1,
  durationMs: 185000,
  topicName: 'Giao tiếp hằng ngày · 每天学习',
  firstAttempt: { score: 75, durationMs: 185000, rank: 12, total: 240 },
  items: [
    {
      itemId: 1,
      prompt: 'Chọn nghĩa của 水.',
      options: [
        { key: 'A', text: 'nước' },
        { key: 'B', text: 'lửa' },
        { key: 'C', text: 'núi' },
        { key: 'D', text: 'cây' },
      ],
      chosen: 'A',
      correct: 'A',
      explanation: '水 có nghĩa là nước. Ví dụ: 喝水 — uống nước.',
    },
    {
      itemId: 2,
      prompt: 'Chọn từ có nghĩa là bạn bè.',
      options: [
        { key: 'A', text: '老师' },
        { key: 'B', text: '朋友' },
        { key: 'C', text: '学校' },
        { key: 'D', text: '学生' },
      ],
      chosen: 'B',
      correct: 'B',
      explanation: '朋友 là bạn bè; 老师 là giáo viên.',
    },
    {
      itemId: 3,
      prompt: 'Chọn nghĩa của 今天.',
      options: [
        { key: 'A', text: 'hôm qua' },
        { key: 'B', text: 'ngày mai' },
        { key: 'C', text: 'hôm nay' },
        { key: 'D', text: 'buổi tối' },
      ],
      chosen: null,
      correct: 'C',
      explanation: '今天 nghĩa là hôm nay. Câu bỏ trống được tính sai.',
    },
    {
      itemId: 4,
      prompt: 'Chọn từ có nghĩa là học tập.',
      options: [
        { key: 'A', text: '工作' },
        { key: 'B', text: '学习' },
        { key: 'C', text: '休息' },
        { key: 'D', text: '吃饭' },
      ],
      chosen: 'B',
      correct: 'B',
      explanation: '学习 nghĩa là học tập. Ví dụ: 我学习中文。',
    },
  ],
}

const sampleRanking: TopicRankEntry[] = [
  'Bạn học mùa thu',
  'Người yêu tiếng Trung',
  '每天进步',
  'Một trang sách mới',
  'Học cùng nhau',
  'Bình minh',
].map((displayName, index) => ({
  userId: index + 1,
  rank: index + 1,
  displayName,
  avatarUrl: null,
  score: 100 - index * 5,
  durationMs: 95000 + index * 12000,
}))

export type QuizResultPreviewVariant =
  'retry' | 'suspicious' | 'zero' | 'no-first' | 'pending' | 'retry-error' | 'no-answers'

export function QuizResultScreenPreview({
  state,
  variant,
}: {
  state: PreviewState
  variant?: QuizResultPreviewVariant
}) {
  const [retrySelected, setRetrySelected] = useState(false)
  const result: QuizResult = {
    ...sampleResult,
    ...(variant === 'retry' ? { rankingEligible: false } : {}),
    ...(variant === 'suspicious' ? { suspicious: true } : {}),
    ...(variant === 'no-first' ? { firstAttempt: null } : {}),
    ...(variant === 'no-answers' ? { items: [] } : {}),
    ...(variant === 'zero'
      ? {
          score: 0,
          correctCount: 0,
          wrongCount: sampleResult.items.length,
          durationMs: 0,
          firstAttempt: { score: 0, durationMs: 0, rank: null, total: null },
          items: sampleResult.items.map((item) => ({ ...item, chosen: null })),
        }
      : {}),
  }
  const content =
    state === 'loading' ? (
      <div
        className="mx-auto max-w-content space-y-4 p-4"
        role="status"
        aria-label="Đang tải kết quả quiz"
      >
        <Skeleton variant="card" />
        <Skeleton variant="card" />
      </div>
    ) : state === 'error' ? (
      <div className="p-4">
        <ErrorState
          title="Không tải được kết quả quiz"
          message="Không thể tải quiz. Vui lòng thử lại."
          onRetry={() => undefined}
        />
      </div>
    ) : state === 'empty' ? (
      <div className="p-4">
        <EmptyState
          title="Chưa có kết quả quiz"
          description="Chọn một quiz và hoàn thành lượt làm để xem kết quả."
        />
      </div>
    ) : retrySelected ? (
      <div className="mx-auto max-w-reading p-6">
        <EmptyState
          title="Đã chọn làm lại quiz mẫu"
          description="Bản xem trước không bắt đầu lượt thật hoặc ghi dữ liệu."
          action={
            <div className="flex flex-wrap gap-3">
              <Button to="/__preview/frame/scr-044-retry/ready">Xem mẫu lượt làm lại</Button>
              <Button variant="outline" onClick={() => setRetrySelected(false)}>
                Quay lại kết quả mẫu
              </Button>
            </div>
          }
        />
      </div>
    ) : (
      <main className="mx-auto w-full min-w-0 max-w-content space-y-6 p-4 sm:p-6">
        <QuizResultView
          result={result}
          pending={variant === 'pending'}
          actionError={
            variant === 'retry-error'
              ? 'Bạn đã đạt giới hạn 30 lượt bắt đầu quiz trong một giờ. Vui lòng thử lại sau.'
              : undefined
          }
          leaderboardAvailable={false}
          onRetry={() => setRetrySelected(true)}
        />
        <section aria-label="Top 10 chủ đề" className="quiz-top-ten min-w-0 space-y-4 pb-24">
          <h2 className="flex items-center gap-2 text-h2">
            <Trophy aria-hidden="true" className="size-5 text-brand-600" />
            Top 10 chủ đề
          </h2>
          <QuizRankingList entries={sampleRanking} />
        </section>
      </main>
    )
  return (
    <AuthContext.Provider value={previewAuth}>
      <LearnerLayout gameOrigin="http://localhost:5174">{content}</LearnerLayout>
    </AuthContext.Provider>
  )
}
