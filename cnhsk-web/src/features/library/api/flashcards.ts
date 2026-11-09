import { apiRequest } from '@/shared/api'

import { checkedIdentifiers, checkBodyIdentifiers, identifierPath } from './identifiers'

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
  return `/api/flashcard-decks/${identifierPath(id)}${suffix}`
}

function cardPath(deckId: number, cardId: number) {
  return deckPath(deckId, `/cards/${identifierPath(cardId)}`)
}

export async function listDecks(signal?: AbortSignal): Promise<DeckSummary[]> {
  return checkedIdentifiers(apiRequest<DeckSummary[]>('/api/flashcard-decks', { signal }))
}

export async function createDeck(body: DeckRequest): Promise<DeckDetail> {
  checkBodyIdentifiers(body)
  return checkedIdentifiers(
    apiRequest<DeckDetail>('/api/flashcard-decks', { body, method: 'POST' }),
  )
}

export async function getDeck(id: number, signal?: AbortSignal): Promise<DeckDetail> {
  return checkedIdentifiers(apiRequest<DeckDetail>(deckPath(id), { signal }))
}

export async function updateDeck(id: number, body: DeckRequest): Promise<DeckSummary> {
  checkBodyIdentifiers(body)
  return checkedIdentifiers(apiRequest<DeckSummary>(deckPath(id), { body, method: 'PATCH' }))
}

export async function deleteDeck(id: number): Promise<void> {
  return checkedIdentifiers(apiRequest<void>(deckPath(id), { method: 'DELETE' }))
}

export async function addCard(deckId: number, body: CardRequest): Promise<Card> {
  checkBodyIdentifiers(body)
  return checkedIdentifiers(apiRequest<Card>(deckPath(deckId, '/cards'), { body, method: 'POST' }))
}

export async function updateCard(deckId: number, cardId: number, body: CardRequest): Promise<Card> {
  checkBodyIdentifiers(body)
  return checkedIdentifiers(apiRequest<Card>(cardPath(deckId, cardId), { body, method: 'PATCH' }))
}

export async function deleteCard(deckId: number, cardId: number): Promise<void> {
  return checkedIdentifiers(apiRequest<void>(cardPath(deckId, cardId), { method: 'DELETE' }))
}

export async function listDueCards(
  id: number,
  ahead = false,
  signal?: AbortSignal,
): Promise<Card[]> {
  const query = new URLSearchParams({ ahead: String(ahead) })
  return checkedIdentifiers(
    apiRequest<Card[]>(`${deckPath(id, '/due')}?${query.toString()}`, { signal }),
  )
}

export async function reviewCard(cardId: number, body: ReviewRequest): Promise<ReviewResult> {
  return checkedIdentifiers(
    apiRequest<ReviewResult>(`/api/flashcards/${identifierPath(cardId)}/review`, {
      body,
      method: 'POST',
    }),
  )
}
