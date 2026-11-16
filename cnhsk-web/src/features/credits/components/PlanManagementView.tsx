import { useEffect, useRef, useState, type FormEvent } from 'react'

import type { ApiError, ApiQueryState } from '@/shared/api'
import {
  Button,
  Card,
  DataTable,
  EmptyState,
  ErrorState,
  FormField,
  Input,
  PageHeader,
  Section,
  Skeleton,
  StatusBadge,
  Textarea,
  type DataTableColumn,
} from '@/shared/ui'

import type { Benefits, PaidFeature, PlanCode, PlanUpdateRequest, PlanView } from '../types'

export interface PlanManagementViewProps {
  state: ApiQueryState
  data?: PlanView[]
  error?: ApiError
  pending?: PlanCode
  saved?: PlanView
  failure?: { code: PlanCode; error: ApiError }
  onSave: (code: PlanCode, body: PlanUpdateRequest) => Promise<void>
  onRetry: () => void
}

const features: readonly PaidFeature[] = ['AI_GENERATE', 'TRANSLATE', 'ASSISTANT', 'GRADING']
const planOrder: readonly PlanCode[] = ['FREE', 'PREMIUM', 'PREMIUM_PLUS']
const planColumns: readonly DataTableColumn<PlanView>[] = [
  {
    id: 'code',
    header: 'Mã gói',
    render: ({ code }) => <strong className="break-words">{code}</strong>,
  },
  {
    id: 'name',
    header: 'Tên gói',
    render: ({ name }) => <span className="break-words">{name || '—'}</span>,
  },
  {
    id: 'users',
    header: 'Người đang dùng',
    numeric: true,
    render: ({ activeUsers }) => activeUsers,
  },
  {
    id: 'status',
    header: 'Trạng thái',
    render: ({ code, exists, isActive }) =>
      code === 'PREMIUM_PLUS' ? (
        <StatusBadge tone="neutral">Chưa hỗ trợ</StatusBadge>
      ) : !exists ? (
        <StatusBadge tone="warning">Chưa cấu hình</StatusBadge>
      ) : isActive ? (
        <StatusBadge tone="success">Đang hoạt động</StatusBadge>
      ) : (
        <StatusBadge tone="neutral">Đã tắt</StatusBadge>
      ),
  },
]
const featureNames: Record<PaidFeature, string> = {
  AI_GENERATE: 'Tạo câu AI',
  TRANSLATE: 'Dịch',
  ASSISTANT: 'Trợ lý',
  GRADING: 'Chấm bài',
}
type PlanDraft = {
  name: string
  description: string
  durationDays: string
  price: string
  benefits: Record<PaidFeature, string>
  isActive: boolean
}
type Validation = Partial<
  Record<'name' | 'description' | 'durationDays' | 'price' | PaidFeature, string>
>

function initialDraft(plan: PlanView): PlanDraft {
  return {
    name: plan.name ?? '',
    description: plan.description ?? '',
    durationDays: plan.durationDays == null ? '' : String(plan.durationDays),
    price: plan.price ?? '',
    benefits: Object.fromEntries(
      features.map((feature) => [
        feature,
        plan.exists ? String(plan.benefits?.[feature] ?? 0) : '',
      ]),
    ) as Record<PaidFeature, string>,
    isActive: plan.isActive ?? false,
  }
}

function validate(draft: PlanDraft, code: PlanCode): Validation {
  const errors: Validation = {}
  if (!draft.name.trim() || draft.name.trim().length > 100)
    errors.name = 'Nhập tên gói, tối đa 100 ký tự.'
  if (draft.description.trim().length > 2000) errors.description = 'Mô tả tối đa 2.000 ký tự.'
  if (code !== 'PREMIUM') return errors
  if (
    !/^\d+$/.test(draft.durationDays) ||
    Number(draft.durationDays) < 1 ||
    Number(draft.durationDays) > 365
  )
    errors.durationDays = 'Số ngày phải là số nguyên từ 1 đến 365.'
  if (!/^\d{1,10}(\.\d{1,2})?$/.test(draft.price.trim()))
    errors.price =
      'Nhập giá không âm, tối đa 10 chữ số phần nguyên và 2 chữ số thập phân; dùng dấu chấm.'
  for (const feature of features)
    if (!/^\d+$/.test(draft.benefits[feature]) || Number(draft.benefits[feature]) > 10000)
      errors[feature] = 'Hạn mức phải là số nguyên từ 0 đến 10.000.'
  return errors
}

function request(plan: PlanView, draft: PlanDraft): PlanUpdateRequest {
  const common = { name: draft.name.trim(), description: draft.description.trim() || null }
  if (plan.code === 'FREE') return common
  const [whole, fraction = ''] = draft.price.trim().split('.')
  const changed = features.some(
    (feature) => Number(draft.benefits[feature]) !== (plan.benefits?.[feature] ?? 0),
  )
  // Preserve a partial benefits snapshot for harmless edits such as changing the list price.
  const benefits: Benefits =
    plan.exists && !changed
      ? { ...plan.benefits }
      : Object.fromEntries(features.map((feature) => [feature, Number(draft.benefits[feature])]))
  return {
    ...common,
    durationDays: Number(draft.durationDays),
    price: `${whole}.${fraction.padEnd(2, '0')}`,
    benefits,
    isActive: draft.isActive,
  }
}

function PlanForm({
  plan,
  pending,
  error,
  onSave,
}: {
  plan: PlanView
  pending: boolean
  error?: ApiError
  onSave: PlanManagementViewProps['onSave']
}) {
  const [draft, setDraft] = useState(() => initialDraft(plan))
  const [validation, setValidation] = useState<Validation>({})
  const [confirmation, setConfirmation] = useState<PlanUpdateRequest>()
  const form = useRef<HTMLFormElement>(null)
  const returnFocus = useRef(false)
  const prefix = `plan-${plan.code}`
  const quotaError =
    error?.errorCode === 'PLAN_IN_USE'
      ? 'Không thể đổi hạn mức khi còn người đang dùng PREMIUM. Giữ hạn mức hiện tại để lưu các thông tin khác.'
      : undefined

  useEffect(() => {
    if (confirmation || pending || !returnFocus.current) return
    form.current?.querySelector<HTMLButtonElement>('button[type="submit"]')?.focus()
    returnFocus.current = false
  }, [confirmation, pending])

  const prepare = (event: FormEvent) => {
    event.preventDefault()
    if (pending || confirmation) return
    const errors = validate(draft, plan.code)
    setValidation(errors)
    const first = Object.keys(errors)[0]
    if (first) {
      ;(form.current?.elements.namedItem(first) as HTMLElement | null)?.focus()
      return
    }
    returnFocus.current = true
    setConfirmation(request(plan, draft))
  }

  const change = (name: 'name' | 'description' | 'durationDays' | 'price', value: string) => {
    setDraft((current) => ({ ...current, [name]: value }))
    setValidation({})
  }

  return (
    <form
      ref={form}
      aria-label={`Cấu hình ${plan.code}`}
      noValidate
      onSubmit={prepare}
      className="space-y-4"
    >
      <fieldset disabled={pending || Boolean(confirmation)}>
        <FormField htmlFor={`${prefix}-name`} label="Tên gói" required error={validation.name}>
          <Input
            name="name"
            value={draft.name}
            onChange={(event) => change('name', event.target.value)}
          />
        </FormField>
        <FormField htmlFor={`${prefix}-description`} label="Mô tả" error={validation.description}>
          <Textarea
            name="description"
            value={draft.description}
            onChange={(event) => change('description', event.target.value)}
          />
        </FormField>
        {plan.code === 'PREMIUM' ? (
          <>
            <div className="grid gap-x-4 md:grid-cols-2">
              <FormField
                htmlFor={`${prefix}-days`}
                label="Số ngày hiệu lực"
                required
                error={validation.durationDays}
              >
                <Input
                  name="durationDays"
                  type="number"
                  min={1}
                  max={365}
                  step={1}
                  value={draft.durationDays}
                  onChange={(event) => change('durationDays', event.target.value)}
                />
              </FormField>
              <FormField
                htmlFor={`${prefix}-price`}
                label="Giá niêm yết"
                required
                error={validation.price}
                hint="giá niêm yết, không phải tiền đã thu"
              >
                <Input
                  name="price"
                  inputMode="decimal"
                  value={draft.price}
                  onChange={(event) => change('price', event.target.value)}
                />
              </FormField>
            </div>
            <fieldset className="space-y-3">
              <legend className="mb-3 text-label text-ink">Hạn mức mỗi tính năng mỗi tháng</legend>
              {plan.activeUsers > 0 ? (
                <p className="text-body-sm text-ink-secondary">
                  Có người đang dùng gói; thay đổi hạn mức sẽ bị máy chủ từ chối.
                </p>
              ) : null}
              <div className="grid gap-x-4 md:grid-cols-2">
                {features.map((feature) => (
                  <FormField
                    key={feature}
                    htmlFor={`${prefix}-${feature}`}
                    label={featureNames[feature]}
                    required
                    error={validation[feature] ?? quotaError}
                  >
                    <Input
                      name={feature}
                      type="number"
                      min={0}
                      max={10000}
                      step={1}
                      value={draft.benefits[feature]}
                      onChange={(event) => {
                        setDraft((current) => ({
                          ...current,
                          benefits: { ...current.benefits, [feature]: event.target.value },
                        }))
                        setValidation({})
                      }}
                    />
                  </FormField>
                ))}
              </div>
            </fieldset>
            <label className="flex min-h-touch items-center gap-3 text-body text-ink">
              <input
                type="checkbox"
                checked={draft.isActive}
                onChange={(event) =>
                  setDraft((current) => ({ ...current, isActive: event.target.checked }))
                }
              />
              Đang bán
            </label>
            <p className="text-body-sm text-ink-secondary">
              Ngừng bán không thu hồi gói đang dùng.
            </p>
          </>
        ) : (
          <p className="mb-4 text-body text-ink">10 lượt/tính năng/tháng</p>
        )}
        <Button type="submit" loading={pending}>
          Lưu {plan.code}
        </Button>
      </fieldset>
      {confirmation ? (
        <section
          aria-label={`Xác nhận lưu ${plan.code}`}
          className="space-y-3 rounded-md border border-line bg-surface-subtle p-4"
        >
          <h3 className="text-label text-ink">Xác nhận lưu {plan.code}</h3>
          <dl className="space-y-2 text-body-sm text-ink">
            <div>
              <dt>Tên gói</dt>
              <dd>{confirmation.name}</dd>
            </div>
            <div>
              <dt>Mô tả</dt>
              <dd>{confirmation.description || 'Không có mô tả'}</dd>
            </div>
            {plan.code === 'PREMIUM' ? (
              <>
                <div>
                  <dt>Số ngày hiệu lực</dt>
                  <dd>{confirmation.durationDays}</dd>
                </div>
                <div>
                  <dt>Giá niêm yết</dt>
                  <dd>{confirmation.price}</dd>
                </div>
                <div>
                  <dt>Ghi chú</dt>
                  <dd>giá niêm yết, không phải tiền đã thu</dd>
                </div>
                {features.map((feature) => (
                  <div key={feature}>
                    <dt>{featureNames[feature]}</dt>
                    <dd>{confirmation.benefits?.[feature] ?? 0}</dd>
                  </div>
                ))}
                <div>
                  <dt>Đang bán</dt>
                  <dd>{confirmation.isActive ? 'Có' : 'Không'}</dd>
                </div>
              </>
            ) : null}
          </dl>
          <div className="flex flex-wrap gap-3">
            <Button
              disabled={pending}
              onClick={() => {
                setConfirmation(undefined)
                void onSave(plan.code, confirmation)
              }}
            >
              Xác nhận lưu
            </Button>
            <Button variant="outline" disabled={pending} onClick={() => setConfirmation(undefined)}>
              Huỷ lưu
            </Button>
          </div>
        </section>
      ) : null}
      {error && !quotaError ? (
        <div role="alert">
          <ErrorState
            title={`Chưa lưu được ${plan.code}`}
            requestId={error.requestId}
            message={
              error.errorCode === 'PLAN_NOT_AVAILABLE'
                ? 'Gói này chưa hỗ trợ cập nhật.'
                : error.errorCode === 'VALIDATION_FAILED'
                  ? 'Thông tin gói chưa hợp lệ. Kiểm tra các trường trước khi lưu lại.'
                  : 'Chưa thể lưu gói. Kiểm tra kết nối và quyền truy cập trước khi thử lại.'
            }
          />
        </div>
      ) : null}
      {quotaError ? (
        <p role="alert" className="text-body-sm text-danger">
          Chưa lưu thay đổi hạn mức.{error?.requestId ? ` Mã lỗi: ${error.requestId}` : ''}
        </p>
      ) : null}
    </form>
  )
}

export function PlanManagementView({
  state,
  data,
  error,
  pending,
  saved,
  failure,
  onSave,
  onRetry,
}: PlanManagementViewProps) {
  const plans = planOrder.flatMap((code) => data?.find((plan) => plan.code === code) ?? [])
  return (
    <div className="mx-auto min-w-0 max-w-content space-y-6">
      <PageHeader
        title="Quản lý gói dịch vụ"
        subtitle="Xem tình trạng gói và cập nhật cấu hình bên dưới."
      />
      {saved ? (
        <p role="status" className="rounded-md bg-success-bg p-4 text-body text-success">
          Đã lưu gói {saved.code}: {saved.name}.
        </p>
      ) : null}
      {state === 'loading' ? (
        <div role="status" aria-label="Đang tải gói dịch vụ">
          <Skeleton variant="table" />
        </div>
      ) : state === 'error' ? (
        <ErrorState
          title="Chưa tải được gói dịch vụ"
          message="Vui lòng thử lại để xem đủ ba mã gói."
          requestId={error?.requestId}
          onRetry={onRetry}
        />
      ) : plans.length === 0 ? (
        <EmptyState
          title="Chưa có dữ liệu gói"
          description="Tải lại để kiểm tra danh sách gói."
          action={<Button onClick={onRetry}>Tải lại</Button>}
        />
      ) : (
        <>
          <Section title="Danh sách gói">
            <DataTable
              caption="Các gói dịch vụ"
              columns={planColumns}
              rows={plans}
              rowKey={({ code }) => code}
            />
          </Section>
          {plans.map((plan) => (
            <Section key={plan.code} title={plan.code}>
              <Card>
                <p className="mb-3 text-body-sm text-ink-secondary">
                  Số người đang dùng: {plan.activeUsers}
                </p>
                {plan.code === 'PREMIUM_PLUS' ? (
                  <p className="text-body text-ink">Chưa hỗ trợ đợt này</p>
                ) : (
                  <>
                    {!plan.exists ? (
                      <p className="mb-3 text-body-sm text-ink-secondary">
                        Chưa có cấu hình gói. Nhập cấu hình để tạo {plan.code}.
                      </p>
                    ) : null}
                    <PlanForm
                      key={JSON.stringify(plan)}
                      plan={plan}
                      pending={Boolean(pending)}
                      error={failure?.code === plan.code ? failure.error : undefined}
                      onSave={onSave}
                    />
                  </>
                )}
              </Card>
            </Section>
          ))}
        </>
      )}
    </div>
  )
}
