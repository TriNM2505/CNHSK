import { useCallback, useEffect, useRef, useState } from 'react'
import type { FormEvent } from 'react'

import type { ServedItem } from '../../shared/api/rounds'
import type { GameProps } from '../../shared/ui/GameShell'
import { ArenaLayout } from '../shared/FallingTarget'
import { DartArena } from './DartArena'

/** Long enough for the dart to fly and the character to break, short enough not to eat the 60 seconds. */
const hitMs = 550
const wrongMs = 900

export function FlyingDartGame({ items, uiConfig, ...controls }: GameProps) {
  const [index, setIndex] = useState(0)
  const [hits, setHits] = useState(0)
  const next = useCallback((hit: boolean) => {
    if (hit) setHits((count) => count + 1)
    setIndex((position) => position + 1)
  }, [])
  const item = items[index]
  const maxLength = typeof uiConfig.maxInputLength === 'number' ? uiConfig.maxInputLength : 20
  return (
    <section aria-label="Phi Đao" className="min-w-0">
      {item
        ? <DartItem key={item.no} {...controls} item={item} hits={hits} maxLength={maxLength} onAdvance={next} />
        : <p role="status" className="core-feedback">Đã đi qua tất cả các chữ.</p>}
    </section>
  )
}

function DartItem({ item, hits, maxLength, disabled, submitAnswer, onItemShown, onAdvance }:
Omit<GameProps, 'items' | 'uiConfig'> & {
  item: ServedItem
  hits: number
  maxLength: number
  onAdvance: (hit: boolean) => void
}) {
  const text = String(item.prompt.text ?? '')
  const [input, setInput] = useState('')
  const [reading, setReading] = useState<string>()
  const [phase, setPhase] = useState<'aim' | 'sending' | 'hit' | 'wrong'>('aim')
  const field = useRef<HTMLInputElement>(null)
  const shown = useRef(false)
  const sent = useRef(false)
  const composing = useRef(false)
  const live = useRef(false)
  const canType = !disabled && phase === 'aim'

  useEffect(() => { live.current = true; return () => { live.current = false } }, [])
  useEffect(() => {
    if (disabled || shown.current) return
    shown.current = true
    onItemShown?.(item.no)
  }, [disabled, item.no, onItemShown])
  useEffect(() => {
    if (canType) field.current?.focus()
  }, [canType])
  useEffect(() => {
    if (phase !== 'hit' && phase !== 'wrong') return
    const timeout = window.setTimeout(() => onAdvance(phase === 'hit'), phase === 'hit' ? hitMs : wrongMs)
    return () => window.clearTimeout(timeout)
  }, [onAdvance, phase])

  async function shoot(event: FormEvent) {
    event.preventDefault()
    const answer = input.trim()
    if (!canType || sent.current || composing.current || !answer) return
    sent.current = true
    setPhase('sending')
    try {
      const result = await submitAnswer(item.no, answer)
      if (!live.current) return
      setReading(result.correctAnswer)
      setPhase(result.correct ? 'hit' : 'wrong')
    } catch {
      if (live.current) onAdvance(false)
    }
  }

  return (
    <ArenaLayout arena={<DartArena text={text} state={phase === 'sending' ? 'aim' : phase} />}>
      <div className="prompt-label">GÕ ÂM HÁN VIỆT ĐỂ PHÓNG DAO</div>
      <p className="brush-hits">Số chữ đã hạ: {hits}</p>
      <form className="dart-form" onSubmit={(event) => void shoot(event)}>
        <label className="block">
          <span className="sr-only">Gõ âm Hán Việt</span>
          <input ref={field} type="text" value={input} maxLength={maxLength} disabled={disabled} readOnly={!canType}
            autoComplete="off" autoCapitalize="off" spellCheck={false} placeholder="Ví dụ: 人 → nhân"
            onChange={(event) => setInput(event.target.value)}
            onCompositionStart={() => { composing.current = true }}
            onCompositionEnd={() => { composing.current = false }}
            className={phase === 'wrong' ? 'input-wrong' : ''} />
        </label>
        <button type="submit" className="primary-button" disabled={!canType || !input.trim()}>Phóng dao</button>
      </form>
      <p className="input-hint">Có dấu hay không dấu đều được.</p>
      {phase === 'sending' && <p role="status" className="feedback">Đang chấm…</p>}
      {phase === 'hit' && <p role="status" className="feedback">Đã hạ chữ {text} · {reading}</p>}
      {phase === 'wrong' && <p role="status" className="feedback">Chưa đúng: {text} đọc là {reading}</p>}
    </ArenaLayout>
  )
}
