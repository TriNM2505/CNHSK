import { CheckCircle2, Circle, CircleDot, RotateCcw, Volume2 } from 'lucide-react'
import { useEffect, useState } from 'react'

import { speakChinese } from '@/shared/lib/speech'
import { Button } from '@/shared/ui'

export type AttemptOption = { key: string; text: string; imageUrl?: string | null }

/** A text made only of emoji stands for a picture, so it is drawn large. */
const EMOJI_ONLY = /^(?:\p{Extended_Pictographic}|\p{Emoji_Modifier}|\u200D|\uFE0F|\u20E3|\s)+$/u
/** The emoji picture that opens a line such as "🐱 猫", drawn large above the rest of the line. */
const LEADING_PICTURE = /^((?:\p{Extended_Pictographic}|\p{Emoji_Modifier}|\u200D|\uFE0F|\u20E3)+)(?:\s+|$)/u
function pictureLines(text: string) {
  return text.split('\n').flatMap((line) => {
    const match = LEADING_PICTURE.exec(line)
    const picture = match?.[1]
    if (!match || !picture || !/\p{Extended_Pictographic}/u.test(picture)) return [{ picture: false, text: line }]
    const rest = line.slice(match[0].length)
    return rest ? [{ picture: true, text: picture }, { picture: false, text: rest }] : [{ picture: true, text: picture }]
  })
}
function isEmojiOnly(text: string) {
  return EMOJI_ONLY.test(text) && /\p{Extended_Pictographic}/u.test(text)
}
/** How a question is answered: pick one option, put the fragments in order, or type the missing characters. */
export type AttemptKind = 'choice' | 'order' | 'fill'
export type AttemptListening = { speakText: string | null; audioUrl: string | null; maxPlays: number }
export type AttemptQuestion = {
  no: number
  text: string
  options: AttemptOption[]
  kind?: AttemptKind
  imageUrl?: string | null
  listening?: AttemptListening | null
}

type Props = {
  question: AttemptQuestion
  answer: string | null
  plays: number
  disabled: boolean
  onAnswer: (answer: string) => void
  onPlayed: () => void
}

function play(listening: AttemptListening): Promise<void> {
  if (listening.audioUrl) {
    const audio = new Audio(listening.audioUrl)
    return new Promise((resolve, reject) => {
      audio.onended = () => resolve()
      audio.onerror = () => reject(new Error('Không phát được âm thanh.'))
      audio.play().catch(reject)
    })
  }
  return speakChinese(listening.speakText ?? '')
}

function ListenButton({ listening, plays, onPlayed }: { listening: AttemptListening; plays: number; onPlayed: () => void }) {
  const [state, setState] = useState<'idle' | 'playing' | 'failed'>('idle')
  const left = Math.max(0, listening.maxPlays - plays)
  return (
    <div className="flex flex-wrap items-center gap-3 rounded-md border border-brand-100 bg-brand-50 px-5 py-4">
      <Button
        disabled={left === 0 || state === 'playing'}
        onClick={() => {
          onPlayed()
          setState('playing')
          play(listening).then(
            () => setState('idle'),
            () => setState('failed'),
          )
        }}
      >
        <Volume2 aria-hidden="true" className="mr-1.5 inline-block size-5 align-middle" />
        {state === 'playing' ? 'Đang phát…' : 'Nghe'}
      </Button>
      <p className="text-body-sm text-ink-secondary">
        {left > 0 ? `Còn ${left} lượt nghe` : 'Đã hết lượt nghe'}
      </p>
      {state === 'failed' ? (
        <p role="alert" className="w-full text-body-sm text-danger">
          Trình duyệt này không đọc được tiếng Trung. Hãy dùng Chrome hoặc Edge.
        </p>
      ) : null}
    </div>
  )
}

function ChoiceOptions({ question, answer, disabled, onAnswer }: Omit<Props, 'plays' | 'onPlayed'>) {
  const pictures = question.options.some(({ imageUrl, text }) => imageUrl || isEmojiOnly(text))
  return (
    <div
      className={pictures ? 'grid grid-cols-2 gap-3 sm:grid-cols-3' : 'grid gap-3'}
      role="group"
      aria-label="Phương án"
    >
      {question.options.map(({ key, text, imageUrl }) => {
        const isSelected = answer === key
        return (
          <Button
            key={key}
            variant="outline"
            className={`h-auto min-h-touch w-full justify-start whitespace-normal rounded-md p-4 text-left [&>span]:flex [&>span]:w-full [&>span]:min-w-0 [&>span]:items-center [&>span]:justify-between [&>span]:gap-3 ${
              isSelected ? 'border-brand-600 bg-brand-50 text-ink shadow-sm' : 'border-line bg-surface text-ink hover:border-brand-600'
            }`}
            aria-label={imageUrl || isEmojiOnly(text) ? `Hình ${key}` : undefined}
            aria-pressed={isSelected}
            disabled={disabled}
            onClick={() => onAnswer(key)}
          >
            <span className="flex min-w-0 items-center gap-3">
              {isSelected ? (
                <CircleDot aria-hidden="true" className="size-5 shrink-0 text-brand-600" />
              ) : (
                <Circle aria-hidden="true" className="size-5 shrink-0 text-ink-muted" />
              )}
              {imageUrl ? (
                <span className="flex min-w-0 flex-col gap-2">
                  <span className="font-semibold">{key}</span>
                  <img alt="" className="aspect-square w-full rounded-sm object-contain" loading="lazy" src={imageUrl} />
                </span>
              ) : isEmojiOnly(text) ? (
                <span className="flex min-w-0 flex-col gap-2">
                  <span className="font-semibold">{key}</span>
                  <span aria-hidden="true" className="text-center text-6xl leading-none">
                    {text}
                  </span>
                </span>
              ) : (
                <span className="min-w-0 font-semibold [overflow-wrap:anywhere]">
                  {key}. <span className="font-hanzi">{text}</span>
                </span>
              )}
            </span>
            {isSelected ? <CheckCircle2 aria-hidden="true" className="size-5 shrink-0 text-brand-600" /> : null}
          </Button>
        )
      })}
    </div>
  )
}

/** Fragments are tapped in order; the answer is their keys in that order, sent once every fragment is used. */
function OrderOptions({ question, answer, disabled, onAnswer }: Omit<Props, 'plays' | 'onPlayed'>) {
  const keys = question.options.map(({ key }) => key)
  const [picked, setPicked] = useState<string[]>(() => (answer ? [...answer].filter((key) => keys.includes(key)) : []))
  useEffect(() => setPicked(answer ? [...answer] : []), [answer, question.no])
  const textOf = (key: string) => question.options.find((option) => option.key === key)?.text ?? ''

  function pick(key: string) {
    const next = [...picked, key]
    setPicked(next)
    if (next.length === keys.length) onAnswer(next.join(''))
  }

  return (
    <div className="space-y-4">
      <p
        aria-label="Câu bạn ghép"
        className="min-h-14 rounded-md border border-dashed border-brand-600 px-4 py-3 font-hanzi text-h3 text-ink"
        lang="zh-CN"
      >
        {picked.map(textOf).join('') || <span className="font-sans text-body text-ink-muted">Bấm các mảnh theo thứ tự</span>}
      </p>
      <div className="flex flex-wrap gap-2.5" role="group" aria-label="Mảnh câu">
        {question.options.map(({ key, text }) => (
          <Button
            key={key}
            variant="outline"
            className="font-hanzi text-h3"
            disabled={disabled || picked.includes(key)}
            onClick={() => pick(key)}
          >
            {text}
          </Button>
        ))}
      </div>
      <Button variant="ghost" size="sm" disabled={disabled || picked.length === 0} onClick={() => setPicked([])}>
        <RotateCcw aria-hidden="true" className="mr-1.5 inline-block size-4 align-middle" />
        Ghép lại
      </Button>
    </div>
  )
}

function FillInput({ question, answer, disabled, onAnswer }: Omit<Props, 'plays' | 'onPlayed'>) {
  const [value, setValue] = useState(answer ?? '')
  useEffect(() => setValue(answer ?? ''), [answer, question.no])
  const typed = value.trim()
  return (
    <form
      className="flex flex-wrap items-center gap-3"
      onSubmit={(event) => {
        event.preventDefault()
        if (typed) onAnswer(typed)
      }}
    >
      <label className="sr-only" htmlFor={`fill-${question.no}`}>
        Chữ Hán cần điền
      </label>
      <input
        className="min-h-touch w-48 rounded-md border border-line bg-surface px-4 font-hanzi text-lg text-ink focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus"
        disabled={disabled}
        id={`fill-${question.no}`}
        lang="zh-CN"
        maxLength={50}
        onChange={(event) => setValue(event.target.value)}
        value={value}
      />
      <Button type="submit" disabled={disabled || !typed || typed === answer}>
        Lưu đáp án
      </Button>
    </form>
  )
}

/** The question itself: what is heard or read, its picture, and the way it is answered. */
export function ExamQuestionBody(props: Props) {
  const { question } = props
  const kind = question.kind ?? 'choice'
  return (
    <>
      {question.listening ? (
        <ListenButton listening={question.listening} plays={props.plays} onPlayed={props.onPlayed} />
      ) : null}
      {question.text ? (
        <p
          className="rounded-md border border-brand-100 bg-brand-50 px-5 py-4 font-hanzi text-h3 text-ink [overflow-wrap:anywhere]"
          lang="zh-CN"
        >
          {pictureLines(question.text).map((line, index) =>
            line.picture ? (
              <span key={index} role="img" aria-label="Hình minh hoạ" className="block text-center text-7xl leading-tight">
                {line.text}
              </span>
            ) : (
              <span key={index} className="block">
                {line.text}
              </span>
            ),
          )}
        </p>
      ) : null}
      {question.imageUrl ? (
        <img
          alt={`Hình của câu ${question.no}`}
          className="max-h-72 w-full rounded-md border border-line bg-surface object-contain"
          src={question.imageUrl}
        />
      ) : null}
      {kind === 'order' ? (
        <OrderOptions {...props} />
      ) : kind === 'fill' ? (
        <FillInput {...props} />
      ) : (
        <ChoiceOptions {...props} />
      )}
    </>
  )
}
