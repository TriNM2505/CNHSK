import { apiRequest } from '@/shared/api'

import { checkedIdentifiers, checkBodyIdentifiers, identifierPath } from './identifiers'

import type { Note, NoteListParams, NotePage, NoteRequest, TrashNote } from '../types'

function notePath(id: number, suffix = '') {
  return `/api/notes/${identifierPath(id)}${suffix}`
}

export async function listNotes(
  { q, tag, page = 0, size = 20 }: NoteListParams = {},
  signal?: AbortSignal,
): Promise<NotePage> {
  const query = new URLSearchParams()
  if (q !== undefined) query.set('q', q)
  if (tag !== undefined) query.set('tag', tag)
  query.set('page', String(page))
  query.set('size', String(size))
  return checkedIdentifiers(apiRequest<NotePage>(`/api/notes?${query.toString()}`, { signal }))
}

export async function createNote(body: NoteRequest): Promise<Note> {
  checkBodyIdentifiers(body)
  return checkedIdentifiers(apiRequest<Note>('/api/notes', { body, method: 'POST' }))
}

export async function getNote(id: number, signal?: AbortSignal): Promise<Note> {
  return checkedIdentifiers(apiRequest<Note>(notePath(id), { signal }))
}

export async function updateNote(id: number, body: NoteRequest): Promise<Note> {
  checkBodyIdentifiers(body)
  return checkedIdentifiers(apiRequest<Note>(notePath(id), { body, method: 'PUT' }))
}

export async function deleteNote(id: number): Promise<void> {
  return checkedIdentifiers(apiRequest<void>(notePath(id), { method: 'DELETE' }))
}

export async function listTrash(signal?: AbortSignal): Promise<TrashNote[]> {
  return checkedIdentifiers(apiRequest<TrashNote[]>('/api/notes/trash', { signal }))
}

export async function restoreNote(id: number): Promise<Note> {
  return checkedIdentifiers(apiRequest<Note>(notePath(id, '/restore'), { method: 'POST' }))
}
