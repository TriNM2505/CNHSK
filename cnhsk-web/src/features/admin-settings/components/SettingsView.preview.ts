import { historyFixture, settingFixture, settingsFixtures, usageFixture } from '../test-fixtures'
import type { SettingsViewProps } from './SettingsView'

const noop = () => undefined
const base: SettingsViewProps = {
  state: 'ready',
  items: settingsFixtures,
  pending: false,
  onEdit: noop,
  onHistory: noop,
  onRetry: noop,
  usage: { state: 'ready', data: usageFixture, onRetry: noop },
}
const editor: NonNullable<SettingsViewProps['editor']> = {
  item: settingFixture,
  catalog: settingsFixtures,
  draft: {
    key: settingFixture.key,
    mode: 'edit',
    value: '25',
    reason: 'Điều chỉnh theo nhu cầu học tập.',
    confirming: false,
  },
  pending: false,
  onChange: noop,
  onSubmit: noop,
  onCancel: noop,
}
export const settingsPreviews = {
  ready: base,
  loading: {
    ...base,
    state: 'loading',
    usage: { ...base.usage, state: 'loading', data: undefined },
  },
  empty: { ...base, items: [], state: 'empty' },
  error: {
    ...base,
    state: 'error',
    error: { message: 'Không thể tải cài đặt.', requestId: 'preview-settings' },
  },
  editing: { ...base, editor },
  validation: {
    ...base,
    editor: { ...editor, draft: { ...editor.draft, value: '-1', reason: '' } },
  },
  confirming: { ...base, editor: { ...editor, draft: { ...editor.draft, confirming: true } } },
  reset: {
    ...base,
    editor: { ...editor, draft: { ...editor.draft, mode: 'reset', confirming: true } },
  },
  history: {
    ...base,
    history: {
      settingKey: settingFixture.key,
      state: 'ready',
      data: historyFixture,
      onPage: noop,
      onRetry: noop,
      onClose: noop,
    },
  },
  historyEmpty: {
    ...base,
    history: {
      settingKey: settingFixture.key,
      state: 'empty',
      onPage: noop,
      onRetry: noop,
      onClose: noop,
    },
  },
  historyError: {
    ...base,
    history: {
      settingKey: settingFixture.key,
      state: 'error',
      error: { message: 'Không thể tải lịch sử.' },
      onPage: noop,
      onRetry: noop,
      onClose: noop,
    },
  },
  usageUnavailable: {
    ...base,
    usage: { ...base.usage, data: { ...usageFixture, used: null, available: false } },
  },
  usageMissing: { ...base, usage: { ...base.usage, state: 'error', data: undefined } },
} satisfies Record<string, SettingsViewProps>
