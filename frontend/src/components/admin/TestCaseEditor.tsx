import { useState } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { addTestCase, deleteTestCase } from '../../api/admin'
import type { TestCase, TestCaseInput } from '../../api/admin'
import { ErrorBanner } from '../ErrorBanner'

export function TestCaseEditor({ problemId, testCases }: { problemId: number; testCases: TestCase[] }) {
  const queryClient = useQueryClient()
  const [input, setInput] = useState('')
  const [expectedOutput, setExpectedOutput] = useState('')
  const [isSample, setIsSample] = useState(false)

  const invalidate = () => {
    queryClient.invalidateQueries({ queryKey: ['adminTestCases', problemId] })
    queryClient.invalidateQueries({ queryKey: ['adminProblem', problemId] })
  }

  const add = useMutation({
    mutationFn: (body: TestCaseInput) => addTestCase(problemId, body),
    onSuccess: () => {
      setInput('')
      setExpectedOutput('')
      setIsSample(false)
      invalidate()
    },
  })

  const remove = useMutation({
    mutationFn: (testCaseId: number) => deleteTestCase(problemId, testCaseId),
    onSuccess: invalidate,
  })

  return (
    <div>
      <table className="problem-table">
        <thead>
          <tr><th>#</th><th>Sample</th><th>Input</th><th>Expected</th><th /></tr>
        </thead>
        <tbody>
          {testCases.map((testCase) => (
            <tr key={testCase.id}>
              <td>{testCase.order}</td>
              <td>{testCase.isSample ? 'yes' : 'no'}</td>
              <td><code>{testCase.input}</code></td>
              <td><code>{testCase.expectedOutput}</code></td>
              <td>
                <button type="button" onClick={() => remove.mutate(testCase.id)}>Delete</button>
              </td>
            </tr>
          ))}
        </tbody>
      </table>

      {(add.isError || remove.isError) && <ErrorBanner error={add.error ?? remove.error} />}

      <form onSubmit={(event) => {
        event.preventDefault()
        add.mutate({ input, expectedOutput, isSample, order: testCases.length, points: 1 })
      }}>
        <label>Input
          <textarea value={input} onChange={(e) => setInput(e.target.value)} required rows={3} />
        </label>
        <label>Expected output
          <textarea value={expectedOutput} onChange={(e) => setExpectedOutput(e.target.value)} required rows={3} />
        </label>
        <label>
          <input type="checkbox" checked={isSample} onChange={(e) => setIsSample(e.target.checked)} />
          {' '}Sample (visible to solvers)
        </label>
        <button type="submit" disabled={add.isPending}>Add test case</button>
      </form>
    </div>
  )
}
