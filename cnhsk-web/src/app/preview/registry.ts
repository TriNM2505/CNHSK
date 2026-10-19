import { createElement, type ReactNode } from 'react'

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
import {
  AudioButtonGallery,
  ChoiceQuestionGallery,
  PinyinInputGallery,
  SessionProgressGallery,
  SessionSummaryGallery,
  StudyCardGallery,
} from '@/app/preview/practiceGalleries'

export type PreviewState = 'loading' | 'empty' | 'error' | 'ready'

export interface PreviewEntry {
  fullFrame?: boolean
  group: 'component' | 'screen'
  id: string
  render: (state: PreviewState) => ReactNode
  scr?: string
  states?: readonly PreviewState[]
  title: string
}

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
  {
    group: 'component',
    id: 'practice-choice-question',
    render: () => createElement(ChoiceQuestionGallery),
    title: 'ChoiceQuestion',
  },
  {
    group: 'component',
    id: 'practice-pinyin-input',
    render: () => createElement(PinyinInputGallery),
    title: 'PinyinInput',
  },
  {
    group: 'component',
    id: 'practice-audio-button',
    render: () => createElement(AudioButtonGallery),
    title: 'AudioButton',
  },
  {
    group: 'component',
    id: 'practice-study-card',
    render: () => createElement(StudyCardGallery),
    title: 'StudyCard',
  },
  {
    group: 'component',
    id: 'practice-session-progress',
    render: () => createElement(SessionProgressGallery),
    title: 'SessionProgress',
  },
  {
    group: 'component',
    id: 'practice-session-summary',
    render: () => createElement(SessionSummaryGallery),
    title: 'SessionSummary',
  },
]

export function getPreviewStates(entry: PreviewEntry): readonly PreviewState[] {
  return entry.states?.length ? entry.states : ['ready']
}
