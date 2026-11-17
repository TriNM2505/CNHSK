import { useState } from 'react'
import type { PreviewState } from '@/app/preview/types'
import type { ServedItemView } from '@/features/practice/types'
import { AuthContext, type AuthContextValue } from '@/shared/auth/AuthContext'
import { LearnerLayout } from '@/shared/layout'
import { Button, EmptyState, ErrorState, Skeleton } from '@/shared/ui'
import { QuizAttemptView } from './QuizAttemptView'

const sampleItems: ServedItemView[] = [
  ['水', 'nước', 'lửa', 'núi', 'cây'],
  ['朋友', 'bạn bè', 'gia đình', 'thầy giáo', 'học sinh'],
  ['今天', 'hôm qua', 'ngày mai', 'hôm nay', 'buổi tối'],
  ['学习', 'nghỉ ngơi', 'học tập', 'đi bộ', 'ăn cơm'],
  ['书', 'bút', 'bàn', 'ghế', 'sách'],
  ['谢谢', 'xin chào', 'cảm ơn', 'tạm biệt', 'xin lỗi'],
  ['中国', 'Việt Nam', 'Nhật Bản', 'Trung Quốc', 'Hàn Quốc'],
  ['老师', 'giáo viên', 'bác sĩ', 'học sinh', 'bạn bè'],
].map(([text, ...options], index) => ({
  no: index + 1,
  type: 'QUESTION_CHOICE',
  skill: 'VOCAB',
  prompt: { text, hint: 'Chọn nghĩa tiếng Việt phù hợp.' },
  options: options.map((option, optionIndex) => ({
    key: String.fromCharCode(65 + optionIndex),
    text: option,
  })),
  rules: {},
}))

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

type AttemptPreviewProps = {
  state: PreviewState
  variant?: 'retry' | 'save-failed' | 'pending' | 'submit' | 'exit'
}

export function QuizAttemptScreenPreview({ state, variant }: AttemptPreviewProps) {
  const [currentNo, setCurrentNo] = useState(1)
  const [choices, setChoices] = useState<Record<number, string | null>>(
    variant === 'save-failed' || variant === 'pending'
      ? { 1: 'A', 2: 'A', 3: 'C' }
      : { 2: 'A', 3: 'C' },
  )
  const [confirming, setConfirming] = useState<'submit' | 'exit' | undefined>(
    variant === 'submit' || variant === 'exit' ? variant : undefined,
  )
  const [saveFailed, setSaveFailed] = useState(variant === 'save-failed')
  const [completed, setCompleted] = useState<'submit' | 'exit'>()
  const content =
    state === 'loading' ? (
      <div
        className="mx-auto max-w-reading space-y-4 p-4"
        role="status"
        aria-label="Đang tải lượt quiz"
      >
        <Skeleton variant="card" />
        <Skeleton variant="card" />
      </div>
    ) : state === 'empty' ? (
      <div className="p-4">
        <EmptyState
          title="Quiz chưa có câu hỏi được duyệt"
          description="Quay lại danh sách để chọn quiz khác."
        />
      </div>
    ) : state === 'error' ? (
      <div className="p-4">
        <ErrorState
          title="Không tải được lượt quiz"
          message="Không thể tải quiz. Vui lòng thử lại."
          onRetry={() => undefined}
        />
      </div>
    ) : completed ? (
      <div className="mx-auto max-w-reading p-6">
        <EmptyState
          title={completed === 'submit' ? 'Đã xác nhận nộp quiz mẫu' : 'Đã xác nhận thoát quiz mẫu'}
          description="Bản xem trước chỉ dùng dữ liệu mẫu, không gửi hay lưu lượt quiz."
          action={
            <Button
              variant="outline"
              onClick={() => {
                setCompleted(undefined)
                setConfirming(undefined)
              }}
            >
              Xem lại bản mẫu
            </Button>
          }
        />
      </div>
    ) : (
      <QuizAttemptView
        topicId={43}
        rankingEligible={variant !== 'retry'}
        items={sampleItems}
        currentNo={currentNo}
        choices={choices}
        elapsedMs={185000}
        pending={variant === 'pending'}
        saveState={
          saveFailed
            ? 'failed'
            : variant === 'pending'
              ? 'saving'
              : choices[currentNo]
                ? 'saved'
                : undefined
        }
        error={
          saveFailed
            ? 'Mất kết nối khi lưu lựa chọn. Câu trả lời của bạn vẫn được giữ trên màn hình.'
            : undefined
        }
        confirming={confirming}
        onNavigate={setCurrentNo}
        onChoose={(key) => setChoices((value) => ({ ...value, [currentNo]: key }))}
        onSubmit={() => setConfirming('submit')}
        onExit={() => setConfirming('exit')}
        onConfirm={() => {
          setCompleted(confirming)
          setConfirming(undefined)
        }}
        onCancel={() => setConfirming(undefined)}
        onRetryAnswer={() => setSaveFailed(false)}
      />
    )
  return (
    <AuthContext.Provider value={previewAuth}>
      <LearnerLayout gameOrigin="http://localhost:5174">{content}</LearnerLayout>
    </AuthContext.Provider>
  )
}
