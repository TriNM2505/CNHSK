import { createElement, type ReactNode } from 'react'

import {
  ButtonGallery,
  CardGallery,
  FormFieldGallery,
  HanziTextGallery,
  IconButtonGallery,
  InputGallery,
  LevelBadgeGallery,
  PageHeaderGallery,
  PasswordInputGallery,
  SectionGallery,
  SelectGallery,
  StatTileGallery,
  StatusBadgeGallery,
  TextareaGallery,
} from '@/app/preview/galleries'

export type PreviewState = 'loading' | 'empty' | 'error' | 'ready'

export interface PreviewEntry {
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
]

export function getPreviewStates(entry: PreviewEntry): readonly PreviewState[] {
  return entry.states?.length ? entry.states : ['ready']
}
