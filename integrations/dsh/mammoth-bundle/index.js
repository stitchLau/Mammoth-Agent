/**
 * Mammoth Host plugin — the authoritative side of the Mammoth DSH Desktop bundle.
 *
 * Scope (spike, not product code):
 * - read the synthetic Mammoth Domain Service over its REST API (Host-side HTTP,
 *   no browser CORS involvement) with a project-scoped read account;
 * - expose those reads to the Mammoth Web Client half of this bundle through an
 *   authenticated same-origin web route behind `ctx.connection.requestRejection`;
 * - expose model-facing tools: project context / project list (read) and
 *   `mammoth_propose_work_item` (a pending proposal, never a direct write);
 * - bind DSH Sessions to a Mammoth project and inject that project's card into
 *   the system prompt of the bound session;
 * - keep the authoritative write behind human approval: the proposal stub applies
 *   the change with a *human* Domain session only after a person approves in the
 *   workbench.
 *
 * The plugin has no third-party JS dependencies and no build step on purpose: a
 * profile-linked bundle must not need its own node_modules for this spike.
 */
import { randomUUID } from 'node:crypto'
import { mkdir, readFile, writeFile } from 'node:fs/promises'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'
import { TOOL_PARAMETERS } from './tool-parameters.js'
import { assertToolParameters } from './tool-schema.js'

/** Cordis plugin name. */
export const name = 'mammoth-host'

/** Services that must exist before this plugin activates. */
export const inject = ['tools', 'webServer', 'connection', 'systemPrompt', 'approval']

const BUNDLE_DIR = dirname(fileURLToPath(import.meta.url))
const PLUGIN_ID = '@mammoth/dsh-bundle'
const API_PREFIX = '/mammoth/api'
const STATE_VERSION = 1
const CARD_TTL_MS = 20_000

// ---------------------------------------------------------------------------
// Small utilities
// ---------------------------------------------------------------------------

/** @param {unknown} value */
const isRecord = value => typeof value === 'object' && value !== null && !Array.isArray(value)

/**
 * Read one JSON file, returning `fallback` when it is missing or malformed.
 * @param {string} path
 * @param {unknown} fallback
 */
async function readJsonFile(path, fallback) {
  try {
    return JSON.parse(await readFile(path, 'utf8'))
  } catch {
    return fallback
  }
}

/** @param {string} path */
async function ensureDir(path) {
  await mkdir(path, { recursive: true })
}

/**
 * Error whose message is safe to show to the model or the workbench page.
 * @param {string} code
 * @param {string} message
 * @param {unknown} [cause]
 */
function mammothError(code, message, cause) {
  const error = new Error(message)
  error.name = 'MammothError'
  error.code = code
  if (cause !== undefined) error.cause = cause
  return error
}

/** @param {unknown} error */
function describeError(error) {
  if (error instanceof Error) {
    const code = typeof error.code === 'string' ? `${error.code}: ` : ''
    return `${code}${error.message}`
  }
  return String(error)
}

// ---------------------------------------------------------------------------
// Domain Service client (Host-side HTTP; no browser, no CORS)
// ---------------------------------------------------------------------------

/** One cookie-pair extractor for Set-Cookie headers on modern and older Node. */
function setCookieValues(response) {
  if (typeof response.headers.getSetCookie === 'function') return response.headers.getSetCookie()
  const raw = response.headers.get('set-cookie')
  return raw === null ? [] : [raw]
}

/**
 * Minimal cookie-jar client for the Mammoth Domain Service.
 * Reads use one synthetic human project member account; the password lives in a
 * machine-local file outside the repository and never reaches the client or the
 * model. Agent accounts cannot log in by design, so the read identity must be a
 * human project member in this spike.
 */
class DomainSession {
  /** @param {{baseUrl: string, credentialsFile: string, timeoutMs: number}} config */
  constructor(config) {
    this.baseUrl = config.baseUrl.replace(/\/+$/, '')
    this.credentialsFile = config.credentialsFile
    this.timeoutMs = config.timeoutMs
    this.cookie = null
    this.identity = null
    this.inflightLogin = null
    this.lastError = null
  }

  /** Log in with the machine-local synthetic read account; cookies stay in memory. */
  async login() {
    const credentials = await readJsonFile(this.credentialsFile, null)
    if (!isRecord(credentials) || typeof credentials.username !== 'string' || typeof credentials.password !== 'string') {
      throw mammothError('MAMMOTH_CREDENTIALS_MISSING',
        `Domain read credentials are missing: ${this.credentialsFile}`)
    }
    const response = await fetch(`${this.baseUrl}/api/auth/login`, {
      method: 'POST',
      headers: { 'content-type': 'application/json', accept: 'application/json' },
      body: JSON.stringify({ username: credentials.username, password: credentials.password }),
      signal: AbortSignal.timeout(this.timeoutMs),
    })
    if (!response.ok) {
      throw mammothError('MAMMOTH_DOMAIN_LOGIN_FAILED',
        `Domain login failed for ${credentials.username}: HTTP ${response.status}`)
    }
    const cookies = setCookieValues(response).map(value => value.split(';', 1)[0]).filter(Boolean)
    const sessionCookie = cookies.find(value => value.startsWith('MAMMOTH_SESSION='))
    if (sessionCookie === undefined) throw mammothError('MAMMOTH_DOMAIN_LOGIN_FAILED', 'Domain login returned no session cookie')
    this.cookie = sessionCookie
    const me = await response.json()
    this.identity = isRecord(me) ? { id: me.id, username: me.username, displayName: me.displayName } : null
    this.lastError = null
    return this.identity
  }

  /** Ensure one live Domain session, retrying a failed/expired session once on demand. */
  async ensure() {
    if (this.cookie !== null) return
    this.inflightLogin ??= this.login().finally(() => { this.inflightLogin = null })
    return this.inflightLogin
  }

  /**
   * Authenticated GET returning parsed JSON.
   * @param {string} path absolute Domain path below the origin, e.g. `/api/projects`
   * @param {{retry?: boolean}} [options]
   */
  async get(path, options = {}) {
    await this.ensure()
    const response = await fetch(`${this.baseUrl}${path}`, {
      headers: { accept: 'application/json', cookie: this.cookie ?? '' },
      signal: AbortSignal.timeout(this.timeoutMs),
    })
    if (response.status === 401 && options.retry !== false) {
      this.cookie = null
      return this.get(path, { retry: false })
    }
    if (!response.ok) {
      const text = await response.text().catch(() => '')
      throw mammothError('MAMMOTH_DOMAIN_HTTP_ERROR',
        `Domain GET ${path} failed: HTTP ${response.status} ${text.slice(0, 200)}`)
    }
    return response.json()
  }

  /** Authenticated GET for a non-JSON body (kept for completeness). */
  async getText(path) {
    await this.ensure()
    const response = await fetch(`${this.baseUrl}${path}`, {
      headers: { accept: 'application/json', cookie: this.cookie ?? '' },
      signal: AbortSignal.timeout(this.timeoutMs),
    })
    if (!response.ok) throw mammothError('MAMMOTH_DOMAIN_HTTP_ERROR', `Domain GET ${path} failed: HTTP ${response.status}`)
    return response.text()
  }

  /** Probe the Domain health endpoint through the read session. */
  async health() {
    try {
      const value = await this.get('/api/health')
      return { ok: true, value }
    } catch (error) {
      return { ok: false, error: describeError(error) }
    }
  }
}

// ---------------------------------------------------------------------------
// Proposal stub client (spike-only loopback service)
// ---------------------------------------------------------------------------

/** Client for the isolated proposal stub that performs the approved Domain write. */
class ProposalStubClient {
  /** @param {{baseUrl: string, token: string}} config */
  constructor(config) {
    this.baseUrl = config.baseUrl.replace(/\/+$/, '')
    this.token = config.token
  }

  /** @param {string} path @param {{method?: string, body?: unknown}} [options] */
  async request(path, options = {}) {
    const response = await fetch(`${this.baseUrl}${path}`, {
      method: options.method ?? 'GET',
      headers: {
        accept: 'application/json',
        ...(this.token === '' ? {} : { 'x-mammoth-stub-token': this.token }),
        ...(options.body === undefined ? {} : { 'content-type': 'application/json' }),
      },
      ...(options.body === undefined ? {} : { body: JSON.stringify(options.body) }),
      signal: AbortSignal.timeout(15_000),
    })
    const text = await response.text()
    let payload = null
    try { payload = text === '' ? null : JSON.parse(text) } catch { payload = { message: text.slice(0, 400) } }
    if (!response.ok) {
      throw mammothError('MAMMOTH_STUB_HTTP_ERROR',
        `Proposal stub ${options.method ?? 'GET'} ${path} failed: HTTP ${response.status} ${text.slice(0, 200)}`)
    }
    return payload
  }

  /** @param {Record<string, unknown>} proposal */
  create(proposal) {
    return this.request('/proposals', { method: 'POST', body: proposal })
  }

  /** @param {string} id @param {string} approver */
  approve(id, approver) {
    // The adapter requires the decision to echo the exact pending proposal id
    // it read; forward it along with the human approver identity.
    return this.request(`/proposals/${encodeURIComponent(id)}/approve`, {
      method: 'POST',
      body: { approver, confirm: id },
    })
  }

  /** @param {string} id @param {string} approver */
  reject(id, approver) {
    return this.request(`/proposals/${encodeURIComponent(id)}/reject`, {
      method: 'POST',
      body: { approver, confirm: id },
    })
  }

  list() {
    return this.request('/proposals')
  }

  health() {
    return this.request('/health')
  }
}

// ---------------------------------------------------------------------------
// Host plugin body
// ---------------------------------------------------------------------------

/**
 * Activate the Mammoth Host plugin.
 * @param {import('@deepseek-ai/cordis').Context} ctx
 */
export async function apply(ctx) {
  const configPath = join(BUNDLE_DIR, 'local', 'config.json')
  const config = await readJsonFile(configPath, null)
  const configError = isRecord(config) ? null : `Mammoth bundle config missing: ${configPath}`
  const statePath = isRecord(config) && typeof config.statePath === 'string'
    ? config.statePath
    : join(BUNDLE_DIR, 'local', 'state.json')

  const domain = isRecord(config)
    ? new DomainSession({
      baseUrl: typeof config.domain?.baseUrl === 'string' ? config.domain.baseUrl : 'http://127.0.0.1:18090',
      credentialsFile: typeof config.domain?.credentialsFile === 'string' ? config.domain.credentialsFile : '',
      timeoutMs: typeof config.domain?.timeoutMs === 'number' ? config.domain.timeoutMs : 8000,
    })
    : null
  const stub = isRecord(config)
    ? new ProposalStubClient({
      baseUrl: typeof config.proposalStub?.baseUrl === 'string' ? config.proposalStub.baseUrl : 'http://127.0.0.1:18091',
      token: typeof config.proposalStub?.token === 'string' ? config.proposalStub.token : '',
    })
    : null
  const mcp = isRecord(config) && isRecord(config.mcp) ? config.mcp : { serverName: 'mammoth_domain', toolNames: [] }
  const startedAt = new Date().toISOString()

  // ---- durable non-authoritative local state ------------------------------
  /** @type {{version: number, activeProjectId: string|null, sessions: Record<string, {projectId: string, boundAt: string, reason: string}>, proposals: Record<string, any>, audit: any[]}} */
  const state = {
    version: STATE_VERSION,
    activeProjectId: null,
    sessions: {},
    proposals: {},
    audit: [],
    ...(await readJsonFile(statePath, {})),
  }
  let saveChain = Promise.resolve()
  /** Persist the plugin's non-authoritative local state (never business truth). */
  const saveState = () => {
    saveChain = saveChain.then(async () => {
      try {
        await ensureDir(dirname(statePath))
        await writeFile(statePath, `${JSON.stringify(state, null, 2)}\n`, 'utf8')
      } catch (error) {
        console.warn('[mammoth] state save failed:', describeError(error))
      }
    })
    return saveChain
  }
  /** Append one audit record for the plugin's own boundary events. */
  const audit = (action, detail) => {
    state.audit.push({ at: new Date().toISOString(), action, detail })
    if (state.audit.length > 500) state.audit.splice(0, state.audit.length - 500)
    void saveState()
  }

  // ---- project card cache --------------------------------------------------
  /** @type {Map<string, {at: number, card: any}>} */
  const cardCache = new Map()

  /**
   * Build one project card from Domain reads (project, customer, members,
   * knowledge, work items). Never writes.
   * @param {string} projectId
   */
  async function buildCard(projectId) {
    if (domain === null) throw mammothError('MAMMOTH_CONFIG_MISSING', configError ?? 'Mammoth bundle config missing')
    const [project, customers, members, knowledge, workItems, directory] = await Promise.all([
      domain.get(`/api/projects/${projectId}`),
      domain.get('/api/customers').catch(() => []),
      domain.get(`/api/projects/${projectId}/members`).catch(() => []),
      domain.get(`/api/projects/${projectId}/knowledge`).catch(() => []),
      domain.get(`/api/projects/${projectId}/work-items`).catch(() => []),
      domain.get('/api/users/directory').catch(() => []),
    ])
    const users = new Map((Array.isArray(directory) ? directory : []).map(user => [user.id, user]))
    const customersById = new Map((Array.isArray(customers) ? customers : []).map(customer => [customer.id, customer]))
    const knowledgeList = Array.isArray(knowledge) ? knowledge : []
    const workItemList = Array.isArray(workItems) ? workItems : []
    const memberList = Array.isArray(members) ? members : []
    const customer = customersById.get(project.customerId)
    const byStatus = {}
    for (const item of workItemList) byStatus[item.status] = (byStatus[item.status] ?? 0) + 1
    return {
      id: project.id,
      code: project.code,
      name: project.name,
      status: project.status,
      createdAt: project.createdAt,
      customer: customer ? { id: customer.id, name: customer.name } : { id: project.customerId, name: null },
      members: memberList.map(member => ({
        userId: member.userId,
        role: member.role,
        displayName: users.get(member.userId)?.displayName ?? null,
        username: users.get(member.userId)?.username ?? null,
      })),
      knowledge: {
        total: knowledgeList.length,
        verified: knowledgeList.filter(doc => doc.status === 'verified').length,
        draft: knowledgeList.filter(doc => doc.status !== 'verified').length,
        items: knowledgeList.slice(0, 40).map(doc => ({
          id: doc.id,
          title: doc.title,
          docType: doc.docType,
          status: doc.status,
          version: doc.version,
          reviewedAt: doc.reviewedAt,
          sourceRefCount: Array.isArray(doc.sourceRefs) ? doc.sourceRefs.length : 0,
        })),
      },
      workItems: {
        total: workItemList.length,
        byStatus,
        items: workItemList.slice(0, 60).map(item => ({
          id: item.id,
          itemNo: item.itemNo,
          itemType: item.itemType,
          title: item.title,
          summary: item.summary,
          status: item.status,
          priority: item.priority,
          updatedAt: item.updatedAt,
        })),
      },
      fetchedAt: new Date().toISOString(),
    }
  }

  /**
   * Cached project card read.
   * @param {string} projectId
   * @param {{force?: boolean}} [options]
   */
  async function projectCard(projectId, options = {}) {
    const cached = cardCache.get(projectId)
    if (options.force !== true && cached !== undefined && Date.now() - cached.at < CARD_TTL_MS) return cached.card
    const card = await buildCard(projectId)
    cardCache.set(projectId, { at: Date.now(), card })
    return card
  }

  /** Warm the cache without blocking the caller. */
  const warmCard = projectId => {
    void projectCard(projectId).catch(error => {
      console.warn('[mammoth] project card warm-up failed:', describeError(error))
    })
  }

  // ---- session ↔ project binding ------------------------------------------
  /**
   * Bind one DSH Session to the project that was active when it started.
   * Bindings are non-authoritative local state; the Domain Service stays the
   * business authority.
   * @param {string} sessionId
   * @param {'session-created' | 'first-user-message' | 'explicit' | 'tool'} reason
   * @param {string | null} projectId
   */
  function bindSession(sessionId, reason, projectId = state.activeProjectId) {
    if (typeof sessionId !== 'string' || sessionId === '') return
    if (projectId === null || projectId === undefined) return
    const existing = state.sessions[sessionId]
    if (existing !== undefined && existing.projectId === projectId) return
    if (existing !== undefined && reason !== 'explicit') return
    state.sessions[sessionId] = { projectId, boundAt: new Date().toISOString(), reason }
    warmCard(projectId)
    void saveState()
  }

  /** Resolve the bound project for a session, falling back to the active project. */
  function projectForSession(sessionId) {
    const bound = typeof sessionId === 'string' ? state.sessions[sessionId]?.projectId : undefined
    return bound ?? state.activeProjectId
  }

  // ---- register model-facing tools ----------------------------------------
  const tools = []

  /** Register one raw ToolDefinition (no dsh-tools import: the bundle has no deps). */
  const registerTool = definition => {
    // Fail plugin activation instead of letting an invalid schema reach the
    // provider: DSH 0.2.2-rc.2's registry validates output.schema but not
    // parameters, and one bad schema fails every request of every session.
    assertToolParameters(definition.name, definition.parameters)
    tools.push(ctx.tools.register(definition))
  }

  registerTool({
    name: 'mammoth_list_projects',
    description:
      'List the Mammoth projects the desktop session can see. Returns project code, name, status, customer name, '
      + 'verified knowledge count and work-item counts. Read-only.',
    parameters: TOOL_PARAMETERS.mammoth_list_projects,
    output: {
      schema: {
        type: 'object',
        additionalProperties: false,
        properties: {
          projects: { type: 'array', items: { type: 'object', additionalProperties: true }, description: 'Visible Mammoth projects.' },
          activeProjectId: { oneOf: [{ type: 'string' }, { type: 'null' }], description: 'Project currently selected in the workbench.' },
          domainUser: { oneOf: [{ type: 'string' }, { type: 'null' }], description: 'Synthetic read identity used by the desktop bridge.' },
        },
        required: ['projects', 'activeProjectId', 'domainUser'],
      },
      render: (_args, value) => [{
        type: 'text',
        text: value.projects.length === 0
          ? 'No Mammoth projects are visible to this desktop session.'
          : `Mammoth projects (${value.projects.length}):\n${value.projects.map(project =>
            `- ${project.code} ${project.name} [${project.status}] customer=${project.customer.name ?? project.customer.id} `
            + `verifiedKnowledge=${project.knowledge.verified} workItems=${project.workItems.total}`
            + `${project.id === value.activeProjectId ? ' (active)' : ''}`).join('\n')}`,
      }],
    },
    async execute(args) {
      if (domain === null) throw mammothError('MAMMOTH_CONFIG_MISSING', configError ?? 'Mammoth bundle config missing')
      const limit = typeof args.limit === 'number' && args.limit > 0 ? Math.min(args.limit, 100) : 20
      const projects = await domain.get('/api/projects')
      const list = (Array.isArray(projects) ? projects : []).slice(0, limit)
      const cards = []
      for (const project of list) {
        try {
          cards.push(await projectCard(project.id))
        } catch {
          cards.push({
            id: project.id, code: project.code, name: project.name, status: project.status,
            customer: { id: project.customerId, name: null },
            knowledge: { total: 0, verified: 0, draft: 0, items: [] },
            workItems: { total: 0, byStatus: {}, items: [] },
            members: [],
          })
        }
      }
      return {
        projects: cards.map(card => ({
          id: card.id,
          code: card.code,
          name: card.name,
          status: card.status,
          customer: card.customer,
          knowledge: { total: card.knowledge.total, verified: card.knowledge.verified, draft: card.knowledge.draft },
          workItems: { total: card.workItems.total, byStatus: card.workItems.byStatus },
        })),
        activeProjectId: state.activeProjectId,
        domainUser: domain.identity?.username ?? null,
      }
    },
  })

  registerTool({
    name: 'mammoth_get_project_context',
    description:
      'Read the current Mammoth project context for this conversation: project card, customer, members, '
      + 'verified knowledge titles and work items. Call this before project analysis. Read-only.',
    parameters: TOOL_PARAMETERS.mammoth_get_project_context,
    output: {
      schema: {
        type: 'object',
        additionalProperties: false,
        properties: {
          bound: { type: 'boolean', description: 'Whether this session already has a bound project.' },
          project: {
            oneOf: [{ type: 'object', additionalProperties: true }, { type: 'null' }],
            description: 'Project card, or null when unbound.',
          },
          sessionId: { type: 'string', description: 'Owning DSH Session id.' },
          hint: { type: 'string', description: 'Next-step guidance.' },
        },
        required: ['bound', 'project', 'hint'],
      },
      render: (_args, value) => [{
        type: 'text',
        text: value.project === null
          ? 'This conversation is not bound to a Mammoth project yet. Select a project in the Mammoth workbench (left sidebar) and start a new session, or pass projectId.'
          : `Mammoth project ${value.project.code} ${value.project.name} [${value.project.status}]\n`
            + `projectId: ${value.project.id}\n`
            + `customer: ${value.project.customer.name ?? value.project.customer.id}\n`
            + `members: ${value.project.members.map(member => `${member.displayName ?? member.userId}:${member.role}`).join(', ') || 'n/a'}\n`
            + `knowledge: verified=${value.project.knowledge.verified} draft=${value.project.knowledge.draft}\n`
            + `work items: ${value.project.workItems.total} (${Object.entries(value.project.workItems.byStatus).map(([key, count]) => `${key}=${count}`).join(', ') || 'none'})\n`
            + `verified knowledge titles:\n${value.project.knowledge.items.filter(doc => doc.status === 'verified').map(doc => `- ${doc.title} (${doc.docType})`).join('\n') || '- none'}\n`
            + `Use mcp__mammoth_domain__search_project_knowledge with projectId ${value.project.id} to read knowledge.`,
      }],
    },
    async execute(args, exec) {
      if (domain === null) throw mammothError('MAMMOTH_CONFIG_MISSING', configError ?? 'Mammoth bundle config missing')
      const sessionId = exec.agent?.session?.id
      const projectId = typeof args.projectId === 'string' && args.projectId !== ''
        ? args.projectId
        : projectForSession(sessionId)
      if (projectId === null || projectId === undefined) {
        return {
          bound: false,
          project: null,
          sessionId: sessionId ?? '',
          hint: 'No active project. Ask the user to select one in the Mammoth workbench.',
        }
      }
      bindSession(sessionId, 'tool', projectId)
      const card = await projectCard(projectId)
      return {
        bound: sessionId !== undefined && state.sessions[sessionId]?.projectId === projectId,
        project: {
          id: card.id,
          code: card.code,
          name: card.name,
          status: card.status,
          customer: card.customer,
          members: card.members,
          knowledge: { total: card.knowledge.total, verified: card.knowledge.verified, draft: card.knowledge.draft, items: card.knowledge.items },
          workItems: { total: card.workItems.total, byStatus: card.workItems.byStatus, items: card.workItems.items },
        },
        sessionId: sessionId ?? '',
        hint: `Read knowledge with mcp__mammoth_domain__ tools using projectId ${projectId}; propose changes with mammoth_propose_work_item.`,
      }
    },
  })

  registerTool({
    name: 'mammoth_propose_work_item',
    description:
      'Create a PENDING proposal for a formal Mammoth work-item (requirement) write. This never writes to the Domain Service: '
      + 'a human must approve the proposal in the Mammoth workbench, then the isolated proposal adapter performs the authoritative '
      + 'Domain Service write as the human approver. Use it whenever the user asks for a requirement/change to be recorded.',
    parameters: TOOL_PARAMETERS.mammoth_propose_work_item,
    output: {
      schema: {
        type: 'object',
        additionalProperties: false,
        properties: {
          status: { type: 'string', description: 'pending, rejected_by_approval_policy, or approval_unavailable.' },
          proposalId: { type: 'string', description: 'Pending proposal id when created.' },
          projectId: { type: 'string', description: 'Project the proposal targets.' },
          projectCode: { type: 'string' },
          projectName: { type: 'string' },
          title: { type: 'string' },
          priority: { type: 'string' },
          approval: { type: 'string', description: 'DSH tool-approval outcome for this call.' },
          message: { type: 'string', description: 'Human-readable outcome and next step.' },
        },
        required: ['status', 'message'],
      },
      presentationMeta: (_args, value) => value,
      render: (_args, value) => [{
        type: 'text',
        text: value.status === 'pending'
          ? `Pending proposal ${value.proposalId} created for ${value.projectCode} ${value.projectName}: "${value.title}" (${value.priority}). `
            + 'No Domain Service write happened. A human must approve it in the Mammoth workbench (Proposals) before the formal write.'
          : `No proposal was created (${value.status}): ${value.message}`,
      }],
    },
    async execute(args, exec) {
      if (domain === null || stub === null) throw mammothError('MAMMOTH_CONFIG_MISSING', configError ?? 'Mammoth bundle config missing')
      const sessionId = exec.agent?.session?.id
      const projectId = typeof args.projectId === 'string' && args.projectId !== ''
        ? args.projectId
        : projectForSession(sessionId)
      if (projectId === null || projectId === undefined) {
        return {
          status: 'rejected_by_approval_policy',
          approval: 'none',
          message: 'No active Mammoth project. Select a project in the Mammoth workbench first.',
        }
      }
      bindSession(sessionId, 'tool', projectId)
      const priority = ['low', 'normal', 'high', 'critical'].includes(args.priority) ? args.priority : 'normal'

      // DSH-side human gate: creating a proposal is a consequential action, so
      // ask the composed approval answerers before anything leaves the Host.
      let approval = 'unavailable'
      try {
        approval = await ctx.approval.request({
          agent: exec.agent,
          toolName: 'mammoth_propose_work_item',
          ...(typeof exec.callId === 'string' ? { callId: exec.callId } : {}),
          ...(exec.signal === undefined ? {} : { signal: exec.signal }),
          reason: `Mammoth：创建待审核提案「${args.title}」（项目 ${projectId}）。批准后仍需在工作台人工确认，才会由 Domain Service 正式写入。`,
        })
      } catch (error) {
        approval = 'unavailable'
        console.warn('[mammoth] approval request failed:', describeError(error))
      }
      // Fail closed: only an explicit one-shot DSH approval may create a
      // proposal. `unavailable`, `rejected`, `cancelled`, and unknown outcomes
      // never reach the adapter.
      if (approval !== 'allowed-once') {
        audit('proposal.blocked', { sessionId, projectId, title: args.title, approval })
        return {
          status: approval === 'rejected' || approval === 'cancelled' ? 'rejected_by_approval_policy' : 'approval_unavailable',
          approval,
          projectId,
          title: args.title,
          priority,
          message: approval === 'rejected' || approval === 'cancelled'
            ? 'The DSH approval gate rejected this call, so no proposal was created. Ask the user to review and retry.'
            : 'The DSH approval gate returned no decision (fail-closed), so no proposal was created.',
        }
      }

      const card = await projectCard(projectId).catch(() => null)
      const proposal = {
        type: 'work-item.create',
        projectId,
        sessionId: sessionId ?? null,
        proposer: {
          kind: 'agent',
          dshSessionId: sessionId ?? null,
          agentUserId: mcp.agentUserId ?? null,
          tool: 'mammoth_propose_work_item',
        },
        title: args.title,
        summary: args.summary,
        priority,
        rationale: args.rationale ?? null,
        dshApproval: approval,
        payload: {
          title: args.title,
          summary: args.summary,
          priority,
        },
      }
      const created = await stub.create(proposal)
      const proposalId = isRecord(created) && typeof created.id === 'string' ? created.id : randomUUID()
      state.proposals[proposalId] = {
        id: proposalId,
        ...proposal,
        status: isRecord(created) && typeof created.status === 'string' ? created.status : 'pending',
        createdAt: new Date().toISOString(),
        decidedAt: null,
        approver: null,
        domainObject: null,
        domainError: null,
        dshApproval: approval,
      }
      void saveState()
      audit('proposal.created', { proposalId, sessionId, projectId, title: args.title, dshApproval: approval })
      return {
        status: 'pending',
        proposalId,
        projectId,
        projectCode: card?.code ?? '',
        projectName: card?.name ?? '',
        title: args.title,
        priority,
        approval,
        message: 'Pending proposal created; no business write has happened yet. Approve it in the Mammoth workbench.',
      }
    },
  })

  // ---- session binding hooks ----------------------------------------------
  ctx.effect(() => ctx.on('session/created', session => {
    bindSession(session?.id, 'session-created')
  }), 'mammoth: bind project on session creation')

  ctx.effect(() => ctx.on('session/event', (session, event) => {
    if (event?.type !== 'user/message') return
    bindSession(session?.id, 'first-user-message')
  }), 'mammoth: bind project on first user message')

  // ---- per-session project context in the system prompt --------------------
  /**
   * Render the bound project's prompt context synchronously from the cache; the
   * async Domain read is warmed when the binding or the active project changes.
   * @param {string | undefined} sessionId
   */
  function projectPromptText(sessionId) {
    const projectId = projectForSession(sessionId)
    if (projectId === null || projectId === undefined) return ''
    const cached = cardCache.get(projectId)?.card
    if (cached === undefined) {
      warmCard(projectId)
      return `[Mammoth] 当前会话绑定项目 ${projectId}；项目卡片正在加载，可调用 mammoth_get_project_context 获取详情。`
    }
    const verifiedTitles = cached.knowledge.items
      .filter(doc => doc.status === 'verified')
      .slice(0, 8)
      .map(doc => `- ${doc.title} (${doc.docType})`)
      .join('\n')
    const requirementLines = cached.workItems.items
      .slice(0, 8)
      .map(item => `- ${item.itemNo} ${item.title} [${item.status}/${item.priority}]`)
      .join('\n')
    return [
      '[Mammoth 项目上下文]',
      `当前工作项环境是 Mammoth 项目工作台，本会话绑定项目：${cached.code} ${cached.name}（projectId=${cached.id}）。`,
      `客户：${cached.customer.name ?? cached.customer.id}；项目状态：${cached.status}；成员：${cached.members.map(member => `${member.displayName ?? member.userId}:${member.role}`).join(', ') || 'n/a'}。`,
      `经验证知识 ${cached.knowledge.verified} 篇（共 ${cached.knowledge.total} 篇）；工作项 ${cached.workItems.total} 条。`,
      verifiedTitles === '' ? '经验证知识标题：无' : `经验证知识标题：\n${verifiedTitles}`,
      requirementLines === '' ? '工作项：无' : `工作项：\n${requirementLines}`,
      `读取知识时使用 mcp__mammoth_domain__search_project_knowledge / get_knowledge_document / get_requirement_context，projectId 必须是 ${cached.id}。`,
      '任何正式业务写入（创建/修改需求、知识、方案、交付）必须先调用 mammoth_propose_work_item 生成待审核提案；不得声称已经直接写入，也不得自行批准。',
      '结论必须引用来源，并区分已证实事实、合理推断、待确认事项与未知。',
    ].join('\n')
  }

  ctx.effect(() => ctx.systemPrompt.section({
    name: 'mammoth:project-context',
    // Place after persona/deployment sections but before the tool catalog's
    // generic guidance; ordering is advisory for the assembled prompt.
    order: 50,
    text: assemble => projectPromptText(assemble?.agent?.session?.id),
  }), 'mammoth: project context prompt section')

  // ---- authenticated web route for the bundle's Client half ---------------
  /** @param {import('node:http').ServerResponse} res @param {number} status @param {unknown} value */
  const sendJson = (res, status, value) => {
    const body = JSON.stringify(value)
    res.statusCode = status
    res.setHeader('content-type', 'application/json; charset=utf-8')
    res.setHeader('cache-control', 'no-store')
    res.end(body)
  }

  /** @param {import('node:http').IncomingMessage} req @param {number} [limit] */
  const readBody = async (req, limit = 64 * 1024) => {
    const chunks = []
    let size = 0
    for await (const chunk of req) {
      size += chunk.length
      if (size > limit) throw mammothError('MAMMOTH_BODY_TOO_LARGE', `request body exceeds ${limit} bytes`)
      chunks.push(chunk)
    }
    if (size === 0) return {}
    try {
      return JSON.parse(Buffer.concat(chunks).toString('utf8'))
    } catch {
      throw mammothError('MAMMOTH_BAD_JSON', 'request body is not valid JSON')
    }
  }

  /** @param {string} sessionId */
  async function sessionContext(sessionId) {
    const boundProjectId = state.sessions[sessionId]?.projectId ?? null
    const effectiveProjectId = boundProjectId ?? state.activeProjectId
    let card = null
    let error = null
    if (effectiveProjectId !== null) {
      try {
        card = await projectCard(effectiveProjectId)
      } catch (cause) {
        error = describeError(cause)
      }
    }
    return {
      sessionId,
      bound: boundProjectId !== null,
      boundProjectId,
      activeProjectId: state.activeProjectId,
      effectiveProjectId,
      project: card,
      error,
    }
  }

  /** @param {string} path @param {unknown} body */
  const proposalDecision = async (path, body) => {
    const match = /^\/proposals\/([^/]+)\/(approve|reject)$/.exec(path)
    if (match === null) throw mammothError('MAMMOTH_NOT_FOUND', `no route for ${path}`)
    if (stub === null) throw mammothError('MAMMOTH_CONFIG_MISSING', configError ?? 'Mammoth bundle config missing')
    const proposalId = decodeURIComponent(match[1])
    const decision = match[2]
    // The approver identity is not client-supplied: this route is only reachable
    // by an authenticated browser Peer (the DSH Desktop workbench), and the
    // human decision must echo the exact pending proposal id it saw. The
    // adapter additionally requires its own per-run token.
    if (!isRecord(body) || body.confirm !== proposalId) {
      throw mammothError('MAMMOTH_APPROVAL_CONFIRM_REQUIRED',
        'the workbench must confirm the exact pending proposal id; approval cannot be triggered by a generic caller')
    }
    const approver = 'local-desktop-human'
    const local = state.proposals[proposalId]
    if (local !== undefined && local.status !== 'pending') {
      throw mammothError('MAMMOTH_PROPOSAL_NOT_PENDING', `proposal ${proposalId} is already ${local.status}`)
    }
    if (local !== undefined && local.proposer?.agentUserId === approver) {
      throw mammothError('MAMMOTH_FOUR_EYES_VIOLATION', 'the proposing agent cannot approve its own proposal')
    }
    const result = decision === 'approve'
      ? await stub.approve(proposalId, approver)
      : await stub.reject(proposalId, approver)
    if (local !== undefined && isRecord(result)) {
      local.status = typeof result.status === 'string' ? result.status : decision === 'approve' ? 'approved' : 'rejected'
      local.decidedAt = new Date().toISOString()
      local.approver = approver
      local.domainObject = result.domainObject ?? null
      local.domainError = result.domainError ?? null
      void saveState()
    }
    audit(`proposal.${decision}`, { proposalId, approver, result: isRecord(result) ? result.status : null })
    return result
  }

  /** Dispatch one `/mammoth/api` request after the connection trust fence. */
  const dispatch = async (req, res) => {
    const url = new URL(String(req.url), 'http://localhost')
    const path = url.pathname.slice(API_PREFIX.length) || '/'
    const method = req.method ?? 'GET'
    if (method === 'GET' && path === '/health') {
      const domainHealth = domain === null ? { ok: false, error: configError } : await domain.health()
      const mcpTools = Array.isArray(mcp.toolNames) ? mcp.toolNames.map(toolName => ({
        name: toolName,
        registered: ctx.tools.get(toolName) !== undefined,
      })) : []
      let stubHealth = null
      try {
        stubHealth = stub === null ? null : await stub.health()
      } catch (error) {
        stubHealth = { ok: false, error: describeError(error) }
      }
      sendJson(res, 200, {
        ok: configError === null,
        plugin: PLUGIN_ID,
        version: isRecord(config) ? config.bundleVersion ?? 'unknown' : 'unknown',
        startedAt,
        configError,
        domain: {
          baseUrl: domain?.baseUrl ?? null,
          identity: domain?.identity ?? null,
          health: domainHealth,
        },
        mcp: { serverName: mcp.serverName ?? 'mammoth_domain', tools: mcpTools },
        stub: stubHealth,
        activeProjectId: state.activeProjectId,
        sessionCount: Object.keys(state.sessions).length,
        proposalCount: Object.keys(state.proposals).length,
      })
      return
    }
    if (method === 'GET' && path === '/projects') {
      if (domain === null) throw mammothError('MAMMOTH_CONFIG_MISSING', configError ?? 'Mammoth bundle config missing')
      const projects = await domain.get('/api/projects')
      const list = Array.isArray(projects) ? projects : []
      const summaries = []
      for (const project of list) {
        try {
          const card = await projectCard(project.id)
          summaries.push({
            id: card.id, code: card.code, name: card.name, status: card.status,
            customer: card.customer,
            knowledge: { total: card.knowledge.total, verified: card.knowledge.verified, draft: card.knowledge.draft },
            workItems: { total: card.workItems.total, byStatus: card.workItems.byStatus },
          })
        } catch (error) {
          summaries.push({
            id: project.id, code: project.code, name: project.name, status: project.status,
            customer: { id: project.customerId, name: null },
            knowledge: { total: 0, verified: 0, draft: 0 },
            workItems: { total: 0, byStatus: {} },
            error: describeError(error),
          })
        }
      }
      sendJson(res, 200, {
        projects: summaries,
        activeProjectId: state.activeProjectId,
        domainUser: domain.identity?.username ?? null,
      })
      return
    }
    if (method === 'GET' && path === '/catalog') {
      if (domain === null) throw mammothError('MAMMOTH_CONFIG_MISSING', configError ?? 'Mammoth bundle config missing')
      const [customers, products] = await Promise.all([
        domain.get('/api/customers').catch(() => []),
        domain.get('/api/products').catch(() => []),
      ])
      sendJson(res, 200, {
        customers: Array.isArray(customers) ? customers : [],
        products: Array.isArray(products) ? products : [],
      })
      return
    }
    if (method === 'GET' && path === '/context') {
      let card = null
      let error = null
      if (state.activeProjectId !== null) {
        try {
          card = await projectCard(state.activeProjectId)
        } catch (cause) {
          error = describeError(cause)
        }
      }
      sendJson(res, 200, { activeProjectId: state.activeProjectId, project: card, error })
      return
    }
    if (method === 'POST' && path === '/context') {
      const body = await readBody(req)
      if (!isRecord(body) || (typeof body.projectId !== 'string' && body.projectId !== null)) {
        throw mammothError('MAMMOTH_BAD_REQUEST', 'body must be { projectId: string | null }')
      }
      state.activeProjectId = body.projectId
      await saveState()
      let card = null
      let error = null
      if (state.activeProjectId !== null) {
        try {
          card = await projectCard(state.activeProjectId, { force: true })
        } catch (cause) {
          error = describeError(cause)
        }
      }
      audit('context.selected', { projectId: state.activeProjectId })
      sendJson(res, 200, { activeProjectId: state.activeProjectId, project: card, error })
      return
    }
    const projectMatch = /^\/projects\/([^/]+)$/.exec(path)
    if (method === 'GET' && projectMatch !== null) {
      const card = await projectCard(decodeURIComponent(projectMatch[1]), { force: url.searchParams.get('refresh') === '1' })
      sendJson(res, 200, { project: card })
      return
    }
    const sessionMatch = /^\/sessions\/([^/]+)\/context$/.exec(path)
    if (method === 'GET' && sessionMatch !== null) {
      sendJson(res, 200, await sessionContext(decodeURIComponent(sessionMatch[1])))
      return
    }
    const bindMatch = /^\/sessions\/([^/]+)\/bind$/.exec(path)
    if (method === 'POST' && bindMatch !== null) {
      const body = await readBody(req)
      const sessionId = decodeURIComponent(bindMatch[1])
      if (!isRecord(body) || (typeof body.projectId !== 'string' && body.projectId !== null)) {
        throw mammothError('MAMMOTH_BAD_REQUEST', 'body must be { projectId: string | null }')
      }
      if (body.projectId === null) delete state.sessions[sessionId]
      else bindSession(sessionId, 'explicit', body.projectId)
      await saveState()
      audit('session.bound', { sessionId, projectId: body.projectId })
      sendJson(res, 200, await sessionContext(sessionId))
      return
    }
    if (method === 'GET' && path === '/proposals') {
      let remote = null
      let remoteError = null
      try {
        remote = stub === null ? [] : await stub.list()
      } catch (error) {
        remoteError = describeError(error)
      }
      const remoteList = Array.isArray(remote) ? remote : Array.isArray(remote?.proposals) ? remote.proposals : []
      const merged = new Map()
      for (const proposal of remoteList) {
        if (isRecord(proposal) && typeof proposal.id === 'string') merged.set(proposal.id, proposal)
      }
      for (const [id, proposal] of Object.entries(state.proposals)) {
        const base = merged.get(id) ?? {}
        merged.set(id, { ...proposal, ...base })
      }
      const proposals = [...merged.values()].sort((a, b) => String(b.createdAt ?? '').localeCompare(String(a.createdAt ?? '')))
      sendJson(res, 200, { proposals, remoteError })
      return
    }
    if (method === 'POST' && /^\/proposals\/[^/]+\/(approve|reject)$/.test(path)) {
      const body = await readBody(req)
      sendJson(res, 200, await proposalDecision(path, body))
      return
    }
    if (method === 'GET' && path === '/mcp') {
      const mcpTools = Array.isArray(mcp.toolNames) ? mcp.toolNames.map(toolName => ({
        name: toolName,
        registered: ctx.tools.get(toolName) !== undefined,
      })) : []
      sendJson(res, 200, { serverName: mcp.serverName ?? 'mammoth_domain', tools: mcpTools })
      return
    }
    if (method === 'GET' && path === '/audit') {
      sendJson(res, 200, { audit: state.audit.slice(-100) })
      return
    }
    throw mammothError('MAMMOTH_NOT_FOUND', `no route for ${method} ${path}`)
  }

  ctx.effect(() => ctx.webServer.register({
    kind: 'prefix',
    path: API_PREFIX,
    handler: async (req, res) => {
      const rejection = ctx.connection.requestRejection(req)
      if (rejection !== undefined) {
        res.statusCode = rejection
        res.end()
        return
      }
      try {
        await dispatch(req, res)
      } catch (error) {
        const status = error?.code === 'MAMMOTH_NOT_FOUND' ? 404 : error?.code === 'MAMMOTH_BAD_REQUEST' ? 400 : 500
        if (status === 500) console.warn('[mammoth] route error:', describeError(error))
        try {
          sendJson(res, status, { error: error?.code ?? 'MAMMOTH_ERROR', message: describeError(error) })
        } catch {
          res.destroy()
        }
      }
    },
  }), 'mammoth: authenticated workbench API route')

  // ---- startup diagnostics -------------------------------------------------
  if (configError !== null) {
    console.warn('[mammoth] bundle is not configured:', configError)
  } else {
    if (state.activeProjectId !== null) warmCard(state.activeProjectId)
    void domain.login().then(identity => {
      console.log(`[mammoth] Domain read session ready as ${identity?.username ?? 'unknown'} (${domain.baseUrl})`)
    }).catch(error => {
      console.warn('[mammoth] Domain read session failed:', describeError(error))
    })
    void stub.health().then(value => {
      console.log('[mammoth] proposal stub ready:', JSON.stringify(value).slice(0, 160))
    }).catch(error => {
      console.warn('[mammoth] proposal stub unreachable:', describeError(error))
    })
  }
  audit('plugin.activated', { bundle: PLUGIN_ID, configError })

  console.log(`[mammoth] host plugin active (bundle ${BUNDLE_DIR})`)
}
