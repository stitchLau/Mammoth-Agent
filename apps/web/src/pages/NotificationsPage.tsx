import { useCallback, useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { BackLink, EmptyState, ErrorBanner, LoadingState, PageHeader } from '../components/ui'
import {
  listNotifications,
  readAllNotifications,
  readNotification,
  type NotificationView,
} from '../lib/api'

/** M4 S5: per-user in-app notification inbox. */
export default function NotificationsPage() {
  const navigate = useNavigate()
  const [notifications, setNotifications] = useState<NotificationView[]>([])
  const [loading, setLoading] = useState(true)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const reload = useCallback(async () => {
    const list = await listNotifications(100)
    setNotifications(list)
  }, [])

  useEffect(() => {
    setLoading(true)
    reload()
      .catch((problem: unknown) => setError(problem instanceof Error ? problem.message : String(problem)))
      .finally(() => setLoading(false))
  }, [reload])

  async function open(notification: NotificationView) {
    setBusy(true)
    setError(null)
    try {
      if (notification.status === 'unread') {
        await readNotification(notification.id)
      }
      if (notification.link) {
        navigate(notification.link)
      } else {
        await reload()
      }
    } catch (problem: unknown) {
      setError(problem instanceof Error ? problem.message : String(problem))
    } finally {
      setBusy(false)
    }
  }

  if (loading) return <LoadingState label="正在加载通知…" />

  return (
    <div>
      <BackLink to="/projects" label="返回项目" />
      <PageHeader
        title="通知"
        subtitle="站内通知默认启用；邮件/企微由管理员配置后才会外发。"
        actions={
          <button
            type="button"
            className="secondary"
            disabled={busy || notifications.every((notification) => notification.status === 'read')}
            onClick={() =>
              void (async () => {
                setBusy(true)
                setError(null)
                try {
                  await readAllNotifications()
                  await reload()
                } catch (problem: unknown) {
                  setError(problem instanceof Error ? problem.message : String(problem))
                } finally {
                  setBusy(false)
                }
              })()
            }
          >
            全部已读
          </button>
        }
      />
      {error && <ErrorBanner message={error} />}
      {notifications.length === 0 ? (
        <EmptyState title="没有通知" hint="方案提交/批准、交付批准、知识审核等事件会出现在这里。" />
      ) : (
        <ul className="notification-list">
          {notifications.map((notification) => (
            <li
              key={notification.id}
              className={notification.status === 'unread' ? 'notification unread' : 'notification'}
            >
              <div>
                <strong>{notification.title}</strong>
                {notification.body && <p className="hint">{notification.body}</p>}
                <p className="hint">
                  {notification.type} · {notification.createdAt}
                </p>
              </div>
              <button
                type="button"
                className="secondary"
                disabled={busy}
                onClick={() => void open(notification)}
              >
                {notification.link ? '查看' : notification.status === 'unread' ? '标为已读' : '已读'}
              </button>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}
