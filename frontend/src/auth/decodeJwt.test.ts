import { describe, expect, it } from 'vitest'
import { decodeJwt } from './decodeJwt'

function encodeSegment(value: object): string {
  return btoa(JSON.stringify(value)).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '')
}

describe('decodeJwt', () => {
  it('extracts sub, uid, and roles from the payload', () => {
    const token = `header.${encodeSegment({ sub: 'alice', uid: 7, roles: ['ROLE_USER'] })}.sig`
    expect(decodeJwt(token)).toEqual({ id: 7, username: 'alice', roles: ['ROLE_USER'] })
  })

  it('defaults roles to an empty array when absent', () => {
    const token = `header.${encodeSegment({ sub: 'bob', uid: 3 })}.sig`
    expect(decodeJwt(token)).toEqual({ id: 3, username: 'bob', roles: [] })
  })

  it('returns null for malformed tokens', () => {
    expect(decodeJwt('not-a-jwt')).toBeNull()
  })
})
