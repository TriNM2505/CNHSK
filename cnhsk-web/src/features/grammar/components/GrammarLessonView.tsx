import { ArrowRight, BookCheck, BookOpenText } from 'lucide-react'

import type { AnswerResult, FinishResult, ServedItemView } from '@/features/practice'
import { ChoiceQuestion, SessionProgress, SessionSummary } from '@/features/practice'
import {
  Button,
  Card,
  ConfirmPanel,
  EmptyState,
  ErrorState,
  HanziText,
  LevelBadge,
  PageHeader,
  Skeleton,
  type PageStateValue,
} from '@/shared/ui'

import { grammarTabForLevel } from '../grammarLevels'
import type { GrammarLesson, GrammarSummary } from '../types'

type LoadError = { message: string; requestId?: string }

export interface GrammarLessonViewProps {
  checkingActive?: boolean
  confirmOpen?: boolean
  current?: number
  error?: LoadError
  finishResult?: FinishResult
  hasActiveSession?: boolean
  item?: ServedItemView
  lesson?: GrammarLesson
  nextLookupError?: string
  nextLookupPending?: boolean
  nextPoint?: GrammarSummary
  onAnswer?: (answer: string) => Promise<unknown>
  onBegin?: () => void
  onNext?: () => void
  onRetry: () => void
  onSkipCancel?: () => void
  onSkipConfirm?: () => void
  onSkipRequest?: () => void
  pending?: boolean
  practiceError?: LoadError
  result?: AnswerResult
  stage?: 'reading' | 'exercise' | 'finished'
  state: PageStateValue
  total?: number
}

function numberFromExtra(result: FinishResult, key: string) {
  const value = result.extra?.[key]
  return typeof value === 'number' && Number.isFinite(value) ? value : undefined
}

function LessonContent({ lesson }: { lesson: GrammarLesson }) {
  return (
    <div className="space-y-5">
      <Card>
        <h2 className="text-h3 text-ink">Cấu trúc</h2>
        <p className="mt-3 font-hanzi text-h2 leading-relaxed text-ink" lang="zh-CN">
          {lesson.structure || lesson.text}
        </p>
      </Card>

      <Card>
        <h2 className="text-h3 text-ink">Giải thích</h2>
        <p className="mt-3 whitespace-pre-line text-body leading-relaxed text-ink-secondary">
          {lesson.explanation}
        </p>
      </Card>

      <section aria-labelledby="grammar-examples-title">
        <h2 className="text-h3 text-ink" id="grammar-examples-title">
          Ví dụ
        </h2>
        <div className="mt-3 divide-y divide-line rounded-md border border-line bg-surface px-4">
          {lesson.examples.map((example, index) => (
            <div className="py-4" key={`${example.cn}-${index}`}>
              <HanziText className="block" size="sm">
                {example.cn}
              </HanziText>
              {example.pinyin ? (
                <p className="mt-1 text-body-sm text-ink-secondary">{example.pinyin}</p>
              ) : null}
              <p className="mt-1 text-body text-ink">{example.vi}</p>
            </div>
          ))}
        </div>
      </section>

      {lesson.commonMistake?.trim() ? (
        <Card className="border-warning bg-warning-bg">
          <h2 className="text-h3 text-warning">Lỗi thường gặp</h2>
          <p className="mt-2 whitespace-pre-line text-body text-ink">{lesson.commonMistake}</p>
        </Card>
      ) : null}
    </div>
  )
}

function FinishedLesson({
  finishResult,
  lesson,
  nextLookupError,
  nextLookupPending,
  nextPoint,
}: Pick<
  GrammarLessonViewProps,
  'finishResult' | 'lesson' | 'nextLookupError' | 'nextLookupPending' | 'nextPoint'
>) {
  if (!finishResult || !lesson) return null
  const total = numberFromExtra(finishResult, 'exercisesTotal') ?? 0
  const correct = numberFromExtra(finishResult, 'correct') ?? 0
  const skipped = finishResult.extra?.skippedExercises === true
  const title = skipped || total === 0 ? 'Đã ghi nhận bài học' : `Đúng ${correct}/${total}`
  const listPath = `/learn/grammar?level=${grammarTabForLevel(lesson.hskLevel)}`

  return (
    <div className="space-y-3">
      <SessionSummary
        actions={
          <>
            <Button to={listPath} variant="outline">
              Quay lại danh sách
            </Button>
            {nextPoint ? (
              <Button to={`/learn/grammar/${nextPoint.id}`}>
                Học điểm tiếp theo
                <ArrowRight aria-hidden="true" className="size-5" />
              </Button>
            ) : null}
          </>
        }
        result={finishResult}
        title={title}
      />
      {total === 0 ? (
        <p className="text-center text-body-sm text-ink-secondary" role="status">
          Điểm này chưa có bài tập.
        </p>
      ) : null}
      {nextLookupPending ? (
        <p className="text-center text-body-sm text-ink-secondary" role="status">
          Đang tìm điểm ngữ pháp tiếp theo…
        </p>
      ) : null}
      {nextLookupError ? (
        <p className="text-center text-body-sm text-danger" role="alert">
          {nextLookupError}
        </p>
      ) : null}
    </div>
  )
}

function LessonLoading() {
  return (
    <div aria-label="Đang tải bài ngữ pháp" className="space-y-4">
      <Skeleton className="h-20" />
      <Skeleton className="h-36" />
      <Skeleton className="h-48" />
    </div>
  )
}

export function GrammarLessonView({
  checkingActive = false,
  confirmOpen = false,
  current = 1,
  error,
  finishResult,
  hasActiveSession = false,
  item,
  lesson,
  nextLookupError,
  nextLookupPending,
  nextPoint,
  onAnswer,
  onBegin,
  onNext,
  onRetry,
  onSkipCancel,
  onSkipConfirm,
  onSkipRequest,
  pending = false,
  practiceError,
  result,
  stage = 'reading',
  state,
  total = 1,
}: GrammarLessonViewProps) {
  if (state === 'loading') return <LessonLoading />

  if (state === 'empty') {
    return (
      <EmptyState
        action={<Button to="/learn/grammar">Quay lại danh sách</Button>}
        description="Điểm ngữ pháp này không tồn tại hoặc không còn khả dụng."
        icon={<BookOpenText aria-hidden="true" className="size-5" />}
        title="Không tìm thấy điểm ngữ pháp"
      />
    )
  }

  if (state === 'error' || !lesson) {
    return (
      <ErrorState
        message={error?.message ?? 'Vui lòng thử lại sau.'}
        onRetry={onRetry}
        requestId={error?.requestId}
        title="Không thể tải bài ngữ pháp"
      />
    )
  }

  return (
    <div>
      <PageHeader
        actions={<LevelBadge exact level={lesson.hskLevel} />}
        subtitle={lesson.titleVi}
        title={<HanziText size="lg">{lesson.text}</HanziText>}
      />

      <LessonContent lesson={lesson} />

      <section aria-label="Hoạt động bài học" className="mt-8 border-t border-line pt-6">
        {stage === 'reading' ? (
          <div className="space-y-3 text-center">
            <Button disabled={checkingActive || pending} loading={pending} onClick={onBegin}>
              <BookCheck aria-hidden="true" className="size-5" />
              {checkingActive
                ? 'Đang kiểm tra lượt học'
                : hasActiveSession
                  ? 'Tiếp tục bài tập'
                  : 'Đã hiểu'}
            </Button>
            {practiceError ? (
              <div className="mx-auto max-w-xl">
                <ErrorState
                  message={practiceError.message}
                  onRetry={onRetry}
                  requestId={practiceError.requestId}
                  title="Không thể tải lượt bài tập"
                />
              </div>
            ) : null}
          </div>
        ) : null}

        {stage === 'exercise' ? (
          <div className="space-y-5">
            {practiceError ? (
              <ErrorState
                message={practiceError.message}
                onRetry={onRetry}
                requestId={practiceError.requestId}
                title="Không thể tiếp tục bài tập"
              />
            ) : item && onAnswer ? (
              <>
                <SessionProgress current={current} total={total} />
                <Card className="space-y-5">
                  <ChoiceQuestion
                    feedbackMode="IMMEDIATE"
                    item={item}
                    key={`grammar-question-${item.no}`}
                    onAnswer={onAnswer}
                    pending={pending}
                    result={result}
                  />
                  <div className="flex flex-wrap justify-end gap-3">
                    <Button disabled={pending} onClick={onSkipRequest} variant="outline">
                      Bỏ qua bài tập
                    </Button>
                    {result ? (
                      <Button disabled={pending} onClick={onNext}>
                        Câu tiếp
                        <ArrowRight aria-hidden="true" className="size-5" />
                      </Button>
                    ) : null}
                  </div>
                </Card>
              </>
            ) : (
              <Skeleton className="h-64" />
            )}
          </div>
        ) : null}

        {stage === 'finished' ? (
          <FinishedLesson
            finishResult={finishResult}
            lesson={lesson}
            nextLookupError={nextLookupError}
            nextLookupPending={nextLookupPending}
            nextPoint={nextPoint}
          />
        ) : null}
      </section>

      <ConfirmPanel
        confirmLabel="Bỏ qua và ghi nhận bài học"
        message="Các câu chưa trả lời sẽ không được ghi. Bài học vẫn được đánh dấu là đã học nhưng không có điểm đầy đủ."
        onCancel={onSkipCancel ?? (() => undefined)}
        onConfirm={onSkipConfirm ?? (() => undefined)}
        open={confirmOpen}
        pending={pending}
        title="Bỏ qua phần bài tập?"
      />
    </div>
  )
}
