import { pageCount } from '../lib/pagination'

export function Pagination(props: {
  page: number
  total: number
  pageSize: number
  onPageChange: (page: number) => void
}) {
  const pages = pageCount(props.total, props.pageSize)
  if (props.total <= props.pageSize) {
    return null
  }
  return (
    <div className="pagination" role="group" aria-label="分页">
      <button
        type="button"
        className="secondary"
        disabled={props.page <= 1}
        onClick={() => props.onPageChange(props.page - 1)}
      >
        上一页
      </button>
      <span className="hint">
        第 {props.page} / {pages} 页（共 {props.total} 条）
      </span>
      <button
        type="button"
        className="secondary"
        disabled={props.page >= pages}
        onClick={() => props.onPageChange(props.page + 1)}
      >
        下一页
      </button>
    </div>
  )
}
