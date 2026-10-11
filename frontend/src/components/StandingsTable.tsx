import type { StandingsResponse } from '../api/contests'

function cellLabel(cell: { solved: boolean; wrongAttempts: number }): string {
  if (cell.solved) {
    return `+${cell.wrongAttempts}`
  }
  return cell.wrongAttempts > 0 ? `−${cell.wrongAttempts}` : '·'
}

export function StandingsTable({ standings }: { standings: StandingsResponse }) {
  if (standings.rows.length === 0) {
    return <p>No participants yet.</p>
  }
  return (
    <>
      {standings.frozen && (
        <p className="frozen-note">
          Standings frozen
          {standings.frozenAt ? ` as of ${new Date(standings.frozenAt).toLocaleTimeString()}` : ''}
        </p>
      )}
      <table className="problem-table">
        <thead>
          <tr>
            <th>#</th>
            <th>User</th>
            <th>Points</th>
            <th>Penalty</th>
            {standings.rows[0].problems.map((cell, index) => (
              <th key={cell.problemId}>{String.fromCharCode(65 + index)}</th>
            ))}
          </tr>
        </thead>
        <tbody>
          {standings.rows.map((row) => (
            <tr key={row.username}>
              <td>{row.rank}</td>
              <td>{row.username}</td>
              <td>{row.totalPoints}</td>
              <td>{row.penalty}</td>
              {row.problems.map((cell) => (
                <td
                  key={cell.problemId}
                  className={`standings-cell ${
                    cell.solved ? 'standings-cell--solved' : cell.wrongAttempts > 0 ? 'standings-cell--unsolved' : ''
                  }`}
                >
                  {cellLabel(cell)}
                </td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </>
  )
}
