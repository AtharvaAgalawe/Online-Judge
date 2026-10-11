import { describe, expect, it } from 'vitest'
import { BOILERPLATE, boilerplateFor, languageKeyFor } from './CodeEditor'

describe('languageKeyFor', () => {
  it('recognises the supported languages by name prefix', () => {
    expect(languageKeyFor('Java 21')).toBe('java')
    expect(languageKeyFor('Python 3.12')).toBe('python')
    expect(languageKeyFor('C 17')).toBe('c')
    expect(languageKeyFor('C++ 17')).toBe('cpp')
    expect(languageKeyFor('Go 1.23')).toBe('go')
  })

  it('maps JavaScript to javascript, not java', () => {
    expect(languageKeyFor('JavaScript (Node 22)')).toBe('javascript')
  })

  it('maps C++ to cpp, not c', () => {
    expect(languageKeyFor('C++ 17')).toBe('cpp')
  })

  it('returns null for languages without an editor integration', () => {
    expect(languageKeyFor('Rust 1.80')).toBeNull()
  })
})

describe('boilerplateFor', () => {
  it('returns the template matching the language', () => {
    expect(boilerplateFor('Java 21')).toBe(BOILERPLATE.java)
    expect(boilerplateFor('Python 3.12')).toBe(BOILERPLATE.python)
    expect(boilerplateFor('C 17')).toBe(BOILERPLATE.c)
    expect(boilerplateFor('C++ 17')).toBe(BOILERPLATE.cpp)
    expect(boilerplateFor('JavaScript (Node 22)')).toBe(BOILERPLATE.javascript)
    expect(boilerplateFor('Go 1.23')).toBe(BOILERPLATE.go)
  })

  it('provides a non-empty template for every supported language', () => {
    for (const template of Object.values(BOILERPLATE)) {
      expect(template.trim()).not.toBe('')
    }
  })

  it('returns an empty string for languages without a template', () => {
    expect(boilerplateFor('Rust 1.80')).toBe('')
  })
})
