import {
  AlertCircle,
  ArrowLeft,
  ArrowRight,
  BarChart3,
  CheckCircle2,
  Circle,
  CircleDot,
  ClipboardList,
  Info,
  Target,
  XCircle,
} from 'lucide-react'
import type { Option } from '@/features/practice'
import { Button, PageHeader, Progress, Skeleton } from '@/shared/ui'

export type TargetedQuestion = {
  text: string
  options: readonly Option[]
  repeated: boolean
}

export type TargetedFeedback = {
  correct: boolean | null | undefined
  correctAnswer: string | null | undefined
  explanation: string | null | undefined
}

type Props = {
  kid: string
  name?: string
  state: 'loading' | 'empty' | 'error' | 'ready' | 'finished'
  question?: TargetedQuestion
  position?: number
  total?: number
  feedback?: TargetedFeedback
  pending?: boolean
  error?: string
  onAnswer?: (key: string) => void
  onContinue?: () => void
  onRetry?: () => void
}

function ChineseLandscapeBannerArt() {
  return (
    <svg
      aria-hidden="true"
      className="pointer-events-none absolute inset-y-0 right-0 hidden h-full w-3/5 text-brand-600 sm:block"
      fill="none"
      preserveAspectRatio="xMaxYMid slice"
      viewBox="0 0 680 132"
    >
      <defs>
        <linearGradient id="cnhsk-target-mtn-far" x1="0" x2="0" y1="0" y2="1">
          <stop offset="0%" stopColor="currentColor" stopOpacity="0.18" />
          <stop offset="100%" stopColor="currentColor" stopOpacity="0.02" />
        </linearGradient>
        <linearGradient id="cnhsk-target-mtn-mid" x1="0" x2="0" y1="0" y2="1">
          <stop offset="0%" stopColor="currentColor" stopOpacity="0.28" />
          <stop offset="100%" stopColor="currentColor" stopOpacity="0.05" />
        </linearGradient>
        <linearGradient id="cnhsk-target-hill-front" x1="0" x2="0" y1="0" y2="1">
          <stop offset="0%" stopColor="currentColor" stopOpacity="0.34" />
          <stop offset="100%" stopColor="currentColor" stopOpacity="0.12" />
        </linearGradient>
      </defs>

      <circle cx="518" cy="34" fill="currentColor" opacity="0.2" r="24" />
      <rect fill="currentColor" height="5" opacity="0.16" rx="2.5" width="54" x="466" y="42" />
      <rect fill="currentColor" height="4" opacity="0.12" rx="2" width="42" x="524" y="24" />
      <rect fill="currentColor" height="4" opacity="0.12" rx="2" width="38" x="380" y="34" />

      <path
        d="M10 132 L110 66 L158 94 L236 22 L318 88 L382 36 L455 92 L520 58 L600 104 L680 132 Z"
        fill="url(#cnhsk-target-mtn-far)"
      />
      <path d="M236 22 L252 88 L195 112 Z" fill="currentColor" opacity="0.08" />
      <path d="M382 36 L396 86 L348 102 Z" fill="currentColor" opacity="0.08" />

      <path
        d="M95 132 L176 74 L248 106 L312 68 L392 108 L468 74 L556 106 L680 132 Z"
        fill="url(#cnhsk-target-mtn-mid)"
      />

      <path
        d="M280 132 C345 92, 415 74, 480 88 C538 100, 588 92, 680 112 L680 132 Z"
        fill="url(#cnhsk-target-hill-front)"
      />
      <path
        d="M390 132 C456 104, 520 98, 585 112 C622 104, 654 106, 680 118 L680 132 Z"
        fill="currentColor"
        opacity="0.22"
      />

      <path
        d="M284 132 C328 96, 376 70, 428 72 C474 74, 514 104, 562 102 C598 100, 632 88, 676 94 L676 106 C632 100, 598 112, 562 114 C512 116, 472 86, 428 84 C378 82, 334 106, 298 132 Z"
        fill="currentColor"
        opacity="0.32"
      />
      <path
        d="M282 132 C326 94, 376 68, 428 70 C474 72, 514 102, 562 100 C598 98, 632 86, 676 92"
        opacity="0.58"
        stroke="currentColor"
        strokeDasharray="5 3.5"
        strokeWidth="4.5"
      />
      <path
        d="M294 132 C336 102, 380 78, 428 80 C474 82, 514 112, 562 110 C598 108, 632 96, 676 102"
        opacity="0.45"
        stroke="currentColor"
        strokeWidth="2"
      />

      <g opacity="0.55" transform="translate(506, 70) scale(0.58)">
        <path d="M22 2 L25 8 L19 8 Z" fill="currentColor" />
        <path d="M4 16 Q14 13 22 6 Q30 13 40 16 Q31 18 22 14 Q13 18 4 16 Z" fill="currentColor" />
        <path d="M0 25 Q12 22 22 16 Q32 22 44 25 Q33 26 22 23 Q11 26 0 25 Z" fill="currentColor" />
        <rect fill="currentColor" height="11" width="3" x="11" y="24" />
        <rect fill="currentColor" height="11" width="3" x="20.5" y="24" />
        <rect fill="currentColor" height="11" width="3" x="30" y="24" />
        <rect fill="currentColor" height="3.5" width="32" x="6" y="34" />
      </g>

      <g opacity="0.5" transform="translate(594, 78) scale(0.62)">
        <path d="M22 2 L25 8 L19 8 Z" fill="currentColor" />
        <path d="M4 16 Q14 13 22 6 Q30 13 40 16 Q31 18 22 14 Q13 18 4 16 Z" fill="currentColor" />
        <path d="M0 25 Q12 22 22 16 Q32 22 44 25 Q33 26 22 23 Q11 26 0 25 Z" fill="currentColor" />
        <rect fill="currentColor" height="11" width="3" x="11" y="24" />
        <rect fill="currentColor" height="11" width="3" x="20.5" y="24" />
        <rect fill="currentColor" height="11" width="3" x="30" y="24" />
        <rect fill="currentColor" height="3.5" width="32" x="6" y="34" />
      </g>

      <g className="text-brand-700" opacity="0.95" transform="translate(426, 26)">
        <path d="M28 0 L31 8 L25 8 Z" fill="currentColor" />
        <path
          d="M6 19 C15 17, 22 12, 28 6 C34 12, 41 17, 50 19 C42 21, 35 18, 28 15 C21 18, 14 21, 6 19 Z"
          fill="currentColor"
        />
        <rect fill="currentColor" height="6" width="22" x="17" y="17" />
        <path
          d="M0 31 C12 28, 21 23, 28 18 C35 23, 44 28, 56 31 C45 33, 36 30, 28 27 C20 30, 11 33, 0 31 Z"
          fill="currentColor"
        />
        <rect fill="currentColor" height="15" width="3" x="12" y="29" />
        <rect fill="currentColor" height="15" width="3" x="20" y="29" />
        <rect fill="currentColor" height="15" width="3" x="33" y="29" />
        <rect fill="currentColor" height="15" width="3" x="41" y="29" />
        <rect fill="currentColor" height="2.5" width="34" x="11" y="39" />
        <path d="M7 44 L49 44 L52 49 L4 49 Z" fill="currentColor" />
      </g>

      <g className="text-brand-700" opacity="0.92">
        <path d="M634 134 L600 -8" stroke="currentColor" strokeWidth="6" />
        <line stroke="white" strokeWidth="2" x1="622" x2="630" y1="96" y2="94" />
        <line stroke="white" strokeWidth="2" x1="613" x2="621" y1="58" y2="56" />
        <line stroke="white" strokeWidth="2" x1="604" x2="612" y1="20" y2="18" />

        <path d="M654 134 L634 -8" stroke="currentColor" strokeWidth="8.5" />
        <line stroke="white" strokeWidth="2.2" x1="644" x2="654" y1="102" y2="100" />
        <line stroke="white" strokeWidth="2.2" x1="639" x2="649" y1="64" y2="62" />
        <line stroke="white" strokeWidth="2.2" x1="634" x2="644" y1="26" y2="24" />

        <path d="M672 134 L662 -8" stroke="currentColor" strokeWidth="6.5" />
        <line stroke="white" strokeWidth="2" x1="664" x2="673" y1="86" y2="85" />
        <line stroke="white" strokeWidth="2" x1="661" x2="670" y1="46" y2="45" />

        <path
          d="M636 10 Q585 6 542 26"
          stroke="currentColor"
          strokeLinecap="round"
          strokeWidth="2"
        />
        <path
          d="M612 36 Q576 42 548 62"
          stroke="currentColor"
          strokeLinecap="round"
          strokeWidth="1.8"
        />

        <path d="M558 18 Q540 32 528 50 Q548 38 558 18 Z" fill="currentColor" />
        <path d="M570 14 Q558 36 550 58 Q568 40 570 14 Z" fill="currentColor" />
        <path d="M584 12 Q578 34 574 54 Q588 36 584 12 Z" fill="currentColor" />
        <path d="M554 20 Q534 20 518 28 Q540 28 554 20 Z" fill="currentColor" opacity="0.85" />

        <path d="M562 52 Q546 66 536 82 Q554 70 562 52 Z" fill="currentColor" />
        <path d="M576 46 Q566 66 560 86 Q576 68 576 46 Z" fill="currentColor" />
        <path d="M590 42 Q586 60 584 78 Q596 62 590 42 Z" fill="currentColor" />
        <path d="M556 54 Q538 56 524 64 Q544 64 556 54 Z" fill="currentColor" opacity="0.8" />

        <path d="M596 8 Q586 24 582 40 Q596 28 596 8 Z" fill="currentColor" />
        <path d="M618 62 Q606 76 600 92 Q614 80 618 62 Z" fill="currentColor" />
        <path d="M628 58 Q622 76 620 92 Q632 76 628 58 Z" fill="currentColor" />
      </g>
    </svg>
  )
}

function NodeCardMountainWatermark() {
  return (
    <svg
      aria-hidden="true"
      className="pointer-events-none absolute inset-y-0 right-16 hidden h-full w-80 text-brand-600 sm:block"
      fill="none"
      preserveAspectRatio="xMaxYMax slice"
      viewBox="0 0 320 96"
    >
      <defs>
        <linearGradient id="cnhsk-nodecard-mtn-far" x1="0" x2="0" y1="0" y2="1">
          <stop offset="0%" stopColor="currentColor" stopOpacity="0.18" />
          <stop offset="100%" stopColor="currentColor" stopOpacity="0.02" />
        </linearGradient>
        <linearGradient id="cnhsk-nodecard-mtn-mid" x1="0" x2="0" y1="0" y2="1">
          <stop offset="0%" stopColor="currentColor" stopOpacity="0.28" />
          <stop offset="100%" stopColor="currentColor" stopOpacity="0.05" />
        </linearGradient>
      </defs>
      <path
        d="M16 96 L84 48 L124 72 L188 16 L254 68 L296 38 L320 62 L320 96 Z"
        fill="url(#cnhsk-nodecard-mtn-far)"
      />
      <path d="M188 16 L202 68 L154 84 Z" fill="currentColor" opacity="0.08" />
      <path
        d="M64 96 L132 54 L188 80 L244 46 L312 84 L320 96 Z"
        fill="url(#cnhsk-nodecard-mtn-mid)"
      />
    </svg>
  )
}

function QuestionFooterMountainArt() {
  return (
    <svg
      aria-hidden="true"
      className="pointer-events-none absolute inset-x-0 bottom-0 mx-auto h-24 w-4/5 max-w-[620px] text-brand-600"
      fill="none"
      preserveAspectRatio="xMidYMax meet"
      viewBox="0 0 520 110"
    >
      <defs>
        <linearGradient id="cnhsk-qfoot-mtn-far" x1="0" x2="0" y1="0" y2="1">
          <stop offset="0%" stopColor="currentColor" stopOpacity="0.18" />
          <stop offset="100%" stopColor="currentColor" stopOpacity="0.02" />
        </linearGradient>
        <linearGradient id="cnhsk-qfoot-mtn-mid" x1="0" x2="0" y1="0" y2="1">
          <stop offset="0%" stopColor="currentColor" stopOpacity="0.28" />
          <stop offset="100%" stopColor="currentColor" stopOpacity="0.05" />
        </linearGradient>
      </defs>
      <path
        d="M10 110 L96 54 L142 78 L224 14 L306 74 L368 28 L438 78 L508 110 Z"
        fill="url(#cnhsk-qfoot-mtn-far)"
      />
      <path d="M224 14 L240 74 L182 94 Z" fill="currentColor" opacity="0.08" />
      <path d="M368 28 L382 72 L336 88 Z" fill="currentColor" opacity="0.08" />
      <path
        d="M52 110 L158 52 L242 84 L328 46 L418 88 L492 110 Z"
        fill="url(#cnhsk-qfoot-mtn-mid)"
      />
    </svg>
  )
}

export function TargetedPracticeView({
  kid,
  name,
  state,
  question,
  position,
  total,
  feedback,
  pending,
  error,
  onAnswer,
  onContinue,
  onRetry,
}: Props) {
  return (
    <main className="mx-auto w-full min-w-0 max-w-content space-y-5 px-4 py-6 pb-16 sm:px-6 lg:max-w-[960px]">
      <div className="relative overflow-hidden rounded-md border border-brand-100 bg-brand-50 px-5 py-5 sm:px-6">
        <PageHeader
          className="relative z-nav mb-0"
          subtitle="Luyện tập trọng tâm để củng cố nút kiến thức."
          title={
            <span className="inline-flex items-center gap-3">
              <span aria-hidden="true" className="h-7 w-1.5 rounded-pill bg-brand-600" />
              <span>Luyện theo nút</span>
            </span>
          }
        />
        <ChineseLandscapeBannerArt />
      </div>

      {/* Thẻ thông tin nút KID & thanh tiến độ */}
      <div className="relative overflow-hidden rounded-lg border border-brand-100 bg-brand-50 px-5 py-4 sm:px-6">
        <NodeCardMountainWatermark />
        <div className="relative z-nav flex items-center gap-4">
          <span className="flex size-14 shrink-0 items-center justify-center rounded-pill bg-brand-100 text-brand-700">
            <Target aria-hidden="true" className="size-7" />
          </span>
          <div className="min-w-0 flex-1 space-y-2.5">
            <div className="flex flex-wrap items-center justify-between gap-3">
              <p className="break-words text-h3 font-bold text-ink">
                {kid}
                {name ? ' · ' + name : ''}
              </p>
              <Button
                className="!rounded-pill bg-surface px-4"
                disabled={pending}
                size="sm"
                to="/learn/progress"
                variant="outline"
              >
                <BarChart3
                  aria-hidden="true"
                  className="mr-1.5 inline-block size-4 align-middle text-brand-700"
                />
                Về tiến độ
              </Button>
            </div>
            {state === 'ready' && total ? (
              <Progress value={position ?? 0} max={total} label={`${position}/${total}`} />
            ) : null}
          </div>
        </div>
      </div>

      {state === 'loading' && <Skeleton variant="card" />}
      {state === 'empty' && (
        <p className="rounded-md border border-brand-100 bg-brand-50 p-6 text-center text-ink">
          Chưa có bài luyện cho nút này
        </p>
      )}
      {state === 'finished' && (
        <p
          role="status"
          className="rounded-md border border-brand-100 bg-brand-50 p-6 text-center text-h3 text-brand-700"
        >
          Đã hoàn thành bài luyện
        </p>
      )}
      {error && (
        <p role="alert" className="text-danger">
          {error}
        </p>
      )}
      {state === 'error' && onRetry && <Button onClick={onRetry}>Thử lại</Button>}

      {state === 'ready' && question && (
        <section className="relative min-w-0 overflow-hidden space-y-5 rounded-lg border border-brand-100 bg-surface p-6 pb-8 sm:p-8">
          <QuestionFooterMountainArt />

          <div className="relative z-nav space-y-5">
            <div className="flex flex-wrap items-center justify-between gap-3">
              <h2 className="flex items-center gap-2.5 text-h2 font-bold text-ink">
                <span className="flex size-9 items-center justify-center rounded-sm border border-brand-100 bg-brand-50 text-brand-700">
                  <ClipboardList aria-hidden="true" className="size-5" />
                </span>
                <span>
                  Câu {position}/{total}
                </span>
              </h2>
              {question.repeated && (
                <p className="inline-flex items-center gap-1.5 rounded-pill bg-brand-100 px-3.5 py-1.5 text-caption font-semibold text-brand-700">
                  <AlertCircle aria-hidden="true" className="size-4 text-brand-700" />
                  <span>Câu bạn vừa làm sai</span>
                </p>
              )}
            </div>

            <div className="flex items-center gap-3 rounded-md border border-brand-100 bg-brand-50 px-5 py-4">
              <span aria-hidden="true" className="h-6 w-1 shrink-0 rounded-pill bg-brand-700" />
              <p className="break-words font-hanzi text-h3 font-bold text-ink" lang="zh-CN">
                {question.text}
              </p>
            </div>

            <div className="grid gap-3" role="group" aria-label="Phương án">
              {question.options.map(({ key, text }) => {
                const isCorrectChoice = Boolean(feedback && feedback.correctAnswer === key)
                return (
                  <Button
                    key={key}
                    variant="outline"
                    className={`h-auto min-h-touch w-full justify-start whitespace-normal rounded-md px-4 py-3.5 text-left disabled:opacity-100 [&>span]:flex [&>span]:w-full [&>span]:items-center [&>span]:justify-between [&>span]:gap-3 ${
                      isCorrectChoice
                        ? 'border-success bg-success-bg/50 text-ink'
                        : 'border-line bg-surface text-ink hover:border-brand-600 hover:bg-brand-50/40'
                    }`}
                    disabled={pending || !!feedback}
                    onClick={() => onAnswer?.(key)}
                  >
                    <span className="flex min-w-0 items-center gap-3">
                      {isCorrectChoice ? (
                        <CircleDot aria-hidden="true" className="size-5 shrink-0 text-success" />
                      ) : (
                        <Circle aria-hidden="true" className="size-5 shrink-0 text-ink-muted" />
                      )}
                      <span className="font-semibold text-ink">
                        {key}. <span className="font-hanzi">{text}</span>
                      </span>
                    </span>
                    {isCorrectChoice && (
                      <CheckCircle2 aria-hidden="true" className="size-5 shrink-0 text-success" />
                    )}
                  </Button>
                )
              })}
            </div>

            {feedback && (
              <div
                role="status"
                className={`flex items-start gap-3.5 rounded-lg border p-4 text-body-sm ${
                  feedback.correct === true
                    ? 'border-success bg-success-bg/65 text-success'
                    : feedback.correct === false
                      ? 'border-danger bg-brand-50/80 text-danger'
                      : 'border-brand-100 bg-brand-50 text-ink'
                }`}
              >
                <span
                  aria-hidden="true"
                  className={`mt-0.5 flex size-8 shrink-0 items-center justify-center rounded-pill ${
                    feedback.correct === true
                      ? 'bg-success/15 text-success'
                      : feedback.correct === false
                        ? 'bg-danger/15 text-danger'
                        : 'bg-brand-100 text-brand-700'
                  }`}
                >
                  {feedback.correct === true ? (
                    <CheckCircle2 className="size-5" />
                  ) : feedback.correct === false ? (
                    <XCircle className="size-5" />
                  ) : (
                    <Info className="size-5" />
                  )}
                </span>
                <div className="min-w-0 flex-1 space-y-1">
                  <p className="text-body font-bold">
                    {feedback.correct === true
                      ? 'Chính xác'
                      : feedback.correct === false
                        ? 'Chưa đúng'
                        : 'Đã ghi nhận'}
                  </p>
                  {feedback.correctAnswer && (
                    <p className="font-semibold text-ink">Đáp án đúng: {feedback.correctAnswer}</p>
                  )}
                  {feedback.explanation && (
                    <p className="text-ink-secondary">{feedback.explanation}</p>
                  )}
                </div>
              </div>
            )}

            <div className="flex items-center justify-between gap-4 pt-3">
              <span
                aria-hidden="true"
                className="inline-flex min-h-touch items-center gap-2 rounded-pill border border-line bg-surface px-5 py-2 text-body-sm font-semibold text-ink-secondary shadow-sm"
              >
                <ArrowLeft className="size-4" />
                <span>Câu trước</span>
              </span>

              {feedback ? (
                <Button className="!rounded-pill px-6" disabled={pending} onClick={onContinue}>
                  <span>{position === total ? 'Hoàn thành' : 'Câu tiếp theo'}</span>
                  <ArrowRight
                    aria-hidden="true"
                    className="ml-1.5 inline-block size-4 align-middle"
                  />
                </Button>
              ) : (
                <span
                  aria-hidden="true"
                  className="inline-flex min-h-touch items-center gap-2 rounded-pill bg-brand-700 px-6 py-2 text-body-sm font-semibold text-surface shadow-sm"
                >
                  <span>{position === total ? 'Hoàn thành' : 'Câu tiếp theo'}</span>
                  <ArrowRight className="size-4" />
                </span>
              )}
            </div>
          </div>
        </section>
      )}
    </main>
  )
}
