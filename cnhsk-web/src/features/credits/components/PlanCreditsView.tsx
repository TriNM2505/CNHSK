import {
  useEffect,
  useLayoutEffect,
  useRef,
  useState,
  type ChangeEvent,
  type KeyboardEvent,
} from 'react'

import type { ApiError, ApiQueryState } from '@/shared/api'
import {
  Button,
  Card,
  ErrorState,
  FormField,
  Input,
  PageHeader,
  Section,
  Skeleton,
} from '@/shared/ui'

import type { PaidFeature, RedeemRequest, RedeemResult, Wallet } from '../types'

export interface PlanCreditsViewProps {
  state: ApiQueryState
  wallet?: Wallet
  error?: ApiError
  pending: boolean
  redeemError?: ApiError
  result?: RedeemResult
  onRetry: () => void
  onRedeem: (body: RedeemRequest) => Promise<void>
}

const formatHint = 'Nhập đủ 16 chữ cái A–Z hoặc chữ số 0–9; có thể dán mã có dấu gạch.'
const invalidCodeMessage = 'Mã thẻ không hợp lệ hoặc không thể sử dụng.'
const featureNames: Record<PaidFeature, string> = {
  AI_GENERATE: 'Tạo câu hỏi',
  TRANSLATE: 'Dịch văn bản',
  ASSISTANT: 'Trợ lý',
  GRADING: 'Chấm bài',
}
const vietnamTime = new Intl.DateTimeFormat('vi-VN', {
  timeZone: 'Asia/Ho_Chi_Minh',
  day: '2-digit',
  month: '2-digit',
  year: 'numeric',
  hour: '2-digit',
  minute: '2-digit',
  hourCycle: 'h23',
})

function rawCode(value: string) {
  return value.replace(/[\s-]/g, '').replace(/[a-z]/g, (letter) => letter.toUpperCase())
}

function formattedCode(value: string) {
  return value.match(/.{1,4}/g)?.join('-') ?? ''
}

function displayPosition(position: number) {
  return position === 0 ? 0 : position + Math.floor((position - 1) / 4)
}

export function PlanCreditsView({
  state,
  wallet,
  error,
  pending,
  redeemError,
  result,
  onRetry,
  onRedeem,
}: PlanCreditsViewProps) {
  const [code, setCode] = useState('')
  const [formatError, setFormatError] = useState<string>()
  const [confirmation, setConfirmation] = useState(false)
  const [retryAt, setRetryAt] = useState<number>()
  const [remaining, setRemaining] = useState(0)
  const input = useRef<HTMLInputElement>(null)
  const caret = useRef<number>()
  const retrySeconds = redeemError?.extra.retryAfterSeconds
  const validRetry =
    redeemError?.status === 429 &&
    typeof retrySeconds === 'number' &&
    Number.isFinite(retrySeconds) &&
    retrySeconds > 0
  const message = !redeemError
    ? undefined
    : redeemError.errorCode === 'INVALID_CARD_CODE'
      ? invalidCodeMessage
      : validRetry
        ? 'Bạn đã thử quá nhiều lần. Vui lòng thử lại sau.'
        : 'Chưa thể nạp mã. Vui lòng thử lại.'

  useEffect(() => {
    if (!redeemError) return
    const seconds = redeemError.extra.retryAfterSeconds
    if (
      redeemError.status === 429 &&
      typeof seconds === 'number' &&
      Number.isFinite(seconds) &&
      seconds > 0
    ) {
      setRetryAt(Date.now() + seconds * 1000)
      setRemaining(Math.ceil(seconds))
    } else {
      setRetryAt(undefined)
      setRemaining(0)
      setConfirmation(false)
    }
  }, [redeemError])

  useEffect(() => {
    if (!result) return
    setCode('')
    setFormatError(undefined)
    setConfirmation(false)
  }, [result])

  useEffect(() => {
    if (retryAt === undefined) return
    const tick = () => {
      const seconds = Math.max(0, Math.ceil((retryAt - Date.now()) / 1000))
      setRemaining(seconds)
      if (seconds === 0) setRetryAt(undefined)
    }
    tick()
    const timer = window.setInterval(tick, 250)
    return () => window.clearInterval(timer)
  }, [retryAt])

  useLayoutEffect(() => {
    if (caret.current === undefined) return
    input.current?.setSelectionRange(caret.current, caret.current)
    caret.current = undefined
  })

  const blocked = pending || remaining > 0
  const changeCode = (event: ChangeEvent<HTMLInputElement>) => {
    const value = event.currentTarget.value
    const next = rawCode(value)
    if (!/^[A-Z0-9]{0,16}$/.test(next)) {
      setFormatError(formatHint)
      setConfirmation(false)
      return
    }
    const beforeCaret = rawCode(value.slice(0, event.currentTarget.selectionStart ?? value.length))
    caret.current = displayPosition(beforeCaret.length)
    setCode(formattedCode(next))
    setFormatError(undefined)
    setConfirmation(false)
  }

  const deleteAcrossSeparator = (event: KeyboardEvent<HTMLInputElement>) => {
    if (event.key !== 'Backspace' && event.key !== 'Delete') return
    const start = event.currentTarget.selectionStart ?? 0
    const end = event.currentTarget.selectionEnd ?? start
    if (start !== end) return
    const backward = event.key === 'Backspace'
    if ((backward ? code[start - 1] : code[start]) !== '-') return
    event.preventDefault()
    const position = rawCode(code.slice(0, start)).length
    const raw = rawCode(code)
    const removed = backward ? position - 1 : position
    setCode(formattedCode(raw.slice(0, removed) + raw.slice(removed + 1)))
    caret.current = displayPosition(backward ? position - 1 : position)
    setFormatError(undefined)
    setConfirmation(false)
  }

  const redeem = async () => {
    if (!confirmation || blocked || (retryAt ?? 0) > Date.now()) return
    await onRedeem({ code })
  }

  return (
    <div className="mx-auto max-w-content">
      <PageHeader
        title="Gói dịch vụ và điểm"
        actions={
          <Button to="/account/credits/history" variant="outline">
            Lịch sử giao dịch
          </Button>
        }
      />
      {result ? (
        <div
          role="status"
          className="mb-6 rounded-md border border-brand-100 bg-brand-50 p-4 text-body text-ink"
        >
          {result.cardType === 'CREDIT'
            ? `Đã nạp +${result.amount} điểm. Số dư: ${result.balance} điểm.`
            : `Đã cập nhật gói ${result.plan.name}.`}
        </div>
      ) : null}
      {state === 'loading' ? (
        <div role="status" aria-label="Đang tải ví điểm">
          <Skeleton variant="card" />
        </div>
      ) : state === 'error' ? (
        <ErrorState
          title="Chưa tải được ví điểm"
          message="Vui lòng thử lại để xem gói và số dư."
          requestId={error?.requestId}
          onRetry={onRetry}
        />
      ) : wallet ? (
        <div className="space-y-6">
          <Section title="Gói hiện tại">
            <Card>
              <dl className="grid gap-4 sm:grid-cols-3">
                <div>
                  <dt className="text-label text-ink-secondary">Tên gói</dt>
                  <dd className="mt-1 text-h3 text-ink">{wallet.plan.name}</dd>
                </div>
                <div>
                  <dt className="text-label text-ink-secondary">Ngày hết hạn</dt>
                  <dd className="mt-1 text-body text-ink">
                    {wallet.plan.expiresAt
                      ? `${vietnamTime.format(new Date(wallet.plan.expiresAt))} (giờ Việt Nam)`
                      : 'Không có ngày hết hạn'}
                  </dd>
                </div>
                <div>
                  <dt className="text-label text-ink-secondary">Số dư điểm</dt>
                  <dd className="mt-1 text-h3 tabular-nums text-brand-700">
                    {wallet.balance} điểm
                  </dd>
                </div>
              </dl>
            </Card>
          </Section>
          <Section
            title="Hạn mức theo tính năng"
            subtitle={`Đặt lại lúc ${vietnamTime.format(new Date(wallet.resetAt))} (giờ Việt Nam)`}
          >
            <div className="overflow-x-auto rounded-md border border-line bg-surface">
              <table className="w-full text-body-sm">
                <caption className="sr-only">Lượt sử dụng theo tính năng</caption>
                <thead>
                  <tr className="border-b border-line bg-surface-subtle text-left">
                    <th scope="col" className="p-3">
                      Tính năng
                    </th>
                    <th scope="col" className="p-3">
                      Hạn mức
                    </th>
                    <th scope="col" className="p-3">
                      Đã dùng
                    </th>
                    <th scope="col" className="p-3">
                      Còn lại
                    </th>
                  </tr>
                </thead>
                <tbody>
                  {wallet.quota.map((line) => (
                    <tr key={line.feature} className="border-b border-line last:border-b-0">
                      <th scope="row" className="p-3 text-left font-medium">
                        {featureNames[line.feature]}
                      </th>
                      <td className="p-3 tabular-nums">{line.limit}</td>
                      <td className="p-3 tabular-nums">{line.used}</td>
                      <td className="p-3 font-semibold tabular-nums">{line.remaining}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </Section>
          <Section title="Nạp mã thẻ">
            <Card>
              <form
                aria-label="Nạp mã thẻ"
                noValidate
                onSubmit={(event) => {
                  event.preventDefault()
                  if (blocked || (retryAt ?? 0) > Date.now()) return
                  if (!/^[A-Z0-9]{16}$/.test(rawCode(code)) || formatError) {
                    setFormatError(formatHint)
                    return
                  }
                  setConfirmation(true)
                }}
              >
                <FormField
                  htmlFor="credit-code"
                  label="Mã thẻ"
                  required
                  hint={formatHint}
                  error={formatError}
                >
                  <Input
                    ref={input}
                    autoComplete="off"
                    spellCheck={false}
                    autoCapitalize="characters"
                    placeholder="XXXX-XXXX-XXXX-XXXX"
                    value={code}
                    disabled={blocked}
                    onChange={changeCode}
                    onKeyDown={deleteAcrossSeparator}
                  />
                </FormField>
                <Button type="submit" disabled={blocked}>
                  Nạp mã
                </Button>
              </form>
              {confirmation ? (
                <section
                  aria-labelledby="redeem-confirm-title"
                  className="mt-4 rounded-md border border-brand-100 bg-brand-50 p-4"
                >
                  <h3 id="redeem-confirm-title" className="text-label text-ink">
                    Xác nhận nạp mã thẻ?
                  </h3>
                  <p className="mt-2 text-body-sm text-ink-secondary">
                    Mã sẽ được sử dụng để cập nhật điểm hoặc gói của bạn.
                  </p>
                  <div className="mt-4 flex flex-wrap gap-2">
                    <Button
                      disabled={blocked}
                      loading={pending}
                      onClick={() => {
                        void redeem()
                      }}
                    >
                      Xác nhận nạp
                    </Button>
                    <Button
                      disabled={pending}
                      variant="outline"
                      onClick={() => setConfirmation(false)}
                    >
                      Huỷ
                    </Button>
                  </div>
                </section>
              ) : null}
              {message ? (
                <p role="alert" className="mt-4 text-body-sm text-danger">
                  {message}
                </p>
              ) : null}
              {remaining > 0 ? (
                <p role="status" className="mt-2 text-body-sm text-ink-secondary">
                  Thử lại sau {remaining} giây.
                </p>
              ) : null}
            </Card>
          </Section>
        </div>
      ) : null}
      <p className="mt-6 text-body-sm text-ink-secondary">
        Mã thẻ do hệ sinh thái phát hành; CNHSK không thu tiền
      </p>
    </div>
  )
}
