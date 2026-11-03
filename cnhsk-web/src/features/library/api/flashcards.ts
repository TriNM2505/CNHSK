import { apiRequest } from '@/shared/api'

import type {
  Card,
  CardRequest,
  DeckDetail,
  DeckRequest,
  DeckSummary,
  ReviewRequest,
  ReviewResult,
} from '../types'

function deckPath(id: number, suffix = '') {
  return `/api/flashcard-decks/${encodeURIComponent(String(id))}${suffix}`
}

function cardPath(deckId: number, cardId: number) {
  return deckPath(deckId, `/cards/${encodeURIComponent(String(cardId))}`)
}

export function listDecks(signal?: AbortSignal): Promise<DeckSummary[]> {
  return apiRequest<DeckSummary[]>('/api/flashcard-decks', { signal })
}

export function createDeck(body: DeckRequest): Promise<DeckDetail> {
  return apiRequest<DeckDetail>('/api/flashcard-decks', { body, method: 'POST' })
}

export function getDeck(id: number, signal?: AbortSignal): Promise<DeckDetail> {
  return apiRequest<DeckDetail>(deckPath(id), { signal })
}

export function updateDeck(id: number, body: DeckRequest): Promise<DeckDetail> {
  return apiRequest<DeckDetail>(deckPath(id), { body, method: 'PATCH' })
}

export function deleteDeck(id: number): Promise<void> {
  return apiRequest<void>(deckPath(id), { method: 'DELETE' })
}

export function addCard(deckId: number, body: CardRequest): Promise<Card> {
  return apiRequest<Card>(deckPath(deckId, '/cards'), { body, method: 'POST' })
}

export function updateCard(deckId: number, cardId: number, body: CardRequest): Promise<Card> {
  return apiRequest<Card>(cardPath(deckId, cardId), { body, method: 'PATCH' })
}

export function deleteCard(deckId: number, cardId: number): Promise<void> {
  return apiRequest<void>(cardPath(deckId, cardId), { method: 'DELETE' })
}

export function listDueCards(id: number, ahead = false, signal?: AbortSignal): Promise<Card[]> {
  const query = new URLSearchParams({ ahead: String(ahead) })
  return apiRequest<Card[]>(`${deckPath(id, '/due')}?${query.toString()}`, { signal })
}

export function reviewCard(cardId: number, body: ReviewRequest): Promise<ReviewResult> {
  return apiRequest<ReviewResult>(`/api/flashcards/${encodeURIComponent(String(cardId))}/review`, {
    body,
    method: 'POST',
  })
}
