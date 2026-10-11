import { useState } from 'react'
import type { FormEvent } from 'react'
import { useNavigate } from 'react-router-dom'
import { useMutation } from '@tanstack/react-query'
import { createContest } from '../../api/admin'
import { AdminNav } from '../../components/AdminNav'
import { ErrorBanner } from '../../components/ErrorBanner'

export function AdminContestCreatePage() {
  const navigate = useNavigate()
  const [name, setName] = useState('')
  const [description, setDescription] = useState('')
  const [startTime, setStartTime] = useState('')
  const [endTime, setEndTime] = useState('')
  const [isPrivate, setIsPrivate] = useState(false)
  const [freezeMinutes, setFreezeMinutes] = useState(60)

  const mutation = useMutation({
    mutationFn: () =>
      createContest({
        name,
        description: description || undefined,
        startTime: new Date(startTime).toISOString(),
        endTime: new Date(endTime).toISOString(),
        isPrivate,
        freezeMinutes,
      }),
    onSuccess: (created) => navigate(`/admin/contests/${created.id}`),
  })

  function onSubmit(event: FormEvent) {
    event.preventDefault()
    mutation.mutate()
  }

  return (
    <section>
      <h1>New contest</h1>
      <AdminNav />
      {mutation.isError && <ErrorBanner error={mutation.error} />}
      <form onSubmit={onSubmit}>
        <label>Name
          <input value={name} onChange={(e) => setName(e.target.value)} required maxLength={200} />
        </label>
        <label>Description
          <textarea value={description} onChange={(e) => setDescription(e.target.value)} rows={4} />
        </label>
        <label>Start time
          <input type="datetime-local" value={startTime}
                 onChange={(e) => setStartTime(e.target.value)} required />
        </label>
        <label>End time
          <input type="datetime-local" value={endTime}
                 onChange={(e) => setEndTime(e.target.value)} required />
        </label>
        <label>Private
          <input type="checkbox" checked={isPrivate}
                 onChange={(e) => setIsPrivate(e.target.checked)} />
        </label>
        <label>Freeze minutes
          <input type="number" min={0} value={freezeMinutes}
                 onChange={(e) => setFreezeMinutes(Number(e.target.value))} />
        </label>
        <button type="submit" disabled={mutation.isPending}>
          {mutation.isPending ? 'Creating…' : 'Create'}
        </button>
      </form>
    </section>
  )
}
