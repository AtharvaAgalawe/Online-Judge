export function toIntParam(value: string | null, fallback: number): number {
  const parsed = Number(value)
  return Number.isInteger(parsed) ? parsed : fallback
}

export function toOptionalIntParam(value: string | null): number | undefined {
  if (value === null || value.trim() === '') return undefined
  const parsed = Number(value)
  return Number.isInteger(parsed) ? parsed : undefined
}
