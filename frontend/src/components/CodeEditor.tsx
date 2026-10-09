import CodeMirror from '@uiw/react-codemirror'
import { java } from '@codemirror/lang-java'
import { python } from '@codemirror/lang-python'
import type { Extension } from '@codemirror/state'

export const BOILERPLATE: Record<'java' | 'python', string> = {
  java: 'public class Main {\n    public static void main(String[] args) {\n        \n    }\n}\n',
  python: 'def main():\n    pass\n\n\nif __name__ == "__main__":\n    main()\n',
}

export function extensionFor(languageName: string): Extension[] {
  const name = languageName.toLowerCase()
  if (name.startsWith('java')) return [java()]
  if (name.startsWith('python')) return [python()]
  return []
}

export function CodeEditor({
  value,
  languageName,
  onChange,
  readOnly = false,
}: {
  value: string
  languageName: string
  onChange: (value: string) => void
  readOnly?: boolean
}) {
  return (
    <CodeMirror
      value={value}
      height="320px"
      readOnly={readOnly}
      extensions={extensionFor(languageName)}
      onChange={(next) => onChange(next)}
    />
  )
}
