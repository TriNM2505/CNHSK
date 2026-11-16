import { Button, Card, EmptyState, PageHeader, Skeleton } from '@/shared/ui'
import { SUPPORTED_HSK_LEVELS, type HskLevel } from '@/shared/ui/LevelBadge'
import { PageState, type PageStateValue } from '@/shared/ui/PageState'
import type { ExamSummary, PlacementLevel, PlacementSummary } from '../types'

const placementLabels: Record<PlacementLevel, string> = {
  BELOW_1: 'Chưa tới HSK 1',
  HSK1: 'HSK 1',
  HSK2: 'HSK 2',
  HSK3: 'HSK 3',
  ABOVE_3: 'HSK 3 trở lên',
}
export type ExamListViewProps = {
  exams: ExamSummary[]
  placement?: PlacementSummary
  level: HskLevel
  state: PageStateValue
  error?: string
  actionError?: string
  pending?: boolean
  onLevelChange: (level: HskLevel) => void
  onRetry: () => void
  onStartExam: (id: number) => void
  onStartPlacement: () => void
}
export function ExamListView({
  exams,
  placement,
  level,
  state,
  error,
  actionError,
  pending,
  onLevelChange,
  onRetry,
  onStartExam,
  onStartPlacement,
}: ExamListViewProps) {
  return (
    <main className="mx-auto w-full min-w-0 max-w-content space-y-6 px-4 py-6 pb-16 sm:px-6">
      <PageHeader
        title="Đề thi thử"
        subtitle="Chọn một đề để luyện tập, hoặc tìm cấp độ phù hợp với bạn."
      />
      {actionError ? (
        <p role="alert" className="text-danger">
          {actionError}
        </p>
      ) : null}
      <div aria-label="Chọn cấp HSK" role="group" className="flex flex-wrap gap-3">
        {SUPPORTED_HSK_LEVELS.map((value) => (
          <Button
            key={value}
            aria-pressed={value === level}
            onClick={() => onLevelChange(value)}
            variant={value === level ? 'primary' : 'outline'}
          >
            HSK {value}
          </Button>
        ))}
      </div>

      <PageState
        state={state}
        empty={
          <EmptyState
            title={`Đề HSK ${level} đang được cập nhật`}
            description="Bạn có thể chọn cấp HSK khác."
          />
        }
        loading={
          <div role="status" aria-label="Đang tải đề thi">
            <Skeleton variant="card" />
          </div>
        }
        error={
          <section role="alert">
            <p>{error ?? 'Không thể tải đề thi.'}</p>
            <Button onClick={onRetry}>Thử lại</Button>
          </section>
        }
      >
        <section
          aria-label="Kiểm tra trình độ"
          className="space-y-4 rounded-md border border-brand-100 bg-brand-50 p-6"
        >
          <h2 className="text-h2">Kiểm tra trình độ</h2>
          <p className="my-3 text-ink-secondary">Tìm cấp độ phù hợp qua bài xếp trình độ.</p>
          {placement?.active ? (
            <Button
              variant="outline"
              className="mt-auto self-start"
              to={`/learn/exams/sessions/${placement.active.sessionId}`}
            >
              Tiếp tục
            </Button>
          ) : placement?.last ? (
            <>
              <p>
                Kết quả gần nhất: {placementLabels[placement.last.level]}
                {placement.last.confidence === 'TENTATIVE' ? ' — Kết quả tạm' : ''}
              </p>
              <Button
                variant="outline"
                className="mr-4"
                to={`/learn/placement/${placement.last.runId}`}
              >
                Xem kết quả
              </Button>
              <Button disabled={pending} onClick={onStartPlacement}>
                Làm lại
              </Button>
            </>
          ) : (
            <Button disabled={pending} onClick={onStartPlacement}>
              Bắt đầu
            </Button>
          )}
        </section>
        <section aria-label={`Đề HSK ${level}`}>
          <h2 className="mb-4 text-h2">HSK {level}</h2>
          {exams.length === 0 ? (
            <p>Đề HSK {level} đang được cập nhật</p>
          ) : (
            <div className="grid min-w-0 gap-4 md:grid-cols-2 xl:grid-cols-3">
              {exams.map((exam) => (
                <Card
                  role="article"
                  key={exam.id}
                  aria-label={exam.title}
                  className="flex min-w-0 flex-col p-6"
                >
                  <h3 className="break-words text-h3">{exam.title}</h3>
                  <dl className="my-4 grid flex-1 grid-cols-2 gap-4 text-body-sm text-ink-secondary">
                    <div>
                      <dt>Số câu</dt>
                      <dd>{exam.questionCount}</dd>
                    </div>
                    <div>
                      <dt>Số phần</dt>
                      <dd>{exam.sections.length}</dd>
                    </div>
                    <div>
                      <dt>Số lần làm</dt>
                      <dd>{exam.myAttempts}</dd>
                    </div>
                    <div>
                      <dt>Điểm cao nhất</dt>
                      <dd>{exam.myBest === null ? 'Chưa có điểm' : `${exam.myBest.percent}%`}</dd>
                    </div>
                  </dl>
                  {exam.activeSessionId !== null ? (
                    <Button
                      variant="outline"
                      className="font-bold text-brand-700"
                      to={`/learn/exams/sessions/${exam.activeSessionId}`}
                    >
                      Tiếp tục
                    </Button>
                  ) : (
                    <Button disabled={pending} onClick={() => onStartExam(exam.id)}>
                      Bắt đầu
                    </Button>
                  )}
                </Card>
              ))}
            </div>
          )}
        </section>
      </PageState>
    </main>
  )
}
