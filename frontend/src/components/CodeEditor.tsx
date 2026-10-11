import CodeMirror from '@uiw/react-codemirror'
import { cpp } from '@codemirror/lang-cpp'
import { java } from '@codemirror/lang-java'
import { javascript } from '@codemirror/lang-javascript'
import { go } from '@codemirror/lang-go'
import { python } from '@codemirror/lang-python'
import type { Extension } from '@codemirror/state'

export type LanguageKey = 'java' | 'python' | 'c' | 'cpp' | 'javascript' | 'go'

export const BOILERPLATE: Record<LanguageKey, string> = {
  java: 'public class Main {\n    public static void main(String[] args) {\n        \n    }\n}\n',
  python: 'def main():\n    pass\n\n\nif __name__ == "__main__":\n    main()\n',
  c: '#include <stdio.h>\n\nint main(void) {\n    printf("\\n");\n    return 0;\n}\n',
  cpp: '#include <iostream>\n\nint main() {\n    std::cout << std::endl;\n    return 0;\n}\n',
  javascript: 'function main() {\n  \n}\n\nmain()\n',
  go: 'package main\n\nimport "fmt"\n\nfunc main() {\n\tfmt.Println()\n}\n',
}

/**
 * Maps a language catalog name to its editor key. Order matters: JavaScript must be
 * checked before Java, and C++ before C, because their prefixes overlap.
 */
export function languageKeyFor(languageName: string): LanguageKey | null {
  const name = languageName.toLowerCase()
  if (name.startsWith('javascript') || name.startsWith('node')) return 'javascript'
  if (name.startsWith('java')) return 'java'
  if (name.startsWith('python')) return 'python'
  if (name.startsWith('c++') || name.startsWith('cpp')) return 'cpp'
  if (name.startsWith('c ') || name === 'c') return 'c'
  if (name.startsWith('go ')) return 'go'
  return null
}

export function boilerplateFor(languageName: string): string {
  const key = languageKeyFor(languageName)
  return key === null ? '' : BOILERPLATE[key]
}

export function extensionFor(languageName: string): Extension[] {
  switch (languageKeyFor(languageName)) {
    case 'java':
      return [java()]
    case 'python':
      return [python()]
    case 'c':
    case 'cpp':
      return [cpp()]
    case 'javascript':
      return [javascript()]
    case 'go':
      return [go()]
    default:
      return []
  }
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
    <div className="code-editor">
      <CodeMirror
        value={value}
        height="320px"
        readOnly={readOnly}
        extensions={extensionFor(languageName)}
        onChange={(next) => onChange(next)}
      />
    </div>
  )
}
