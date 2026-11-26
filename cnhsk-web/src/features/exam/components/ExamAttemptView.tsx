import {
  ArrowLeft,
  ArrowRight,
  Bookmark,
  BookOpen,
  CheckCircle2,
  CircleAlert,
  ClipboardCheck,
  ClipboardList,
  LogOut,
} from 'lucide-react'
import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { Button, Card, ConfirmPanel, PageHeader, Progress } from '@/shared/ui'

import { ExamQuestionBody, type AttemptQuestion } from './ExamQuestionBody'

export type { AttemptQuestion } from './ExamQuestionBody'
export type AttemptSection = { index: number; name: string; questions: AttemptQuestion[] }

type Props = {
  title: string
  sections: AttemptSection[]
  currentNo: number
  choices: Readonly<Record<number, string | null>>
  marks: readonly number[]
  saveState?: 'saving' | 'saved' | 'failed'
  placement: boolean
  pending: boolean
  readOnly: boolean
  confirming: boolean
  error?: string
  onNavigate: (no: number) => void
  onChoose: (key: string) => void
  onSkip: () => void
  onMark: () => void
  onConfirmChange: (open: boolean) => void
  onSubmit: () => void
  onContinue: () => void
  retryNext?: boolean
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
        <linearGradient id="cnhsk-examatt-mtn-far" x1="0" x2="0" y1="0" y2="1">
          <stop offset="0%" stopColor="currentColor" stopOpacity="0.18" />
          <stop offset="100%" stopColor="currentColor" stopOpacity="0.02" />
        </linearGradient>
        <linearGradient id="cnhsk-examatt-mtn-mid" x1="0" x2="0" y1="0" y2="1">
          <stop offset="0%" stopColor="currentColor" stopOpacity="0.28" />
          <stop offset="100%" stopColor="currentColor" stopOpacity="0.05" />
        </linearGradient>
        <linearGradient id="cnhsk-examatt-hill-front" x1="0" x2="0" y1="0" y2="1">
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
        fill="url(#cnhsk-examatt-mtn-far)"
      />
      <path d="M236 22 L252 88 L195 112 Z" fill="currentColor" opacity="0.08" />
      <path d="M382 36 L396 86 L348 102 Z" fill="currentColor" opacity="0.08" />

      <path
        d="M95 132 L176 74 L248 106 L312 68 L392 108 L468 74 L556 106 L680 132 Z"
        fill="url(#cnhsk-examatt-mtn-mid)"
      />

      <path
        d="M280 132 C345 92, 415 74, 480 88 C538 100, 588 92, 680 112 L680 132 Z"
        fill="url(#cnhsk-examatt-hill-front)"
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

function ProgressCardBambooArt() {
  return (
    <svg
      aria-hidden="true"
      className="pointer-events-none absolute right-0 top-0 h-32 w-28 text-brand-600 opacity-55"
      fill="none"
      viewBox="0 0 120 130"
    >
      <path d="M104 130 Q98 62 78 -4" stroke="currentColor" strokeWidth="2.5" />
      <path d="M96 48 Q72 42 48 54" stroke="currentColor" strokeLinecap="round" strokeWidth="1.5" />
      <path d="M88 22 Q68 14 48 22" stroke="currentColor" strokeLinecap="round" strokeWidth="1.4" />
      {/* Các chùm lá trúc đỏ */}
      <path d="M64 16 Q48 24 38 36 Q56 28 64 16 Z" fill="currentColor" />
      <path d="M74 16 Q64 32 58 46 Q72 34 74 16 Z" fill="currentColor" />
      <path d="M84 18 Q80 34 78 48 Q88 34 84 18 Z" fill="currentColor" />
      <path d="M62 46 Q46 56 36 70 Q54 60 62 46 Z" fill="currentColor" />
      <path d="M74 44 Q64 60 58 76 Q72 62 74 44 Z" fill="currentColor" />
      <path d="M86 44 Q82 60 80 76 Q90 60 86 44 Z" fill="currentColor" />
      <path d="M98 54 Q108 66 116 80 Q104 70 98 54 Z" fill="currentColor" />
      <path d="M94 28 Q106 38 114 52 Q100 42 94 28 Z" fill="currentColor" />
    </svg>
  )
}

function NavCardMountainArt() {
  return (
    <svg
      aria-hidden="true"
      className="pointer-events-none absolute bottom-0 right-0 h-20 w-48 text-brand-600"
      fill="none"
      preserveAspectRatio="xMaxYMax meet"
      viewBox="0 0 200 84"
    >
      <defs>
        <linearGradient id="cnhsk-nav-mtn" x1="0" x2="0" y1="0" y2="1">
          <stop offset="0%" stopColor="currentColor" stopOpacity="0.28" />
          <stop offset="100%" stopColor="currentColor" stopOpacity="0.02" />
        </linearGradient>
      </defs>
      <path
        d="M12 84 C54 56, 92 52, 134 72 C158 52, 182 48, 200 64 L200 84 Z"
        fill="url(#cnhsk-nav-mtn)"
      />
      <path d="M84 84 C124 58, 162 50, 200 72 L200 84 Z" fill="currentColor" opacity="0.2" />
      <g className="text-brand-700" opacity="0.48" transform="translate(148, 28) scale(0.55)">
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
        <rect fill="currentColor" height="14" width="3" x="12" y="29" />
        <rect fill="currentColor" height="14" width="3" x="21" y="29" />
        <rect fill="currentColor" height="14" width="3" x="32" y="29" />
        <rect fill="currentColor" height="14" width="3" x="41" y="29" />
        <path d="M7 43 L49 43 L52 48 L4 48 Z" fill="currentColor" />
      </g>
    </svg>
  )
}

function QuestionFooterMountainArt() {
  return (
    <svg
      aria-hidden="true"
      className="pointer-events-none absolute inset-x-0 bottom-0 h-20 w-full text-brand-600"
      fill="none"
      preserveAspectRatio="none"
      viewBox="0 0 720 80"
    >
      <defs>
        <linearGradient id="cnhsk-qfoot-mtn" x1="0" x2="0" y1="0" y2="1">
          <stop offset="0%" stopColor="currentColor" stopOpacity="0.18" />
          <stop offset="100%" stopColor="currentColor" stopOpacity="0" />
        </linearGradient>
      </defs>
      {/* Áng mây xoắn cổ phong bên phải */}
      <path
        d="M650 18 C658 12, 668 12, 674 18 C682 14, 692 16, 694 24 C702 22, 710 26, 710 32 H642 C640 26, 644 20, 650 18 Z"
        opacity="0.35"
        stroke="currentColor"
        strokeWidth="2"
      />
      <path
        d="M90 80 L215 36 L295 66 L390 42 L485 72 L595 34 L695 76 L720 66 L720 80 Z"
        fill="url(#cnhsk-qfoot-mtn)"
      />
      <path
        d="M175 80 L255 48 L335 76 L465 52 L560 76 L640 50 L720 80 Z"
        fill="currentColor"
        opacity="0.1"
      />
    </svg>
  )
}

export function ExamAttemptView(props: Props) {
  const navigate = useNavigate()
  const [leaving, setLeaving] = useState(false)
  // Listening plays per question; the server rejects answers that report more than the allowed plays.
  const [plays, setPlays] = useState<Record<number, number>>({})
  const { sections, currentNo, choices, marks, placement, pending, readOnly } = props
  const questions = sections.flatMap((section) => section.questions)
  const index = questions.findIndex((question) => question.no === currentNo)
  const question = questions[index]
  const answered = questions.filter(({ no }) => choices[no] != null).length
  const remaining = questions.length - answered

  const progress = (
    <Card className="relative overflow-hidden rounded-lg border-brand-100 bg-brand-50 p-5">
      <ProgressCardBambooArt />
      <div className="relative z-nav space-y-3.5">
        <div className="flex items-center gap-2.5">
          <ClipboardCheck aria-hidden="true" className="size-5 shrink-0 text-brand-700" />
          <p className="text-label text-ink">
            Đã làm {answered}/{questions.length} câu · Còn lại {remaining} câu
          </p>
        </div>
        <div>
          <Button
            to="/learn/exams"
            disabled={pending}
            size="sm"
            variant="outline"
            className="bg-surface"
            onClick={(event) => {
              if (!readOnly) {
                event.preventDefault()
                setLeaving(true)
              }
            }}
          >
            <LogOut aria-hidden="true" className="mr-1.5 inline-block size-4 align-middle" />
            Thoát
          </Button>
        </div>
        <Progress
          value={answered}
          max={questions.length}
          label={`${answered}/${questions.length}`}
        />
      </div>
    </Card>
  )

  const error = props.error && (
    <p role="alert" className="text-danger">
      {props.error}
    </p>
  )

  const questionNavigation = (
    <details
      open
      className="relative overflow-hidden rounded-lg border border-brand-100 bg-brand-50 p-5"
    >
      <NavCardMountainArt />
      <summary className="relative z-nav min-h-touch cursor-pointer text-label font-bold text-ink focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus">
        Điều hướng câu hỏi
      </summary>
      <nav aria-label="Các phần của đề" className="relative z-nav mt-3 space-y-4">
        {sections.map((section) => (
          <div key={section.index} role="group" aria-label={section.name}>
            <h2 className="mb-3 flex items-center gap-2 text-h3 text-ink [overflow-wrap:anywhere]">
              <BookOpen aria-hidden="true" className="size-5 shrink-0 text-brand-700" />
              <span>{section.name}</span>
            </h2>
            <div className="flex flex-wrap gap-2.5">
              {section.questions.map(({ no }) => {
                const isCurrent = no === currentNo
                const isAnswered = choices[no] != null
                const isMarked = marks.includes(no)
                return (
                  <Button
                    key={no}
                    variant={isCurrent ? 'primary' : 'outline'}
                    className={`h-auto min-h-touch !rounded-pill px-4 py-2 whitespace-normal ${
                      isCurrent
                        ? 'bg-brand-600 text-surface'
                        : 'border-line bg-surface text-ink hover:border-brand-600'
                    }`}
                    aria-current={isCurrent ? 'step' : undefined}
                    onClick={() => props.onNavigate(no)}
                  >
                    <span>
                      Câu {no}
                      {isMarked ? ' · Đã đánh dấu' : ''}
                    </span>
                    {isAnswered ? (
                      <CheckCircle2
                        aria-hidden="true"
                        className={`ml-1.5 inline-block size-4 align-middle ${
                          isCurrent ? 'text-surface' : 'text-brand-600'
                        }`}
                      />
                    ) : null}
                  </Button>
                )
              })}
            </div>
          </div>
        ))}
      </nav>
    </details>
  )

  const stepButtons = (
    <div className="relative z-nav flex flex-wrap items-center justify-between gap-3 border-t border-brand-100 pt-5">
      <Button
        variant="outline"
        className="!rounded-pill px-5"
        disabled={!questions[index - 1]}
        onClick={() => {
          if (questions[index - 1]) props.onNavigate(questions[index - 1]!.no)
        }}
      >
        <ArrowLeft aria-hidden="true" className="mr-1.5 inline-block size-4 align-middle" />
        Câu trước
      </Button>

      <p className="text-label font-bold tabular-nums text-ink">
        <span className="text-brand-700">{index >= 0 ? index + 1 : currentNo}</span> /{' '}
        {questions.length}
      </p>

      <div className="flex flex-wrap items-center gap-2.5">
        {placement && !readOnly && (
          <Button
            variant="outline"
            className="!rounded-pill"
            disabled={pending || !question}
            onClick={props.onSkip}
          >
            Bỏ qua
          </Button>
        )}
        {questions[index + 1] ? (
          <Button
            className="!rounded-pill px-6"
            onClick={() => props.onNavigate(questions[index + 1]!.no)}
          >
            Câu tiếp theo
            <ArrowRight aria-hidden="true" className="ml-1.5 inline-block size-4 align-middle" />
          </Button>
        ) : placement ? (
          readOnly ? (
            <Button className="!rounded-pill px-6" disabled={pending} onClick={props.onContinue}>
              {props.retryNext ? 'Thử lại' : 'Tiếp tục'}
            </Button>
          ) : (
            <Button
              className="!rounded-pill px-6"
              disabled={pending || !question}
              onClick={props.onSubmit}
            >
              Nộp bài
            </Button>
          )
        ) : null}
      </div>
    </div>
  )

  const questionPanel = question && (
    <section
      className={`relative min-w-0 overflow-hidden border border-brand-100 bg-surface p-6 sm:p-8 ${
        placement ? 'rounded-md space-y-5' : 'rounded-lg space-y-6'
      }`}
    >
      <QuestionFooterMountainArt />
      <div className="relative z-nav space-y-5">
        <h2 className="flex items-center gap-2.5 text-h2 text-ink">
          <span className="flex size-9 shrink-0 items-center justify-center rounded-sm bg-brand-50 text-brand-700">
            <ClipboardList aria-hidden="true" className="size-5" />
          </span>
          <span>Câu {question.no}</span>
        </h2>

        <ExamQuestionBody
          question={question}
          answer={choices[question.no] ?? null}
          plays={plays[question.no] ?? 0}
          disabled={pending || readOnly}
          onAnswer={props.onChoose}
          onPlayed={() => setPlays((current) => ({ ...current, [question.no]: (current[question.no] ?? 0) + 1 }))}
        />

        {!placement && (
          <div className="flex flex-wrap items-center justify-between gap-3 pt-1">
            {!readOnly ? (
              <Button
                variant="outline"
                className={`border-brand-600 ${
                  marks.includes(question.no)
                    ? 'bg-brand-100 text-brand-700'
                    : 'bg-brand-50 text-brand-700 hover:bg-brand-100'
                }`}
                aria-pressed={marks.includes(question.no)}
                onClick={props.onMark}
              >
                <Bookmark aria-hidden="true" className="mr-1.5 inline-block size-4 align-middle" />
                Đánh dấu câu
              </Button>
            ) : (
              <span />
            )}

            {props.saveState ? (
              <p
                role="status"
                className={`inline-flex items-center gap-1.5 rounded-pill px-3.5 py-1.5 text-caption font-semibold ${
                  props.saveState === 'saved'
                    ? 'bg-success-bg text-success'
                    : props.saveState === 'failed'
                      ? 'bg-danger-bg text-danger'
                      : 'bg-brand-50 text-ink-secondary'
                }`}
              >
                {props.saveState === 'saved' ? (
                  <CheckCircle2 aria-hidden="true" className="size-4 shrink-0" />
                ) : props.saveState === 'failed' ? (
                  <CircleAlert aria-hidden="true" className="size-4 shrink-0" />
                ) : null}
                <span>
                  {props.saveState === 'saved'
                    ? 'Đã lưu'
                    : props.saveState === 'failed'
                      ? 'Chưa lưu được'
                      : 'Đang lưu…'}
                </span>
              </p>
            ) : null}
          </div>
        )}

        {stepButtons}
      </div>
    </section>
  )

  const submitButton = readOnly ? (
    <Button
      className={placement ? undefined : 'w-full !rounded-pill'}
      disabled={pending}
      onClick={props.onContinue}
    >
      {placement ? (props.retryNext ? 'Thử lại' : 'Tiếp tục') : 'Xem kết quả'}
    </Button>
  ) : (
    <Button
      className={placement ? undefined : 'w-full !rounded-pill'}
      disabled={pending || !question}
      onClick={placement ? props.onSubmit : () => props.onConfirmChange(true)}
    >
      Nộp bài
    </Button>
  )

  const confirmActions = (
    <>
      <Button variant="outline" disabled={pending} onClick={() => props.onConfirmChange(false)}>
        Quay lại làm bài
      </Button>
      <Button
        className={placement ? 'ml-3' : undefined}
        disabled={pending}
        onClick={props.onSubmit}
      >
        Xác nhận nộp bài
      </Button>
    </>
  )

  const confirmation = !readOnly && props.confirming && (
    <section
      aria-label="Xác nhận nộp bài"
      className="space-y-3 rounded-md border border-brand-100 bg-brand-50 p-4"
    >
      <p>Còn {remaining} câu chưa trả lời. Bạn muốn nộp bài?</p>
      {placement ? confirmActions : <div className="flex flex-wrap gap-3">{confirmActions}</div>}
    </section>
  )

  return (
    <main
      className={`mx-auto w-full min-w-0 space-y-5 px-4 py-6 pb-16 sm:px-6 ${
        placement ? 'max-w-reading' : 'max-w-content lg:max-w-[1072px]'
      }`}
    >
      <div className="relative overflow-hidden rounded-md border border-brand-100 bg-brand-50 px-5 py-5 sm:px-6">
        <PageHeader
          className="relative z-nav mb-0"
          subtitle={placement ? 'Kiểm tra xếp trình độ HSK' : 'Phiên làm bài thi thử HSK'}
          title={
            <span className="inline-flex items-center gap-3">
              <span aria-hidden="true" className="h-7 w-1.5 rounded-pill bg-brand-600" />
              <span>{props.title}</span>
            </span>
          }
        />
        <ChineseLandscapeBannerArt />
      </div>
      {placement ? (
        <>
          {progress}
          {error}
          {questionPanel}
          {questions[index + 1] ? <div className="flex justify-end">{submitButton}</div> : null}
          {confirmation}
        </>
      ) : (
        <>
          {error}
          <div className="grid min-w-0 items-start gap-4 lg:grid-cols-[minmax(0,1fr)_310px] lg:grid-rows-[auto_1fr] lg:gap-6">
            <div className="min-w-0 lg:col-start-2 lg:row-start-1">{progress}</div>
            <div className="min-w-0 space-y-4 lg:col-start-1 lg:row-span-2 lg:row-start-1">
              {questionPanel}
            </div>
            <aside
              aria-label="Điều khiển bài làm"
              className="min-w-0 space-y-4 lg:col-start-2 lg:row-start-2"
            >
              {questionNavigation}
              <Card className="rounded-lg border-brand-100 bg-brand-50">{submitButton}</Card>
              {confirmation}
            </aside>
          </div>
        </>
      )}
      <ConfirmPanel
        open={leaving}
        title="Rời bài làm?"
        message="Đáp án chưa lưu có thể bị mất. Bạn muốn rời bài làm?"
        confirmLabel="Rời bài làm"
        cancelLabel="Tiếp tục làm bài"
        pending={pending}
        onCancel={() => setLeaving(false)}
        onConfirm={() => navigate('/learn/exams')}
      />
    </main>
  )
}
