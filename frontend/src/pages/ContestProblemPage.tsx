import { useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { fetchContestProblem } from '../api/contests'
import { fetchLanguages } from '../api/languages'
import { createSubmission } from '../api/submissions'
import { BOILERPLATE, CodeEditor } from '../components/CodeEditor'
import { ErrorBanner } from '../components/ErrorBanner'
import { LanguageSelect } from '../components/LanguageSelect'
import { Spinner } from '../components/Spinner'

const MAX_SOURCE_LENGTH = 65_536

function boilerplateFor(languageName: string): string {
  const name = languageName.toLowerCase()
  if (name.startsWith('java')) return BOILERPLATE.java
  if (name.startsWith('python')) return BOILERPLATE.python
  return ''
}

export function ContestProblemPage() {
  const { id = '', slug = '' } = useParams()
  const contestId = Number(id)
  const validId = /^\d+$/.test(id)
  const navigate = useNavigate()
  const queryClient = useQueryClient()

  const detail = useQuery({
    queryKey: ['contestProblem', contestId, slug],
    queryFn: () => fetchContestProblem(contestId, slug),
    enabled: validId,
  })
  const languages = useQuery({ queryKey: ['languages'], queryFn: fetchLanguages })

  const [languageId, setLanguageId] = useState<number | null>(null)
  const [sourceCode, setSourceCode] = useState('')

  const submit = useMutation({
    mutationFn: (input: { sourceCode: string; languageId: number; idempotencyKey: string }) =>
      createSubmission(
        {
          problemId: detail.data!.problemId,
          languageId: input.languageId,
          sourceCode: input.sourceCode,
        },
        input.idempotencyKey,
      ),
    onSuccess: (accepted) => {
      queryClient.invalidateQueries({ queryKey: ['history'] })
      navigate(`/submissions/${accepted.submissionId}`)
    },
  })

  function onLanguageChange(nextId: number) {
    setLanguageId(nextId)
    const selected = languages.data?.find((language) => language.id === nextId)
    if (selected && sourceCode.trim() === '') setSourceCode(boilerplateFor(selected.name))
  }

  if (!validId) return <p>Problem not found.</p>
  if (detail.isPending) return <Spinner label="Loading problem" />
  if (detail.isError) return <ErrorBanner error={detail.error} />

  const problem = detail.data
  const tooLong = sourceCode.length > MAX_SOURCE_LENGTH

  return (
    <section>
      <p className="contest-meta">
        <Link to={`/contests/${contestId}`}>{problem.contestName}</Link> · {problem.points} points
      </p>
      {problem.contestStatus === 'ENDED' && (
        <p>
          This problem is now public — view it on the{' '}
          <Link to={`/problems/${problem.slug}`}>problems page</Link>.
        </p>
      )}
      <h1>{problem.title}</h1>
      <p className="problem-meta">
        {problem.difficulty} · {problem.timeLimitMs} ms ·{' '}
        {Math.round(problem.memoryLimitKb / 1024)} MB
      </p>
      <article className="statement">{problem.statement}</article>
      <h2>Sample cases</h2>
      <ul>
        {problem.sampleTestCases.map((sample) => (
          <li key={sample.order}>
            <p className="sample-label">Input</p>
            <pre>{sample.input}</pre>
            <p className="sample-label">Expected</p>
            <pre>{sample.expectedOutput}</pre>
          </li>
        ))}
      </ul>

      <h2>Submit</h2>
      <div className="submit-panel">
        {languages.isError && <ErrorBanner error={languages.error} />}
        {submit.isError && <ErrorBanner error={submit.error} />}
        <LanguageSelect
          languages={languages.data ?? []}
          value={languageId}
          onChange={onLanguageChange}
        />
        <CodeEditor
          value={sourceCode}
          languageName={
            languages.data?.find((language) => language.id === languageId)?.name ?? ''
          }
          onChange={setSourceCode}
        />
        {tooLong && <p className="error-inline">Source exceeds {MAX_SOURCE_LENGTH} characters.</p>}
        <button
          type="button"
          disabled={submit.isPending || languageId === null || sourceCode.trim() === '' || tooLong}
          onClick={() =>
            submit.mutate({
              sourceCode,
              languageId: languageId!,
              idempotencyKey: crypto.randomUUID(),
            })
          }
        >
          {submit.isPending ? 'Submitting…' : 'Submit'}
        </button>
      </div>
    </section>
  )
}
