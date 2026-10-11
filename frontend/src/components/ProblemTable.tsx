import type { ProblemSummary } from '../api/types'

export function ProblemTable({
  problems,
  onSelect,
}: {
  problems: ProblemSummary[]
  onSelect: (slug: string) => void
}) {
  return (
    <table className="problem-table">
      <thead>
        <tr>
          <th>Title</th>
          <th>Difficulty</th>
          <th>Tags</th>
          <th>Acceptance</th>
        </tr>
      </thead>
      <tbody>
        {problems.map((problem) => (
          <tr key={problem.id}>
            <td>
              <button type="button" className="linklike" onClick={() => onSelect(problem.slug)}>
                {problem.title}
              </button>
            </td>
            <td>{problem.difficulty}</td>
            <td>{problem.tags.join(', ')}</td>
            <td>{problem.acceptanceRate.toFixed(1)}%</td>
          </tr>
        ))}
      </tbody>
    </table>
  )
}
