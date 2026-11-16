#!/usr/bin/env node
/**
 * Render the machine-local parts of the Mammoth spike bundle.
 *
 * Writes:
 *   integrations/dsh/mammoth-bundle/cordis.patch.yml   (git-ignored)
 *   <stateDir>/config.json                             (git-ignored)
 *   <stateDir>/credentials.json                        (git-ignored, 0600 best effort)
 *
 * The Domain Service runs on the spike data root; the read account is a
 * SYNTHETIC human account holding a project role (agent accounts cannot log in
 * by design). The password never enters the repository: pass it through
 * MAMMOTH_READ_PASSWORD or the prompt.
 *
 * Usage (PowerShell):
 *   node scripts/configure.mjs `
 *     --java "D:\Tools\Java\jdk-17.0.15\bin\java.exe" `
 *     --jar "E:\...\services\domain\target\mammoth-domain-service.jar" `
 *     --data-root "C:\Users\...\AppData\Local\Temp\mammoth-spike-data\root" `
 *     --agent-user-id "fb228729-..." `
 *     --domain-base http://127.0.0.1:18090 `
 *     --read-username spike_reviewer `
 *     --stub-url http://127.0.0.1:18091 `
 *     --state-dir "C:\Users\...\AppData\Local\Temp\mammoth-spike"
 */
import { mkdirSync, readFileSync, writeFileSync } from 'node:fs'
import { randomUUID } from 'node:crypto'
import { dirname, join, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const here = dirname(fileURLToPath(import.meta.url))
const bundleDir = resolve(here, '..')

function parseArgs(argv) {
  const out = {}
  for (let i = 0; i < argv.length; i++) {
    const arg = argv[i]
    if (!arg.startsWith('--')) continue
    const key = arg.slice(2)
    const next = argv[i + 1]
    if (next === undefined || next.startsWith('--')) out[key] = true
    else { out[key] = next; i++ }
  }
  return out
}

const args = parseArgs(process.argv.slice(2))
const required = ['java', 'jar', 'data-root', 'agent-user-id', 'domain-base', 'read-username', 'state-dir']
const missing = required.filter(key => typeof args[key] !== 'string')
if (missing.length > 0) {
  console.error(`missing required arguments: ${missing.map(key => `--${key}`).join(', ')}`)
  process.exit(2)
}

const password = process.env.MAMMOTH_READ_PASSWORD
if (!password) {
  console.error('missing MAMMOTH_READ_PASSWORD (synthetic spike account password)')
  process.exit(2)
}

const stateDir = resolve(args['state-dir'])
// ADR-0015 P2/P3: the Host only receives the *propose* token. The apply token belongs to the
// isolated approval service (see scripts/windows/approval-service-provision.ps1) and must never
// be written into this bundle config.
const agentWriteToken = process.env.MAMMOTH_AGENT_WRITE_TOKEN ?? ''
if (agentWriteToken === '') {
  console.warn('MAMMOTH_AGENT_WRITE_TOKEN is empty: the workbench can list proposals but the propose tool will fail closed')
}
mkdirSync(stateDir, { recursive: true })
mkdirSync(join(bundleDir, 'local'), { recursive: true })

const replacements = {
  '{{JAVA_EXE}}': resolve(args.java),
  '{{MAMMOTH_JAR}}': resolve(args.jar),
  '{{DATA_ROOT}}': resolve(args['data-root']),
  '{{AGENT_USER_ID}}': args['agent-user-id'],
}
let patch = readFileSync(join(bundleDir, 'cordis.patch.template.yml'), 'utf8')
for (const [token, value] of Object.entries(replacements)) patch = patch.split(token).join(value)
writeFileSync(join(bundleDir, 'cordis.patch.yml'), patch, 'utf8')

const config = {
  bundleVersion: JSON.parse(readFileSync(join(bundleDir, 'package.json'), 'utf8')).version,
  domain: {
    baseUrl: args['domain-base'],
    credentialsFile: join(stateDir, 'credentials.json'),
    timeoutMs: 8000,
  },
  // P3: Domain controlled-write API (propose + own read model only).
  agentWrite: {
    baseUrl: args['domain-base'],
    token: agentWriteToken,
    agentUserId: args['agent-user-id'],
    timeoutMs: 15000,
  },
  statePath: join(stateDir, 'state.json'),
  mcp: {
    serverName: 'mammoth_domain',
    agentUserId: args['agent-user-id'],
    toolNames: [
      'mcp__mammoth_domain__search_project_knowledge',
      'mcp__mammoth_domain__get_knowledge_document',
      'mcp__mammoth_domain__get_requirement_context',
    ],
  },
  spike: {
    label: 'MAMMOTH-DSH-DESKTOP-SPIKE',
    syntheticDataOnly: true,
  },
}
writeFileSync(join(stateDir, 'config.json'), `${JSON.stringify(config, null, 2)}\n`, 'utf8')
writeFileSync(join(bundleDir, 'local', 'config.json'), `${JSON.stringify(config, null, 2)}\n`, 'utf8')
writeFileSync(config.domain.credentialsFile, `${JSON.stringify({
  username: args['read-username'],
  password,
}, null, 2)}\n`, { encoding: 'utf8', mode: 0o600 })

console.log('rendered:')
console.log(`  ${join(bundleDir, 'cordis.patch.yml')}`)
console.log(`  ${join(bundleDir, 'local', 'config.json')}`)
console.log(`  ${config.domain.credentialsFile}`)
console.log(`  ${config.statePath}`)
console.log('')
console.log('Note: the Host holds only the propose token. Approval happens in a human Domain browser')
console.log('session; the isolated approval service (scripts/windows/approval-service-provision.ps1)')
console.log('holds the apply token and applies approved proposals. No stub/adapter is required.')
