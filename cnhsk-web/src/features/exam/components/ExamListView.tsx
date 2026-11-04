import { Link } from 'react-router-dom'
import { Button } from '@/shared/ui/Button'
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
    <main className="mx-auto max-w-content space-y-6 p-4 sm:p-6">
      <h1 className="text-h1-sm text-ink sm:text-h1">Đề thi thử</h1>
      {actionError ? (
        <p role="alert" className="text-danger">
          {actionError}
        </p>
      ) : null}
      <PageState
        state={state}
        empty={null}
        loading={
          <div
            role="status"
            aria-label="Đang tải đề thi"
            className="animate-pulse rounded-md bg-canvas p-8"
          >
            Đang tải đề thi…
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
          className="rounded-md border border-line bg-surface p-6"
        >
          <h2 className="text-h2">Kiểm tra trình độ</h2>
          <p className="my-3 text-ink-secondary">Tìm cấp độ phù hợp qua bài xếp trình độ.</p>
          {placement?.active ? (
            <Link
              className="font-bold text-brand-700"
              to={`/learn/exams/sessions/${placement.active.sessionId}`}
            >
              Tiếp tục
            </Link>
          ) : placement?.last ? (
            <>
              <p>
                Kết quả gần nhất: {placementLabels[placement.last.level]}
                {placement.last.confidence === 'TENTATIVE' ? ' — Kết quả tạm' : ''}
              </p>
              <Link className="mr-4 text-brand-700" to={`/learn/placement/${placement.last.runId}`}>
                Xem kết quả
              </Link>
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
        <div aria-label="Chọn cấp HSK" role="group" className="my-6 flex gap-3">
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
        <section aria-label={`Đề HSK ${level}`}>
          <h2 className="mb-4 text-h2">HSK {level}</h2>
          {exams.length === 0 ? (
            <p>Đề HSK {level} đang được cập nhật</p>
          ) : (
            <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
              {exams.map((exam) => (
                <article
                  key={exam.id}
                  aria-label={exam.title}
                  className="rounded-md border border-line bg-surface p-5"
                >
                  <h3 className="text-h3">{exam.title}</h3>
                  <dl className="my-4 space-y-2 text-body-sm">
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
                    <Link
                      className="font-bold text-brand-700"
                      to={`/learn/exams/sessions/${exam.activeSessionId}`}
                    >
                      Tiếp tục
                    </Link>
                  ) : (
                    <Button disabled={pending} onClick={() => onStartExam(exam.id)}>
                      Bắt đầu
                    </Button>
                  )}
                </article>
              ))}
            </div>
          )}
        </section>
      </PageState>
    </main>
  )
}
