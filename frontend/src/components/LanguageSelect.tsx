import type { Language } from '../api/types'

export function LanguageSelect({
  languages,
  value,
  onChange,
}: {
  languages: Language[]
  value: number | null
  onChange: (languageId: number) => void
}) {
  return (
    <select
      aria-label="Language"
      value={value ?? ''}
      onChange={(event) => onChange(Number(event.target.value))}
    >
      <option value="" disabled>
        Select a language
      </option>
      {languages.map((language) => (
        <option key={language.id} value={language.id}>
          {language.name}
        </option>
      ))}
    </select>
  )
}
