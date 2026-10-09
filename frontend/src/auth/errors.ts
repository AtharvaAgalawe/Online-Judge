export class AutoLoginFailedError extends Error {
  constructor() {
    super('Account created, but automatic login failed.')
    this.name = 'AutoLoginFailedError'
  }
}
