import {
  ArrowRight,
  BarChart3,
  CalendarClock,
  CheckCircle2,
  Eye,
  Pause,
  SkipForward,
  Sparkles,
  Target,
} from 'lucide-react'

import { ChoiceQuestion } from '@/features/practice'
import type { AnswerResult, ServedItemView, SessionView } from '@/features/practice/types'
import { Button, Card, ErrorState, HanziText, PageHeader, Progress, Skeleton } from '@/shared/ui'

import { formatMasteryDateTime } from '../formatDateTime'
import type { FlashcardRating } from '../types'

const RATINGS: readonly {
  label: string
  value: FlashcardRating
  hint: string
  toneClass: string
}[] = [
  {
    label: 'Quên',
    value: 'AGAIN',
    hint: 'Ôn lại ngay',
    toneClass: 'border-danger bg-danger-bg text-danger hover:border-danger hover:text-danger',
  },
  {
    label: 'Khó',
    value: 'HARD',
    hint: 'Nhớ mang máng',
    toneClass: 'border-warning bg-warning-bg text-warning hover:border-warning hover:text-warning',
  },
  {
    label: 'Được',
    value: 'GOOD',
    hint: 'Nhớ đúng',
    toneClass: 'border-brand-100 bg-brand-50 text-brand-700 hover:border-brand-600',
  },
  {
    label: 'Dễ',
    value: 'EASY',
    hint: 'Nhớ rất rõ',
    toneClass: 'border-success bg-success-bg text-success hover:border-success hover:text-success',
  },
]

export type DueReviewViewProps =
  | { state: 'loading' }
  | { state: 'error'; error: string; onRetry: () => void }
  | { state: 'empty'; onStartAhead: () => void }
  | {
      state: 'finished'
      answeredCount: number
      correctChoices: number
      gradedCount: number
      summaryLoading: boolean
      summaryError: boolean
      nextDueAt: string | null
      onRetrySummary: () => void
    }
  | {
      state: 'ready'
      session: SessionView
      currentItem: ServedItemView
      remaining: number
      pending: boolean
      result?: AnswerResult
      selectedAnswer?: string | null
      currentItemAnswered: boolean
      isChoice: boolean
      revealed: boolean
      error?: string
      onAnswer: (answer: string) => Promise<unknown>
      onRating: (rating: FlashcardRating) => void
      onReveal: () => void
      onAdvance: () => void
      onSkip: () => void
      onPause: () => void
      onSync: () => void
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
        <linearGradient id="cnhsk-review-mtn-far" x1="0" x2="0" y1="0" y2="1">
          <stop offset="0%" stopColor="currentColor" stopOpacity="0.18" />
          <stop offset="100%" stopColor="currentColor" stopOpacity="0.02" />
        </linearGradient>
        <linearGradient id="cnhsk-review-mtn-mid" x1="0" x2="0" y1="0" y2="1">
          <stop offset="0%" stopColor="currentColor" stopOpacity="0.28" />
          <stop offset="100%" stopColor="currentColor" stopOpacity="0.05" />
        </linearGradient>
        <linearGradient id="cnhsk-review-hill-front" x1="0" x2="0" y1="0" y2="1">
          <stop offset="0%" stopColor="currentColor" stopOpacity="0.34" />
          <stop offset="100%" stopColor="currentColor" stopOpacity="0.12" />
        </linearGradient>
      </defs>

      {/* Mặt trời tròn & áng mây ngang cổ phong */}
      <circle cx="518" cy="34" fill="currentColor" opacity="0.2" r="24" />
      <rect fill="currentColor" height="5" opacity="0.16" rx="2.5" width="54" x="466" y="42" />
      <rect fill="currentColor" height="4" opacity="0.12" rx="2" width="42" x="524" y="24" />
      <rect fill="currentColor" height="4" opacity="0.12" rx="2" width="38" x="380" y="34" />

      {/* Dãy núi thủy mặc xa (trái sang phải) */}
      <path
        d="M10 132 L110 66 L158 94 L236 22 L318 88 L382 36 L455 92 L520 58 L600 104 L680 132 Z"
        fill="url(#cnhsk-review-mtn-far)"
      />
      {/* Đường sống núi tạo khối */}
      <path d="M236 22 L252 88 L195 112 Z" fill="currentColor" opacity="0.08" />
      <path d="M382 36 L396 86 L348 102 Z" fill="currentColor" opacity="0.08" />

      {/* Lớp núi trung cảnh */}
      <path
        d="M95 132 L176 74 L248 106 L312 68 L392 108 L468 74 L556 106 L680 132 Z"
        fill="url(#cnhsk-review-mtn-mid)"
      />

      {/* Ngọn đồi tiền cảnh bên phải */}
      <path
        d="M280 132 C345 92, 415 74, 480 88 C538 100, 588 92, 680 112 L680 132 Z"
        fill="url(#cnhsk-review-hill-front)"
      />
      <path
        d="M390 132 C456 104, 520 98, 585 112 C622 104, 654 106, 680 118 L680 132 Z"
        fill="currentColor"
        opacity="0.22"
      />

      {/* Vạn Lý Trường Thành uốn lượn dọc sườn đồi */}
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

      {/* Đình cổ nhỏ phía xa (trên sườn đồi giữa) */}
      <g opacity="0.55" transform="translate(506, 70) scale(0.58)">
        <path d="M22 2 L25 8 L19 8 Z" fill="currentColor" />
        <path d="M4 16 Q14 13 22 6 Q30 13 40 16 Q31 18 22 14 Q13 18 4 16 Z" fill="currentColor" />
        <path d="M0 25 Q12 22 22 16 Q32 22 44 25 Q33 26 22 23 Q11 26 0 25 Z" fill="currentColor" />
        <rect fill="currentColor" height="11" width="3" x="11" y="24" />
        <rect fill="currentColor" height="11" width="3" x="20.5" y="24" />
        <rect fill="currentColor" height="11" width="3" x="30" y="24" />
        <rect fill="currentColor" height="3.5" width="32" x="6" y="34" />
      </g>

      {/* Đình cổ nhỏ bên phải (gần khóm trúc) */}
      <g opacity="0.5" transform="translate(594, 78) scale(0.62)">
        <path d="M22 2 L25 8 L19 8 Z" fill="currentColor" />
        <path d="M4 16 Q14 13 22 6 Q30 13 40 16 Q31 18 22 14 Q13 18 4 16 Z" fill="currentColor" />
        <path d="M0 25 Q12 22 22 16 Q32 22 44 25 Q33 26 22 23 Q11 26 0 25 Z" fill="currentColor" />
        <rect fill="currentColor" height="11" width="3" x="11" y="24" />
        <rect fill="currentColor" height="11" width="3" x="20.5" y="24" />
        <rect fill="currentColor" height="11" width="3" x="30" y="24" />
        <rect fill="currentColor" height="3.5" width="32" x="6" y="34" />
      </g>

      {/* Ngôi đình / chùa cổ 2 tầng mái cong chính (đỏ đậm nổi bật trên đỉnh đồi) */}
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

      {/* Khóm tre / trúc đỏ đậm sắc nét ở góc phải */}
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

function ReviewHeaderBanner({ title, subtitle }: { title: string; subtitle: string }) {
  return (
    <div className="relative overflow-hidden rounded-md border border-brand-100 bg-brand-50 px-5 py-5 sm:px-6">
      <PageHeader
        className="relative z-nav mb-0"
        subtitle={subtitle}
        title={
          <span className="inline-flex items-center gap-3">
            <span aria-hidden="true" className="h-7 w-1.5 rounded-pill bg-brand-600" />
            <span>{title}</span>
          </span>
        }
      />
      <ChineseLandscapeBannerArt />
    </div>
  )
}

export function DueReviewView(props: DueReviewViewProps) {
  return (
    <main className="mx-auto w-full min-w-0 max-w-reading space-y-5 px-4 py-6 pb-16 sm:px-6">
      <ReviewHeaderBanner
        subtitle={
          props.state === 'finished'
            ? 'Kết quả phiên ôn tập vừa hoàn thành.'
            : 'Ôn lại các từ vựng và kiến thức đã đến lịch để nhớ lâu hơn.'
        }
        title={props.state === 'finished' ? 'Đã hoàn thành ôn tập' : 'Ôn tập'}
      />

      {props.state === 'loading' ? <Skeleton variant="card" /> : null}

      {props.state === 'error' ? (
        <ErrorState message={props.error} onRetry={props.onRetry} />
      ) : null}

      {props.state === 'empty' ? (
        <Card className="space-y-4 border-brand-100 bg-brand-50 p-8 text-center">
          <div className="mx-auto flex size-12 items-center justify-center rounded-pill bg-brand-100 text-brand-700">
            <CheckCircle2 aria-hidden="true" className="size-6" />
          </div>
          <div className="space-y-1">
            <p role="status" className="text-h3 text-ink">
              Chưa có mục đến hạn
            </p>
            <p className="text-body-sm text-ink-secondary">
              Bạn đã hoàn thành các mục cần ôn hiện tại. Có thể chủ động ôn trước lịch.
            </p>
          </div>
          <div className="pt-1">
            <Button onClick={props.onStartAhead}>
              <Sparkles aria-hidden="true" className="mr-1.5 inline-block size-4 align-middle" />
              Ôn trước
            </Button>
          </div>
        </Card>
      ) : null}

      {props.state === 'finished' ? (
        <Card className="space-y-5 border-brand-100 bg-surface p-6 sm:p-8">
          <div className="flex items-start gap-3.5 rounded-md border border-brand-100 bg-brand-50 p-4">
            <div className="flex size-11 shrink-0 items-center justify-center rounded-pill bg-brand-100 text-brand-700">
              <CheckCircle2 aria-hidden="true" className="size-6" />
            </div>
            <div className="min-w-0 space-y-1">
              <p role="status" className="text-h3 text-ink">
                Đã xử lý {props.answeredCount} mục
              </p>
              <p className="text-body-sm text-ink-secondary">
                Số câu chọn đúng: {props.correctChoices} / {props.gradedCount}
              </p>
            </div>
          </div>

          {props.summaryLoading ? <Skeleton variant="text" /> : null}
          {props.summaryError ? (
            <ErrorState message="Không tải được hạn ôn kế tiếp" onRetry={props.onRetrySummary} />
          ) : null}
          {!props.summaryLoading && !props.summaryError && props.nextDueAt ? (
            <div className="flex items-center gap-2.5 rounded-md bg-canvas p-4 text-body-sm text-ink">
              <CalendarClock aria-hidden="true" className="size-5 shrink-0 text-brand-600" />
              <p>Lần ôn tiếp theo: {formatMasteryDateTime(props.nextDueAt)}</p>
            </div>
          ) : null}
          {!props.summaryLoading && !props.summaryError && props.nextDueAt === null ? (
            <div className="flex items-center gap-2.5 rounded-md bg-canvas p-4 text-body-sm text-ink-secondary">
              <CalendarClock aria-hidden="true" className="size-5 shrink-0 text-brand-600" />
              <p>Chưa có lịch ôn tiếp theo</p>
            </div>
          ) : null}

          <div className="flex flex-wrap gap-3 pt-1">
            <Button to="/learn/progress" variant="outline">
              <BarChart3
                aria-hidden="true"
                className="mr-1.5 inline-block size-4 align-middle text-brand-600"
              />
              Xem tiến độ
            </Button>
            <Button to="/learn" variant="ghost">
              Về trang học tập
            </Button>
          </div>
        </Card>
      ) : null}

      {props.state === 'ready' ? (
        <>
          <Card className="space-y-3.5 border-brand-100 bg-brand-50 p-5">
            <div className="flex flex-wrap items-center justify-between gap-3">
              <div className="flex items-center gap-3">
                <div className="flex size-10 shrink-0 items-center justify-center rounded-pill bg-brand-100 text-brand-700">
                  <Target aria-hidden="true" className="size-5" />
                </div>
                <div>
                  <div aria-live="polite" className="text-h3 text-ink">
                    {props.remaining} mục còn lại
                  </div>
                  <p className="text-caption text-ink-secondary">
                    Câu {props.currentItem.no} / {props.session.items.length}
                  </p>
                </div>
              </div>
              <Button disabled={props.pending} onClick={props.onPause} size="sm" variant="outline">
                <Pause aria-hidden="true" className="mr-1.5 inline-block size-4 align-middle" />
                Tạm dừng
              </Button>
            </div>
            <Progress
              value={props.session.items.length - props.remaining}
              max={props.session.items.length}
              label={`${props.session.items.length - props.remaining}/${props.session.items.length}`}
            />
          </Card>

          {props.error ? <ErrorState message={props.error} onRetry={props.onSync} /> : null}

          <Card className="min-w-0 space-y-6 p-6 sm:p-8">
            <div className="flex items-center justify-between gap-2 border-b border-line pb-3">
              <span className="inline-flex items-center gap-1.5 rounded-pill bg-brand-50 px-3 py-1 text-caption font-semibold text-brand-700">
                <Sparkles aria-hidden="true" className="size-3.5" />
                {props.isChoice ? 'Trắc nghiệm nghĩa' : 'Thẻ ghi nhớ'}
              </span>
              <span className="text-caption text-ink-secondary">Mục #{props.currentItem.no}</span>
            </div>

            {props.isChoice ? (
              <ChoiceQuestion
                feedbackMode={props.session.feedback}
                item={props.currentItem}
                onAnswer={props.onAnswer}
                pending={props.pending}
                result={props.result}
                selectedAnswer={props.selectedAnswer}
              />
            ) : props.currentItem.type === 'FLASHCARD_RATING' ? (
              <section aria-labelledby="review-flashcard-title" className="space-y-6 text-center">
                <div className="rounded-md border border-brand-100 bg-brand-50 px-4 py-8">
                  <HanziText className="block break-words" id="review-flashcard-title" size="lg">
                    {props.currentItem.prompt.text}
                  </HanziText>
                </div>
                {props.revealed ? (
                  <div className="space-y-5">
                    <div className="space-y-2 rounded-md border border-line bg-canvas p-5">
                      {props.currentItem.prompt.pinyin ? (
                        <p className="text-h2 text-brand-700">{props.currentItem.prompt.pinyin}</p>
                      ) : null}
                      {props.currentItem.prompt.sinoViet ? (
                        <p className="text-label text-ink-secondary">
                          {props.currentItem.prompt.sinoViet}
                        </p>
                      ) : null}
                      {props.currentItem.prompt.meaning ? (
                        <p className="text-h3 text-ink">{props.currentItem.prompt.meaning}</p>
                      ) : null}
                      {props.currentItem.prompt.examples?.length ? (
                        <ul className="mt-3 space-y-2 border-t border-line pt-3 text-body-sm text-ink-secondary">
                          {props.currentItem.prompt.examples.map((example, index) => (
                            <li key={`${example.cn ?? 'example'}-${index}`}>
                              {example.cn ? (
                                <span className="font-hanzi text-ink">{example.cn}</span>
                              ) : null}
                              {example.vi ? <span> — {example.vi}</span> : null}
                            </li>
                          ))}
                        </ul>
                      ) : null}
                    </div>

                    <div className="space-y-2">
                      <p className="text-caption text-ink-secondary">
                        Tự đánh giá mức độ nhớ của bạn:
                      </p>
                      <div
                        aria-label="Đánh giá mức độ nhớ"
                        className="grid grid-cols-2 gap-3 sm:grid-cols-4"
                        role="group"
                      >
                        {RATINGS.map(({ label, value, toneClass }) => (
                          <Button
                            className={toneClass}
                            disabled={props.pending}
                            key={value}
                            onClick={() => props.onRating(value)}
                            variant="outline"
                          >
                            {label}
                          </Button>
                        ))}
                      </div>
                    </div>
                  </div>
                ) : (
                  <Button disabled={props.pending} onClick={props.onReveal}>
                    <Eye aria-hidden="true" className="mr-1.5 inline-block size-4 align-middle" />
                    Hiện đáp án
                  </Button>
                )}
              </section>
            ) : (
              <ErrorState
                message={`Loại câu hỏi không được hỗ trợ: ${props.currentItem.type}`}
                onRetry={props.onSync}
              />
            )}

            {(props.isChoice && props.result) || !props.currentItemAnswered ? (
              <div className="flex flex-wrap items-center justify-between gap-3 border-t border-line pt-4">
                {!props.currentItemAnswered ? (
                  <Button disabled={props.pending} onClick={props.onSkip} variant="outline">
                    <SkipForward
                      aria-hidden="true"
                      className="mr-1.5 inline-block size-4 align-middle"
                    />
                    Bỏ qua
                  </Button>
                ) : null}
                {props.isChoice && props.result ? (
                  <Button className="ml-auto" disabled={props.pending} onClick={props.onAdvance}>
                    {props.remaining === 0 ? 'Kết thúc ôn tập' : 'Tiếp'}
                    <ArrowRight
                      aria-hidden="true"
                      className="ml-1.5 inline-block size-4 align-middle"
                    />
                  </Button>
                ) : null}
              </div>
            ) : null}
          </Card>
        </>
      ) : null}
    </main>
  )
}
