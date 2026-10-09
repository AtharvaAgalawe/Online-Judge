import { apiFetch } from './client'
import type { Language } from './types'

export function fetchLanguages(): Promise<Language[]> {
  return apiFetch<Language[]>('/api/v1/languages')
}
