import { useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import { useParams } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { fetchAdminContest, updateContest } from '../../api/admin'
import type { UpdateContestBody } from '../../api/admin'
import { AdminNav } from '../../components/AdminNav'
import { ContestStatusChip } from '../../components/ContestStatusChip'
import { ErrorBanner } from '../../components/ErrorBanner'
import { Spinner } from '../../components/Spinner'

function toLocalInput(iso: string): string {
  const date = new Date(iso)
  const local = new Date(date.getTime() - date.getTimezoneOffset() * 60000)
  return local.toISOString().slice(0, 16)
}

export function AdminContestEditPage() {
  const { id = '' } = useParams()
  const contestId = Number(id)
  const queryClient = useQueryClient()

  const detail = useQuery({
    queryKey: ['adminContest', contestId],
    queryFn: () => fetchAdminContest(contestId),
  })

  const [name, setName] = useState('')
  const [description, setDescription] = useState('')
  const [startTime, setStartTime] = useState('')
  const [endTime, setEndTime] = useState('')
  const [isPrivate, setIsPrivate] = useState(false)
  const [freezeMinutes, setFreezeMinutes] = useState(60)

  useEffect(() => {
    if (detail.data) {
      setName(detail.data.name)
      setDescription(detail.data.description ?? '')
      setStartTime(toLocalInput(detail.data.startTime))
      setEndTime(toLocalInput(detail.data.endTime))
      setIsPrivate(detail.data.isPrivate)
      setFreezeMinutes(detail.data.freezeMinutes)
    }
  }, [detail.data])

  const mutation = useMutation({
    mutationFn: (body: UpdateContestBody) => updateContest(contestId, body),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['adminContest', contestId] })
      queryClient.invalidateQueries({ queryKey: ['adminContests'] })
    },
  })

  if (detail.isPending) return <Spinner label="Loading contest" />
  if (detail.isError) return <ErrorBanner error={detail.error} />
  const contest = detail.data
  const editable = contest.status === 'UPCOMING'

  function onSubmit(event: FormEvent) {
    event.preventDefault()
    mutation.mutate({
      name,
      description: description || undefined,
      startTime: new Date(startTime).toISOString(),
      endTime: new Date(endTime).toISOString(),
      isPrivate,
      freezeMinutes,
    })
  }

  return (
    <section>
      <h1>Edit · {contest.name}</h1>
      <AdminNav />
      <p>Status: <ContestStatusChip status={contest.status} /></p>
      {!editable && (
        <p role="status">This contest has started and can no longer be edited.</p>
      )}
      {mutation.isError && <ErrorBanner error={mutation.error} />}
      <form onSubmit={onSubmit}>
        <fieldset disabled={!editable}>
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
          <button type="submit" disabled={mutation.isPending}>Save metadata</button>
        </fieldset>
      </form>
    </section>
  )
}
