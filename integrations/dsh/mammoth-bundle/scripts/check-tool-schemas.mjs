#!/usr/bin/env node
/**
 * Preflight check for every Mammoth tool schema.
 *
 * Run before reloading the bundle into a DSH profile (and after any edit to
 * tool-parameters.js, index.js or tool-schema.js):
 *
 *   node scripts/check-tool-schemas.mjs
 *
 * It has two parts:
 * 1. fixtures that prove the guard catches the 2026-11-13 regression
 *    (`required: true` inside a property of a raw schema) and other invalid forms;
 * 2. a real activation pass: import index.js, run `apply()` against a minimal
 *    fake Cordis context, capture the registered tool definitions and validate
 *    their `parameters` exactly as `registerTool()` does.
 *
 * The activation pass points at the real bundle-local `local/config.json` if it
 * exists, but exits before any Domain/Stub request could matter.
 */
import { pathToFileURL, fileURLToPath } from 'node:url'
import { dirname, join } from 'node:path'
import { assertToolParameters, validateToolParameters } from '../tool-schema.js'
import { TOOL_PARAMETERS } from '../tool-parameters.js'

const BUNDLE_DIR = dirname(dirname(fileURLToPath(import.meta.url)))

let failures = 0

/** @param {string} label @param {boolean} ok @param {string} [detail] */
function report(label, ok, detail = '') {
  console.log(`${ok ? 'PASS' : 'FAIL'} ${label}${detail === '' ? '' : ` :: ${detail}`}`)
  if (!ok) failures += 1
}

// ---------------------------------------------------------------------------
// 1. Fixtures: the guard must reject what the provider rejects.
// ---------------------------------------------------------------------------

const valid = {
  type: 'object',
  additionalProperties: false,
  properties: {
    title: { type: 'string', description: 'Title.' },
    priority: { type: 'string', enum: ['low', 'high'] },
  },
  required: ['title'],
}
report('fixture: valid raw schema', validateToolParameters(valid).length === 0)

const regression = {
  type: 'object',
  additionalProperties: false,
  properties: {
    title: { type: 'string', required: true, description: 'Title.' },
  },
}
const regressionViolations = validateToolParameters(regression)
report(
  'fixture: 2026-11-13 regression (property required: true)',
  regressionViolations.some(violation => violation.includes('required: true')),
  regressionViolations.join('; '),
)

const missingName = {
  type: 'object',
  additionalProperties: false,
  properties: { title: { type: 'string' } },
  required: ['summary'],
}
report(
  'fixture: required names an undeclared property',
  validateToolParameters(missingName).some(violation => violation.includes('not in properties')),
)

const badKeyword = {
  type: 'object',
  additionalProperties: false,
  properties: { when: { type: 'string', format: 'date-time' } },
}
report(
  'fixture: unsupported keyword is rejected',
  validateToolParameters(badKeyword).some(violation => violation.includes('not a supported keyword')),
)

const nonObjectRoot = { type: 'string' }
report(
  'fixture: non-object root is rejected',
  validateToolParameters(nonObjectRoot).some(violation => violation.includes('must be "object"')),
)

// ---------------------------------------------------------------------------
// 2. Declared schemas: every tool Mammoth registers must pass.
// ---------------------------------------------------------------------------

for (const [name, parameters] of Object.entries(TOOL_PARAMETERS)) {
  const violations = validateToolParameters(parameters)
  report(`declared schema: ${name}`, violations.length === 0, violations.join('; '))
}

// ---------------------------------------------------------------------------
// 3. Activation pass: capture the real definitions from `apply()`.
// ---------------------------------------------------------------------------

const captured = []
const fakeCtx = {
  tools: {
    register(definition) {
      captured.push(definition)
      return () => {}
    },
    get() {
      return undefined
    },
  },
  effect(fn) {
    const dispose = fn()
    return typeof dispose === 'function' ? dispose : () => {}
  },
  on() {
    return () => {}
  },
  systemPrompt: { section() { return () => {} } },
  webServer: { register() { return () => {} } },
  connection: { requestRejection() { return undefined } },
  approval: { async request() { return 'unavailable' } },
}

const plugin = await import(pathToFileURL(join(BUNDLE_DIR, 'index.js')).href)
await plugin.apply(fakeCtx)

report('activation: registers three tools', captured.length === 3, captured.map(definition => definition.name).join(','))
for (const definition of captured) {
  try {
    assertToolParameters(definition.name, definition.parameters)
    report(`activation: ${definition.name}`, true)
  } catch (error) {
    report(`activation: ${definition.name}`, false, error instanceof Error ? error.message : String(error))
  }
}
for (const name of Object.keys(TOOL_PARAMETERS)) {
  report(`activation: ${name} is registered`, captured.some(definition => definition.name === name))
}

console.log(failures === 0 ? '\nAll tool schema checks passed.' : `\n${failures} tool schema check(s) failed.`)
process.exit(failures === 0 ? 0 : 1)
