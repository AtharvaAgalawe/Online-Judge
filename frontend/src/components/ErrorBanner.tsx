import { messageOf } from '../helpers/apiError'

export function ErrorBanner({ error }: { error: unknown }) {
  return (
    <div role="alert" className="error-banner">
      {messageOf(error)}
    </div>
  )
}
