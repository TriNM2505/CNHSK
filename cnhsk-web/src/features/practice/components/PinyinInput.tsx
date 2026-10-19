import { useEffect, useState, type FormEvent } from 'react'

import { Button, FormField, HanziText, Input } from '@/shared/ui'

export interface PinyinInputProps {
  prompt: string
  hint?: string | null
  initialValue?: string | null
  disabled?: boolean
  pending?: boolean
  onSubmit: (answer: string) => Promise<unknown> | unknown
}

export function PinyinInput({
  prompt,
  hint,
  initialValue,
  disabled = false,
  pending = false,
  onSubmit,
}: PinyinInputProps) {
  const [answer, setAnswer] = useState(initialValue ?? '')

  useEffect(() => setAnswer(initialValue ?? ''), [initialValue, prompt])

  const submit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    const normalizedAnswer = answer.trim()
    if (!normalizedAnswer || disabled || pending) return
    void onSubmit(normalizedAnswer)
  }

  return (
    <form className="space-y-4" onSubmit={submit}>
      <div className="text-center">
        <HanziText className="block" size="lg">
          {prompt}
        </HanziText>
        {hint ? <p className="mt-2 text-body-sm text-ink-secondary">Gợi ý: {hint}</p> : null}
      </div>
      <FormField hint="Gõ số thanh, ví dụ ni3 hao3" htmlFor="practice-pinyin-answer" label="Pinyin">
        <Input
          autoComplete="off"
          disabled={disabled || pending}
          id="practice-pinyin-answer"
          onChange={(event) => setAnswer(event.target.value)}
          placeholder="ni3 hao3"
          value={answer}
        />
      </FormField>
      <Button disabled={!answer.trim() || disabled} loading={pending} type="submit">
        Trả lời
      </Button>
    </form>
  )
}
