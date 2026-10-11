// Exercises the exact API sequence the Phase 11 UI performs.
// Usage: BASE_URL=http://localhost:8080 node scripts/verify-phase11-live.mjs
const BASE_URL = process.env.BASE_URL ?? 'http://localhost:8080'
const USERNAME = process.env.OJ_USER ?? `phase11${Date.now()}`
const PASSWORD = 'secret123'
const EMAIL = `${USERNAME}@example.com`

let failed = false

function pass(step, detail = '') {
  console.log(`PASS: ${step}${detail ? ` (${detail})` : ''}`)
}

function fail(step, error) {
  failed = true
  console.error(`FAIL: ${step} -> ${error}`)
}

async function step(name, fn) {
  try {
    const result = await fn()
    pass(name, result ?? '')
    return result
  } catch (error) {
    fail(name, error?.message ?? error)
    throw error
  }
}

async function json(path, init) {
  const response = await fetch(`${BASE_URL}${path}`, init)
  const text = await response.text()
  const body = text ? JSON.parse(text) : undefined
  if (!response.ok) throw new Error(`${response.status} ${body?.title ?? ''} ${body?.detail ?? ''}`)
  return body
}

async function main() {
  const health = await step('backend reachable', async () => {
    const response = await fetch(`${BASE_URL}/api/v1/languages`)
    if (!response.ok) throw new Error(`status ${response.status}`)
    return `${response.status}`
  })
  void health

  await step('register', () =>
    json('/api/v1/auth/register', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ username: USERNAME, email: EMAIL, password: PASSWORD }),
    }),
  )

  const tokens = await step('login', () =>
    json('/api/v1/auth/login', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ username: USERNAME, password: PASSWORD }),
    }),
  )

  await step('refresh', () =>
    json('/api/v1/auth/refresh', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ refreshToken: tokens.refreshToken }),
    }),
  )

  const authHeaders = { Authorization: `Bearer ${tokens.accessToken}` }

  const problems = await step('list problems', () => json('/api/v1/problems?page=0&size=5'))
  if (!problems.content || problems.content.length === 0) {
    throw new Error('no published problems to submit against; seed one before running')
  }
  const problem = problems.content[0]

  await step('problem detail', () => json(`/api/v1/problems/${problem.slug}`))
  await step('problem stats', () => json(`/api/v1/problems/${problem.slug}/stats`))

  const languages = await step('languages', async () => {
    const list = await json('/api/v1/languages')
    if (!Array.isArray(list) || list.length === 0) throw new Error('no languages available')
    return list
  })

  const detail = await json(`/api/v1/problems/${problem.slug}`)
  void detail

  const accepted = await step('submit', () =>
    json('/api/v1/submissions', {
      method: 'POST',
      headers: { ...authHeaders, 'Content-Type': 'application/json', 'Idempotency-Key': crypto.randomUUID() },
      body: JSON.stringify({
        problemId: problem.id,
        languageId: languages[0].id,
        sourceCode: 'print("hello")',
      }),
    }),
  )

  await step('poll status to terminal', async () => {
    const deadline = Date.now() + 60_000
    const terminal = new Set([
      'COMPLETED',
      'COMPILATION_ERROR',
      'TIME_LIMIT_EXCEEDED',
      'MEMORY_LIMIT_EXCEEDED',
      'RUNTIME_ERROR',
      'SYSTEM_ERROR',
    ])
    while (Date.now() < deadline) {
      const status = await json(`/api/v1/submissions/${accepted.submissionId}/status`, {
        headers: authHeaders,
      })
      if (terminal.has(status.status)) return status.status
      await new Promise((resolve) => setTimeout(resolve, 1000))
    }
    throw new Error('timed out waiting for a terminal status')
  })

  await step('history', async () => {
    const history = await json('/api/v1/submissions?page=0&size=5', { headers: authHeaders })
    if (!history.content || history.content.length === 0) throw new Error('history is empty')
    return `${history.totalElements} row(s)`
  })

  if (failed) {
    console.error('\nRESULT: FAIL')
    process.exit(1)
  }
  console.log('\nRESULT: PASS')
}

main().catch(() => {
  console.error('\nRESULT: FAIL')
  process.exit(1)
})
