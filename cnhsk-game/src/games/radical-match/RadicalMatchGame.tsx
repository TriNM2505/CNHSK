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

  if (!item) return <p role="status">Đã đi qua tất cả các chữ.</p>
  const correctLabel = options.find((option) => option.key === feedback?.correctAnswer)?.text
  return (
    <section aria-label="Ghép Bộ Thủ" className="min-w-0 space-y-3 text-center">
      <h2 className="break-words text-[96px] leading-none">{String(item.prompt.text ?? '')}</h2>
      <p className="break-words text-ink-secondary">{String(item.prompt.hint ?? '')}</p>
      {options.length > 0 ? (
        <div className="grid grid-cols-2 gap-2" aria-label="Phương án" role="group">
          {options.map((option) => (
            <button type="button" key={option.key} disabled={disabled || selected !== undefined}
              className={`min-w-0 rounded-sm border px-1 text-[64px] leading-tight ${
                feedback?.correctAnswer === option.key ? 'border-brand-800 bg-brand-800 text-white'
                : selected === option.key ? 'border-brand-800 font-bold' : 'border-line'
              }`}
              onClick={() => void choose(option.key)}
            >{option.text}</button>
          ))}
        </div>
      ) : (
        <button type="button" disabled={disabled} className="min-h-touch rounded-sm border border-line px-4"
          onClick={() => {
            onMissed(item.no)
            setPosition(position + 1)
          }}
        >Bỏ qua câu thiếu phương án</button>
      )}
      {selected && !feedback && <p role="status">{showGrading ? 'Đang chấm…' : 'Đã gửi câu trả lời.'}</p>}
      {feedback && <p role="status">{feedback.correct ? 'Chính xác' : `Bộ thủ đúng: ${correctLabel ?? ''}`}</p>}
    </section>
  )
}
