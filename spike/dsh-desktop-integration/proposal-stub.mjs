#!/usr/bin/env node
/**
 * Mammoth Proposal Adapter — SPIKE-ONLY isolated stub.
 *
 * Why this exists: the Mammoth Domain Service (product code) has NO agent-facing
 * propose/write API and agent accounts cannot log in by design. This stub
 * therefore holds the *pending* proposals an agent creates, and only after a
 * person clicks Approve in the Mammoth workbench does it perform the formal
 * Domain write — with a HUMAN Domain account. That keeps the authoritative
 * business change inside the Domain Service below a human identity, while the
 * agent identity only ever creates pending records here.
 *
 * This file is NOT product code and is never installed as part of the bundle.
 * It listens on loopback only and stores its state in a temp directory.
 *
 * Env:
 *   MAMMOTH_DOMAIN_BASE          default http://127.0.0.1:18090
 *   MAMMOTH_APPROVER_USERNAME    synthetic human Domain account (required)
 *   MAMMOTH_APPROVER_PASSWORD    its password (required; never logged)
 *   MAMMOTH_STUB_PORT            default 18091
 *   MAMMOTH_STUB_STATE           default %TEMP%/mammoth-spike/proposals.json
 *   MAMMOTH_STUB_MAX_BODY_BYTES  default 65536
 */
import { createServer } from 'node:http'
import { mkdirSync, readFileSync, appendFileSync, writeFileSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { tmpdir } from 'node:os'

const DOMAIN_BASE = (process.env.MAMMOTH_DOMAIN_BASE ?? 'http://127.0.0.1:18090').replace(/\/+$/, '')
const APPROVER_USERNAME = process.env.MAMMOTH_APPROVER_USERNAME ?? ''
const APPROVER_PASSWORD = process.env.MAMMOTH_APPROVER_PASSWORD ?? ''
const PORT = Number(process.env.MAMMOTH_STUB_PORT ?? 18091)
const HOST = '127.0.0.1'
const STATE_PATH = process.env.MAMMOTH_STUB_STATE ?? join(tmpdir(), 'mammoth-spike', 'proposals.json')
const AUDIT_PATH = join(dirname(STATE_PATH), 'proposal-audit.jsonl')
const MAX_BODY = Number(process.env.MAMMOTH_STUB_MAX_BODY_BYTES ?? 65536)

if (APPROVER_USERNAME === '' || APPROVER_PASSWORD === '') {
  console.error('[proposal-stub] MAMMOTH_APPROVER_USERNAME and MAMMOTH_APPROVER_PASSWORD are required')
  process.exit(2)
}

mkdirSync(dirname(STATE_PATH), { recursive: true })

/** @typedef {{id: string, type: string, projectId: string, status: string, title: string, summary: string, priority: string, rationale: string|null, proposer: object, payload: object, dshApproval: string|null, createdAt: string, decidedAt: string|null, approver: string|null, domainObject: object|null, domainError: string|null}} ProposalRecord */

/** Load the stub's proposal store (spike-only, not business authority). */
function loadStore() {
  try {
    const parsed = JSON.parse(readFileSync(STATE_PATH, 'utf8'))
    if (parsed !== null && typeof parsed === 'object' && Array.isArray(parsed.proposals)) return parsed
  } catch {
    // missing or malformed store starts empty
  }
  return { proposals: [] }
}

const store = loadStore()

/** Persist the proposal store. */
function saveStore() {
  writeFileSync(STATE_PATH, `${JSON.stringify(store, null, 2)}\n`, 'utf8')
}

/** Append one audit line (no credentials, no session tokens). */
function audit(action, detail) {
  appendFileSync(AUDIT_PATH, `${JSON.stringify({ at: new Date().toISOString(), action, detail })}\n`, 'utf8')
}

// ---------------------------------------------------------------------------
// Domain write client (human approver identity)
// ---------------------------------------------------------------------------
/**
 * Log in as the human approver and return a cookie plus CSRF token.
 * The password stays in this process's environment; it is never logged or sent
 * to the workbench.
 */
async function domainLogin() {
  const login = await fetch(`${DOMAIN_BASE}/api/auth/login`, {
    method: 'POST',
    headers: { 'content-type': 'application/json', accept: 'application/json' },
    body: JSON.stringify({ username: APPROVER_USERNAME, password: APPROVER_PASSWORD }),
    signal: AbortSignal.timeout(10_000),
  })
  if (!login.ok) throw new Error(`approver login failed: HTTP ${login.status}`)
  const cookies = typeof login.headers.getSetCookie === 'function'
    ? login.headers.getSetCookie()
    : [login.headers.get('set-cookie') ?? '']
  const session = cookies.map(value => value.split(';', 1)[0]).find(value => value.startsWith('MAMMOTH_SESSION='))
  if (session === undefined) throw new Error('approver login returned no session cookie')
  const csrf = await fetch(`${DOMAIN_BASE}/api/auth/csrf`, {
    headers: { accept: 'application/json', cookie: session },
    signal: AbortSignal.timeout(10_000),
  })
  if (!csrf.ok) throw new Error(`CSRF request failed: HTTP ${csrf.status}`)
  const csrfBody = await csrf.json()
  const csrfCookies = typeof csrf.headers.getSetCookie === 'function'
    ? csrf.headers.getSetCookie()
    : [csrf.headers.get('set-cookie') ?? '']
  const xsrfCookie = csrfCookies.map(value => value.split(';', 1)[0]).find(value => value.startsWith('XSRF-TOKEN='))
  const cookieHeader = [session, xsrfCookie].filter(Boolean).join('; ')
  return { cookie: cookieHeader, token: csrfBody.token }
}

/**
 * Perform the authoritative Domain write for an approved proposal.
 * Only `work-item.create` is implemented: it uses the existing human-facing
 * Domain endpoint `POST /api/projects/{projectId}/work-items`.
 * @param {ProposalRecord} proposal
 * @param {{cookie: string, token: string}} session
 */
async function performDomainWrite(proposal, session) {
  if (proposal.type !== 'work-item.create') {
    throw new Error(`unsupported proposal type for the spike: ${proposal.type}`)
  }
  const response = await fetch(`${DOMAIN_BASE}/api/projects/${encodeURIComponent(proposal.projectId)}/work-items`, {
    method: 'POST',
    headers: {
      accept: 'application/json',
      'content-type': 'application/json',
      cookie: session.cookie,
      'x-xsrf-token': session.token,
    },
    body: JSON.stringify({
      title: proposal.payload.title ?? proposal.title,
      summary: proposal.payload.summary ?? proposal.summary ?? '',
      priority: proposal.payload.priority ?? proposal.priority ?? 'normal',
      sourceNote: `mammoth-dsh-proposal:${proposal.id}`,
    }),
    signal: AbortSignal.timeout(15_000),
  })
  const text = await response.text()
  if (!response.ok) {
    throw new Error(`Domain work-item write failed: HTTP ${response.status} ${text.slice(0, 300)}`)
  }
  let created = null
  try { created = text === '' ? null : JSON.parse(text) } catch { created = { raw: text.slice(0, 500) } }
  return {
    id: created?.id ?? null,
    itemNo: created?.itemNo ?? null,
    status: created?.status ?? null,
    raw: created,
  }
}

// ---------------------------------------------------------------------------
// HTTP plumbing
// ---------------------------------------------------------------------------
/** @param {import('node:http').ServerResponse} res */
function sendJson(res, status, value) {
  const body = JSON.stringify(value)
  res.writeHead(status, {
    'content-type': 'application/json; charset=utf-8',
    'cache-control': 'no-store',
    'access-control-allow-origin': '*',
  })
  res.end(body)
}

/** @param {import('node:http').IncomingMessage} req */
async function readBody(req) {
  const chunks = []
  let size = 0
  for await (const chunk of req) {
    size += chunk.length
    if (size > MAX_BODY) throw new Error('request body too large')
    chunks.push(chunk)
  }
  if (size === 0) return {}
  const value = JSON.parse(Buffer.concat(chunks).toString('utf8'))
  if (value === null || typeof value !== 'object' || Array.isArray(value)) throw new Error('body must be a JSON object')
  return value
}

/** @param {string} id */
function findProposal(id) {
  return store.proposals.find(proposal => proposal.id === id)
}

const server = createServer(async (req, res) => {
  const url = new URL(String(req.url), `http://${HOST}:${PORT}`)
  const path = url.pathname
  try {
    if (req.method === 'GET' && path === '/health') {
      sendJson(res, 200, {
        ok: true,
        service: 'mammoth-proposal-adapter',
        spike: true,
        domainBase: DOMAIN_BASE,
        approver: APPROVER_USERNAME,
        proposalCount: store.proposals.length,
        pending: store.proposals.filter(proposal => proposal.status === 'pending').length,
      })
      return
    }
    if (req.method === 'GET' && path === '/proposals') {
      sendJson(res, 200, store.proposals.slice().sort((a, b) => String(b.createdAt).localeCompare(String(a.createdAt))))
      return
    }
    if (req.method === 'POST' && path === '/proposals') {
      const body = await readBody(req)
      if (typeof body.projectId !== 'string' || typeof body.title !== 'string' || typeof body.summary !== 'string') {
        sendJson(res, 400, { error: 'bad-request', message: 'projectId, title and summary are required' })
        return
      }
      const proposer = body.proposer !== null && typeof body.proposer === 'object' ? body.proposer : {}
      if (proposer.kind !== 'agent') {
        sendJson(res, 403, { error: 'proposer-must-be-agent', message: 'only the agent tool may create proposals' })
        return
      }
      /** @type {ProposalRecord} */
      const proposal = {
        id: typeof body.id === 'string' && body.id !== '' ? body.id : crypto.randomUUID(),
        type: typeof body.type === 'string' ? body.type : 'work-item.create',
        projectId: body.projectId,
        status: 'pending',
        title: body.title,
        summary: body.summary,
        priority: typeof body.priority === 'string' ? body.priority : 'normal',
        rationale: typeof body.rationale === 'string' ? body.rationale : null,
        proposer,
        payload: body.payload !== null && typeof body.payload === 'object' ? body.payload : {},
        dshApproval: typeof body.dshApproval === 'string' ? body.dshApproval : null,
        createdAt: new Date().toISOString(),
        decidedAt: null,
        approver: null,
        domainObject: null,
        domainError: null,
      }
      store.proposals.push(proposal)
      saveStore()
      audit('proposal.created', { id: proposal.id, projectId: proposal.projectId, title: proposal.title, proposer: proposer.agentUserId ?? null })
      sendJson(res, 201, proposal)
      return
    }
    const approveMatch = /^\/proposals\/([^/]+)\/approve$/.exec(path)
    if (req.method === 'POST' && approveMatch !== null) {
      const id = decodeURIComponent(approveMatch[1])
      const body = await readBody(req)
      const proposal = findProposal(id)
      if (proposal === undefined) { sendJson(res, 404, { error: 'not-found', message: `unknown proposal ${id}` }); return }
      if (proposal.status !== 'pending') { sendJson(res, 409, { error: 'not-pending', message: `proposal ${id} is already ${proposal.status}` }); return }
      const approver = typeof body.approver === 'string' && body.approver !== '' ? body.approver : 'local-desktop-human'
      // Four-eyes: the approver is a human identity, never the proposing agent.
      if (approver === proposal.proposer?.agentUserId) {
        sendJson(res, 403, { error: 'four-eyes-violation', message: 'the proposer cannot approve their own proposal' })
        return
      }
      // The formal write happens here, only after this human approval action.
      try {
        const session = await domainLogin()
        const domainObject = await performDomainWrite(proposal, session)
        proposal.status = 'approved'
        proposal.decidedAt = new Date().toISOString()
        proposal.approver = approver
        proposal.domainObject = domainObject
        proposal.domainError = null
        saveStore()
        audit('proposal.approved', { id, approver, domainObject })
        sendJson(res, 200, proposal)
      } catch (error) {
        proposal.domainError = error instanceof Error ? error.message : String(error)
        saveStore()
        audit('proposal.approve-failed', { id, approver, error: proposal.domainError })
        sendJson(res, 502, { error: 'domain-write-failed', message: proposal.domainError, proposal })
      }
      return
    }
    const rejectMatch = /^\/proposals\/([^/]+)\/reject$/.exec(path)
    if (req.method === 'POST' && rejectMatch !== null) {
      const id = decodeURIComponent(rejectMatch[1])
      const body = await readBody(req)
      const proposal = findProposal(id)
      if (proposal === undefined) { sendJson(res, 404, { error: 'not-found', message: `unknown proposal ${id}` }); return }
      if (proposal.status !== 'pending') { sendJson(res, 409, { error: 'not-pending', message: `proposal ${id} is already ${proposal.status}` }); return }
      proposal.status = 'rejected'
      proposal.decidedAt = new Date().toISOString()
      proposal.approver = typeof body.approver === 'string' ? body.approver : 'local-desktop-human'
      saveStore()
      audit('proposal.rejected', { id, approver: proposal.approver })
      sendJson(res, 200, proposal)
      return
    }
    if (req.method === 'GET' && path === '/audit') {
      let lines = []
      try { lines = readFileSync(AUDIT_PATH, 'utf8').trim().split('\n').filter(Boolean).map(line => JSON.parse(line)) } catch { lines = [] }
      sendJson(res, 200, { audit: lines.slice(-200) })
      return
    }
    sendJson(res, 404, { error: 'not-found', message: `no route for ${req.method} ${path}` })
  } catch (error) {
    sendJson(res, 500, { error: 'internal', message: error instanceof Error ? error.message : String(error) })
  }
})

server.listen(PORT, HOST, () => {
  console.log(`[proposal-stub] listening on http://${HOST}:${PORT}`)
  console.log(`[proposal-stub] domain=${DOMAIN_BASE} approver=${APPROVER_USERNAME} state=${STATE_PATH}`)
  audit('stub.started', { port: PORT, domain: DOMAIN_BASE })
})
