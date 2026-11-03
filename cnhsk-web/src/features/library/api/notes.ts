import { apiRequest } from '@/shared/api'

import type { Note, NoteListParams, NotePage, NoteRequest, TrashNote } from '../types'

function notePath(id: number, suffix = '') {
  return `/api/notes/${encodeURIComponent(String(id))}${suffix}`
}

export function listNotes(
  { q, tag, page = 0, size = 20 }: NoteListParams = {},
  signal?: AbortSignal,
): Promise<NotePage> {
  const query = new URLSearchParams()
  if (q !== undefined) query.set('q', q)
  if (tag !== undefined) query.set('tag', tag)
  query.set('page', String(page))
  query.set('size', String(size))
  return apiRequest<NotePage>(`/api/notes?${query.toString()}`, { signal })
}

export function createNote(body: NoteRequest): Promise<Note> {
  return apiRequest<Note>('/api/notes', { body, method: 'POST' })
}

export function getNote(id: number, signal?: AbortSignal): Promise<Note> {
  return apiRequest<Note>(notePath(id), { signal })
}

export function updateNote(id: number, body: NoteRequest): Promise<Note> {
  return apiRequest<Note>(notePath(id), { body, method: 'PUT' })
}

export function deleteNote(id: number): Promise<void> {
  return apiRequest<void>(notePath(id), { method: 'DELETE' })
}

export function listTrash(signal?: AbortSignal): Promise<TrashNote[]> {
  return apiRequest<TrashNote[]>('/api/notes/trash', { signal })
}

export function restoreNote(id: number): Promise<Note> {
  return apiRequest<Note>(notePath(id, '/restore'), { method: 'POST' })
}
