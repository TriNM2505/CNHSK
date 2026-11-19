import {
  AlertTriangle,
  ArrowLeft,
  ArrowRight,
  BookCheck,
  BookOpenText,
  CheckCircle2,
  Lightbulb,
  Quote,
  Sparkles,
  Target,
} from 'lucide-react'

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
  PageBanner,
  Skeleton,
  StatusBadge,
  type PageStateValue,
} from '@/shared/ui'

import { grammarTabForLevel } from '../grammarLevels'
import type { GrammarLesson, GrammarSummary } from '../types'
import './GrammarLessonView.css'

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

function LessonProse({ text }: { text: string }) {
  return <span className={/\p{Script=Han}/u.test(text) ? 'font-hanzi' : undefined}>{text}</span>
}

function LessonContent({ lesson }: { lesson: GrammarLesson }) {
  return (
    <div className="min-w-0 space-y-6">
      <Card className="relative isolate overflow-hidden border-brand-100 bg-brand-50 p-5 sm:p-6">
        <div className="relative z-base space-y-4">
          <div className="flex items-center gap-3">
            <span className="flex size-10 shrink-0 items-center justify-center rounded-md border border-brand-100 bg-surface text-brand-600">
              <Sparkles aria-hidden="true" className="size-5" />
            </span>
            <div>
              <h2 className="text-h3 text-ink">Cấu trúc</h2>
              <p className="text-body-sm text-ink-secondary">Ghi nhớ mẫu câu trước khi đặt câu.</p>
            </div>
          </div>
          <HanziText
            className="block whitespace-pre-wrap break-words font-semibold text-brand-800"
            size="sm"
          >
            {lesson.structure || lesson.text}
          </HanziText>
        </div>
      </Card>

      <Card className="relative isolate overflow-hidden p-5 sm:p-6">
        <div className="relative z-base space-y-4">
          <div className="flex items-center gap-3">
            <span className="flex size-10 shrink-0 items-center justify-center rounded-md bg-brand-50 text-brand-600">
              <Lightbulb aria-hidden="true" className="size-5" />
            </span>
            <h2 className="text-h3 text-ink">Giải thích</h2>
          </div>
          <p className="whitespace-pre-line break-words text-body text-ink-secondary">
            <LessonProse text={lesson.explanation} />
          </p>
        </div>
      </Card>

      <section aria-labelledby="grammar-examples-title" className="space-y-4">
        <div className="flex flex-wrap items-center justify-between gap-2">
          <h2
            className="inline-flex items-center gap-2 text-h3 text-ink"
            id="grammar-examples-title"
          >
            <Quote aria-hidden="true" className="size-5 shrink-0 text-brand-600" />
            Ví dụ
          </h2>
          <StatusBadge tone="neutral">{lesson.examples.length} câu mẫu</StatusBadge>
        </div>
        {lesson.examples.length === 0 ? (
          <EmptyState
            title="Chưa có ví dụ"
            description="Bạn vẫn có thể đọc cấu trúc và phần giải thích của bài học."
          />
        ) : (
          <div className="space-y-4">
            {lesson.examples.map((example, index) => (
              <Card
                className="relative isolate overflow-hidden p-5 sm:p-6"
                key={`${example.cn}-${index}`}
              >
                <div className="relative z-base flex min-w-0 items-start gap-4">
                  <span className="flex size-9 shrink-0 items-center justify-center rounded-pill border border-brand-100 bg-brand-50 text-label tabular-nums text-brand-700">
                    {String(index + 1).padStart(2, '0')}
                  </span>
                  <div className="min-w-0 flex-1 space-y-2">
                    <HanziText className="block break-words" size="sm">
                      {example.cn}
                    </HanziText>
                    {example.pinyin ? (
                      <p className="break-words text-body font-medium text-brand-700">
                        {example.pinyin}
                      </p>
                    ) : null}
                    <p className="break-words text-body text-ink-secondary">
                      <LessonProse text={example.vi} />
                    </p>
                  </div>
                </div>
              </Card>
            ))}
          </div>
        )}
      </section>

      {lesson.commonMistake?.trim() ? (
        <Card className="border-warning bg-warning-bg p-5">
          <div className="flex items-start gap-3">
            <AlertTriangle aria-hidden="true" className="mt-1 size-5 shrink-0 text-warning" />
            <div className="min-w-0 space-y-2">
              <h2 className="text-h3 text-warning">Lỗi thường gặp</h2>
              <p className="whitespace-pre-line break-words text-body text-ink">
                <LessonProse text={lesson.commonMistake} />
              </p>
            </div>
          </div>
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
    <Card className="space-y-4 border-brand-100 bg-brand-50 p-5 sm:p-6">
      <SessionSummary
        actions={
          <>
            <Button to={listPath} variant="outline">
              Quay lại danh sách
            </Button>
            {nextPoint ? (
              <Button to={`/learn/grammar/${nextPoint.id}`}>
                Học điểm tiếp theo
                <ArrowRight
                  aria-hidden="true"
                  className="ml-1.5 inline-block size-4 align-middle"
                />
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
    </Card>
  )
}

function LessonLoading() {
  return (
    <div
      aria-label="Đang tải bài ngữ pháp"
      className="grid min-w-0 items-start gap-6 lg:grid-cols-3"
      role="status"
    >
      <div className="space-y-6 lg:col-span-2">
        <Skeleton className="h-36" />
        <Skeleton className="h-48" />
        <Skeleton className="h-64" />
      </div>
      <Skeleton className="h-80" />
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
  const ready = state === 'ready' && Boolean(lesson)
  const listPath = lesson
    ? `/learn/grammar?level=${grammarTabForLevel(lesson.hskLevel)}`
    : '/learn/grammar'
  return (
    <main className="mx-auto w-full min-w-0 max-w-content space-y-6 px-4 py-6 pb-24 sm:px-6 lg:px-8">
      <PageBanner
        actions={lesson ? <LevelBadge exact level={lesson.hskLevel} /> : undefined}
        subtitle={lesson?.titleVi ?? 'Đọc cấu trúc, hiểu cách dùng và luyện tập trong ngữ cảnh.'}
        title={lesson ? <HanziText size="lg">{lesson.text}</HanziText> : 'Bài ngữ pháp'}
      />

      {state === 'loading' ? <LessonLoading /> : null}
      {state === 'empty' ? (
        <EmptyState
          action={<Button to="/learn/grammar">Quay lại danh sách</Button>}
          description="Điểm ngữ pháp này không tồn tại hoặc không còn khả dụng."
          icon={<BookOpenText aria-hidden="true" className="size-5" />}
          title="Không tìm thấy điểm ngữ pháp"
        />
      ) : null}
      {state === 'error' || (state !== 'loading' && state !== 'empty' && !lesson) ? (
        <ErrorState
          message={error?.message ?? 'Vui lòng thử lại sau.'}
          onRetry={onRetry}
          requestId={error?.requestId}
          title="Không thể tải bài ngữ pháp"
        />
      ) : null}

      {ready && lesson ? (
        <>
          {stage !== 'finished' ? (
            <div className="flex flex-wrap items-center justify-between gap-3">
              <Button size="sm" to={listPath} variant="ghost">
                <ArrowLeft aria-hidden="true" className="mr-1.5 inline-block size-4 align-middle" />
                Quay lại danh sách
              </Button>
              <div className="flex flex-wrap items-center gap-2">
                {lesson.learned ? <StatusBadge tone="success">Đã học</StatusBadge> : null}
              </div>
            </div>
          ) : null}

          {stage === 'reading' ? (
            <div className="grammar-lesson-layout grid min-w-0 items-start gap-6 lg:grid-cols-3">
              <div className="grammar-lesson-article min-w-0 max-w-reading lg:col-span-2">
                <LessonContent lesson={lesson} />
              </div>
              <aside
                className="grammar-lesson-sidebar min-w-0 lg:sticky lg:top-20"
                aria-label="Hoạt động bài học"
              >
                <Card className="relative isolate overflow-hidden border-brand-100 bg-brand-50 p-5 sm:p-6">
                  <div className="relative z-base space-y-5">
                    <div className="space-y-2 text-center">
                      <h2 className="text-h3 text-brand-800">Luyện tập củng cố</h2>
                      <p className="text-body-sm text-ink-secondary">
                        {hasActiveSession
                          ? 'Tiếp tục phần bài tập đang dở của bạn.'
                          : 'Đọc ví dụ, thử đặt câu rồi kiểm tra cách dùng qua bài tập.'}
                      </p>
                    </div>
                    <div className="flex items-start gap-2 rounded-md border border-brand-100 bg-surface p-3 text-body-sm text-ink">
                      <Target
                        aria-hidden="true"
                        className="mt-0.5 size-4 shrink-0 text-brand-600"
                      />
                      <span>
                        {lesson.exerciseCount > 0
                          ? `${lesson.exerciseCount} câu bài tập ngắn kiểm tra mức độ hiểu bài`
                          : 'Xác nhận bạn đã đọc hiểu điểm ngữ pháp này'}
                      </span>
                    </div>
                    <Button
                      className="w-full rounded-pill"
                      disabled={checkingActive || pending}
                      loading={pending}
                      onClick={onBegin}
                    >
                      <BookCheck
                        aria-hidden="true"
                        className="mr-1.5 inline-block size-4 align-middle"
                      />
                      {checkingActive
                        ? 'Đang kiểm tra lượt học'
                        : hasActiveSession
                          ? 'Tiếp tục bài tập'
                          : 'Đã hiểu'}
                      <ArrowRight
                        aria-hidden="true"
                        className="ml-1.5 inline-block size-4 align-middle"
                      />
                    </Button>
                    {practiceError ? (
                      <ErrorState
                        message={practiceError.message}
                        onRetry={onRetry}
                        requestId={practiceError.requestId}
                        title="Không thể tải lượt bài tập"
                      />
                    ) : null}
                  </div>
                </Card>
              </aside>
            </div>
          ) : (
            <div className="grammar-lesson-practice space-y-8">
              <section
                aria-label="Hoạt động bài học"
                className="mx-auto w-full min-w-0 max-w-reading space-y-4"
              >
                {stage === 'exercise' ? (
                  <>
                    <div className="flex items-center gap-2 text-brand-700">
                      <BookCheck aria-hidden="true" className="size-5 shrink-0" />
                      <h2 className="text-h3 text-ink">Bài tập củng cố</h2>
                    </div>
                    {practiceError ? (
                      <ErrorState
                        message={practiceError.message}
                        onRetry={onRetry}
                        requestId={practiceError.requestId}
                        title="Không thể tiếp tục bài tập"
                      />
                    ) : item && onAnswer ? (
                      <>
                        <Card className="border-brand-100 bg-brand-50">
                          <SessionProgress current={current} total={total} />
                        </Card>
                        <Card className="space-y-6 p-5 sm:p-6">
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
                                <ArrowRight
                                  aria-hidden="true"
                                  className="ml-1.5 inline-block size-4 align-middle"
                                />
                              </Button>
                            ) : null}
                          </div>
                        </Card>
                      </>
                    ) : (
                      <Skeleton className="h-64" />
                    )}
                  </>
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
              <section
                aria-label="Nội dung bài học"
                className="mx-auto w-full min-w-0 max-w-reading space-y-4"
              >
                <p className="inline-flex items-center gap-2 text-label text-ink-secondary">
                  <CheckCircle2 aria-hidden="true" className="size-4 shrink-0 text-brand-600" />
                  Xem lại nội dung bài học
                </p>
                <LessonContent lesson={lesson} />
              </section>
            </div>
          )}

          <ConfirmPanel
            confirmLabel="Bỏ qua và ghi nhận bài học"
            message="Các câu chưa trả lời sẽ không được ghi. Bài học vẫn được đánh dấu là đã học nhưng không có điểm đầy đủ."
            onCancel={onSkipCancel ?? (() => undefined)}
            onConfirm={onSkipConfirm ?? (() => undefined)}
            open={confirmOpen}
            pending={pending}
            title="Bỏ qua phần bài tập?"
          />
        </>
      ) : null}
    </main>
  )
}
