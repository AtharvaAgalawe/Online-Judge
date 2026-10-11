import { Link } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'

type Token = [className: string | null, text: string]

const SNIPPET: Token[][] = [
  [['tok-com', '// two-sum — find two indices that add up to target']],
  [['tok-key', 'import'], [null, ' java.util.*;']],
  [],
  [
    ['tok-key', 'public'],
    [null, ' '],
    ['tok-key', 'class'],
    [null, ' '],
    ['tok-type', 'Main'],
    [null, ' {'],
  ],
  [
    [null, '    '],
    ['tok-key', 'public'],
    [null, ' '],
    ['tok-key', 'static'],
    [null, ' '],
    ['tok-key', 'void'],
    [null, ' '],
    ['tok-type', 'main'],
    [null, '('],
    ['tok-type', 'String'],
    [null, '[] args) {'],
  ],
  [
    [null, '        '],
    ['tok-type', 'Scanner'],
    [null, ' sc = '],
    ['tok-key', 'new'],
    [null, ' '],
    ['tok-type', 'Scanner'],
    [null, '('],
    ['tok-type', 'System'],
    [null, '.in);'],
  ],
  [[null, '        '], ['tok-key', 'int'], [null, ' target = sc.nextInt();']],
  [
    [null, '        '],
    ['tok-key', 'int'],
    [null, '[] nums = { '],
    ['tok-num', '2'],
    [null, ', '],
    ['tok-num', '7'],
    [null, ', '],
    ['tok-num', '11'],
    [null, ', '],
    ['tok-num', '15'],
    [null, ' };'],
  ],
  [
    [null, '        '],
    ['tok-type', 'Map'],
    [null, '<'],
    ['tok-type', 'Integer'],
    [null, ', '],
    ['tok-type', 'Integer'],
    [null, '> seen = '],
    ['tok-key', 'new'],
    [null, ' '],
    ['tok-type', 'HashMap'],
    [null, '<>();'],
  ],
  [
    [null, '        '],
    ['tok-key', 'for'],
    [null, ' ('],
    ['tok-key', 'int'],
    [null, ' i = '],
    ['tok-num', '0'],
    [null, '; i < nums.length; i++) {'],
  ],
  [[null, '            '], ['tok-key', 'int'], [null, ' need = target - nums[i];']],
  [[null, '            '], ['tok-key', 'if'], [null, ' (seen.containsKey(need)) {']],
  [
    [null, '                '],
    ['tok-type', 'System'],
    [null, '.out.println(seen.get(need) + '],
    ['tok-str', '" "'],
    [null, ' + i);'],
  ],
  [[null, '                '], ['tok-key', 'return'], [null, ';']],
  [[null, '            '], [null, '}']],
  [[null, '            '], [null, 'seen.put(nums[i], i);']],
  [[null, '        '], [null, '}']],
  [[null, '    '], [null, '}']],
  [[null, '}']],
]

export function LandingPage() {
  const { status } = useAuth()
  const authenticated = status === 'authenticated'

  return (
    <div className="landing">
      <div className="tape" />
      <section className="hero">
        <h1 className="hero__title">
          {'Where Your Code Meets Its Judge'}{' '}
          <span className="hero__title-faded">{"And 'TLE' Wasn't Your Fault"}</span>
        </h1>
        <p className="hero__sub">
          Compiled, sandboxed, and run in isolated containers — verdicts in seconds, not excuses.
        </p>
        <div className="hero__actions">
          <Link to="/problems" className="btn btn--solid">
            Browse Problems
          </Link>
          {authenticated ? (
            <Link to="/submissions" className="btn btn--outline">
              My Submissions
            </Link>
          ) : (
            <Link to="/register" className="btn btn--outline">
              Create Account
            </Link>
          )}
        </div>
        <div className="code-window">
          <div className="code-window__chrome">
            <span className="code-window__dot code-window__dot--red" />
            <span className="code-window__dot code-window__dot--amber" />
            <span className="code-window__dot code-window__dot--green" />
            <span className="code-window__title">Main.java — two-sum</span>
          </div>
          <div className="code-window__body">
            <div className="code-window__tree">
              <div className="code-window__tree-title">Explorer</div>
              <ul>
                <li>online-judge/</li>
                <li>├─ src/</li>
                <li>│  ├─ <span>Main.java</span></li>
                <li>│  └─ <span>solution.py</span></li>
                <li>└─ tests/</li>
              </ul>
            </div>
            <pre className="code-window__code">
              {SNIPPET.map((line, lineIndex) => (
                <span className="code-window__line" key={lineIndex}>
                  {line.map(([className, text], tokenIndex) =>
                    className ? (
                      <span key={tokenIndex} className={className}>
                        {text}
                      </span>
                    ) : (
                      <span key={tokenIndex}>{text}</span>
                    ),
                  )}
                  {'\n'}
                </span>
              ))}
            </pre>
          </div>
        </div>
      </section>
      <div className="tape" />
    </div>
  )
}
