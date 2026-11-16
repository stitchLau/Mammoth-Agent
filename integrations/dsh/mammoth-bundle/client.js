/**
 * Mammoth Web Client plugin — browser half of the Mammoth DSH Desktop bundle.
 *
 * Spike scope (not product code):
 * - a left-sidebar entry and a full `main` workbench page that reads real
 *   Mammoth Domain Service data through the Host half's authenticated route;
 * - a conversation header project badge and a right-sidebar project-context tab
 *   (through the official `sidebarRightTabs` extension seat, not by replacing
 *   the shipped `rightbar` slots);
 * - structured tool-result cards for the Mammoth proposal tool and the three
 *   Mammoth Domain MCP read tools.
 *
 * This file is a prebuilt lazy-CJS module: it must be a single self-contained
 * `window.__ModuleLoader__.load` factory, because a profile-installed bundle has
 * no build step. It only requires `react` from the browser module table and uses
 * theme tokens for styling.
 */
window.__ModuleLoader__.load({
  id: '@mammoth/dsh-bundle',
  factory(require) {
    const React = require('react')
    const h = React.createElement
    const { useCallback, useEffect, useMemo, useRef, useState } = React

    const NS = 'mammoth'
    const PANEL_ID = 'mammoth'
    const RIGHT_TAB_KIND = 'mammoth-project'
    const TAB_TYPE_ID = '@mammoth/dsh-bundle'
    const TOOL_PROPOSE = 'mammoth_propose_work_item'
    const TOOL_MCP_SEARCH = 'mcp__mammoth_domain__search_project_knowledge'
    const TOOL_MCP_DOCUMENT = 'mcp__mammoth_domain__get_knowledge_document'
    const TOOL_MCP_REQUIREMENT = 'mcp__mammoth_domain__get_requirement_context'

    // ---------------------------------------------------------------------
    // Locale dictionaries (registered through ctx.locale; also rendered by the
    // plugin-manager page from locale/*.json for metadata).
    // ---------------------------------------------------------------------
    const zh = {
      panel: 'Mammoth',
      workbench: 'Mammoth 工作台',
      projectContext: 'Mammoth 项目上下文',
      guideHint: '查看当前会话绑定的 Mammoth 项目、客户、经验证知识与工作项。',
      home: '工作台首页',
      projects: '项目',
      customers: '客户',
      products: '产品',
      requirements: '需求池',
      knowledge: '知识中心',
      proposals: '待审核提案',
      settings: '设置与诊断',
      activeProject: '当前项目',
      noActiveProject: '尚未选择当前项目',
      selectProject: '设为当前项目',
      openConversation: '返回对话',
      analyzeInChat: '新建会话并分析',
      refresh: '刷新',
      loading: '加载中…',
      error: '错误',
      noData: '暂无数据',
      bindSession: '绑定当前项目',
      bound: '已绑定项目',
      unbound: '会话未绑定项目',
      status: '状态',
      customer: '客户',
      members: '成员',
      verifiedKnowledge: '经验证知识',
      workItems: '工作项',
      approve: '批准并写入',
      reject: '拒绝',
      approveNote: '人类审批在 Domain 的浏览器会话中完成（本页只读）；批准后由独立审批服务自动应用。',
      pendingProposal: '待审核提案',
      proposalCreated: '提案已创建',
      noProposal: '暂无提案',
      diagnostics: '诊断',
      bundle: 'Bundle',
      domainSession: 'Domain 读取身份',
      mcpTools: 'Mammoth Domain MCP 工具',
      location: '页面地址',
      history: '插件审计（本地，非业务权威）',
      proposalsNote: 'Agent 通过 mammoth_propose_work_item 创建待审核提案；人类在 Domain 浏览器会话中批准，独立审批服务自动应用；本页只读展示状态。',
    }
    const en = {
      panel: 'Mammoth',
      workbench: 'Mammoth workbench',
      projectContext: 'Mammoth project context',
      guideHint: 'Open the Mammoth project bound to this conversation: customer, verified knowledge and work items.',
      home: 'Workbench home',
      projects: 'Projects',
      customers: 'Customers',
      products: 'Products',
      requirements: 'Requirements',
      knowledge: 'Knowledge',
      proposals: 'Pending proposals',
      settings: 'Settings & diagnostics',
      activeProject: 'Active project',
      noActiveProject: 'No project selected yet',
      selectProject: 'Set as active project',
      openConversation: 'Back to conversation',
      analyzeInChat: 'New session & analyze',
      refresh: 'Refresh',
      loading: 'Loading…',
      error: 'Error',
      noData: 'No data',
      bindSession: 'Bind active project',
      bound: 'Project bound',
      unbound: 'Session not bound to a project',
      status: 'Status',
      customer: 'Customer',
      members: 'Members',
      verifiedKnowledge: 'Verified knowledge',
      workItems: 'Work items',
      approve: 'Approve & write',
      reject: 'Reject',
      approveNote: 'After approval the isolated proposal adapter performs the authoritative Domain write with a human Domain session; the agent cannot self-approve.',
      pendingProposal: 'Pending proposal',
      proposalCreated: 'Proposal created',
      noProposal: 'No proposals',
      diagnostics: 'Diagnostics',
      bundle: 'Bundle',
      domainSession: 'Domain read identity',
      mcpTools: 'Mammoth Domain MCP tools',
      location: 'Page address',
      history: 'Plugin audit (local, not business authority)',
      proposalsNote: 'The agent creates pending proposals with mammoth_propose_work_item; only a human approval here lets the Domain Service perform the formal write.',
    }

    // ---------------------------------------------------------------------
    // Authenticated Host route client.
    //
    // The Host half registers `/mammoth/api/*` behind the connection trust
    // fence, so the request must be same-origin and carry the DSH browser
    // cookie. The served Web page uses document-relative paths (the official
    // open-in-app client does the same); a shell-owned carrier may expose a
    // different base, which we discover lazily.
    // ---------------------------------------------------------------------
    const apiState = { base: null, error: null }
    const candidateBases = () => {
      const bases = []
      if (typeof location !== 'undefined' && typeof location.href === 'string') {
        try {
          const origin = new URL(location.href).origin
          if (origin && origin !== 'null') bases.push(`${origin}/mammoth/api`)
        } catch {
          // ignore a non-URL document base
        }
        if (location.protocol === 'http:' || location.protocol === 'https:') bases.push('mammoth/api')
      }
      const transport = globalThis.__DSH_TRANSPORT__
      if (transport !== null && typeof transport === 'object' && typeof transport.streamBaseUrl === 'string') {
        bases.push(`${transport.streamBaseUrl.replace(/\/+$/, '')}/mammoth/api`)
      }
      return [...new Set(bases)]
    }

    async function apiRequest(path, options = {}) {
      const tried = apiState.base !== null ? [apiState.base] : candidateBases()
      let lastError = null
      for (const base of tried) {
        try {
          const response = await fetch(`${base}${path}`, {
            method: options.method ?? 'GET',
            headers: {
              accept: 'application/json',
              ...(options.body === undefined ? {} : { 'content-type': 'application/json' }),
            },
            ...(options.body === undefined ? {} : { body: JSON.stringify(options.body) }),
          })
          const text = await response.text()
          let payload = null
          try {
            payload = text === '' ? null : JSON.parse(text)
          } catch {
            throw new Error(`non-JSON response from ${base} (HTTP ${response.status})`)
          }
          if (!response.ok) {
            const detail = payload !== null && typeof payload === 'object' && typeof payload.message === 'string'
              ? payload.message
              : `HTTP ${response.status}`
            const error = new Error(detail)
            error.code = payload !== null && typeof payload === 'object' ? payload.error : undefined
            error.httpStatus = response.status
            throw error
          }
          apiState.base = base
          apiState.error = null
          return payload
        } catch (error) {
          lastError = error
          // A business HTTP error proves the route is reachable; stop retrying.
          if (error !== null && typeof error === 'object' && typeof error.httpStatus === 'number') throw error
        }
      }
      apiState.base = null
      apiState.error = lastError instanceof Error ? lastError.message : String(lastError)
      throw lastError instanceof Error ? lastError : new Error(String(lastError))
    }

    const api = {
      health: () => apiRequest('/health'),
      projects: () => apiRequest('/projects'),
      project: (projectId, refresh) => apiRequest(`/projects/${encodeURIComponent(projectId)}${refresh ? '?refresh=1' : ''}`),
      catalog: () => apiRequest('/catalog'),
      context: () => apiRequest('/context'),
      setContext: projectId => apiRequest('/context', { method: 'POST', body: { projectId } }),
      sessionContext: sessionId => apiRequest(`/sessions/${encodeURIComponent(sessionId)}/context`),
      bindSession: (sessionId, projectId) => apiRequest(`/sessions/${encodeURIComponent(sessionId)}/bind`, { method: 'POST', body: { projectId } }),
      proposals: () => apiRequest('/proposals'),
      approveProposal: proposalId => apiRequest(`/proposals/${encodeURIComponent(proposalId)}/approve`, { method: 'POST', body: { confirm: proposalId } }),
      rejectProposal: proposalId => apiRequest(`/proposals/${encodeURIComponent(proposalId)}/reject`, { method: 'POST', body: { confirm: proposalId } }),
      audit: () => apiRequest('/audit'),
      get base() { return apiState.base },
      get baseError() { return apiState.error },
    }

    // ---------------------------------------------------------------------
    // Small observable store shared by the workbench, the header badge and the
    // right-sidebar tab. View state only; never business authority.
    // ---------------------------------------------------------------------
    function createSharedStore() {
      let snapshot = {
        activeProjectId: null,
        activeProject: null,
        projects: [],
        projectsLoaded: false,
        projectsError: null,
        sessionContexts: new Map(),
      }
      const listeners = new Set()
      return {
        get() { return snapshot },
        set(patch) {
          snapshot = { ...snapshot, ...patch }
          for (const listener of [...listeners]) {
            try { listener() } catch (error) { console.warn('[mammoth] store listener failed', error) }
          }
        },
        subscribe(listener) {
          listeners.add(listener)
          return () => { listeners.delete(listener) }
        },
      }
    }
    const shared = createSharedStore()

    function useShared() {
      const [, force] = useState(0)
      useEffect(() => shared.subscribe(() => force(value => value + 1)), [])
      return shared.get()
    }

    // ---------------------------------------------------------------------
    // Theme-token styling. Component-local <style> element so unmounting the
    // page removes it; only --dsw-alias-* tokens are used.
    // ---------------------------------------------------------------------
    const styles = `
.mammoth-root { height: 100%; overflow: auto; color: var(--dsw-alias-label-primary, #1f2328); background: var(--dsw-alias-bg-base, transparent); font-size: 13px; }
.mammoth-shell { display: flex; min-height: 100%; }
.mammoth-nav { flex: 0 0 176px; padding: 16px 8px 24px; border-right: 1px solid var(--dsw-alias-border-l2, rgba(0,0,0,.08)); }
.mammoth-nav-item { display: flex; align-items: center; gap: 8px; width: 100%; padding: 7px 10px; margin: 2px 0; border: 0; border-radius: 8px; background: transparent; color: var(--dsw-alias-label-secondary, #57606a); font: inherit; text-align: left; cursor: pointer; }
.mammoth-nav-item:hover { background: var(--dsw-alias-bg-layer-2, rgba(0,0,0,.04)); }
.mammoth-nav-item[data-active="true"] { background: var(--dsw-alias-bg-layer-3, rgba(0,0,0,.07)); color: var(--dsw-alias-label-primary, #1f2328); font-weight: 600; }
.mammoth-main { flex: 1 1 auto; min-width: 0; padding: 18px 22px 40px; }
.mammoth-header { display: flex; align-items: baseline; gap: 12px; flex-wrap: wrap; margin-bottom: 14px; }
.mammoth-title { font-size: 18px; font-weight: 650; margin: 0; }
.mammoth-sub { color: var(--dsw-alias-label-tertiary, #6e7781); font-size: 12px; }
.mammoth-actions { display: flex; gap: 8px; flex-wrap: wrap; margin-left: auto; }
.mammoth-btn { padding: 5px 11px; border-radius: 8px; border: 1px solid var(--dsw-alias-border-l3, rgba(0,0,0,.15)); background: var(--dsw-alias-bg-layer-1, #fff); color: var(--dsw-alias-label-primary, #1f2328); font: inherit; cursor: pointer; }
.mammoth-btn:hover { border-color: var(--dsw-alias-brand-primary, #2f81f7); }
.mammoth-btn[disabled] { opacity: .5; cursor: default; }
.mammoth-btn-primary { background: var(--dsw-alias-button-primary-fill, #1f6feb); color: var(--dsw-alias-button-primary-label, #fff); border-color: transparent; }
.mammoth-cards { display: grid; grid-template-columns: repeat(auto-fill, minmax(248px, 1fr)); gap: 12px; }
.mammoth-card { border: 1px solid var(--dsw-alias-border-l2, rgba(0,0,0,.08)); border-radius: 12px; padding: 12px 14px; background: var(--dsw-alias-bg-layer-1, #fff); }
.mammoth-card-title { font-weight: 620; margin: 0 0 6px; display: flex; align-items: center; gap: 8px; }
.mammoth-kv { display: grid; grid-template-columns: max-content 1fr; gap: 4px 12px; font-size: 12px; }
.mammoth-kv dt { color: var(--dsw-alias-label-tertiary, #6e7781); }
.mammoth-kv dd { margin: 0; color: var(--dsw-alias-label-primary, #1f2328); }
.mammoth-chip { display: inline-block; padding: 1px 7px; border-radius: 999px; font-size: 11px; background: var(--dsw-alias-bg-layer-2, rgba(0,0,0,.05)); color: var(--dsw-alias-label-secondary, #57606a); }
.mammoth-chip-ok { background: var(--dsw-alias-state-success-primary, #1a7f37); color: #fff; }
.mammoth-chip-warn { background: var(--dsw-alias-state-warn-primary, #9a6700); color: #fff; }
.mammoth-chip-error { background: var(--dsw-alias-state-error-primary, #cf222e); color: #fff; }
.mammoth-table { width: 100%; border-collapse: collapse; font-size: 12px; }
.mammoth-table th, .mammoth-table td { text-align: left; padding: 7px 8px; border-bottom: 1px solid var(--dsw-alias-border-l1, rgba(0,0,0,.06)); vertical-align: top; }
.mammoth-table th { color: var(--dsw-alias-label-tertiary, #6e7781); font-weight: 500; }
.mammoth-row-link { color: var(--dsw-alias-brand-primary, #0969da); cursor: pointer; background: none; border: 0; padding: 0; font: inherit; }
.mammoth-section { margin: 18px 0 8px; font-size: 13px; font-weight: 620; }
.mammoth-note { font-size: 12px; color: var(--dsw-alias-label-tertiary, #6e7781); }
.mammoth-error { color: var(--dsw-alias-state-error-primary, #cf222e); font-size: 12px; white-space: pre-wrap; }
.mammoth-pre { white-space: pre-wrap; word-break: break-word; font-size: 12px; background: var(--dsw-alias-bg-layer-2, rgba(0,0,0,.04)); border-radius: 8px; padding: 8px 10px; }
.mammoth-toolcard { border: 1px solid var(--dsw-alias-border-l2, rgba(0,0,0,.08)); border-radius: 10px; padding: 8px 11px; margin: 2px 0; background: var(--dsw-alias-bg-layer-1, #fff); font-size: 12px; }
.mammoth-toolcard-head { display: flex; align-items: center; gap: 8px; font-weight: 600; }
.mammoth-toolcard-body { margin-top: 6px; }
.mammoth-badge { display: inline-flex; align-items: center; gap: 6px; padding: 3px 9px; border-radius: 999px; border: 1px solid var(--dsw-alias-border-l3, rgba(0,0,0,.15)); background: var(--dsw-alias-bg-layer-1, #fff); color: var(--dsw-alias-label-secondary, #57606a); font-size: 12px; cursor: pointer; }
.mammoth-badge:hover { border-color: var(--dsw-alias-brand-primary, #2f81f7); color: var(--dsw-alias-label-primary, #1f2328); }
.mammoth-badge[data-bound="true"] { border-color: var(--dsw-alias-brand-primary, #2f81f7); color: var(--dsw-alias-label-primary, #1f2328); }
.mammoth-panel { padding: 12px; }
.mammoth-panel h3 { margin: 4px 0 10px; font-size: 14px; }
.mammoth-list { margin: 6px 0 0; padding-left: 18px; }
.mammoth-list li { margin: 3px 0; }
.mammoth-tabs { display: flex; gap: 6px; margin: 10px 0 12px; flex-wrap: wrap; }
.mammoth-tab { padding: 5px 10px; border-radius: 8px; border: 1px solid transparent; background: transparent; color: var(--dsw-alias-label-secondary, #57606a); font: inherit; cursor: pointer; }
.mammoth-tab[data-active="true"] { background: var(--dsw-alias-bg-layer-2, rgba(0,0,0,.05)); color: var(--dsw-alias-label-primary, #1f2328); font-weight: 600; }
.mammoth-inline { display: flex; gap: 8px; align-items: center; flex-wrap: wrap; }
.mammoth-tech summary { cursor: pointer; font-size: 12px; color: var(--dsw-alias-label-tertiary, #6e7781); margin: 4px 0; }
.mammoth-tech[open] summary { margin-bottom: 8px; }
.mammoth-divider { height: 1px; background: var(--dsw-alias-border-l1, rgba(0,0,0,.06)); margin: 14px 0; }
`

    // ---------------------------------------------------------------------
    // Small presentational helpers
    // ---------------------------------------------------------------------
    /** @param {{children?: any}} props */
    function Styles() {
      return h('style', { 'data-mammoth-styles': '' }, styles)
    }

    function Chip({ tone = 'plain', children }) {
      const cls = tone === 'ok' ? 'mammoth-chip mammoth-chip-ok'
        : tone === 'warn' ? 'mammoth-chip mammoth-chip-warn'
          : tone === 'error' ? 'mammoth-chip mammoth-chip-error' : 'mammoth-chip'
      return h('span', { className: cls }, children)
    }

    function Kv({ rows }) {
      const items = []
      for (const [key, value] of rows) {
        if (value === null || value === undefined || value === '') continue
        items.push(h('dt', { key: `k-${key}` }, key), h('dd', { key: `v-${key}` }, value))
      }
      if (items.length === 0) return null
      return h('dl', { className: 'mammoth-kv' }, items)
    }

    function Section({ title, children, actions }) {
      return h('div', null,
        h('div', { className: 'mammoth-inline' },
          h('div', { className: 'mammoth-section' }, title),
          actions === undefined ? null : h('div', { className: 'mammoth-actions' }, actions)),
        children)
    }

    function ErrorNote({ error }) {
      if (error === null || error === undefined || error === '') return null
      return h('div', { className: 'mammoth-error' }, typeof error === 'string' ? error : String(error?.message ?? error))
    }

    function Loading({ t }) {
      return h('div', { className: 'mammoth-note' }, t('loading'))
    }

    function ProjectSummaryCard({ project, t, actions }) {
      return h('div', { className: 'mammoth-card' },
        h('div', { className: 'mammoth-card-title' },
          h('span', null, `${project.code} ${project.name}`),
          h(Chip, null, project.status)),
        h(Kv, {
          rows: [
            [t('customer'), project.customer?.name ?? project.customer?.id ?? '—'],
            [t('verifiedKnowledge'), `${project.knowledge?.verified ?? 0} / ${project.knowledge?.total ?? 0}`],
            [t('workItems'), String(project.workItems?.total ?? 0)],
          ],
        }),
        actions === undefined ? null : h('div', { className: 'mammoth-actions', style: { marginTop: '10px' } }, actions))
    }

    // ---------------------------------------------------------------------
    // Workbench page (main panel, root scope, not Session-bound)
    // ---------------------------------------------------------------------
    const NAV = [
      ['home', 'home'],
      ['projects', 'projects'],
      ['requirements', 'requirements'],
      ['knowledge', 'knowledge'],
      ['proposals', 'proposals'],
      ['customers', 'customers'],
      ['products', 'products'],
      ['settings', 'settings'],
    ]

    function WorkbenchPage(props) {
      const t = props.t ?? (key => key)
      const snap = useShared()
      const [view, setView] = useState({ name: 'home', projectId: null, tab: 'overview' })
      const [detail, setDetail] = useState({ projectId: null, project: null, error: null, loading: false })
      const [catalog, setCatalog] = useState({ customers: [], products: [], error: null })
      const [proposals, setProposals] = useState({ items: [], error: null, busy: null })
      const [health, setHealth] = useState({ value: null, error: null })
      const [message, setMessage] = useState(null)

      const refreshAll = useCallback(async () => {
        try {
          const healthValue = await api.health()
          setHealth({ value: healthValue, error: null })
        } catch (error) {
          setHealth({ value: null, error: error.message })
        }
        try {
          const projectsValue = await api.projects()
          shared.set({
            projects: Array.isArray(projectsValue.projects) ? projectsValue.projects : [],
            activeProjectId: projectsValue.activeProjectId ?? null,
            projectsLoaded: true,
            projectsError: null,
          })
        } catch (error) {
          shared.set({ projectsLoaded: true, projectsError: error.message })
        }
        try {
          const contextValue = await api.context()
          shared.set({ activeProjectId: contextValue.activeProjectId ?? null, activeProject: contextValue.project ?? null })
        } catch {
          // keep the project list state; the context endpoint has its own errors
        }
        void loadCatalog()
        void loadProposals()
        void loadAudit()
      }, [])

      const loadCatalog = useCallback(async () => {
        try {
          const value = await api.catalog()
          setCatalog({ customers: value.customers ?? [], products: value.products ?? [], error: null })
        } catch (error) {
          setCatalog(current => ({ ...current, error: error.message }))
        }
      }, [])

      const loadProposals = useCallback(async () => {
        try {
          const value = await api.proposals()
          setProposals({ items: Array.isArray(value.proposals) ? value.proposals : [], error: value.remoteError ?? null, busy: null })
        } catch (error) {
          setProposals({ items: [], error: error.message, busy: null })
        }
      }, [])

      const [audit, setAudit] = useState({ items: [], error: null })
      const loadAudit = useCallback(async () => {
        try {
          const value = await api.audit()
          setAudit({ items: Array.isArray(value.audit) ? value.audit : [], error: null })
        } catch (error) {
          setAudit({ items: [], error: error.message })
        }
      }, [])

      useEffect(() => { void refreshAll() }, [refreshAll])

      const loadDetail = useCallback(async projectId => {
        if (projectId === null) return
        setDetail({ projectId, project: null, error: null, loading: true })
        try {
          const value = await api.project(projectId, true)
          setDetail({ projectId, project: value.project, error: null, loading: false })
        } catch (error) {
          setDetail({ projectId, project: null, error: error.message, loading: false })
        }
      }, [])

      useEffect(() => {
        if (view.name === 'project' && view.projectId !== null && detail.projectId !== view.projectId) void loadDetail(view.projectId)
        if (view.name === 'requirements' && view.projectId !== null) void loadDetail(view.projectId)
        if (view.name === 'knowledge' && view.projectId !== null) void loadDetail(view.projectId)
      }, [view, detail.projectId, loadDetail])

      const setActiveProject = useCallback(async projectId => {
        try {
          const value = await api.setContext(projectId)
          shared.set({ activeProjectId: value.activeProjectId, activeProject: value.project })
          setMessage(value.error ? { kind: 'error', text: value.error } : { kind: 'ok', text: `${t('activeProject')}: ${value.project?.code ?? projectId}` })
        } catch (error) {
          setMessage({ kind: 'error', text: error.message })
        }
      }, [t])

      const decideProposal = useCallback(async (proposalId, decision) => {
        setProposals(current => ({ ...current, busy: proposalId }))
        try {
          const result = decision === 'approve'
            ? await api.approveProposal(proposalId)
            : await api.rejectProposal(proposalId)
          setMessage({
            kind: result?.status === 'approved' || result?.status === 'rejected' ? 'ok' : 'error',
            text: JSON.stringify(result?.domainObject ?? result?.domainError ?? result?.status ?? result, null, 0),
          })
          await loadProposals()
          await refreshAll()
        } catch (error) {
          // Surface the failure both in the proposals page and in the page header,
          // so a failed click never looks like "nothing happened".
          setProposals(current => ({ ...current, error: error.message }))
          setMessage({ kind: 'error', text: error.message })
        }
      }, [loadProposals, refreshAll])

      const navItems = NAV.map(([key, labelKey]) => h('button', {
        key,
        className: 'mammoth-nav-item',
        'data-active': String(view.name === key || (key === 'projects' && view.name === 'project')),
        onClick: () => setView({ name: key, projectId: view.projectId, tab: view.tab }),
      }, t(labelKey)))

      const activeProjectLabel = snap.activeProject
        ? `${snap.activeProject.code} ${snap.activeProject.name}`
        : t('noActiveProject')

      let content = null
      if (view.name === 'home') content = renderHome({
        t, snap, health, proposals, message,
        onOpenProject: projectId => setView({ name: 'project', projectId, tab: 'overview' }),
        onSetActive: setActiveProject,
        onOpenConversation: props.openConversation,
        onDecideProposal: decideProposal,
        onRefresh: refreshAll,
      })
      else if (view.name === 'projects') content = renderProjects({ t, snap, onOpenProject: projectId => setView({ name: 'project', projectId, tab: 'overview' }) })
      else if (view.name === 'project') content = renderProjectDetail({
        t, detail, message, health,
        tab: view.tab,
        onTab: tab => setView({ ...view, tab }),
        onSetActive: setActiveProject,
        onOpenConversation: props.openConversation,
        onRefresh: () => loadDetail(view.projectId),
      })
      else if (view.name === 'requirements') content = renderProjectScoped({ t, snap, detail, kind: 'requirements', onPickProject: projectId => setView({ ...view, projectId }), onOpenProject: projectId => setView({ name: 'project', projectId, tab: 'requirements' }) })
      else if (view.name === 'knowledge') content = renderProjectScoped({ t, snap, detail, kind: 'knowledge', onPickProject: projectId => setView({ ...view, projectId }), onOpenProject: projectId => setView({ name: 'project', projectId, tab: 'knowledge' }) })
      else if (view.name === 'proposals') content = renderProposals({
        t,
        proposals,
        message,
        onDecide: decideProposal,
        onRefresh: loadProposals,
        onOpenRequirements: projectId => setView({ name: 'requirements', projectId }),
      })
      else if (view.name === 'customers') content = renderCatalog({ t, title: t('customers'), items: catalog.customers, error: catalog.error })
      else if (view.name === 'products') content = renderCatalog({ t, title: t('products'), items: catalog.products, error: catalog.error })
      else content = renderSettings({ t, health, snap, audit, location: typeof location === 'undefined' ? '' : location.href })
      const clientInfo = {
        location: typeof location === 'undefined' ? 'unknown' : location.href,
        transportPresent: typeof globalThis.__DSH_TRANSPORT__ === 'object' && globalThis.__DSH_TRANSPORT__ !== null,
        streamBaseUrl: globalThis.__DSH_TRANSPORT__?.streamBaseUrl ?? null,
        apiBase: api.base,
        apiBaseError: api.baseError,
        diagnostics: clientDiagnostics,
      }
      if (view.name === 'settings') content = renderSettings({ t, health, snap, audit, location: typeof location === 'undefined' ? '' : location.href, clientInfo })
      else if (view.name === 'home' || view.name === 'projects' || view.name === 'project' || view.name === 'requirements' || view.name === 'knowledge' || view.name === 'proposals' || view.name === 'customers' || view.name === 'products') {
        // content already selected above; the diagnostics live in Settings.
      } else content = renderSettings({ t, health, snap, audit, location: typeof location === 'undefined' ? '' : location.href, clientInfo })

      return h('div', { className: 'mammoth-root' },
        h(Styles),
        h('div', { className: 'mammoth-shell' },
          h('nav', { className: 'mammoth-nav' }, navItems),
          h('div', { className: 'mammoth-main' },
            clientDiagnostics.errors.length > 0
              ? h('div', { className: 'mammoth-error', 'data-mammoth-diag': 'errors' },
                `Mammoth client diagnostics (${clientDiagnostics.errors.length}): `,
                clientDiagnostics.errors.map((entry, index) => h('div', { key: index }, `${entry.phase}: ${entry.message}`)))
              : null,
            h('div', { className: 'mammoth-header' },
              h('h2', { className: 'mammoth-title' }, t('workbench')),
              h('span', { className: 'mammoth-sub' }, `${t('activeProject')}: ${activeProjectLabel}`),
              message === null ? null : h('span', { className: message.kind === 'error' ? 'mammoth-chip mammoth-chip-error' : 'mammoth-chip mammoth-chip-ok' }, message.text),
              h('div', { className: 'mammoth-actions' },
                h('button', { className: 'mammoth-btn', onClick: () => { void refreshAll() } }, t('refresh')),
                h('button', { className: 'mammoth-btn', onClick: () => props.openConversation() }, t('openConversation')))),
            content)))
    }

    /** @param {any} ctx */
    function renderHome({ t, snap, health, proposals, message, onOpenProject, onSetActive, onDecideProposal, onRefresh }) {
      const pending = proposals.items.filter(item => item.status === 'pending')
      return h('div', null,
        h('div', { className: 'mammoth-cards' },
          h('div', { className: 'mammoth-card' },
            h('div', { className: 'mammoth-card-title' }, t('activeProject')),
            snap.activeProject
              ? h(Kv, { rows: [
                ['Code', snap.activeProject.code],
                [t('customer'), snap.activeProject.customer?.name ?? snap.activeProject.customer?.id ?? '—'],
                [t('status'), snap.activeProject.status],
                [t('verifiedKnowledge'), `${snap.activeProject.knowledge?.verified ?? 0} / ${snap.activeProject.knowledge?.total ?? 0}`],
                [t('workItems'), String(snap.activeProject.workItems?.total ?? 0)],
              ] })
              : h('div', { className: 'mammoth-note' }, t('noActiveProject')),
            h('div', { className: 'mammoth-actions', style: { marginTop: '10px' } },
              h('button', { className: 'mammoth-btn mammoth-btn-primary', onClick: () => { if (snap.activeProject) onOpenProject(snap.activeProject.id) } }, t('projects')))),
          h('div', { className: 'mammoth-card' },
            h('div', { className: 'mammoth-card-title' }, t('proposals'), pending.length > 0 ? h(Chip, { tone: 'warn' }, String(pending.length)) : null),
            pending.length === 0
              ? h('div', { className: 'mammoth-note' }, t('noProposal'))
              : h('ul', { className: 'mammoth-list' }, pending.slice(0, 4).map(proposal => h('li', { key: proposal.id },
                h('div', { className: 'mammoth-inline' },
                  h('span', null, `${proposal.title ?? proposal.summary ?? '(untitled)'} · ${proposal.status}`),
                  h('span', { className: 'mammoth-note' }, t('approveNote')))))),
            h('div', { className: 'mammoth-note' }, t('proposalsNote'))),
          h('div', { className: 'mammoth-card' },
            h('div', { className: 'mammoth-card-title' }, t('diagnostics')),
            h(Kv, { rows: [
              [t('bundle'), health.value?.version ?? '—'],
              [t('domainSession'), health.value?.domain?.identity?.username ?? (health.error ?? '—')],
              [t('mcpTools'), `${(health.value?.mcp?.tools ?? []).filter(tool => tool.registered).length}/${(health.value?.mcp?.tools ?? []).length}`],
            ] }),
            health.error === null ? null : h(ErrorNote, { error: health.error }))),
        h(Section, {
          title: t('projects'),
          actions: [h('button', { key: 'r', className: 'mammoth-btn', onClick: () => { void onRefresh() } }, t('refresh'))],
        }, renderProjectTable({ t, projects: snap.projects, onOpenProject, onSetActive, activeProjectId: snap.activeProjectId, error: snap.projectsError })))
    }

    function renderProjectTable({ t, projects, onOpenProject, onSetActive, activeProjectId, error }) {
      if (error) return h(ErrorNote, { error })
      if (!projects || projects.length === 0) return h('div', { className: 'mammoth-note' }, t('noData'))
      return h('table', { className: 'mammoth-table' },
        h('thead', null, h('tr', null,
          h('th', null, 'Code'), h('th', null, t('projects')), h('th', null, t('customer')),
          h('th', null, t('status')), h('th', null, t('verifiedKnowledge')), h('th', null, t('workItems')), h('th', null, ''))),
        h('tbody', null, projects.map(project => h('tr', { key: project.id },
          h('td', null, project.code),
          h('td', null, h('button', { className: 'mammoth-row-link', onClick: () => onOpenProject(project.id) }, project.name)),
          h('td', null, project.customer?.name ?? project.customer?.id ?? '—'),
          h('td', null, project.status),
          h('td', null, String(project.knowledge?.verified ?? 0)),
          h('td', null, String(project.workItems?.total ?? 0)),
          h('td', null, project.id === activeProjectId
            ? h(Chip, { tone: 'ok' }, t('activeProject'))
            : h('button', { className: 'mammoth-btn', onClick: () => onSetActive(project.id) }, t('selectProject')))))))
    }

    function renderProjects({ t, snap, onOpenProject }) {
      return h('div', null,
        h(Section, { title: t('projects') }, renderProjectTable({
          t,
          projects: snap.projects,
          onOpenProject,
          onSetActive: () => {},
          activeProjectId: snap.activeProjectId,
          error: snap.projectsError,
        })))
    }

    function renderProjectDetail({ t, detail, message, health, tab, onTab, onSetActive, onOpenConversation, onRefresh }) {
      if (detail.loading) return h(Loading, { t })
      if (detail.error) return h(ErrorNote, { error: detail.error })
      if (!detail.project) return h('div', { className: 'mammoth-note' }, t('noData'))
      const project = detail.project
      const tabs = [
        ['overview', t('home')],
        ['requirements', t('requirements')],
        ['knowledge', t('knowledge')],
      ]
      const tabBar = h('div', { className: 'mammoth-tabs' }, tabs.map(([key, label]) => h('button', {
        key, className: 'mammoth-tab', 'data-active': String(tab === key), onClick: () => onTab(key),
      }, label)))
      const overview = h('div', null,
        h('div', { className: 'mammoth-cards' },
          h('div', { className: 'mammoth-card' },
            h('div', { className: 'mammoth-card-title' }, `${project.code} ${project.name}`),
            h(Kv, { rows: [
              [t('customer'), project.customer?.name ?? project.customer?.id ?? '—'],
              [t('status'), project.status],
              ['projectId', project.id],
              [t('members'), project.members?.map(member => `${member.displayName ?? member.username ?? member.userId} (${member.role})`).join(', ') || '—'],
              [t('verifiedKnowledge'), `${project.knowledge?.verified ?? 0} / ${project.knowledge?.total ?? 0}`],
              [t('workItems'), String(project.workItems?.total ?? 0)],
            ] }))),
        h('div', { className: 'mammoth-actions', style: { margin: '12px 0' } },
          h('button', { className: 'mammoth-btn mammoth-btn-primary', onClick: () => onSetActive(project.id) }, t('selectProject')),
          h('button', { className: 'mammoth-btn', onClick: () => onOpenConversation() }, t('openConversation')),
          h('button', { className: 'mammoth-btn', onClick: () => onRefresh() }, t('refresh'))))
      const requirements = h('div', null, renderWorkItemsTable({ t, items: project.workItems?.items ?? [] }))
      const knowledge = h('div', null, renderKnowledgeTable({ t, items: project.knowledge?.items ?? [] }))
      return h('div', null,
        h('div', { className: 'mammoth-inline' }, h('div', { className: 'mammoth-sub' }, `${t('projects')} / ${project.code}`)),
        tabBar,
        message === null ? null : h('div', { className: 'mammoth-note' }, message.text),
        tab === 'requirements' ? requirements : tab === 'knowledge' ? knowledge : overview)
    }

    function renderWorkItemsTable({ t, items }) {
      if (!Array.isArray(items) || items.length === 0) return h('div', { className: 'mammoth-note' }, t('noData'))
      return h('table', { className: 'mammoth-table' },
        h('thead', null, h('tr', null,
          h('th', null, 'No'), h('th', null, t('requirements')), h('th', null, t('status')), h('th', null, 'Priority'), h('th', null, 'Updated'))),
        h('tbody', null, items.map(item => h('tr', { key: item.id },
          h('td', null, item.itemNo),
          h('td', null, item.title),
          h('td', null, item.status),
          h('td', null, item.priority),
          h('td', null, item.updatedAt ?? '')))))
    }

    function renderKnowledgeTable({ t, items }) {
      if (!Array.isArray(items) || items.length === 0) return h('div', { className: 'mammoth-note' }, t('noData'))
      return h('table', { className: 'mammoth-table' },
        h('thead', null, h('tr', null,
          h('th', null, t('knowledge')), h('th', null, 'Type'), h('th', null, t('status')), h('th', null, 'Sources'), h('th', null, 'v'))),
        h('tbody', null, items.map(doc => h('tr', { key: doc.id },
          h('td', null, doc.title),
          h('td', null, doc.docType),
          h('td', null, doc.status === 'verified' ? h(Chip, { tone: 'ok' }, doc.status) : h(Chip, null, doc.status)),
          h('td', null, String(doc.sourceRefCount ?? 0)),
          h('td', null, String(doc.version ?? ''))))))
    }

    function renderProjectScoped({ t, snap, detail, kind, onPickProject, onOpenProject }) {
      const project = detail.project
      return h('div', null,
        h(Section, { title: kind === 'requirements' ? t('requirements') : t('knowledge') },
          h('div', { className: 'mammoth-inline' },
            h('select', {
              className: 'mammoth-btn',
              value: project?.id ?? '',
              onChange: event => onPickProject(event.target.value === '' ? null : event.target.value),
            },
            h('option', { value: '' }, t('selectProject')),
            snap.projects.map(item => h('option', { key: item.id, value: item.id }, `${item.code} ${item.name}`))),
            project === null ? null : h('button', { className: 'mammoth-btn', onClick: () => onOpenProject(project.id) }, project.code))),
        detail.loading ? h(Loading, { t }) : detail.error ? h(ErrorNote, { error: detail.error })
          : project === null ? h('div', { className: 'mammoth-note' }, t('noActiveProject'))
            : kind === 'requirements'
              ? renderWorkItemsTable({ t, items: project.workItems?.items ?? [] })
              : renderKnowledgeTable({ t, items: project.knowledge?.items ?? [] }))
    }

    function renderProposals({ t, proposals, message, onDecide, onRefresh, onOpenRequirements }) {
      const items = proposals.items
      const formatTime = value => {
        if (value === null || value === undefined || value === '') return null
        try {
          return new Date(value).toLocaleString('zh-CN', { hour12: false })
        } catch {
          return String(value)
        }
      }
      const statusLabel = status => status === 'pending' ? '待审核'
        : status === 'approved' ? '已批准'
          : status === 'rejected' ? '已拒绝' : (status ?? 'unknown')
      return h('div', null,
        h(Section, { title: t('proposals'), actions: [h('button', { key: 'r', className: 'mammoth-btn', onClick: () => { void onRefresh() } }, t('refresh'))] },
          h('div', { className: 'mammoth-note', style: { marginBottom: '10px' } }, t('proposalsNote')),
          proposals.error === null ? null : h(ErrorNote, { error: proposals.error }),
          items.length === 0 ? h('div', { className: 'mammoth-note' }, t('noProposal'))
            : items.map(proposal => {
              const domain = proposal.domainObject !== null && typeof proposal.domainObject === 'object'
                ? proposal.domainObject
                : null
              const technical = {
                proposalId: proposal.id,
                projectId: proposal.projectId,
                status: proposal.status ?? null,
                dshApproval: proposal.dshApproval ?? null,
                approver: proposal.approver ?? null,
                decidedAt: proposal.decidedAt ?? null,
                domainObject: proposal.domainObject ?? null,
                domainError: proposal.domainError ?? null,
              }
              return h('div', { key: proposal.id, className: 'mammoth-card', style: { marginBottom: '10px' } },
                h('div', { className: 'mammoth-card-title' },
                  h('span', null, proposal.title ?? proposal.summary ?? '(untitled)'),
                  h(Chip, { tone: proposal.status === 'pending' ? 'warn' : proposal.status === 'approved' ? 'ok' : proposal.status === 'rejected' ? 'error' : 'plain' }, statusLabel(proposal.status))),
                // Human-readable business fields only. Raw IDs and the Domain
                // JSON response live in the collapsed technical section below.
                h(Kv, { rows: [
                  ['优先级', proposal.priority],
                  ['创建时间', formatTime(proposal.createdAt)],
                  ['摘要', proposal.summary],
                  ['依据/说明', proposal.rationale],
                  ['审批人', proposal.approver],
                  domain === null ? null : ['Domain 需求', `${domain.itemNo ?? ''} ${domain.title ?? ''}`.trim()],
                  domain === null ? null : ['Domain 状态', domain.status],
                  proposal.domainError === null || proposal.domainError === undefined ? null : ['写入失败', proposal.domainError],
                ].filter(row => row !== null) }),
                proposal.status === 'approved' && domain !== null
                  ? h('div', { className: 'mammoth-actions', style: { marginTop: '10px' } },
                    h('button', { className: 'mammoth-btn', onClick: () => { onOpenRequirements?.(proposal.projectId) } }, `在需求池查看 ${domain.itemNo ?? ''}`.trim()),
                    h('span', { className: 'mammoth-note' }, 'Domain Service 已按人工批准写入；本页仅展示提案记录。'))
                  : null,
                proposal.status === 'pending' || proposal.status === 'awaiting_second'
                  ? h('div', { className: 'mammoth-actions', style: { marginTop: '10px' } },
                    h('span', { className: 'mammoth-note' }, t('approveNote')))
                  : null,
                h('details', { className: 'mammoth-tech', style: { marginTop: '8px' } },
                  h('summary', null, '技术信息（提案 ID / DSH 审批 / Domain 原始响应）'),
                  h('div', { className: 'mammoth-pre' }, JSON.stringify(technical, null, 2))))
            })),
        message === null ? null : h('div', { className: 'mammoth-note' }, message.text))
    }

    function renderCatalog({ t, title, items, error }) {
      if (error) return h(ErrorNote, { error })
      if (!Array.isArray(items) || items.length === 0) return h('div', { className: 'mammoth-note' }, t('noData'))
      return h('div', null,
        h(Section, { title }, h('table', { className: 'mammoth-table' },
          h('thead', null, h('tr', null, h('th', null, 'ID'), h('th', null, 'Name'), h('th', null, t('status')))),
          h('tbody', null, items.map(item => h('tr', { key: item.id },
            h('td', null, item.id),
            h('td', null, item.name),
            h('td', null, item.status ?? '')))))))
    }

    function renderSettings({ t, health, snap, audit, location, clientInfo }) {
      return h('div', null,
        h(Section, { title: t('diagnostics') },
          h('div', { className: 'mammoth-card' },
            h(Kv, { rows: [
              [t('bundle'), health.value?.version ?? '—'],
              ['plugin', health.value?.plugin ?? '—'],
              ['startedAt', health.value?.startedAt ?? '—'],
              [t('location'), location],
              [t('domainSession'), health.value?.domain?.identity ? `${health.value.domain.identity.username} (${health.value.domain.identity.id})` : '—'],
              ['domainBaseUrl', health.value?.domain?.baseUrl ?? '—'],
              ['domainHealth', health.value?.domain?.health?.ok === true ? 'ok' : JSON.stringify(health.value?.domain?.health ?? null)],
              ['agentWrite', health.value?.agentWrite ? (health.value.agentWrite.configured ? 'ok' : 'not configured') : 'missing'],
              [t('mcpTools'), (health.value?.mcp?.tools ?? []).map(tool => `${tool.registered ? '✓' : '✗'} ${tool.name}`).join('\n')],
              ['activeProjectId', snap.activeProjectId ?? '—'],
              ['sessionsBound', String(health.value?.sessionCount ?? '—')],
              ['proposals', String(health.value?.proposalCount ?? '—')],
              ['configError', health.value?.configError ?? health.error ?? '—'],
            ] }),
            h('div', { className: 'mammoth-pre', style: { marginTop: '10px' } }, JSON.stringify(health.value ?? { error: health.error }, null, 2))),
          h('div', { className: 'mammoth-note', style: { marginTop: '8px' } }, t('history')),
          h('div', { className: 'mammoth-pre' }, JSON.stringify(audit.items.slice(-12), null, 2)),
          audit.error === null ? null : h(ErrorNote, { error: audit.error }),
          h('div', { className: 'mammoth-section' }, 'Client runtime'),
          h('div', { className: 'mammoth-pre' }, JSON.stringify(clientInfo ?? {}, null, 2))))
    }

    // ---------------------------------------------------------------------
    // Sidebar icon (registered into sidebar.panellist; owner props: size/active)
    // ---------------------------------------------------------------------
    function MammothIcon(props) {
      const size = typeof props.size === 'number' ? props.size : 18
      return h('svg', {
        width: size, height: size, viewBox: '0 0 32 32', role: 'img', 'aria-label': 'Mammoth',
        style: { display: 'block' },
      },
      h('rect', { width: 32, height: 32, rx: 8, fill: props.active ? 'var(--dsw-alias-brand-primary, #2f81f7)' : 'currentColor', opacity: props.active ? 1 : 0.16 }),
      h('path', {
        d: 'M7 23V11l5 6 4-8 4 8 5-6v12',
        fill: 'none',
        stroke: props.active ? '#fff' : 'currentColor',
        strokeWidth: 2.4,
        strokeLinecap: 'round',
        strokeLinejoin: 'round',
      }))
    }

    // ---------------------------------------------------------------------
    // Conversation header project badge (conversation.session.header.actions)
    // ---------------------------------------------------------------------
    function ProjectHeaderBadge(props) {
      const t = props.t ?? (key => key)
      const sessionId = typeof props.sessionId === 'string' ? props.sessionId : props.injected?.sessionId
      const [state, setState] = useState({ value: null, error: null })
      const snap = useShared()

      const load = useCallback(async () => {
        if (sessionId === undefined) return
        try {
          const value = await api.sessionContext(sessionId)
          setState({ value, error: null })
          const existing = shared.get().sessionContexts
          existing.set(sessionId, value)
        } catch (error) {
          setState({ value: null, error: error.message })
        }
      }, [sessionId])

      useEffect(() => { void load() }, [load, snap.activeProjectId])

      const project = state.value?.project ?? null
      const bound = state.value?.bound === true
      const label = project !== null
        ? `${project.code} ${project.name}`
        : t('unbound')
      return h('button', {
        className: 'mammoth-badge',
        'data-bound': String(bound),
        title: bound ? `${t('bound')}: ${project?.id ?? ''}` : t('unbound'),
        onClick: () => {
          if (typeof props.injected?.openProjectTab === 'function') props.injected.openProjectTab()
          else if (api !== null) void api.sessionContext(sessionId).catch(() => {})
        },
      },
      h('span', { 'aria-hidden': true }, '◆'),
      h('span', null, label),
      bound ? null : h('span', { className: 'mammoth-chip' }, t('bindSession')))
    }

    // ---------------------------------------------------------------------
    // Right-sidebar project context tab body (sidebar.right.pane.tab)
    // ---------------------------------------------------------------------
    function ProjectContextPanel(props) {
      const t = props.t ?? (key => key)
      const sessionId = typeof props.sessionId === 'string' ? props.sessionId : props.injected?.sessionId
      const [state, setState] = useState({ value: null, error: null, busy: false })
      const snap = useShared()

      const load = useCallback(async () => {
        if (sessionId === undefined) {
          setState({ value: null, error: 'no session', busy: false })
          return
        }
        try {
          const value = await api.sessionContext(sessionId)
          setState({ value, error: null, busy: false })
        } catch (error) {
          setState(current => ({ ...current, error: error.message, busy: false }))
        }
      }, [sessionId])

      useEffect(() => { void load() }, [load, snap.activeProjectId])

      const bind = useCallback(async () => {
        if (sessionId === undefined) return
        setState(current => ({ ...current, busy: true }))
        try {
          await api.bindSession(sessionId, snap.activeProjectId)
          await load()
        } catch (error) {
          setState(current => ({ ...current, error: error.message, busy: false }))
        }
      }, [sessionId, snap.activeProjectId, load])

      const value = state.value
      const project = value?.project ?? null
      return h('div', { className: 'mammoth-panel' },
        h(Styles),
        h('h3', null, t('projectContext')),
        value === null ? h(Loading, { t }) : null,
        state.error === null ? null : h(ErrorNote, { error: state.error }),
        project === null
          ? h('div', null,
            h('div', { className: 'mammoth-note' }, t('noActiveProject')),
            snapshotNote({ t, value, snap }))
          : h('div', null,
            h('div', { className: 'mammoth-inline' },
              h('strong', null, `${project.code} ${project.name}`),
              h(Chip, { tone: 'ok' }, project.status)),
            h('div', { className: 'mammoth-note', style: { margin: '6px 0 10px' } },
              value?.bound === true ? t('bound') : t('unbound')),
            h(Kv, { rows: [
              ['projectId', project.id],
              [t('customer'), project.customer?.name ?? project.customer?.id ?? '—'],
              [t('members'), project.members?.map(member => `${member.displayName ?? member.username ?? member.userId} (${member.role})`).join(', ') || '—'],
              [t('verifiedKnowledge'), `${project.knowledge?.verified ?? 0} / ${project.knowledge?.total ?? 0}`],
              [t('workItems'), String(project.workItems?.total ?? 0)],
            ] }),
            h('div', { className: 'mammoth-actions', style: { marginTop: '10px' } },
              value?.bound !== true
                ? h('button', { className: 'mammoth-btn mammoth-btn-primary', disabled: state.busy || snap.activeProjectId === null, onClick: () => { void bind() } }, t('bindSession'))
                : null,
              h('button', { className: 'mammoth-btn', onClick: () => { void load() } }, t('refresh')),
              h('button', { className: 'mammoth-btn', onClick: () => props.injected?.openWorkbench?.() }, t('workbench'))),
            h('div', { className: 'mammoth-section' }, t('verifiedKnowledge')),
            project.knowledge?.items?.length
              ? h('ul', { className: 'mammoth-list' }, project.knowledge.items.filter(doc => doc.status === 'verified').slice(0, 10).map(doc => h('li', { key: doc.id }, `${doc.title} · ${doc.docType}`)))
              : h('div', { className: 'mammoth-note' }, t('noData')),
            h('div', { className: 'mammoth-section' }, t('workItems')),
            project.workItems?.items?.length
              ? h('ul', { className: 'mammoth-list' }, project.workItems.items.slice(0, 10).map(item => h('li', { key: item.id }, `${item.itemNo} ${item.title} [${item.status}]`)))
              : h('div', { className: 'mammoth-note' }, t('noData'))))
    }

    function snapshotNote({ t, value, snap }) {
      if (snap.activeProjectId === null) return null
      return h('div', { className: 'mammoth-note', style: { marginTop: '6px' } },
        `${t('activeProject')}: ${snap.activeProject?.code ?? snap.activeProjectId}`)
    }

    // ---------------------------------------------------------------------
    // Tool result cards (tool.call.toolview, keyed by wire tool name)
    // ---------------------------------------------------------------------
    function parseResultJson(block) {
      if (block === null || typeof block !== 'object') return { value: null, error: null }
      if (block.meta !== undefined && block.meta !== null) return { value: block.meta, error: null }
      const content = Array.isArray(block.content) ? block.content : []
      const text = content.filter(part => part !== null && typeof part === 'object' && part.type === 'text').map(part => part.text).join('\n')
      if (text.trim() === '') return { value: null, error: null }
      try {
        return { value: JSON.parse(text), error: null }
      } catch {
        return { value: null, error: text.slice(0, 600) }
      }
    }

    function ToolCardFrame({ title, chips, children }) {
      return h('div', { className: 'mammoth-toolcard' },
        h(Styles),
        h('div', { className: 'mammoth-toolcard-head' },
          h('span', null, '◆ Mammoth'),
          h('span', null, title),
          ...(chips ?? []).map((chip, index) => h(Chip, { key: `c${index}`, tone: chip.tone }, chip.text))),
        h('div', { className: 'mammoth-toolcard-body' }, children))
    }

    function ToolCardProposal(props) {
      const t = props.t ?? (key => key)
      try {
        if (props.phase === 'preparing' || props.phase === 'start') {
          return h(ToolCardFrame, { title: t('pendingProposal') }, h('div', { className: 'mammoth-note' }, 'Agent 正在创建待审核提案…'))
        }
        const parsed = parseResultJson(props.block)
        const value = parsed.value
        if (value === null || typeof value !== 'object') {
          return h(ToolCardFrame, { title: t('proposeTitle') }, h('div', { className: 'mammoth-note' }, parsed.error ?? 'no structured result'))
        }
        const tone = value.status === 'pending' ? 'warn' : value.status === 'rejected_by_approval_policy' ? 'error' : 'plain'
        return h(ToolCardFrame, {
          title: t('pendingProposal'),
          chips: [{ text: String(value.status ?? 'unknown'), tone }],
        },
        h(Kv, { rows: [
          ['proposalId', value.proposalId],
          ['project', value.projectCode ? `${value.projectCode} ${value.projectName ?? ''}` : value.projectId],
          ['title', value.title],
          ['priority', value.priority],
          ['dshApproval', value.approval],
          ['message', value.message],
        ] }),
        value.status === 'pending'
          ? h('div', { className: 'mammoth-note', style: { marginTop: '6px' } }, '未写入 Domain Service。请在 Mammoth 工作台「待审核提案」中人工批准。')
          : null)
      } catch (error) {
        return h(ToolCardFrame, { title: t('proposeTitle') }, h(ErrorNote, { error: error.message }))
      }
    }

    function ToolCardKnowledgeSearch(props) {
      try {
        if (props.phase !== 'result') return h(ToolCardFrame, { title: 'Mammoth 知识检索' }, h('div', { className: 'mammoth-note' }, '检索中…'))
        const parsed = parseResultJson(props.block)
        const items = Array.isArray(parsed.value) ? parsed.value : []
        if (items.length === 0) {
          return h(ToolCardFrame, { title: 'Mammoth 知识检索' }, h('div', { className: 'mammoth-note' }, parsed.error ?? '无命中'))
        }
        return h(ToolCardFrame, {
          title: 'Mammoth 经验证知识',
          chips: [{ text: `${items.length} chunks`, tone: 'ok' }],
        },
        h('ul', { className: 'mammoth-list' }, items.slice(0, 6).map((item, index) => h('li', { key: item.chunkId ?? index },
          h('div', { className: 'mammoth-inline' }, h('strong', null, item.title ?? 'knowledge'), h(Chip, null, `${item.sourceRefCount ?? 0} sources`)),
          h('div', { className: 'mammoth-note' }, String(item.text ?? '').slice(0, 260))))))
      } catch (error) {
        return h(ToolCardFrame, { title: 'Mammoth 知识检索' }, h(ErrorNote, { error: error.message }))
      }
    }

    function ToolCardKnowledgeDocument(props) {
      try {
        if (props.phase !== 'result') return h(ToolCardFrame, { title: 'Mammoth 知识文档' }, h('div', { className: 'mammoth-note' }, '读取中…'))
        const parsed = parseResultJson(props.block)
        const doc = parsed.value
        if (doc === null || typeof doc !== 'object') {
          return h(ToolCardFrame, { title: 'Mammoth 知识文档' }, h('div', { className: 'mammoth-note' }, parsed.error ?? 'no document'))
        }
        return h(ToolCardFrame, {
          title: String(doc.title ?? 'knowledge document'),
          chips: [{ text: String(doc.status ?? ''), tone: doc.status === 'verified' ? 'ok' : 'warn' }, { text: String(doc.docType ?? '') }],
        },
        h(Kv, { rows: [['documentId', doc.id], ['reviewedAt', doc.reviewedAt], ['sources', String((doc.sourceRefs ?? []).length)]] }),
        h('div', { className: 'mammoth-pre' }, String(doc.body ?? '').slice(0, 700)))
      } catch (error) {
        return h(ToolCardFrame, { title: 'Mammoth 知识文档' }, h(ErrorNote, { error: error.message }))
      }
    }

    function ToolCardRequirementContext(props) {
      try {
        if (props.phase !== 'result') return h(ToolCardFrame, { title: 'Mammoth 需求上下文' }, h('div', { className: 'mammoth-note' }, '读取中…'))
        const parsed = parseResultJson(props.block)
        const item = parsed.value
        if (item === null || typeof item !== 'object') {
          return h(ToolCardFrame, { title: 'Mammoth 需求上下文' }, h('div', { className: 'mammoth-note' }, parsed.error ?? 'no requirement'))
        }
        const blocking = Array.isArray(item.blocking) ? item.blocking : []
        return h(ToolCardFrame, {
          title: `${item.itemNo ?? ''} ${item.title ?? ''}`.trim(),
          chips: [
            { text: String(item.status ?? ''), tone: 'plain' },
            { text: item.readinessOk === true ? 'ready' : `blocked ×${blocking.length}`, tone: item.readinessOk === true ? 'ok' : 'warn' },
          ],
        },
        h(Kv, { rows: [['priority', item.priority], ['contextSnapshotId', item.contextSnapshotId], ['approvedSolution', item.approvedSolutionTitle]] }),
        blocking.length === 0 ? null : h('div', null,
          h('div', { className: 'mammoth-section' }, 'Blocking'),
          h('ul', { className: 'mammoth-list' }, blocking.slice(0, 6).map((reason, index) => h('li', { key: index }, String(reason))))))
      } catch (error) {
        return h(ToolCardFrame, { title: 'Mammoth 需求上下文' }, h(ErrorNote, { error: error.message }))
      }
    }

    // Client-side diagnostics: every registration step is attempted
    // independently and failures are recorded so the workbench can show them
    // inside the product UI (there is no accessible browser console in the
    // Desktop shell for this spike).
    const clientDiagnostics = { errors: [], notes: [], startedAt: new Date().toISOString() }
    function recordDiag(phase, error) {
      const message = error instanceof Error ? `${error.name}: ${error.message}` : String(error)
      clientDiagnostics.errors.push({ phase, message })
      console.warn(`[mammoth] client registration failed in ${phase}:`, error)
    }
    function guardDiag(phase, fn) {
      try {
        return fn()
      } catch (error) {
        recordDiag(phase, error)
        return undefined
      }
    }

    // Surface bindings: assigned by `apply` (which closes over the Cordis
    // context) and read by the proxy components registered into slots.
    const MainBindings = { openConversation: () => {}, openWorkbench: () => {} }
    const HeaderBindings = { openProjectTab: () => {} }
    const PanelBindings = { openWorkbench: () => {} }

    // ---------------------------------------------------------------------
    // Plugin body
    // ---------------------------------------------------------------------
    return {
      inject: ['slots', 'locale', 'layout', 'sidebarRightTabs', 'sidebarRight'],
      apply(ctx) {
        clientDiagnostics.notes.push('apply entered')
        // 1) Locale first; a failure must not stop the UI registrations below.
        const t = guardDiag('locale.register', () => {
          ctx.effect(() => ctx.locale.register(NS, { zh, en }), 'mammoth: locale dictionaries')
          return ctx.locale.bind(NS)
        }) ?? (key => key)

        // 2) The two registrations that make the entry visible at all.
        guardDiag('slots.inject(sidebar.panellist)', () => ctx.slots.inject('sidebar.panellist', () => ctx.slots.register({
          name: 'sidebar.panellist',
          id: PANEL_ID,
          order: 10,
          label: () => t('panel'),
          locale: NS,
        }, MammothIcon)))
        clientDiagnostics.notes.push('sidebar.panellist injected')

        guardDiag('slots.inject(main)', () => ctx.slots.inject('main', () => ctx.slots.register({
          name: 'main',
          key: PANEL_ID,
          locale: NS,
        }, WorkbenchPageProxy)))
        clientDiagnostics.notes.push('main injected')

        // 3) Optional product surfaces: each failure is isolated.
        guardDiag('sidebarRightTabs.register', () => ctx.effect(() => ctx.sidebarRightTabs.register({
          id: TAB_TYPE_ID,
          kind: RIGHT_TAB_KIND,
          title: () => t('projectContext'),
          // The official registry shape is an ARRAY of guide entries; each entry
          // carries its own id and order (verified against
          // @deepseek-ai/dsh-client-ui-sidebar-right tab-registry.ts).
          guide: [{
            id: 'mammoth-project-context',
            order: 10,
            title: () => t('projectContext'),
            description: () => t('guideHint'),
          }],
        }), 'mammoth: right sidebar tab type'))

        const openProjectTab = () => {
          try {
            ctx.sidebarRight.openTab(RIGHT_TAB_KIND)
          } catch (error) {
            recordDiag('sidebarRight.openTab', error)
          }
        }
        const openWorkbench = () => {
          try { ctx.layout.selectPanel(PANEL_ID) } catch (error) { recordDiag('layout.selectPanel', error) }
        }
        const openConversation = () => {
          try { ctx.layout.selectPanel(null) } catch (error) { recordDiag('layout.selectPanel(null)', error) }
        }
        MainBindings.openConversation = openConversation
        MainBindings.openWorkbench = openWorkbench
        HeaderBindings.openProjectTab = openProjectTab
        PanelBindings.openWorkbench = openWorkbench

        guardDiag('slots.inject(conversation.session.header.actions)', () => ctx.slots.inject('conversation.session.header.actions', () => ctx.slots.register({
          name: 'conversation.session.header.actions',
          id: 'mammoth-project-badge',
          order: 20,
          locale: NS,
        }, ProjectHeaderBadgeProxy)))

        guardDiag('slots.inject(sidebar.right.pane.tab)', () => ctx.slots.inject('sidebar.right.pane.tab', () => ctx.slots.register({
          name: 'sidebar.right.pane.tab',
          key: TAB_TYPE_ID,
          locale: NS,
        }, ProjectContextPanelProxy)))

        const cards = [
          [TOOL_PROPOSE, ToolCardProposal],
          [TOOL_MCP_SEARCH, ToolCardKnowledgeSearch],
          [TOOL_MCP_DOCUMENT, ToolCardKnowledgeDocument],
          [TOOL_MCP_REQUIREMENT, ToolCardRequirementContext],
        ]
        for (const [toolName, Card] of cards) {
          guardDiag(`slots.inject(tool.call.toolview:${toolName})`, () => ctx.slots.inject('tool.call.toolview', () => ctx.slots.register({
            name: 'tool.call.toolview',
            key: toolName,
            locale: NS,
          }, Card)))
        }

        console.log('[mammoth] client plugin active', JSON.stringify(clientDiagnostics))
      },
    }

    function WorkbenchPageProxy(props) {
      return h(WorkbenchPage, { ...props, openConversation: MainBindings.openConversation })
    }
    function ProjectHeaderBadgeProxy(props) {
      return h(ProjectHeaderBadge, { ...props, injected: {
        openProjectTab: HeaderBindings.openProjectTab,
      } })
    }
    function ProjectContextPanelProxy(props) {
      return h(ProjectContextPanel, { ...props, injected: {
        openWorkbench: PanelBindings.openWorkbench,
      } })
    }
  },
})
