// Exercises the admin Phase 12 flow against a running backend. The wrapper must
// provision ADMIN_USER (registered + ROLE_ADMIN) before invoking this.
// Usage: BASE_URL=http://localhost:8080 ADMIN_USER=admin123 node scripts/verify-phase12-live.mjs
const BASE_URL = process.env.BASE_URL ?? 'http://localhost:8080'
const ADMIN_USER = process.env.ADMIN_USER
const PASSWORD = 'secret123'

let failed = false
const pass = (msg) => console.log(`PASS: ${msg}`)
const fail = (msg, error) => { failed = true; console.error(`FAIL: ${msg} -> ${error}`) }

async function step(name, fn) {
  try {
    const detail = await fn()
    pass(name + (detail ? ` (${detail})` : ''))
    return detail
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

function auth(token, extra = {}) {
  return { headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' }, ...extra }
}

async function main() {
  if (!ADMIN_USER) throw new Error('ADMIN_USER env is required (wrapper provisions it)')
  const tokens = await step('admin login', () => json('/api/v1/auth/login', {
    method: 'POST', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username: ADMIN_USER, password: PASSWORD }),
  }))
  const admin = tokens.accessToken

  const regularName = `phase12u${Date.now()}`
  const regular = await step('register regular user', async () => {
    await json('/api/v1/auth/register', { method: 'POST', headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ username: regularName, email: `${regularName}@example.com`, password: PASSWORD }) })
    const r = await json('/api/v1/auth/login', { method: 'POST', headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ username: regularName, password: PASSWORD }) })
    return r.accessToken
  })

  await step('non-admin gets 403 on admin list', async () => {
    const response = await fetch(`${BASE_URL}/api/v1/admin/problems`, { headers: { Authorization: `Bearer ${regular}` } })
    if (response.status !== 403) throw new Error(`expected 403, got ${response.status}`)
    return '403'
  })

  const stamp = Date.now()
  const created = await step('create problem', () => json('/api/v1/admin/problems', auth(admin, {
    method: 'POST', body: JSON.stringify({ title: `Phase12 ${stamp}`, statement: 's', difficulty: 'EASY' }),
  })))

  await step('add hidden test case', () => json(`/api/v1/admin/problems/${created.id}/test-cases`, auth(admin, {
    method: 'POST', body: JSON.stringify({ input: '1', expectedOutput: '1', isSample: false }),
  })))

  await step('add sample test case', () => json(`/api/v1/admin/problems/${created.id}/test-cases`, auth(admin, {
    method: 'POST', body: JSON.stringify({ input: '2', expectedOutput: '2', isSample: true }),
  })))

  await step('publish', () => json(`/api/v1/admin/problems/${created.id}`, auth(admin, {
    method: 'PUT', body: JSON.stringify({ published: true }),
  })))

  await step('admin list shows published problem', async () => {
    const page = await json(`/api/v1/admin/problems?search=Phase12%20${stamp}`, auth(admin))
    if (!page.content?.some((p) => p.id === created.id && p.published)) throw new Error('not found / not published')
    return `${page.totalElements} row(s)`
  })

  await step('admin detail shows published', async () => {
    const detail = await json(`/api/v1/admin/problems/${created.id}`, auth(admin))
    if (detail.published !== true) throw new Error('published flag false')
    return detail.slug
  })

  await step('public list shows published problem', async () => {
    const page = await json(`/api/v1/problems?search=Phase12%20${stamp}`)
    if (!page.content?.some((p) => p.id === created.id)) throw new Error('absent from public list')
    return 'visible'
  })

  await step('admin submissions browser', () => json('/api/v1/admin/submissions?page=0&size=5', auth(admin)))

  await step('queue status', async () => {
    const status = await json('/api/v1/admin/system/queue-status', auth(admin))
    if (typeof status.queued !== 'number' || !Array.isArray(status.stale)) throw new Error('bad shape')
    return `queued=${status.queued}`
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
