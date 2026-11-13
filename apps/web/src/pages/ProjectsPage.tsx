import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { useSession } from '../app/session'
import { ErrorBanner, LoadingState, PageHeader } from '../components/ui'
import {
  createCustomer,
  createProduct,
  createProject,
  listCustomers,
  listProducts,
  listProjects,
  type Customer,
  type Product,
  type Project,
} from '../lib/api'
import { formatProjectCode } from '../lib/format'

/** Landing page: project list plus the minimal master-data setup flow (UI-SPEC P03). */
export default function ProjectsPage() {
  const { state } = useSession()
  const orgAdmin = state.status === 'authenticated' && state.user.orgAdmin
  const [projects, setProjects] = useState<Project[] | null>(null)
  const [customers, setCustomers] = useState<Customer[]>([])
  const [products, setProducts] = useState<Product[]>([])
  const [customerName, setCustomerName] = useState('')
  const [productName, setProductName] = useState('')
  const [projectCode, setProjectCode] = useState('')
  const [projectName, setProjectName] = useState('')
  const [customerId, setCustomerId] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const reload = useCallback(async () => {
    const [projectList, customerList, productList] = await Promise.all([
      listProjects(),
      listCustomers(),
      listProducts(),
    ])
    setProjects(projectList)
    setCustomers(customerList)
    setProducts(productList)
    setCustomerId((current) => current || customerList[0]?.id || '')
  }, [])

  useEffect(() => {
    reload().catch((problem: unknown) => setError(problem instanceof Error ? problem.message : String(problem)))
  }, [reload])

  async function run(action: () => Promise<void>) {
    setBusy(true)
    setError(null)
    try {
      await action()
    } catch (problem: unknown) {
      setError(problem instanceof Error ? problem.message : String(problem))
    } finally {
      setBusy(false)
    }
  }

  return (
    <>
      <PageHeader
        title="我的项目"
        subtitle="每个项目有独立的资料、知识与成员权限"
        actions={<span className="hint">{projects ? `${projects.length} 个项目` : ''}</span>}
      />
      {error && <ErrorBanner message={error} onRetry={() => void reload()} />}
      {projects === null && <LoadingState label="正在加载项目…" />}
      {projects !== null && projects.length === 0 && (
        <p className="state">还没有项目。先在下方创建客户与项目。</p>
      )}
      {projects !== null && projects.length > 0 && (
        <ul className="project-grid">
          {projects.map((project) => (
            <li key={project.id}>
              <Link className="project-card" to={`/projects/${project.id}/artifacts`}>
                <span className="code">{project.code}</span>
                <strong>{project.name}</strong>
                <span className="hint">创建于 {project.createdAt}</span>
              </Link>
            </li>
          ))}
        </ul>
      )}

      <details className="panel" open={projects?.length === 0}>
        <summary>新建客户 / 产品 / 项目</summary>
        <div className="grid">
          {orgAdmin && (
            <>
              <form
                className="field"
                onSubmit={(event) => {
                  event.preventDefault()
                  void run(async () => {
                    await createCustomer(customerName.trim())
                    setCustomerName('')
                    await reload()
                  })
                }}
              >
                <h3>新建客户</h3>
                <input value={customerName} onChange={(e) => setCustomerName(e.target.value)} placeholder="客户名称" />
                <button type="submit" disabled={busy || customerName.trim() === ''}>
                  创建客户
                </button>
                <p className="hint">{customers.length} 个客户</p>
              </form>

              <form
                className="field"
                onSubmit={(event) => {
                  event.preventDefault()
                  void run(async () => {
                    await createProduct(productName.trim())
                    setProductName('')
                    await reload()
                  })
                }}
              >
                <h3>新建产品</h3>
                <input value={productName} onChange={(e) => setProductName(e.target.value)} placeholder="产品名称" />
                <button type="submit" disabled={busy || productName.trim() === ''}>
                  创建产品
                </button>
                <p className="hint">{products.length} 个产品</p>
              </form>
            </>
          )}

          <form
            className="field"
            onSubmit={(event) => {
              event.preventDefault()
              void run(async () => {
                await createProject(customerId, projectCode.trim(), projectName.trim())
                setProjectCode('')
                setProjectName('')
                await reload()
              })
            }}
          >
            <h3>新建项目</h3>
            <select value={customerId} onChange={(e) => setCustomerId(e.target.value)} aria-label="选择客户">
              {customers.length === 0 && <option value="">（先创建客户）</option>}
              {customers.map((customer) => (
                <option key={customer.id} value={customer.id}>
                  {customer.name}
                </option>
              ))}
            </select>
            <input
              value={projectCode}
              onChange={(e) => setProjectCode(e.target.value)}
              placeholder={`项目编码，如 ${formatProjectCode('ACME', 'MES', 2026)}`}
            />
            <input value={projectName} onChange={(e) => setProjectName(e.target.value)} placeholder="项目名称" />
            <button type="submit" disabled={busy || customerId === '' || projectCode.trim() === ''}>
              创建项目
            </button>
          </form>
        </div>
        {!orgAdmin && (
          <p className="hint">客户/产品主数据由组织管理员维护；创建项目需要已有客户。</p>
        )}
      </details>
    </>
  )
}
