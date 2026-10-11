import { Link, useNavigate, useParams } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { fetchContest, fetchStandings, registerForContest, unregisterFromContest } from '../api/contests'
import { useAuth } from '../auth/AuthContext'
import { ContestStatusChip } from '../components/ContestStatusChip'
import { Countdown } from '../components/Countdown'
import { ErrorBanner } from '../components/ErrorBanner'
import { Spinner } from '../components/Spinner'
import { StandingsTable } from '../components/StandingsTable'

export function ContestDetailPage() {
  const { id = '' } = useParams()
  const contestId = Number(id)
  const validId = /^\d+$/.test(id)
  const { status: authStatus } = useAuth()
  const navigate = useNavigate()
  const queryClient = useQueryClient()

  const query = useQuery({
    queryKey: ['contest', contestId],
    queryFn: () => fetchContest(contestId),
    enabled: validId,
    refetchInterval: 30_000,
  })
  const standings = useQuery({
    queryKey: ['standings', contestId],
    queryFn: () => fetchStandings(contestId),
    enabled: validId && query.data?.problemsRevealed === true,
    refetchInterval: query.data?.status === 'RUNNING' ? 15_000 : false,
  })
  const register = useMutation({
    mutationFn: () => registerForContest(contestId),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['contest', contestId] }),
  })
  const unregister = useMutation({
    mutationFn: () => unregisterFromContest(contestId),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['contest', contestId] }),
  })

  if (!validId) return <p>Contest not found.</p>
  if (query.isPending) return <Spinner label="Loading contest" />
  if (query.isError) return <ErrorBanner error={query.error} />
  const contest = query.data

  return (
    <section>
      <div className="contest-header">
        <h1>{contest.name}</h1>
        <ContestStatusChip status={contest.status} />
      </div>
      <p className="contest-meta">
        {new Date(contest.startTime).toLocaleString()} – {new Date(contest.endTime).toLocaleString()} ·{' '}
        {contest.participantCount} participants
      </p>
      {contest.status === 'UPCOMING' && <Countdown target={contest.startTime} label="Starts in" />}
      {contest.status === 'RUNNING' && <Countdown target={contest.endTime} label="Ends in" />}
      {contest.description && <p>{contest.description}</p>}
      <div className="contest-actions">
        {authStatus !== 'authenticated' && contest.registrationOpen && (
          <Link to="/login" className="btn btn--outline">
            Sign in to register
          </Link>
        )}
        {authStatus === 'authenticated' && contest.registrationOpen && !contest.registered && (
          <button
            type="button"
            className="btn btn--solid"
            disabled={register.isPending}
            onClick={() => register.mutate()}
          >
            {register.isPending ? 'Registering…' : 'Register'}
          </button>
        )}
        {authStatus === 'authenticated' && contest.registered && contest.registrationOpen && (
          <button
            type="button"
            className="btn btn--outline"
            disabled={unregister.isPending}
            onClick={() => unregister.mutate()}
          >
            Registered ✓ · Unregister
          </button>
        )}
        {contest.registered && !contest.registrationOpen && <span className="contest-meta">Registered ✓</span>}
      </div>
      {register.isError && <ErrorBanner error={register.error} />}
      <h2>Problems</h2>
      {!contest.problemsRevealed || !contest.problems ? (
        <p>Problems will be revealed when the contest starts.</p>
      ) : contest.problems.length === 0 ? (
        <p>No problems have been added yet.</p>
      ) : (
        <table className="problem-table">
          <thead>
            <tr>
              <th>#</th>
              <th>Title</th>
              <th>Difficulty</th>
              <th>Points</th>
              <th>Solved</th>
            </tr>
          </thead>
          <tbody>
            {contest.problems.map((problem, index) => (
              <tr key={problem.problemId}>
                <td>{String.fromCharCode(65 + index)}</td>
                <td>
                  <button
                    type="button"
                    className="linklike"
                    onClick={() => navigate(`/contests/${contestId}/problems/${problem.slug}`)}
                  >
                    {problem.title}
                  </button>
                </td>
                <td>{problem.difficulty}</td>
                <td>{problem.points}</td>
                <td>{problem.solvedByMe ? '✓' : ''}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
      {contest.problemsRevealed && (
        <>
          <h2>Standings</h2>
          {standings.isPending && <Spinner label="Loading standings" />}
          {standings.isError && <ErrorBanner error={standings.error} />}
          {standings.isSuccess && <StandingsTable standings={standings.data} />}
        </>
      )}
    </section>
  )
}
