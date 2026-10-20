import { createElement } from 'react'

import type { PreviewEntry, PreviewState } from '@/app/preview/types'
import {
  ButtonGallery,
  CardGallery,
  ConfirmPanelGallery,
  DataTableGallery,
  EmptyStateGallery,
  ErrorStateGallery,
  FormFieldGallery,
  HanziTextGallery,
  IconButtonGallery,
  InputGallery,
  LevelBadgeGallery,
  PageStateGallery,
  PageHeaderGallery,
  PaginationGallery,
  PasswordInputGallery,
  ProgressGallery,
  SectionGallery,
  SelectGallery,
  SkeletonGallery,
  StatTileGallery,
  StatusBadgeGallery,
  TabsGallery,
  TextareaGallery,
  ToastGallery,
} from '@/app/preview/galleries'
import {
  AdminLayoutGallery,
  LearnerLayoutGallery,
  PublicLayoutGallery,
} from '@/app/preview/layoutGalleries'
import { previews as accountPreviews } from '@/features/account/catalog.preview'
import { previews as adminUserPreviews } from '@/features/admin-users/catalog.preview'
import { previews as authPreviews } from '@/features/auth/catalog.preview'
import { previews as contentImportPreviews } from '@/features/content-import/catalog.preview'
import { previews as grammarPreviews } from '@/features/grammar/catalog.preview'
import { previews as hanziPreviews } from '@/features/hanzi/catalog.preview'
import { previews as practicePreviews } from '@/features/practice/catalog.preview'
import { previews as pronunciationPreviews } from '@/features/pronunciation/catalog.preview'
import { previews as vocabPreviews } from '@/features/vocab/catalog.preview'

export type { PreviewEntry, PreviewState } from '@/app/preview/types'

export const previewRegistry: readonly PreviewEntry[] = [
  {
    group: 'component',
    id: 'component-button',
    render: () => createElement(ButtonGallery),
    title: 'Button',
  },
  {
    group: 'component',
    id: 'component-icon-button',
    render: () => createElement(IconButtonGallery),
    title: 'IconButton',
  },
  {
    group: 'component',
    id: 'component-card',
    render: () => createElement(CardGallery),
    title: 'Card',
  },
  {
    group: 'component',
    id: 'component-section',
    render: () => createElement(SectionGallery),
    title: 'Section',
  },
  {
    group: 'component',
    id: 'component-page-header',
    render: () => createElement(PageHeaderGallery),
    title: 'PageHeader',
  },
  {
    group: 'component',
    id: 'component-form-field',
    render: () => createElement(FormFieldGallery),
    title: 'FormField',
  },
  {
    group: 'component',
    id: 'component-input',
    render: () => createElement(InputGallery),
    title: 'Input',
  },
  {
    group: 'component',
    id: 'component-select',
    render: () => createElement(SelectGallery),
    title: 'Select',
  },
  {
    group: 'component',
    id: 'component-textarea',
    render: () => createElement(TextareaGallery),
    title: 'Textarea',
  },
  {
    group: 'component',
    id: 'component-password-input',
    render: () => createElement(PasswordInputGallery),
    title: 'PasswordInput',
  },
  {
    group: 'component',
    id: 'component-status-badge',
    render: () => createElement(StatusBadgeGallery),
    title: 'StatusBadge',
  },
  {
    group: 'component',
    id: 'component-level-badge',
    render: () => createElement(LevelBadgeGallery),
    title: 'LevelBadge',
  },
  {
    group: 'component',
    id: 'component-hanzi-text',
    render: () => createElement(HanziTextGallery),
    title: 'HanziText',
  },
  {
    group: 'component',
    id: 'component-stat-tile',
    render: () => createElement(StatTileGallery),
    title: 'StatTile',
  },
  {
    group: 'component',
    id: 'component-progress',
    render: () => createElement(ProgressGallery),
    title: 'Progress',
  },
  {
    group: 'component',
    id: 'component-data-table',
    render: () => createElement(DataTableGallery),
    title: 'DataTable',
  },
  {
    group: 'component',
    id: 'component-pagination',
    render: () => createElement(PaginationGallery),
    title: 'Pagination',
  },
  {
    group: 'component',
    id: 'component-tabs',
    render: () => createElement(TabsGallery),
    title: 'Tabs',
  },
  {
    group: 'component',
    id: 'component-confirm-panel',
    render: () => createElement(ConfirmPanelGallery),
    title: 'ConfirmPanel',
  },
  {
    group: 'component',
    id: 'component-toast',
    render: () => createElement(ToastGallery),
    title: 'Toast + useToast',
  },
  {
    group: 'component',
    id: 'component-empty-state',
    render: () => createElement(EmptyStateGallery),
    title: 'EmptyState',
  },
  {
    group: 'component',
    id: 'component-error-state',
    render: () => createElement(ErrorStateGallery),
    title: 'ErrorState',
  },
  {
    group: 'component',
    id: 'component-skeleton',
    render: () => createElement(SkeletonGallery),
    title: 'Skeleton',
  },
  {
    group: 'component',
    id: 'component-page-state',
    render: () => createElement(PageStateGallery),
    title: 'PageState',
  },
  {
    fullFrame: true,
    group: 'component',
    id: 'layout-public',
    render: () => createElement(PublicLayoutGallery),
    title: 'PublicLayout',
  },
  {
    fullFrame: true,
    group: 'component',
    id: 'layout-learner',
    render: () => createElement(LearnerLayoutGallery),
    title: 'LearnerLayout',
  },
  {
    fullFrame: true,
    group: 'component',
    id: 'layout-admin',
    render: () => createElement(AdminLayoutGallery),
    title: 'AdminLayout',
  },
  ...authPreviews,
  ...accountPreviews,
  ...adminUserPreviews,
  ...contentImportPreviews,
  ...vocabPreviews,
  ...grammarPreviews,
  ...pronunciationPreviews,
  ...hanziPreviews,
  ...practicePreviews,
]

export function getPreviewStates(entry: PreviewEntry): readonly PreviewState[] {
  return entry.states?.length ? entry.states : ['ready']
}
