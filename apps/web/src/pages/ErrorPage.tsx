import { Link } from 'react-router-dom'

const COPY: Record<number, { title: string; hint: string }> = {
  403: { title: '没有访问权限', hint: '当前账号没有执行该操作所需的项目角色。请联系项目负责人（owner）调整成员角色。' },
  404: { title: '页面不存在', hint: '链接可能已失效，或该项目/知识文档已被移除。' },
}

export default function ErrorPage(props: { status: 403 | 404 }) {
  const copy = COPY[props.status]
  return (
    <div className="auth-page">
      <div className="auth-card">
        <h1>{props.status}</h1>
        <h2>{copy.title}</h2>
        <p className="hint">{copy.hint}</p>
        <p>
          <Link to="/projects">返回项目列表</Link>
        </p>
      </div>
    </div>
  )
}
