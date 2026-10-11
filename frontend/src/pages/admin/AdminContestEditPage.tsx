import { useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  attachContestProblem,
  createContestProblemInline,
  deleteContest,
  detachContestProblem,
  fetchAdminContest,
  fetchAttachableProblems,
  updateContest,
  updateContestProblem,
} from '../../api/admin'
import type { UpdateContestBody } from '../../api/admin'
import type { ContestProblemSummary } from '../../api/contests'
import type { Difficulty } from '../../api/types'
import { AdminNav } from '../../components/AdminNav'
import { ContestStatusChip } from '../../components/ContestStatusChip'
import { ErrorBanner } from '../../components/ErrorBanner'
import { Spinner } from '../../components/Spinner'

const LETTERS = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ'

function letterAt(index: number): string {
  return LETTERS[index] ?? String(index + 1)
}

function toLocalInput(iso: string): string {
  const date = new Date(iso)
  const local = new Date(date.getTime() - date.getTimezoneOffset() * 60000)
  return local.toISOString().slice(0, 16)
}

function AttachedProblemRow({ contestId, problem, index, editable }: {
  contestId: number
  problem: ContestProblemSummary
  index: number
  editable: boolean
}) {
  const queryClient = useQueryClient()
  const [points, setPoints] = useState(String(problem.points))
  const [displayOrder, setDisplayOrder] = useState(String(problem.displayOrder))

  useEffect(() => {
    setPoints(String(problem.points))
    setDisplayOrder(String(problem.displayOrder))
  }, [problem.points, problem.displayOrder])

  const save = useMutation({
    mutationFn: () =>
      updateContestProblem(contestId, problem.problemId, {
        points: Number(points),
        displayOrder: Number(displayOrder),
      }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['adminContest', contestId] }),
  })

  const detach = useMutation({
    mutationFn: () => detachContestProblem(contestId, problem.problemId),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['adminContest', contestId] }),
  })

  function onDetach() {
    if (window.confirm(`Detach "${problem.title}" from this contest?`)) {
      detach.mutate()
    }
  }

  return (
    <tr>
      <td>{letterAt(index)}</td>
      <td><Link to={`/admin/problems/${problem.problemId}`}>{problem.title}</Link></td>
      <td>{problem.difficulty}</td>
      <td>
        {editable ? (
          <input type="number" min={1} max={10000} value={points}
                 aria-label={`Points for ${problem.title}`}
                 onChange={(e) => setPoints(e.target.value)} />
        ) : problem.points}
      </td>
      <td>
        {editable ? (
          <input type="number" min={0} value={displayOrder}
                 aria-label={`Order for ${problem.title}`}
                 onChange={(e) => setDisplayOrder(e.target.value)} />
        ) : problem.displayOrder}
      </td>
      <td>
        {editable && (
          <>
            <button type="button" onClick={() => save.mutate()} disabled={save.isPending}>
              Save
            </button>
            <button type="button" onClick={onDetach} disabled={detach.isPending}>Detach</button>
          </>
        )}
        {save.isError && <ErrorBanner error={save.error} />}
        {detach.isError && <ErrorBanner error={detach.error} />}
      </td>
    </tr>
  )
}

export function AdminContestEditPage() {
  const { id = '' } = useParams()
  const contestId = Number(id)
  const navigate = useNavigate()
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

  const [pickerOpen, setPickerOpen] = useState(false)
  const [searchInput, setSearchInput] = useState('')
  const [search, setSearch] = useState('')

  const [newTitle, setNewTitle] = useState('')
  const [newStatement, setNewStatement] = useState('')
  const [newDifficulty, setNewDifficulty] = useState<Difficulty>('EASY')
  const [newTimeLimitMs, setNewTimeLimitMs] = useState(2000)
  const [newMemoryLimitKb, setNewMemoryLimitKb] = useState(262144)
  const [newPoints, setNewPoints] = useState(100)
  const [created, setCreated] = useState<{ problemId: number; slug: string } | null>(null)

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

  const remove = useMutation({
    mutationFn: () => deleteContest(contestId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['adminContests'] })
      navigate('/admin/contests')
    },
  })

  const attachable = useQuery({
    queryKey: ['attachableProblems', search],
    queryFn: () => fetchAttachableProblems(search || undefined),
    enabled: pickerOpen,
  })

  const attach = useMutation({
    mutationFn: (problemId: number) => attachContestProblem(contestId, { problemId }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['adminContest', contestId] })
      queryClient.invalidateQueries({ queryKey: ['attachableProblems'] })
    },
  })

  const createInline = useMutation({
    mutationFn: () =>
      createContestProblemInline(contestId, {
        title: newTitle,
        statement: newStatement,
        difficulty: newDifficulty,
        timeLimitMs: newTimeLimitMs,
        memoryLimitKb: newMemoryLimitKb,
        points: newPoints,
      }),
    onSuccess: (result) => {
      setCreated(result)
      setNewTitle('')
      setNewStatement('')
      setNewDifficulty('EASY')
      setNewTimeLimitMs(2000)
      setNewMemoryLimitKb(262144)
      setNewPoints(100)
      queryClient.invalidateQueries({ queryKey: ['adminContest', contestId] })
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

  function onCreateProblem(event: FormEvent) {
    event.preventDefault()
    createInline.mutate()
  }

  function onDelete() {
    if (window.confirm(`Delete contest "${contest.name}"?`)) {
      remove.mutate()
    }
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
      {remove.isError && <ErrorBanner error={remove.error} />}
      <div className="contest-actions">
        <button type="button" disabled={!editable || remove.isPending} onClick={onDelete}>
          {remove.isPending ? 'Deleting…' : 'Delete contest'}
        </button>
      </div>
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

      <h2>Problems</h2>
      <div className="filters">
        <button type="button" disabled={!editable}
                onClick={() => setPickerOpen((open) => !open)}>
          Attach problem
        </button>
      </div>

      {pickerOpen && (
        <div className="submit-panel">
          <form onSubmit={(e) => { e.preventDefault(); setSearch(searchInput) }}>
            <label>Search problems
              <input value={searchInput} onChange={(e) => setSearchInput(e.target.value)} />
            </label>
            <button type="submit">Search</button>
          </form>
          {attachable.isPending && <Spinner label="Loading attachable problems" />}
          {attachable.isError && <ErrorBanner error={attachable.error} />}
          {attachable.isSuccess && (
            <table className="problem-table">
              <thead>
                <tr><th>Title</th><th>Difficulty</th><th></th></tr>
              </thead>
              <tbody>
                {attachable.data.map((problem) => (
                  <tr key={problem.id}>
                    <td>{problem.title}</td>
                    <td>{problem.difficulty}</td>
                    <td>
                      <button type="button" disabled={attach.isPending}
                              onClick={() => attach.mutate(problem.id)}>
                        Attach
                      </button>
                    </td>
                  </tr>
                ))}
                {attachable.data.length === 0 && (
                  <tr><td colSpan={3}>No unattached draft problems found.</td></tr>
                )}
              </tbody>
            </table>
          )}
          {attach.isError && <ErrorBanner error={attach.error} />}
        </div>
      )}

      <table className="problem-table">
        <thead>
          <tr><th>#</th><th>Title</th><th>Difficulty</th><th>Points</th><th>Order</th><th>Actions</th></tr>
        </thead>
        <tbody>
          {contest.problems.map((problem, index) => (
            <AttachedProblemRow key={problem.problemId} contestId={contestId}
                                problem={problem} index={index} editable={editable} />
          ))}
          {contest.problems.length === 0 && (
            <tr><td colSpan={6}>No problems attached yet.</td></tr>
          )}
        </tbody>
      </table>

      {editable && (
        <div className="submit-panel">
          <h3>Create new problem</h3>
          {createInline.isError && <ErrorBanner error={createInline.error} />}
          <form onSubmit={onCreateProblem}>
            <label>Title
              <input value={newTitle} onChange={(e) => setNewTitle(e.target.value)}
                     required maxLength={200} />
            </label>
            <label>Statement
              <textarea value={newStatement} onChange={(e) => setNewStatement(e.target.value)}
                        required rows={6} />
            </label>
            <label>Difficulty
              <select value={newDifficulty}
                      onChange={(e) => setNewDifficulty(e.target.value as Difficulty)}>
                <option value="EASY">Easy</option>
                <option value="MEDIUM">Medium</option>
                <option value="HARD">Hard</option>
              </select>
            </label>
            <label>Time limit (ms)
              <input type="number" min={100} max={60000} value={newTimeLimitMs}
                     onChange={(e) => setNewTimeLimitMs(Number(e.target.value))} />
            </label>
            <label>Memory limit (KB)
              <input type="number" min={1024} max={2097152} value={newMemoryLimitKb}
                     onChange={(e) => setNewMemoryLimitKb(Number(e.target.value))} />
            </label>
            <label>Points
              <input type="number" min={1} max={10000} value={newPoints}
                     onChange={(e) => setNewPoints(Number(e.target.value))} />
            </label>
            <button type="submit" disabled={createInline.isPending}>
              {createInline.isPending ? 'Creating…' : 'Create problem'}
            </button>
          </form>
          {created && (
            <p role="status">
              Problem created with id {created.problemId}.{' '}
              <Link to={`/admin/problems/${created.problemId}`}>Manage test cases</Link>
            </p>
          )}
        </div>
      )}
    </section>
  )
}
