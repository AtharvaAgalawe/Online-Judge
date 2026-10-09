import { useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import { Link, useParams } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { fetchAdminProblem, fetchTestCases, updateProblem } from '../../api/admin'
import type { UpdateProblemBody } from '../../api/admin'
import type { Difficulty } from '../../api/types'
import { ErrorBanner } from '../../components/ErrorBanner'
import { Spinner } from '../../components/Spinner'
import { TestCaseEditor } from '../../components/admin/TestCaseEditor'

export function AdminProblemEditPage() {
  const { id = '' } = useParams()
  const problemId = Number(id)
  const queryClient = useQueryClient()

  const detail = useQuery({ queryKey: ['adminProblem', problemId], queryFn: () => fetchAdminProblem(problemId) })
  const testCases = useQuery({ queryKey: ['adminTestCases', problemId], queryFn: () => fetchTestCases(problemId) })

  const [title, setTitle] = useState('')
  const [statement, setStatement] = useState('')
  const [difficulty, setDifficulty] = useState<Difficulty>('EASY')
  const [timeLimitMs, setTimeLimitMs] = useState(2000)
  const [memoryLimitKb, setMemoryLimitKb] = useState(262144)
  const [tags, setTags] = useState('')

  useEffect(() => {
    if (detail.data) {
      setTitle(detail.data.title)
      setStatement(detail.data.statement)
      setDifficulty(detail.data.difficulty)
      setTimeLimitMs(detail.data.timeLimitMs)
      setMemoryLimitKb(detail.data.memoryLimitKb)
      setTags(detail.data.tags.join(', '))
    }
  }, [detail.data])

  const mutation = useMutation({
    mutationFn: (body: UpdateProblemBody) => updateProblem(problemId, body),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['adminProblem', problemId] })
      queryClient.invalidateQueries({ queryKey: ['adminProblems'] })
      queryClient.invalidateQueries({ queryKey: ['problem', detail.data?.slug] })
      queryClient.invalidateQueries({ queryKey: ['problems'] })
    },
  })

  if (detail.isPending) return <Spinner label="Loading problem" />
  if (detail.isError) return <ErrorBanner error={detail.error} />
  const problem = detail.data

  function onSubmit(event: FormEvent) {
    event.preventDefault()
    mutation.mutate({
      title,
      statement,
      difficulty,
      timeLimitMs,
      memoryLimitKb,
      tags: tags.split(',').map((tag) => tag.trim()).filter(Boolean),
    })
  }

  return (
    <section>
      <h1>Edit · {problem.title}</h1>
      <p>
        Status: <strong>{problem.published ? 'Published' : 'Draft'}</strong> ·{' '}
        <Link to={`/problems/${problem.slug}`}>public view</Link>
      </p>
      {mutation.isError && <ErrorBanner error={mutation.error} />}
      <button
        type="button"
        disabled={!problem.published && problem.testCaseCount === 0}
        onClick={() => mutation.mutate({ published: !problem.published })}
      >
        {problem.published ? 'Unpublish' : 'Publish'}
      </button>
      {!problem.published && problem.testCaseCount === 0 && (
        <p role="status">Add at least one test case before publishing.</p>
      )}

      <h2>Metadata</h2>
      <form onSubmit={onSubmit}>
        <label>Title<input value={title} onChange={(e) => setTitle(e.target.value)} required /></label>
        <label>Statement<textarea value={statement} onChange={(e) => setStatement(e.target.value)} rows={8} required /></label>
        <label>Difficulty
          <select value={difficulty} onChange={(e) => setDifficulty(e.target.value as Difficulty)}>
            <option value="EASY">Easy</option>
            <option value="MEDIUM">Medium</option>
            <option value="HARD">Hard</option>
          </select>
        </label>
        <label>Time limit (ms)<input type="number" value={timeLimitMs} onChange={(e) => setTimeLimitMs(Number(e.target.value))} /></label>
        <label>Memory limit (KB)<input type="number" value={memoryLimitKb} onChange={(e) => setMemoryLimitKb(Number(e.target.value))} /></label>
        <label>Tags<input value={tags} onChange={(e) => setTags(e.target.value)} /></label>
        <button type="submit" disabled={mutation.isPending}>Save metadata</button>
      </form>

      <h2>Test cases</h2>
      {testCases.isPending && <Spinner label="Loading test cases" />}
      {testCases.isError && <ErrorBanner error={testCases.error} />}
      {testCases.isSuccess && <TestCaseEditor problemId={problemId} testCases={testCases.data} />}
    </section>
  )
}
