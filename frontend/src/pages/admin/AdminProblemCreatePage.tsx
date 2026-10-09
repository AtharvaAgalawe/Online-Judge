import { useState } from 'react'
import type { FormEvent } from 'react'
import { useNavigate } from 'react-router-dom'
import { useMutation } from '@tanstack/react-query'
import { createProblem } from '../../api/admin'
import { ErrorBanner } from '../../components/ErrorBanner'
import type { Difficulty } from '../../api/types'

export function AdminProblemCreatePage() {
  const navigate = useNavigate()
  const [title, setTitle] = useState('')
  const [statement, setStatement] = useState('')
  const [difficulty, setDifficulty] = useState<Difficulty>('EASY')
  const [tags, setTags] = useState('')

  const mutation = useMutation({
    mutationFn: () =>
      createProblem({
        title,
        statement,
        difficulty,
        tags: tags.split(',').map((tag) => tag.trim()).filter(Boolean),
      }),
    onSuccess: (created) => navigate(`/admin/problems/${created.id}`),
  })

  function onSubmit(event: FormEvent) {
    event.preventDefault()
    mutation.mutate()
  }

  return (
    <section>
      <h1>New problem</h1>
      {mutation.isError && <ErrorBanner error={mutation.error} />}
      <form onSubmit={onSubmit}>
        <label>Title
          <input value={title} onChange={(e) => setTitle(e.target.value)} required maxLength={200} />
        </label>
        <label>Statement
          <textarea value={statement} onChange={(e) => setStatement(e.target.value)} required rows={8} />
        </label>
        <label>Difficulty
          <select value={difficulty} onChange={(e) => setDifficulty(e.target.value as Difficulty)}>
            <option value="EASY">Easy</option>
            <option value="MEDIUM">Medium</option>
            <option value="HARD">Hard</option>
          </select>
        </label>
        <label>Tags (comma-separated)
          <input value={tags} onChange={(e) => setTags(e.target.value)} />
        </label>
        <button type="submit" disabled={mutation.isPending}>
          {mutation.isPending ? 'Creating…' : 'Create'}
        </button>
      </form>
    </section>
  )
}
