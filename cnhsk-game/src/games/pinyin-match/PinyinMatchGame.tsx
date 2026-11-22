import { useCallback, useEffect, useRef, useState } from 'react'

import type { ServedItem } from '../../shared/api/rounds'
import type { GameAnswer, GameProps } from '../../shared/ui/GameShell'

type BoardProps = Pick<GameProps, 'disabled' | 'submitAnswer'> & {
  words: readonly ServedItem[]
  last: boolean
  onComplete: () => void
}

type Selection = { no: number; key: string }
type Feedback = GameAnswer & Selection
/** A pair that has been answered: its word and the card that belongs to it. */
type Settled = { no: number; key?: string; correct: boolean }

/** How long a finished board stays up before the next one replaces it. */
const boardDoneMs = 900

const tile = 'min-w-0 break-words'

function readBoards(config: GameProps['uiConfig'], items: GameProps['items']) {
  if (!Array.isArray(config.boards)) return []
  const itemsByNo = new Map(items.map((item) => [item.no, item]))
  return config.boards.map((board: unknown) =>
    Array.isArray(board)
      ? board.flatMap((no: unknown) => {
          const item = typeof no === 'number' ? itemsByNo.get(no) : undefined
          return item ? [item] : []
        })
      : [],
  )
}

export function PinyinMatchGame({ items, uiConfig, submitAnswer, disabled }: GameProps) {
  const [index, setIndex] = useState(0)
  const nextBoard = useCallback(() => setIndex((previous) => previous + 1), [])
  const boards = readBoards(uiConfig, items)
  const words = boards[index]

  if (boards.length === 0 || (words && words.length !== 6)) {
    return <p role="alert">Chưa tải được bảng Ghép Pinyin.</p>
  }
  if (!words) return <p role="status">Đã ghép xong các bảng.</p>

  return (
    <section aria-label="Ghép Pinyin" className="min-w-0">
      <div className="core-panel core-game-panel match-panel">
        <div className="core-panel-heading">
          <div>
            <h2>Bảng {index + 1}/{boards.length}</h2>
            <p>Chọn một thẻ chữ Hán, rồi chọn pinyin tương ứng.</p>
          </div>
        </div>
        <PinyinBoard key={index} words={words} disabled={disabled} submitAnswer={submitAnswer}
          last={index === boards.length - 1} onComplete={nextBoard} />
      </div>
    </section>
  )
}

function PinyinBoard({ words, last, disabled, submitAnswer, onComplete }: BoardProps) {
  const options = words[0]?.options ?? []
  const [selected, setSelected] = useState<number>()
  const [submission, setSubmission] = useState<Selection>()
  const [feedback, setFeedback] = useState<Feedback>()
  const [showGrading, setShowGrading] = useState(false)
  const [settled, setSettled] = useState<Settled[]>([])
  const [rejected, setRejected] = useState(false)
  const pending = useRef(false)
  const live = useRef(false)

  useEffect(() => {
    live.current = true
    return () => { live.current = false }
  }, [])

  useEffect(() => {
    if (!submission || feedback) return
    const timeout = window.setTimeout(() => setShowGrading(false), 300)
    return () => window.clearTimeout(timeout)
  }, [feedback, submission])

  useEffect(() => {
    if (!feedback) return
    const timeout = window.setTimeout(() => {
      setSettled((previous) => [...previous, { no: feedback.no, key: feedback.correctAnswer, correct: feedback.correct }])
      setSelected(undefined)
      setSubmission(undefined)
      setFeedback(undefined)
      pending.current = false
    }, feedback.correct ? 250 : 800)
    return () => window.clearTimeout(timeout)
  }, [feedback])

  const complete = settled.length === words.length
  useEffect(() => {
    if (!complete) return
    const timeout = window.setTimeout(onComplete, boardDoneMs)
    return () => window.clearTimeout(timeout)
  }, [complete, onComplete])

  async function chooseCard(key: string) {
    if (selected === undefined || disabled || pending.current || settled.some((pair) => pair.key === key)) return
    pending.current = true
    const choice = { no: selected, key }
    setRejected(false)
    setSubmission(choice)
    setShowGrading(true)

    try {
      const response = await submitAnswer(choice.no, choice.key)
      if (live.current) setFeedback({ ...choice, ...response })
    } catch {
      if (!live.current) return
      setSettled((previous) => [...previous, { no: choice.no, correct: false }])
      setSelected(undefined)
      setSubmission(undefined)
      setShowGrading(false)
      setRejected(true)
      pending.current = false
    }
  }

  if (options.length !== 6) return <p role="alert">Bảng này thiếu thẻ pinyin.</p>

  const locked = disabled || submission !== undefined
  const correctCard = options.find((option) => option.key === feedback?.correctAnswer)
  const matchedWord = words.find((word) => word.no === feedback?.no)
  const settledClass = (pair: Pick<Settled, 'correct'>) => pair.correct ? 'settled settled-right' : 'settled settled-wrong'
  const wordClass = (no: number) => {
    const pair = settled.find((entry) => entry.no === no) ?? (feedback?.no === no ? feedback : undefined)
    return pair ? settledClass(pair) : selected === no ? 'active' : ''
  }
  const cardClass = (key: string) => {
    const pair = settled.find((entry) => entry.key === key)
    if (pair) return settledClass(pair)
    if (key === feedback?.correctAnswer) return feedback.correct ? settledClass(feedback) : 'answer-correct'
    return key === submission?.key ? 'active' : ''
  }
  const done = (pair: Settled | undefined) => pair && <span aria-hidden="true">{pair.correct ? '✓' : '✗'}</span>

  return (
    <>
      <div className="pair-progress" aria-hidden="true">
        {words.map((word, i) => <span key={word.no} className={i < settled.length ? 'filled' : ''} />)}
      </div>
      <div className="match-columns">
        <div className="min-w-0">
          <h3 className="match-heading">Từ tiếng Trung</h3>
          <div role="group" aria-label="Từ tiếng Trung" className="match-stack">
            {words.map((word) => {
              const pair = settled.find((entry) => entry.no === word.no)
              return (
                <button type="button" key={word.no} aria-pressed={selected === word.no} disabled={locked || pair !== undefined}
                  className={`match-tile ${tile} ${wordClass(word.no)}`} onClick={() => setSelected(word.no)}
                >{String(word.prompt.text ?? '')}{done(pair)}</button>
              )
            })}
          </div>
        </div>
        <div className="min-w-0">
          <h3 className="match-heading">Pinyin</h3>
          <div role="group" aria-label="Pinyin" className="match-stack">
            {options.map((option) => {
              const pair = settled.find((entry) => entry.key === option.key)
              return (
                <button type="button" key={option.key} disabled={locked || selected === undefined || pair !== undefined}
                  className={`match-tile ${tile} ${cardClass(option.key)}`} onClick={() => void chooseCard(option.key)}
                >{option.text}{done(pair)}</button>
              )
            })}
          </div>
        </div>
      </div>
      <p role="status" className="core-feedback">
        {feedback
          ? feedback.correct
            ? 'Chính xác'
            : `Cặp đúng: ${String(matchedWord?.prompt.text ?? '')} — ${correctCard?.text ?? ''}`
          : submission
            ? showGrading ? 'Đang chấm…' : 'Đã gửi câu trả lời.'
            : complete ? last ? 'Đã ghép xong bảng cuối.' : 'Xong bảng này, sang bảng tiếp theo…'
            : rejected ? 'Không nhận được kết quả cho cặp này. Chọn từ tiếp theo.' : ''}
      </p>
    </>
  )
}
