import { useEffect, useRef, useState } from 'react'

import type { ServedItem } from '../../shared/api/rounds'
import type { GameAnswer, GameProps } from '../../shared/ui/GameShell'

type BoardProps = Pick<GameProps, 'disabled' | 'submitAnswer'> & {
  words: readonly ServedItem[]
  onComplete: () => void
}

type Selection = { no: number; key: string }
type Feedback = GameAnswer & Selection

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
            <span className="core-progress">BẢNG {index + 1} / {boards.length}</span>
            <h2>Bảng {index + 1}/{boards.length}</h2>
            <p>Chọn một thẻ chữ Hán, rồi chọn pinyin tương ứng.</p>
          </div>
        </div>
        <PinyinBoard key={index} words={words} disabled={disabled} submitAnswer={submitAnswer}
          onComplete={() => setIndex((previous) => previous + 1)} />
      </div>
    </section>
  )
}

function PinyinBoard({ words, disabled, submitAnswer, onComplete }: BoardProps) {
  const options = words[0]?.options ?? []
  const [selected, setSelected] = useState<number>()
  const [submission, setSubmission] = useState<Selection>()
  const [feedback, setFeedback] = useState<Feedback>()
  const [showGrading, setShowGrading] = useState(false)
  const [removedWords, setRemovedWords] = useState<number[]>([])
  const [removedKeys, setRemovedKeys] = useState<string[]>([])
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
      setRemovedWords((previous) => [...previous, feedback.no])
      setRemovedKeys((previous) => [...previous, feedback.correctAnswer])
      setSelected(undefined)
      setSubmission(undefined)
      setFeedback(undefined)
      pending.current = false
    }, feedback.correct ? 250 : 800)
    return () => window.clearTimeout(timeout)
  }, [feedback])

  useEffect(() => {
    if (removedWords.length === words.length) onComplete()
  }, [onComplete, removedWords.length, words.length])

  async function chooseCard(key: string) {
    if (selected === undefined || disabled || pending.current || removedKeys.includes(key)) return
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
      setRemovedWords((previous) => [...previous, choice.no])
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
  const fade = 'opacity-0 transition-opacity duration-[250ms] motion-reduce:transition-none'
  const cardClass = (key: string) => {
    if (key === feedback?.correctAnswer) return feedback.correct ? fade : 'answer-correct'
    return key === submission?.key ? 'active' : ''
  }

  return (
    <>
      <div className="pair-progress" aria-hidden="true">
        {words.map((word, i) => <span key={word.no} className={i < removedWords.length ? 'filled' : ''} />)}
      </div>
      <div className="match-columns">
        <div className="min-w-0">
          <h3 className="match-heading">Từ tiếng Trung</h3>
          <div role="group" aria-label="Từ tiếng Trung" className="match-stack">
            {words.filter((word) => !removedWords.includes(word.no)).map((word) => (
              <button type="button" key={word.no} aria-pressed={selected === word.no} disabled={locked}
                className={`match-tile ${tile} ${selected === word.no ? 'active' : ''} ${
                  feedback?.correct && feedback.no === word.no ? fade : ''
                }`}
                onClick={() => setSelected(word.no)}
              >{String(word.prompt.text ?? '')}</button>
            ))}
          </div>
        </div>
        <div className="min-w-0">
          <h3 className="match-heading">Pinyin</h3>
          <div role="group" aria-label="Pinyin" className="match-stack">
            {options.filter((option) => !removedKeys.includes(option.key)).map((option) => (
              <button type="button" key={option.key} disabled={locked || selected === undefined}
                className={`match-tile ${tile} ${cardClass(option.key)}`}
                onClick={() => void chooseCard(option.key)}
              >{option.text}</button>
            ))}
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
            : rejected ? 'Không nhận được kết quả cho cặp này. Chọn từ tiếp theo.' : ''}
      </p>
    </>
  )
}
