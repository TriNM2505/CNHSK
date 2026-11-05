import { useEffect, useRef, useState, type FormEvent } from 'react'

import type { ApiError, ApiQueryState } from '@/shared/api'
import { downloadBlob } from '@/shared/lib/downloadBlob'
import {
  Button,
  DataTable,
  EmptyState,
  ErrorState,
  FormField,
  Input,
  PageHeader,
  Pagination,
  Section,
  Select,
  Skeleton,
  type DataTableColumn,
} from '@/shared/ui'

import type {
  BatchPage,
  BatchSummary,
  CardType,
  Channel,
  CreateBatchRequest,
  CreatedBatch,
  VoidResult,
} from '../types'

export interface CardBatchViewProps {
  state: ApiQueryState
  data?: BatchPage
  error?: ApiError
  page: number
  pending?: 'create' | 'void'
  created?: CreatedBatch
  createError?: ApiError
  voided?: VoidResult
  voidError?: ApiError
  onPageChange: (page: number) => void
  onRetry: () => void
  onCreate: (body: CreateBatchRequest) => Promise<void>
  onVoid: (batchId: string) => Promise<void>
}

type BatchDraft = {
  cardType: CardType | ''
  channel: Channel | ''
  quantity: string
  value: string
  planDays: string
  partnerCode: string
  issuedTo: string
  campaign: string
  expiresAt: string
}

const channelNames: Record<Channel, string> = {
  ECOSYSTEM_GIFT: 'Quà hệ sinh thái',
  PARTNER_BATCH: 'Lô đối tác',
  DIRECT: 'Trực tiếp',
}
const cardNames: Record<CardType, string> = {
  CREDIT: 'Điểm (CREDIT)',
  SUBSCRIPTION: 'Gói PREMIUM (SUBSCRIPTION)',
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

function emptyDraft(): BatchDraft {
  return {
    cardType: '',
    channel: '',
    quantity: '',
    value: '',
    planDays: '',
    partnerCode: '',
    issuedTo: '',
    campaign: '',
    expiresAt: '',
  }
}

function integerInRange(value: string, max: number) {
  return /^\d+$/.test(value) && Number(value) >= 1 && Number(value) <= max
}

function expiryInstant(value: string) {
  // datetime-local is explicitly entered in Vietnam time, independent of the browser zone.
  return new Date(`${value}+07:00`)
}

function validate(draft: BatchDraft) {
  const errors: Partial<Record<keyof BatchDraft, string>> = {}
  if (!draft.cardType) errors.cardType = 'Chọn loại thẻ.'
  if (!draft.channel) errors.channel = 'Chọn kênh phát hành.'
  if (!integerInRange(draft.quantity, 100))
    errors.quantity = 'Số lượng mã phải là số nguyên từ 1 đến 100.'
  if (draft.cardType === 'CREDIT' && !integerInRange(draft.value, 10000))
    errors.value = 'Giá trị điểm phải là số nguyên từ 1 đến 10.000.'
  if (draft.cardType === 'SUBSCRIPTION' && !integerInRange(draft.planDays, 365))
    errors.planDays = 'Số ngày phải là số nguyên từ 1 đến 365.'
  if (
    draft.channel === 'PARTNER_BATCH' &&
    (!draft.partnerCode.trim() || draft.partnerCode.trim().length > 50)
  )
    errors.partnerCode = 'Nhập mã đối tác, tối đa 50 ký tự.'
  if (
    draft.channel === 'ECOSYSTEM_GIFT' &&
    (!draft.issuedTo.trim() || draft.issuedTo.trim().length > 255)
  )
    errors.issuedTo = 'Nhập thông tin người nhận, tối đa 255 ký tự.'
  if (draft.campaign.trim().length > 50) errors.campaign = 'Chiến dịch tối đa 50 ký tự.'
  if (
    draft.expiresAt &&
    (!Number.isFinite(expiryInstant(draft.expiresAt).getTime()) ||
      expiryInstant(draft.expiresAt).getTime() <= Date.now())
  )
    errors.expiresAt = 'Chọn thời điểm hết hạn trong tương lai hoặc để trống.'
  return errors
}

function batchRequest(draft: BatchDraft): CreateBatchRequest {
  return {
    cardType: draft.cardType as CardType,
    channel: draft.channel as Channel,
    quantity: Number(draft.quantity),
    ...(draft.cardType === 'CREDIT'
      ? { value: Number(draft.value) }
      : { planDays: Number(draft.planDays) }),
    ...(draft.channel === 'PARTNER_BATCH' ? { partnerCode: draft.partnerCode.trim() } : {}),
    ...(draft.channel === 'ECOSYSTEM_GIFT' ? { issuedTo: draft.issuedTo.trim() } : {}),
    ...(draft.campaign.trim() ? { campaign: draft.campaign.trim() } : {}),
    ...(draft.expiresAt ? { expiresAt: expiryInstant(draft.expiresAt).toISOString() } : {}),
  }
}

function createErrorMessage(error: ApiError) {
  if (error.status === 0 || error.status >= 500)
    return 'Không rõ lô đã được tạo chưa — kiểm tra bảng lô trước khi tạo lại'
  if (error.errorCode === 'PLAN_NOT_AVAILABLE')
    return 'Gói PREMIUM chưa có hoặc đang ngừng phát hành. Vui lòng kiểm tra cấu hình gói.'
  if (error.errorCode === 'VALIDATION_FAILED')
    return 'Thông tin lô chưa hợp lệ. Kiểm tra các trường và hạn dùng trước khi phát hành lại.'
  if (error.status === 401 || error.status === 403)
    return 'Phiên đăng nhập hoặc quyền quản trị không còn hợp lệ. Vui lòng kiểm tra lại quyền truy cập.'
  return 'Chưa thể phát hành lô mã. Vui lòng kiểm tra thông tin và thử lại.'
}

export function CardBatchView({
  state,
  data,
  error,
  page,
  pending,
  created,
  createError,
  voided,
  voidError,
  onPageChange,
  onRetry,
  onCreate,
  onVoid,
}: CardBatchViewProps) {
  const [draft, setDraft] = useState(emptyDraft)
  const [validation, setValidation] = useState<Partial<Record<keyof BatchDraft, string>>>({})
  const [confirmation, setConfirmation] = useState<CreateBatchRequest>()
  const [selected, setSelected] = useState<BatchSummary>()
  const [downloadError, setDownloadError] = useState<string>()
  const form = useRef<HTMLFormElement>(null)
  const returnFocus = useRef<HTMLElement | null>(null)
  const disabled = Boolean(pending) || Boolean(confirmation)

  useEffect(() => {
    if (confirmation || selected || pending || !returnFocus.current) return
    const target = returnFocus.current.isConnected
      ? returnFocus.current
      : form.current?.querySelector<HTMLButtonElement>('button[type="submit"]')
    target?.focus()
    returnFocus.current = null
  }, [confirmation, selected, pending])

  const change = (field: keyof BatchDraft, value: string) => {
    setDraft((current) => ({
      ...current,
      [field]: value,
      ...(field === 'cardType' ? { value: '', planDays: '' } : {}),
      ...(field === 'channel' ? { partnerCode: '', issuedTo: '' } : {}),
    }))
    setValidation({})
  }

  const prepare = (event: FormEvent) => {
    event.preventDefault()
    if (pending || confirmation) return
    const errors = validate(draft)
    setValidation(errors)
    const first = Object.keys(errors)[0]
    if (first) {
      ;(form.current?.elements.namedItem(first) as HTMLElement | null)?.focus()
      return
    }
    setSelected(undefined)
    returnFocus.current =
      form.current?.querySelector<HTMLButtonElement>('button[type="submit"]') ?? null
    setConfirmation(batchRequest(draft))
  }

  const download = () => {
    if (!created) return
    setDownloadError(undefined)
    try {
      const csv =
        ['batchId,code', ...created.codes.map((code) => `${created.batchId},${code}`)].join(
          '\r\n',
        ) + '\r\n'
      downloadBlob(
        new Blob([csv], { type: 'text/csv;charset=utf-8' }),
        `card-batch-${created.batchId}.csv`,
      )
    } catch {
      setDownloadError(
        'Chưa tải được CSV. Mã vẫn hiện ở đây; vui lòng thử tải lại trước khi rời trang.',
      )
    }
  }

  const columns: readonly DataTableColumn<BatchSummary>[] = [
    { id: 'id', header: 'Mã lô', render: (batch) => batch.batchId },
    {
      id: 'kind',
      header: 'Loại / Giá trị',
      render: (batch) => (
        <span>
          {cardNames[batch.cardType]} ·{' '}
          {batch.cardType === 'CREDIT'
            ? `${batch.value ?? '—'} điểm`
            : `${batch.planDays ?? '—'} ngày`}
        </span>
      ),
    },
    {
      id: 'channel',
      header: 'Kênh / Chiến dịch',
      render: (batch) => (
        <span>
          {channelNames[batch.channel]}
          {batch.campaign ? ` · ${batch.campaign}` : ''}
        </span>
      ),
    },
    {
      id: 'used',
      header: 'Đã dùng / Tổng',
      numeric: true,
      render: (batch) => `${batch.used}/${batch.total}`,
    },
    { id: 'unused', header: 'Chưa dùng', numeric: true, render: (batch) => batch.unused },
    { id: 'voided', header: 'Đã khoá', numeric: true, render: (batch) => batch.voided },
    {
      id: 'created',
      header: 'Ngày tạo (giờ Việt Nam)',
      render: (batch) => (
        <div>
          <time dateTime={batch.createdAt}>{vietnamTime.format(new Date(batch.createdAt))}</time>
          {batch.createdByName ? (
            <p className="text-body-sm text-ink-secondary">{batch.createdByName}</p>
          ) : null}
        </div>
      ),
    },
    {
      id: 'expiry',
      header: 'Hạn dùng (giờ Việt Nam)',
      render: (batch) =>
        batch.expiresAt ? (
          <time dateTime={batch.expiresAt}>{vietnamTime.format(new Date(batch.expiresAt))}</time>
        ) : (
          'Không có hạn'
        ),
    },
    {
      id: 'action',
      header: 'Thao tác',
      render: (batch) =>
        batch.unused > 0 ? (
          <Button
            size="sm"
            variant="outline"
            disabled={disabled}
            aria-label={`Khoá lô ${batch.batchId}`}
            onClick={(event) => {
              returnFocus.current = event.currentTarget
              setSelected(batch)
            }}
          >
            Khoá lô
          </Button>
        ) : (
          <span className="text-body-sm text-ink-secondary">Không còn mã chưa dùng</span>
        ),
    },
  ]

  return (
    <div className="mx-auto max-w-content space-y-6">
      <PageHeader title="Lô mã thẻ" />
      <Section
        title="Phát hành lô mã"
        subtitle="Tối đa 100 mã mỗi lô. Thẻ gói chỉ kích hoạt PREMIUM."
      >
        <form ref={form} aria-label="Phát hành lô mã" noValidate onSubmit={prepare}>
          <div className="grid gap-x-4 md:grid-cols-2">
            <FormField
              htmlFor="batch-cardType"
              label="Loại thẻ"
              required
              error={validation.cardType}
            >
              <Select
                name="cardType"
                value={draft.cardType}
                disabled={disabled}
                onChange={(event) => change('cardType', event.target.value)}
              >
                <option value="">Chọn loại thẻ</option>
                <option value="CREDIT">Điểm (CREDIT)</option>
                <option value="SUBSCRIPTION">Gói PREMIUM (SUBSCRIPTION)</option>
              </Select>
            </FormField>
            <FormField
              htmlFor="batch-channel"
              label="Kênh phát hành"
              required
              error={validation.channel}
            >
              <Select
                name="channel"
                value={draft.channel}
                disabled={disabled}
                onChange={(event) => change('channel', event.target.value)}
              >
                <option value="">Chọn kênh phát hành</option>
                {Object.entries(channelNames).map(([value, name]) => (
                  <option key={value} value={value}>
                    {name}
                  </option>
                ))}
              </Select>
            </FormField>
            <FormField
              htmlFor="batch-quantity"
              label="Số lượng mã"
              required
              error={validation.quantity}
              hint="Số nguyên từ 1 đến 100."
            >
              <Input
                name="quantity"
                type="number"
                min={1}
                max={100}
                step={1}
                value={draft.quantity}
                disabled={disabled}
                onChange={(event) => change('quantity', event.target.value)}
              />
            </FormField>
            {draft.cardType === 'CREDIT' ? (
              <FormField
                htmlFor="batch-value"
                label="Giá trị điểm mỗi mã"
                required
                error={validation.value}
                hint="Số nguyên từ 1 đến 10.000 điểm."
              >
                <Input
                  name="value"
                  type="number"
                  min={1}
                  max={10000}
                  step={1}
                  value={draft.value}
                  disabled={disabled}
                  onChange={(event) => change('value', event.target.value)}
                />
              </FormField>
            ) : draft.cardType === 'SUBSCRIPTION' ? (
              <FormField
                htmlFor="batch-planDays"
                label="Số ngày hiệu lực"
                required
                error={validation.planDays}
                hint="Từ 1 đến 365 ngày, kích hoạt hoặc gia hạn PREMIUM."
              >
                <Input
                  name="planDays"
                  type="number"
                  min={1}
                  max={365}
                  step={1}
                  value={draft.planDays}
                  disabled={disabled}
                  onChange={(event) => change('planDays', event.target.value)}
                />
              </FormField>
            ) : null}
            {draft.channel === 'PARTNER_BATCH' ? (
              <FormField
                htmlFor="batch-partnerCode"
                label="Mã đối tác"
                required
                error={validation.partnerCode}
                hint="Tối đa 50 ký tự."
              >
                <Input
                  name="partnerCode"
                  value={draft.partnerCode}
                  disabled={disabled}
                  onChange={(event) => change('partnerCode', event.target.value)}
                />
              </FormField>
            ) : draft.channel === 'ECOSYSTEM_GIFT' ? (
              <FormField
                htmlFor="batch-issuedTo"
                label="Thông tin người nhận quà"
                required
                error={validation.issuedTo}
                hint="Tối đa 255 ký tự."
              >
                <Input
                  name="issuedTo"
                  value={draft.issuedTo}
                  disabled={disabled}
                  onChange={(event) => change('issuedTo', event.target.value)}
                />
              </FormField>
            ) : null}
            <FormField
              htmlFor="batch-campaign"
              label="Chiến dịch"
              error={validation.campaign}
              hint="Không bắt buộc, tối đa 50 ký tự."
            >
              <Input
                name="campaign"
                value={draft.campaign}
                disabled={disabled}
                onChange={(event) => change('campaign', event.target.value)}
              />
            </FormField>
            <FormField
              htmlFor="batch-expiresAt"
              label="Hạn dùng (giờ Việt Nam)"
              error={validation.expiresAt}
              hint="Để trống nếu không có hạn dùng."
            >
              <Input
                name="expiresAt"
                type="datetime-local"
                value={draft.expiresAt}
                disabled={disabled}
                onChange={(event) => change('expiresAt', event.target.value)}
              />
            </FormField>
          </div>
          <Button type="submit" disabled={disabled}>
            Phát hành lô mã
          </Button>
        </form>
        {confirmation ? (
          <section
            aria-label="Xác nhận phát hành"
            className="mt-4 space-y-3 rounded-md border border-line bg-surface-subtle p-4"
          >
            <h3 className="text-label text-ink">Xác nhận phát hành lô mã</h3>
            <dl className="space-y-2 text-body-sm text-ink">
              <div>
                <dt>Kênh phát hành</dt>
                <dd>{channelNames[confirmation.channel]}</dd>
              </div>
              <div>
                <dt>Loại thẻ</dt>
                <dd>{cardNames[confirmation.cardType]}</dd>
              </div>
              <div>
                <dt>Số lượng mã</dt>
                <dd>{confirmation.quantity}</dd>
              </div>
              <div>
                <dt>
                  {confirmation.cardType === 'CREDIT' ? 'Giá trị điểm mỗi mã' : 'Số ngày hiệu lực'}
                </dt>
                <dd>{confirmation.value ?? confirmation.planDays}</dd>
              </div>
              {confirmation.partnerCode ? (
                <div>
                  <dt>Mã đối tác</dt>
                  <dd>{confirmation.partnerCode}</dd>
                </div>
              ) : null}
              {confirmation.issuedTo ? (
                <div>
                  <dt>Thông tin người nhận quà</dt>
                  <dd>{confirmation.issuedTo}</dd>
                </div>
              ) : null}
              {confirmation.campaign ? (
                <div>
                  <dt>Chiến dịch</dt>
                  <dd>{confirmation.campaign}</dd>
                </div>
              ) : null}
              <div>
                <dt>Hạn dùng (giờ Việt Nam)</dt>
                <dd>
                  {confirmation.expiresAt
                    ? vietnamTime.format(new Date(confirmation.expiresAt))
                    : 'Không có hạn'}
                </dd>
              </div>
            </dl>
            <p className="text-body-sm text-ink-secondary">Mã chỉ hiện một lần — tải ngay</p>
            {created ? (
              <p className="text-body-sm font-semibold text-danger">
                Mã lô trước sẽ mất khi phát hành thành công lô mới.
              </p>
            ) : null}
            <div className="flex flex-wrap gap-3">
              <Button
                disabled={Boolean(pending)}
                onClick={() => {
                  setConfirmation(undefined)
                  void onCreate(confirmation)
                }}
              >
                Xác nhận phát hành
              </Button>
              <Button
                variant="outline"
                disabled={Boolean(pending)}
                onClick={() => setConfirmation(undefined)}
              >
                Huỷ phát hành
              </Button>
            </div>
          </section>
        ) : null}
        {pending === 'create' ? (
          <p role="status" className="mt-3 text-body-sm text-ink-secondary">
            Đang phát hành lô mã…
          </p>
        ) : null}
        {createError ? (
          <div role="alert" className="mt-4">
            <ErrorState
              title="Thông báo phát hành"
              message={createErrorMessage(createError)}
              requestId={createError.requestId}
            />
          </div>
        ) : null}
      </Section>

      {created ? (
        <Section title="Mã thẻ vừa phát hành">
          <div role="status" className="space-y-3 rounded-md border border-line bg-surface p-4">
            <p className="text-label text-ink">Đã phát hành lô {created.batchId}</p>
            <p className="font-semibold text-danger">Mã chỉ hiện một lần — tải ngay</p>
            <p className="text-body-sm text-ink-secondary">
              Rời hoặc tải lại trang sẽ mất mã. Không thể lấy lại mã từ bảng lô.
            </p>
            <ul aria-label="Mã vừa phát hành" className="space-y-2 font-mono text-body text-ink">
              {created.codes.map((code) => (
                <li key={code}>{code}</li>
              ))}
            </ul>
            <Button variant="outline" onClick={download}>
              Tải CSV
            </Button>
            {downloadError ? (
              <p role="alert" className="text-body-sm text-danger">
                {downloadError}
              </p>
            ) : null}
          </div>
        </Section>
      ) : null}

      <Section
        title="Danh sách lô"
        subtitle="Lô mới nhất trước · Chỉ hiển thị số liệu lô, không lưu mã thẻ."
        actions={
          <Button
            variant="outline"
            disabled={Boolean(pending) || state === 'loading'}
            onClick={onRetry}
          >
            Tải lại danh sách
          </Button>
        }
      >
        {selected ? (
          <section
            aria-label="Xác nhận khoá lô"
            className="mb-4 space-y-3 rounded-md border border-line bg-surface-subtle p-4"
          >
            <h3 className="text-label text-ink">Khoá lô {selected.batchId}?</h3>
            <p className="text-body-sm text-ink">
              Chỉ khoá mã chưa dùng. Hiện có {selected.unused} mã chưa dùng; {selected.used} mã đã
              dùng được giữ nguyên. Số mã khoá thực tế do máy chủ xác nhận.
            </p>
            <div className="flex flex-wrap gap-3">
              <Button
                variant="danger"
                disabled={Boolean(pending)}
                onClick={() => {
                  setSelected(undefined)
                  void onVoid(selected.batchId)
                }}
              >
                Xác nhận khoá lô
              </Button>
              <Button
                variant="outline"
                disabled={Boolean(pending)}
                onClick={() => setSelected(undefined)}
              >
                Huỷ khoá lô
              </Button>
            </div>
          </section>
        ) : null}
        {pending === 'void' ? (
          <p role="status" className="mb-3 text-body-sm text-ink-secondary">
            Đang khoá mã chưa dùng…
          </p>
        ) : null}
        {voided ? (
          <p role="status" className="mb-3 text-body-sm text-ink">
            Lô {voided.batchId}: đã khoá {voided.voided} mã chưa dùng.
          </p>
        ) : null}
        {voidError ? (
          <div role="alert" className="mb-4">
            <ErrorState
              title="Chưa khoá được lô"
              message={
                voidError.errorCode === 'CARD_BATCH_NOT_FOUND'
                  ? 'Không tìm thấy lô mã. Tải lại danh sách để kiểm tra.'
                  : 'Chưa thể khoá lô mã. Tải lại danh sách để kiểm tra trước khi thử lại.'
              }
              requestId={voidError.requestId}
            />
          </div>
        ) : null}
        {state === 'loading' ? (
          <div role="status" aria-label="Đang tải danh sách lô">
            <Skeleton variant="table" />
          </div>
        ) : state === 'error' ? (
          <ErrorState
            title="Chưa tải được danh sách lô"
            message="Vui lòng thử lại để xem danh sách lô."
            requestId={error?.requestId}
            onRetry={onRetry}
          />
        ) : state === 'empty' ? (
          <EmptyState
            title="Chưa có lô mã"
            description="Bạn có thể phát hành lô mã bằng biểu mẫu phía trên."
          />
        ) : (
          <DataTable
            caption="Lô mã thẻ"
            columns={columns}
            rowKey={(batch) => batch.batchId}
            rows={data?.items ?? []}
          />
        )}
        {data && data.total > 0 ? (
          <Pagination
            page={page + 1}
            size={20}
            total={data.total}
            onChange={(next) => {
              if (pending) return
              setSelected(undefined)
              onPageChange(next - 1)
            }}
          />
        ) : null}
      </Section>
    </div>
  )
}
