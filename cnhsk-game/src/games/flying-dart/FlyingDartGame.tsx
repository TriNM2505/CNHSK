import { useCallback, useEffect, useRef, useState } from 'react'
import type { KeyboardEvent } from 'react'

import { validatePinyinInput } from '../../engine/pinyin'
import type { ServedItem } from '../../shared/api/rounds'
import type { GameProps } from '../../shared/ui/GameShell'
import { FallingTarget, MissedTarget } from '../shared/FallingTarget'
import type { ReadPinyin } from '../shared/FallingTarget'
import { itemFallDuration, useFallTimer } from '../shared/useFallTimer'

export function FlyingDartGame({ items, uiConfig, readPinyin, ...controls }: GameProps & { readPinyin?: ReadPinyin }) {
  const [index, setIndex] = useState(0)
  const next = useCallback(() => setIndex((position) => position + 1), [])
  const item = items[index]
  const maxLength = typeof uiConfig.maxInputLength === 'number' ? uiConfig.maxInputLength : 40
  return (
    <section aria-label="Phi Đao" className="min-w-0 space-y-3 text-center">
      {item ? <DartItem key={item.no} {...controls} item={item} duration={itemFallDuration(index, uiConfig)}
        maxLength={maxLength} readPinyin={readPinyin} onAdvance={next} />
        : <p role="status">Đã đi qua tất cả các từ.</p>}
    </section>
  )
}

function DartItem({ item, duration, maxLength, readPinyin, disabled, submitAnswer, onItemShown, onMissed, onAdvance }: Omit<GameProps, 'items' | 'uiConfig'> & {
  item: ServedItem
  duration: number
  maxLength: number
  readPinyin?: ReadPinyin
  onAdvance: () => void
}) {
  const text = String(item.prompt.text ?? '')
  const [input, setInput] = useState('')
  const [message, setMessage] = useState<string>()
  const [pinyin, setPinyin] = useState<string>()
  const [phase, setPhase] = useState<'falling' | 'sending' | 'wrong' | 'hit' | 'miss'>('falling')
  const field = useRef<HTMLInputElement>(null)
  const shown = useRef(false)
  const sent = useRef(false)
  const grounded = useRef(false)
  const live = useRef(false)
  const falling = phase !== 'hit' && phase !== 'miss'
  const fall = useFallTimer(duration, falling && !disabled, ground)
  const canType = !disabled && (phase === 'falling' || phase === 'wrong')

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
    if (falling) return
    const timeout = window.setTimeout(onAdvance, phase === 'hit' ? 200 : 600)
    return () => window.clearTimeout(timeout)
  }, [falling, onAdvance, phase])

  function ground() {
    if (grounded.current) return
    grounded.current = true
    if (!sent.current) onMissed(item.no)
    if (phase !== 'sending') setPhase('miss')
  }

  async function enter(event: KeyboardEvent<HTMLInputElement>) {
    if (event.key !== 'Enter' || event.nativeEvent.isComposing || event.keyCode === 229 || !canType) return
    event.preventDefault()
    if (fall.expired()) { ground(); return }
    const format = validatePinyinInput(input)
    if (format !== 'ok') {
      if (format === 'partial-tones') setMessage('Gõ đủ thanh cho mọi âm tiết hoặc bỏ hết thanh')
      else if (format === 'invalid') setMessage('Kiểm tra định dạng pinyin.')
      return
    }
    setMessage(undefined)
    if (sent.current) return
    sent.current = true
    setPhase('sending')
    try {
      const result = await submitAnswer(item.no, input.trim())
      if (!live.current) return
      if (result.correct) {
        setPhase('hit')
      } else {
        setInput('')
        setPinyin(result.correctAnswer)
        setPhase(grounded.current || fall.expired() ? 'miss' : 'wrong')
      }
    } catch {
      if (live.current) onAdvance()
    }
  }

  if (phase === 'miss') {
    return <MissedTarget text={text} pinyin={pinyin} lexemeId={sent.current ? undefined : item.lexemeId}
      readPinyin={readPinyin} />
  }
  return (
    <>
      <FallingTarget text={text} hint={String(item.prompt.hint ?? '')} remaining={fall.remaining}
        duration={duration} hit={phase === 'hit'} projectile />
      {pinyin && <p role="status">Pinyin đúng: {pinyin}</p>}
      <label className="block">
        <span>Gõ pinyin</span>
        <input ref={field} type="text" value={input} maxLength={maxLength} disabled={disabled} readOnly={!canType}
          autoComplete="off" autoCapitalize="off" spellCheck={false} onKeyDown={(event) => void enter(event)}
          onChange={(event) => { setInput(event.target.value); setMessage(undefined) }}
          className={'mx-auto mt-1 block min-h-touch w-full max-w-sm rounded-sm border px-3 ' +
            (phase === 'wrong' ? 'border-red-700 motion-safe:animate-[dart-shake_240ms_ease-in-out]' : 'border-line')} />
      </label>
      {phase === 'wrong' && <style>{'@keyframes dart-shake{25%{transform:translateX(-4px)}75%{transform:translateX(4px)}}'}</style>}
      {phase === 'sending' && <p role="status">Đang chấm…</p>}
      {phase === 'hit' && <p role="status">Đã hạ từ {text}</p>}
      {message && <p role="alert">{message}</p>}
    </>
  )
}
