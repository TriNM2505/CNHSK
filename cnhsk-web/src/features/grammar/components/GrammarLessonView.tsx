import {
  AlertTriangle,
  ArrowLeft,
  ArrowRight,
  BookCheck,
  BookOpenText,
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
  PageHeader,
  Skeleton,
  StatusBadge,
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

function ChineseLandscapeBannerArt() {
  return (
    <svg
      aria-hidden="true"
      className="pointer-events-none absolute inset-y-0 right-0 hidden h-full w-80 select-none sm:block"
      fill="none"
      preserveAspectRatio="xMaxYMid slice"
      viewBox="0 0 360 140"
    >
      {/* Rising sun */}
      <circle className="fill-brand-100 opacity-70" cx="272" cy="34" r="22" />
      <circle className="fill-brand-50 opacity-60" cx="272" cy="34" r="15" />

      {/* Distant mountains */}
      <path
        className="fill-brand-100 opacity-45"
        d="M20 140L95 62L155 112L218 44L290 105L335 68L360 92V140H20Z"
      />

      {/* Midground mountains */}
      <path
        className="fill-brand-100 opacity-80"
        d="M85 140L152 76L208 118L268 66L328 110L360 84V140H85Z"
      />

      {/* Winding ridge / Great Wall */}
      <path
        className="stroke-brand-600 opacity-35"
        d="M175 138C198 110 226 94 258 92C286 90 312 104 348 118"
        strokeDasharray="4 3"
        strokeLinecap="round"
        strokeWidth="5"
      />
      <path
        className="stroke-brand-700 opacity-45"
        d="M172 140C196 112 225 96 258 94C286 92 312 106 350 120"
        strokeLinecap="round"
        strokeWidth="2"
      />

      {/* Traditional pavilion / pagoda silhouette on the ridge */}
      <g className="fill-brand-600 opacity-85" transform="translate(242, 54)">
        {/* Top roof */}
        <path d="M16 0L2 10H30L16 0Z" />
        <path d="M0 10C4 9 28 9 32 10L29 13H3L0 10Z" />
        {/* Upper tier */}
        <rect height="6" width="16" x="8" y="13" />
        {/* Second roof */}
        <path d="M-3 19H35L31 23H1L-3 19Z" />
        {/* Pillars & base */}
        <rect height="11" width="3" x="6" y="23" />
        <rect height="11" width="3" x="14.5" y="23" />
        <rect height="11" width="3" x="23" y="23" />
        <rect height="4" rx="1" width="30" x="1" y="34" />
      </g>

      {/* Bamboo stalks & leaves on right edge */}
      <g className="stroke-brand-700 opacity-85" strokeLinecap="round" strokeWidth="2.5">
        <path d="M332 4L318 140" />
        <path d="M346 0L338 140" />
        <path d="M355 18L350 140" />
      </g>
      <g className="fill-brand-700 opacity-85">
        <path d="M330 22C318 20 310 25 304 32C314 31 323 28 330 22Z" />
        <path d="M328 38C315 38 307 44 301 52C312 50 321 45 328 38Z" />
        <path d="M343 28C333 26 325 31 319 38C328 37 336 34 343 28Z" />
        <path d="M341 50C330 50 322 56 316 64C326 62 334 57 341 50Z" />
      </g>
    </svg>
  )
}

function LessonContent({ lesson }: { lesson: GrammarLesson }) {
  return (
    <div className="space-y-5">
      {/* Two soft-tinted feature cards inspired by reference mockup */}
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
        <Card className="flex flex-col justify-between gap-4 border-brand-100 bg-surface-subtle p-5">
          <div className="space-y-3">
            <div className="flex items-start justify-between gap-2">
              <div className="flex size-10 shrink-0 items-center justify-center rounded-md border border-brand-100 bg-surface text-brand-700 shadow-sm">
                <Sparkles aria-hidden="true" className="size-5" />
              </div>
              <span className="rounded-pill bg-brand-100 px-3 py-1 text-caption font-semibold text-brand-700">
                Công thức
              </span>
            </div>
            <div>
              <p
                className="break-words font-hanzi text-h2 font-bold leading-snug text-ink"
                lang="zh-CN"
              >
                {lesson.structure || lesson.text}
              </p>
              <h2 className="mt-2 text-h3 text-ink">Cấu trúc</h2>
              <p className="mt-0.5 text-body-sm text-ink-secondary">
                Mẫu câu chuẩn áp dụng trong bài học
              </p>
            </div>
          </div>
        </Card>

        <Card className="flex flex-col justify-between gap-4 border-brand-100 bg-surface-subtle p-5">
          <div className="space-y-3">
            <div className="flex items-start justify-between gap-2">
              <div className="flex size-10 shrink-0 items-center justify-center rounded-md border border-brand-100 bg-surface text-brand-700 shadow-sm">
                <BookOpenText aria-hidden="true" className="size-5" />
              </div>
              <span className="rounded-pill bg-brand-100 px-3 py-1 text-caption font-semibold text-brand-700">
                Trọng tâm
              </span>
            </div>
            <div>
              <h2 className="text-h3 text-ink">Giải thích</h2>
              <p className="mt-2 whitespace-pre-line break-words text-body leading-relaxed text-ink-secondary">
                {lesson.explanation}
              </p>
            </div>
          </div>
        </Card>
      </div>

      {/* Examples section styled like "Gợi ý hôm nay" cards in reference mockup */}
      <section aria-labelledby="grammar-examples-title" className="space-y-4">
        <div className="flex items-center justify-between gap-2">
          <div className="flex items-center gap-2">
            <Sparkles aria-hidden="true" className="size-5 shrink-0 text-brand-600" />
            <h2 className="text-h3 text-ink" id="grammar-examples-title">
              Ví dụ
            </h2>
          </div>
          <span className="rounded-pill border border-brand-100 bg-brand-50 px-3 py-0.5 text-caption font-semibold text-brand-700">
            {lesson.examples.length} câu mẫu
          </span>
        </div>

        <div
          className={
            lesson.examples.length > 1
              ? 'grid grid-cols-1 gap-4 sm:grid-cols-2'
              : 'grid grid-cols-1 gap-4'
          }
        >
          {lesson.examples.map((example, index) => (
            <Card
              className="flex flex-col justify-between gap-4 p-5"
              key={`${example.cn}-${index}`}
            >
              <div className="space-y-2.5">
                <div className="flex items-center gap-2">
                  <Quote aria-hidden="true" className="size-4 shrink-0 text-brand-600" />
                  <HanziText className="block break-words font-semibold text-ink" size="sm">
                    {example.cn}
                  </HanziText>
                </div>
                {example.pinyin ? (
                  <p className="break-words text-body-sm font-medium text-brand-700">
                    {example.pinyin}
                  </p>
                ) : null}
                <p className="break-words text-body text-ink-secondary">{example.vi}</p>
              </div>

              <div className="space-y-2 pt-1">
                <div className="flex items-center justify-between text-caption">
                  <span className="text-ink-secondary">Câu mẫu ứng dụng</span>
                  <span className="font-semibold text-ink">
                    #{String(index + 1).padStart(2, '0')}
                  </span>
                </div>
                <div className="h-1.5 w-full overflow-hidden rounded-pill bg-brand-100">
                  <div className="h-full w-2/3 rounded-pill bg-brand-600" />
                </div>
              </div>
            </Card>
          ))}
        </div>
      </section>

      {lesson.commonMistake?.trim() ? (
        <Card className="border-warning bg-warning-bg p-5 sm:p-6">
          <div className="flex items-start gap-3">
            <AlertTriangle aria-hidden="true" className="mt-0.5 size-5 shrink-0 text-warning" />
            <div className="min-w-0 flex-1">
              <h2 className="text-h3 text-warning">Lỗi thường gặp</h2>
              <p className="mt-2 whitespace-pre-line break-words text-body text-ink">
                {lesson.commonMistake}
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
    <div className="space-y-3">
      <SessionSummary
        actions={
          <>
            <Button to={listPath} variant="outline">
              Quay lại danh sách
            </Button>
            {nextPoint ? (
              <Button to={`/learn/grammar/${nextPoint.id}`}>
                <span className="inline-flex items-center gap-2">
                  Học điểm tiếp theo
                  <ArrowRight aria-hidden="true" className="size-4 shrink-0" />
                </span>
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
    <div aria-label="Đang tải bài ngữ pháp" className="space-y-4" role="status">
      <Skeleton className="h-24" />
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
        <Skeleton className="h-36" />
        <Skeleton className="h-36" />
      </div>
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
  if (state === 'loading') {
    return (
      <main className="mx-auto w-full min-w-0 max-w-reading px-4 py-6 pb-20 sm:px-6">
        <LessonLoading />
      </main>
    )
  }

  if (state === 'empty') {
    return (
      <main className="mx-auto w-full min-w-0 max-w-reading px-4 py-6 pb-20 sm:px-6">
        <EmptyState
          action={<Button to="/learn/grammar">Quay lại danh sách</Button>}
          description="Điểm ngữ pháp này không tồn tại hoặc không còn khả dụng."
          icon={<BookOpenText aria-hidden="true" className="size-5" />}
          title="Không tìm thấy điểm ngữ pháp"
        />
      </main>
    )
  }

  if (state === 'error' || !lesson) {
    return (
      <main className="mx-auto w-full min-w-0 max-w-reading px-4 py-6 pb-20 sm:px-6">
        <ErrorState
          message={error?.message ?? 'Vui lòng thử lại sau.'}
          onRetry={onRetry}
          requestId={error?.requestId}
          title="Không thể tải bài ngữ pháp"
        />
      </main>
    )
  }

  const listPath = `/learn/grammar?level=${grammarTabForLevel(lesson.hskLevel)}`

  return (
    <main className="mx-auto w-full min-w-0 max-w-reading space-y-5 px-4 py-6 pb-20 sm:px-6">
      {stage !== 'finished' ? (
        <div className="flex flex-wrap items-center justify-between gap-2">
          <Button size="sm" to={listPath} variant="ghost">
            <span className="inline-flex items-center gap-2">
              <ArrowLeft aria-hidden="true" className="size-4 shrink-0" />
              Quay lại danh sách
            </span>
          </Button>
          <div className="flex flex-wrap items-center gap-2">
            <LevelBadge exact level={lesson.hskLevel} />
            {lesson.learned ? <StatusBadge tone="success">Đã học</StatusBadge> : null}
          </div>
        </div>
      ) : null}

      {/* Hero banner card with vertical red bar + Chinese landscape illustration */}
      <div className="relative overflow-hidden rounded-lg border border-brand-100 bg-surface-subtle p-5 shadow-sm sm:p-6">
        <ChineseLandscapeBannerArt />
        <div className="relative z-base border-l-4 border-brand-600 pl-3.5 sm:max-w-md">
          <PageHeader
            className="mb-0"
            subtitle={lesson.titleVi}
            title={<HanziText size="lg">{lesson.text}</HanziText>}
          />
        </div>
      </div>

      <LessonContent lesson={lesson} />

      <section aria-label="Hoạt động bài học" className="pt-1">
        {stage === 'reading' ? (
          <Card className="space-y-4 border-brand-100 bg-surface-subtle p-5 sm:p-6">
            <div className="flex flex-wrap items-center justify-between gap-4">
              <div className="flex min-w-0 items-center gap-3.5">
                <div className="flex size-11 shrink-0 items-center justify-center rounded-pill bg-brand-100 text-brand-700">
                  <Target aria-hidden="true" className="size-5" />
                </div>
                <div className="min-w-0">
                  <p className="text-h3 font-semibold text-ink">Luyện tập củng cố</p>
                  <p className="text-body-sm text-ink-secondary">
                    {lesson.exerciseCount > 0
                      ? `${lesson.exerciseCount} câu bài tập ngắn kiểm tra mức độ hiểu bài`
                      : 'Xác nhận bạn đã đọc hiểu điểm ngữ pháp này'}
                  </p>
                </div>
              </div>

              <div className="flex flex-wrap items-center gap-2.5">
                <Button disabled={checkingActive || pending} loading={pending} onClick={onBegin}>
                  <span className="inline-flex items-center gap-2">
                    <BookCheck aria-hidden="true" className="size-4 shrink-0" />
                    {checkingActive
                      ? 'Đang kiểm tra lượt học'
                      : hasActiveSession
                        ? 'Tiếp tục bài tập'
                        : 'Đã hiểu'}
                    <ArrowRight aria-hidden="true" className="size-4 shrink-0" />
                  </span>
                </Button>
              </div>
            </div>

            {practiceError ? (
              <div className="pt-2 text-left">
                <ErrorState
                  message={practiceError.message}
                  onRetry={onRetry}
                  requestId={practiceError.requestId}
                  title="Không thể tải lượt bài tập"
                />
              </div>
            ) : null}
          </Card>
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
                <Card className="space-y-5 p-5 sm:p-6">
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
                        <span className="inline-flex items-center gap-2">
                          Câu tiếp
                          <ArrowRight aria-hidden="true" className="size-4 shrink-0" />
                        </span>
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
    </main>
  )
}
