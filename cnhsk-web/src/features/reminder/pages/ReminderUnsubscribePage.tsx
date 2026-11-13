import { useState } from 'react'
import { useSearchParams } from 'react-router-dom'

import { toApiError, useApiMutation } from '@/shared/api'
import { Button, Card, PageHeader } from '@/shared/ui'

import { unsubscribeReminderEmail } from '../api/reminder'

function UnsubscribeConfirmation({ token }: { token: string }) {
  const mutation = useApiMutation(unsubscribeReminderEmail)
  const missing = !token.trim()
  const [outcome, setOutcome] = useState<'success' | 'invalid' | 'limited' | 'error'>()

  async function unsubscribe() {
    if (missing || mutation.pending || outcome === 'success' || outcome === 'invalid') return
    setOutcome(undefined)
    try {
      await mutation.run(token)
      setOutcome('success')
    } catch (error) {
      const failure = toApiError(error)
      setOutcome(failure.status === 400 ? 'invalid' : failure.status === 429 ? 'limited' : 'error')
    }
  }

  return (
    <Card className="space-y-4 p-4 sm:p-6">
      <p className="text-body-sm text-ink-secondary">
        Bấm nút bên dưới để ngừng nhận email nhắc học. Nhắc trên Web vẫn giữ nguyên.
      </p>
      {missing ? (
        <p className="text-danger" role="alert">
          Liên kết không hợp lệ
        </p>
      ) : null}
      {outcome === 'invalid' ? (
        <div className="space-y-3">
          <p className="text-danger" role="alert">
            Liên kết không còn hiệu lực
          </p>
          <Button to="/login?redirect=%2Fme%2Freminders" variant="outline">
            Đăng nhập để cài đặt nhắc học
          </Button>
        </div>
      ) : null}
      {outcome === 'limited' ? (
        <p className="text-danger" role="alert">
          Bạn thao tác quá nhanh. Vui lòng thử lại sau.
        </p>
      ) : null}
      {outcome === 'error' ? (
        <p className="text-danger" role="alert">
          Không thể tắt nhắc qua email. Vui lòng thử lại.
        </p>
      ) : null}
      {outcome === 'success' ? (
        <p className="text-success" role="status">
          Đã tắt. Bạn có thể bật lại trong Cài đặt nhắc học.
        </p>
      ) : null}
      <Button
        disabled={missing || outcome === 'success' || outcome === 'invalid'}
        loading={mutation.pending}
        onClick={() => void unsubscribe()}
      >
        Tắt nhắc qua email
      </Button>
    </Card>
  )
}

export default function ReminderUnsubscribePage() {
  const [params] = useSearchParams()
  const token = params.get('token') ?? ''
  return (
    <main className="mx-auto w-full max-w-reading px-4 py-8 sm:px-6">
      <PageHeader title="Tắt nhắc qua email" />
      <UnsubscribeConfirmation key={token} token={token} />
    </main>
  )
}
