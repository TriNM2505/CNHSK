import { Link } from 'react-router-dom'
import { ApiError } from '@/shared/api'

export function InteractionError({ error }: { error: unknown }) {
  if (!error) return null
  const message = error instanceof ApiError ? error.message : 'Không thể kết nối tới máy chủ. Vui lòng thử lại.'
  return <div role="alert" className="my-3 whitespace-pre-wrap rounded-md border border-danger p-3">
    <p>{message}</p>
    {error instanceof ApiError && error.requestId ? <p>Mã yêu cầu: {error.requestId}</p> : null}
    {error instanceof ApiError && typeof error.extra.retryAfterSeconds === 'number' ? <p>Thử lại sau {error.extra.retryAfterSeconds} giây.</p> : null}
    {error instanceof ApiError && Array.isArray(error.details) ? error.details.map((detail: unknown, index: number) => {
      if (typeof detail !== 'object' || detail === null || !('field' in detail) || !('message' in detail)) return null
      return typeof detail.field === 'string' && ['content', 'reason', 'detail'].includes(detail.field) && typeof detail.message === 'string' ? <p key={index}>{detail.message}</p> : null
    }) : null}
    {error instanceof ApiError && error.errorCode === 'ACCOUNT_UNVERIFIED' ? <Link to="/verify-email">Xác minh email</Link> : null}
  </div>
}
