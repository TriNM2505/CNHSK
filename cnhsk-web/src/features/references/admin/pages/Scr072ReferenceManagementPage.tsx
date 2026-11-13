import { useEffect, useRef, useState } from 'react'

import { useApiMutation, useApiQuery } from '@/shared/api'
import { useCurrentUser } from '@/shared/auth'
import { Button, Card } from '@/shared/ui'

import {
  createReference,
  getAdminReference,
  listAdminReferences,
  transitionReference,
  updateReference,
} from '../api/references'
import { ReferenceAdminView } from '../components/ReferenceAdminView'
import { ReferenceEditorForm } from '../components/ReferenceEditorForm'
import { draftOf, newDraft, statusLabels } from '../referenceForm'
import type { AdminListParams, AdminReference, ReferenceAction, ReferenceInput } from '../types'

function Editor({
  id,
  onSaved,
  onCancel,
}: {
  id: number | 'new'
  onSaved: (item: AdminReference) => void
  onCancel: () => void
}) {
  const detail = useApiQuery(
    (signal) => (id === 'new' ? Promise.resolve(null) : getAdminReference(id, signal)),
    [id],
  )
  const mutation = useApiMutation((input: ReferenceInput) =>
    id === 'new' ? createReference(input) : updateReference(id, input),
  )
  const [saved, setSaved] = useState(false)
  const alive = useRef(true)
  useEffect(() => {
    alive.current = true
    return () => {
      alive.current = false
    }
  }, [])
  if (detail.state === 'loading')
    return (
      <Card>
        <p role="status">Đang tải biểu mẫu…</p>
      </Card>
    )
  if (detail.state === 'error')
    return (
      <Card>
        <p role="alert">{detail.error?.message}</p>
        <Button onClick={detail.reload}>Thử tải biểu mẫu lại</Button>
        <Button onClick={onCancel} variant="ghost">
          Đóng biểu mẫu
        </Button>
      </Card>
    )
  return (
    <ReferenceEditorForm
      initial={detail.data ? draftOf(detail.data) : newDraft()}
      editing={id !== 'new'}
      pending={mutation.pending || saved}
      error={mutation.error}
      onCancel={onCancel}
      onSubmit={(input) => {
        void mutation
          .run(input)
          .then((item) => {
            if (alive.current) {
              setSaved(true)
              onSaved(item)
            }
          })
          .catch(() => undefined)
      }}
    />
  )
}

function Management() {
  const [filters, setFilters] = useState<AdminListParams>({ page: 0 })
  const [editorId, setEditorId] = useState<number | 'new'>()
  const [confirmation, setConfirmation] = useState<{
    item: AdminReference
    action: ReferenceAction
  }>()
  const [notice, setNotice] = useState<string>()
  const [revision, setRevision] = useState(0)
  const query = useApiQuery((signal) => listAdminReferences(filters, signal), [filters], {
    isEmpty: (data) => data.items.length === 0,
  })
  const mutation = useApiMutation(({ id, action }: { id: number; action: ReferenceAction }) =>
    transitionReference(id, action),
  )
  useEffect(() => {
    if (query.state === 'empty' && query.data && (filters.page ?? 0) > 0) {
      const page = Math.max(0, Math.ceil(query.data.total / query.data.size) - 1)
      setFilters((current) => ({ ...current, page }))
    }
  }, [query.state, query.data, filters.page])
  return (
    <ReferenceAdminView
      state={query.state}
      data={query.data}
      error={query.error}
      filters={filters}
      busy={mutation.pending || editorId !== undefined}
      notice={notice}
      onFilter={(next) => setFilters({ ...next, page: 0 })}
      onPage={(page) => setFilters((current) => ({ ...current, page }))}
      onRetry={query.reload}
      onCreate={() => {
        setEditorId('new')
        setRevision((value) => value + 1)
        setNotice(undefined)
        setConfirmation(undefined)
      }}
      onEdit={(id) => {
        setEditorId(id)
        setRevision((value) => value + 1)
        setNotice(undefined)
        setConfirmation(undefined)
      }}
      editor={
        editorId === undefined ? undefined : (
          <Editor
            key={`${editorId}-${revision}`}
            id={editorId}
            onCancel={() => setEditorId(undefined)}
            onSaved={(item) => {
              setNotice(`Đã lưu ${item.title} — ${statusLabels[item.status]}.`)
              setEditorId(undefined)
              query.reload()
            }}
          />
        )
      }
      confirmation={confirmation}
      actionError={mutation.error}
      onAction={(item, action) => {
        setConfirmation({ item, action })
        setNotice(undefined)
        mutation.reset()
      }}
      onCancel={() => {
        if (!mutation.pending) setConfirmation(undefined)
      }}
      onConfirm={() => {
        if (!confirmation) return
        void mutation
          .run({ id: confirmation.item.id, action: confirmation.action })
          .then((item) => {
            setNotice(`Đã cập nhật ${item.title} — ${statusLabels[item.status]}.`)
            setConfirmation(undefined)
            query.reload()
          })
          .catch(() => undefined)
      }}
    />
  )
}

export default function Scr072ReferenceManagementPage() {
  const auth = useCurrentUser()
  if (!auth.user?.roles.includes('CONTENT_EDITOR'))
    return <p role="alert">Cần quyền biên tập nội dung để quản lý tài nguyên.</p>
  return <Management key={auth.user.id} />
}
