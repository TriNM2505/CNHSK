import { useEffect, useRef, useState } from 'react'

import type { GameAnswer, GameProps } from '../../shared/ui/GameShell'

export function RadicalMatchGame({ items, disabled, submitAnswer, onMissed, onItemShown }: GameProps) {
  const [position, setPosition] = useState(0)
  const [selected, setSelected] = useState<string>()
  const [feedback, setFeedback] = useState<GameAnswer>()
  const [showGrading, setShowGrading] = useState(false)
  const entered = useRef(false)
  const mounted = useRef(false)
  const item = items[position]
  const options = item?.options ?? []

  useEffect(() => {
    mounted.current = true
    return () => { mounted.current = false }
  }, [])

  useEffect(() => {
    if (item && options.length > 0 && !disabled) onItemShown?.(item.no)
  }, [disabled, item, onItemShown, options.length])

  useEffect(() => {
    if (!selected || feedback) return
    const timeout = window.setTimeout(() => setShowGrading(false), 300)
    return () => window.clearTimeout(timeout)
  }, [feedback, selected])

  useEffect(() => {
    if (!feedback) return
    const timeout = window.setTimeout(() => {
      entered.current = false
      setSelected(undefined)
      setFeedback(undefined)
      setPosition((current) => current + 1)
    }, feedback.correct ? 500 : 800)
    return () => window.clearTimeout(timeout)
  }, [feedback])

  async function choose(key: string) {
    if (!item || disabled || entered.current) return
    entered.current = true
    setSelected(key)
    setShowGrading(true)
    try {
      const response = await submitAnswer(item.no, key)
      if (mounted.current) setFeedback(response)
    } catch {
      // The shell owns retry and terminal errors.
    }
  }

  if (!item) return <p role="status" className="core-feedback">Đã đi qua tất cả các chữ.</p>
  const correctLabel = options.find((option) => option.key === feedback?.correctAnswer)?.text
  const answerClass = (key: string) => {
    if (!feedback) return ''
    if (key === feedback.correctAnswer) return 'answer-correct'
    return key === selected ? 'answer-wrong' : ''
  }
  return (
    <section aria-label="Ghép Bộ Thủ" className="min-w-0">
      <div className="core-panel core-center core-game-panel radical-panel">
        <div className="core-progress">CHỮ {position + 1} / {items.length}</div>
        <div className="question-progress"><span style={{ width: `${(position + 1) / items.length * 100}%` }} /></div>
        <span className="question-kicker">CHỌN BỘ THỦ ĐÚNG</span>
        <div className="radical-orbit">
          <span className="orbit-mark orbit-a" aria-hidden="true">✦</span>
          <h2 className="core-hanzi break-words">{String(item.prompt.text ?? '')}</h2>
          <span className="orbit-mark orbit-b" aria-hidden="true">✧</span>
        </div>
        <p className="character-meta break-words">{String(item.prompt.hint ?? '')}</p>
        {options.length > 0 ? (
          <div className="answer-grid radical-grid" aria-label="Phương án" role="group">
            {options.map((option, index) => (
              <button type="button" key={option.key} disabled={disabled || selected !== undefined}
                className={answerClass(option.key)} onClick={() => void choose(option.key)}
              >
                <span className="answer-index" aria-hidden="true">{String.fromCharCode(65 + index)}</span>
                <b>{option.text}</b>
              </button>
            ))}
          </div>
        ) : (
          <button type="button" disabled={disabled} className="secondary-button"
            onClick={() => {
              onMissed(item.no)
              setPosition(position + 1)
            }}
          >Bỏ qua câu thiếu phương án</button>
        )}
      </div>
      <div className="core-feedback">
        {selected && !feedback && <p role="status">{showGrading ? 'Đang chấm…' : 'Đã gửi câu trả lời.'}</p>}
        {feedback && <p role="status">{feedback.correct ? 'Chính xác' : `Bộ thủ đúng: ${correctLabel ?? ''}`}</p>}
        {!selected && !feedback && <p>Chọn đáp án để ghi điểm.</p>}
      </div>
    </section>
  )
}
