package com.mammoth.domain.web;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * ADR-0015 P3: minimal human approval page for the controlled-write proposals.
 *
 * <p>Served by the Domain Service and executed in a normal browser with the human's own Domain
 * session/CSRF cookie. This is intentionally a thin page (not the final SPA IA): it proves that the
 * approval decision is made by a human browser session, while the DSH Host only holds the propose
 * token and the isolated approval service applies approved proposals.
 */
@RestController
public class ApprovalPageController {

  private static final String PAGE = """
      <!doctype html>
      <html lang="zh-CN"><head><meta charset="utf-8"/><title>Mammoth 提案审批</title>
      <style>
        body{font-family:system-ui,Segoe UI,Arial;margin:24px;max-width:980px;color:#1f2328}
        h1{font-size:20px} .muted{color:#59636e;font-size:13px}
        .card{border:1px solid #d0d7de;border-radius:8px;padding:12px;margin:10px 0}
        .row{display:flex;gap:8px;align-items:center;flex-wrap:wrap}
        .chip{font-size:12px;padding:1px 8px;border-radius:999px;background:#eaeef2}
        .pending{background:#fff8c5} .approved{background:#dafbe1} .applied{background:#ddf4ff}
        button{padding:4px 12px;border-radius:6px;border:1px solid #d0d7de;background:#f6f8fa;cursor:pointer}
        .primary{background:#1f883d;color:#fff;border-color:#1f883d}
        .danger{color:#cf222e} pre{white-space:pre-wrap;background:#f6f8fa;padding:8px;border-radius:6px}
        a{color:#0969da}
      </style></head><body>
      <h1>Mammoth 提案审批（人类会话）</h1>
      <p class="muted">此页面由 Domain Service 提供，使用你本人的 Domain 登录会话审批；DSH/Agent 无法代替你操作。
      批准后由独立审批服务自动应用并在 Domain 生成正式对象。合成数据环境。</p>
      <div id="loginBox" style="display:none">
        <p><input id="username" placeholder="Domain 用户名" size="20"/>
           <input id="password" type="password" placeholder="密码" size="20"/>
           <button id="loginBtn" class="primary">登录 Domain</button></p>
        <p class="muted">本 Domain Service 不托管 SPA，因此登录直接在审批页内完成；登录后使用你自己的会话审批。</p>
      </div>
      <p id="auth" class="muted">正在检查登录状态…</p>
      <p><label>项目 ID：<input id="projectId" size="40"/></label> <button id="refresh">刷新</button></p>
      <div id="list"></div>
      <script>
        const qs = new URLSearchParams(location.search);
        const projectInput = document.getElementById('projectId');
        projectInput.value = qs.get('projectId') || '';
        function cookie(name){
          const row = document.cookie.split(';').map(v => v.trim()).find(v => v.startsWith(name + '='));
          return row ? decodeURIComponent(row.slice(name.length + 1)) : '';
        }
        async function csrf(){
          await fetch('/api/auth/csrf', { credentials:'same-origin' });
          return cookie('XSRF-TOKEN');
        }
        async function me(){
          const r = await fetch('/api/auth/me', { credentials:'same-origin' });
          return r.ok ? r.json() : null;
        }
        function esc(v){ return String(v ?? '').replace(/[&<>"]/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;'}[c])); }
        async function decide(id, action){
          const headers = { 'content-type':'application/json', 'x-xsrf-token': await csrf() };
          const body = action === 'approve' ? { reason:'浏览器人工批准' } : { reason: prompt('拒绝理由') || '' };
          const r = await fetch('/api/agent/proposals/' + encodeURIComponent(id) + '/' + action,
            { method:'POST', credentials:'same-origin', headers, body: JSON.stringify(body) });
          const text = await r.text();
          alert((r.ok ? '成功：' : '失败：') + r.status + ' ' + text.slice(0, 300));
          await load();
        }
        async function load(){
          const user = await me();
          document.getElementById('loginBox').style.display = user ? 'none' : 'block';
          document.getElementById('auth').textContent = user
            ? '已登录：' + (user.displayName || user.username) + '（' + (user.username || '') + '）'
            : '未登录或会话已过期，请在下方登录 Domain。';
          if (!user) { document.getElementById('list').innerHTML = ''; return; }
          const projectId = projectInput.value.trim();
          if (!projectId) { document.getElementById('list').innerHTML = '<p class="muted">请输入项目 ID（工作台当前项目）。</p>'; return; }
          const r = await fetch('/api/agent/proposals?projectId=' + encodeURIComponent(projectId), { credentials:'same-origin' });
          if (!r.ok) { document.getElementById('list').innerHTML = '<p class="danger">读取提案失败：HTTP ' + r.status + '</p>'; return; }
          const items = await r.json();
          const list = document.getElementById('list');
          list.innerHTML = '';
          if (!items.length) { list.innerHTML = '<p class="muted">暂无提案。</p>'; return; }
          for (const p of items) {
            const card = document.createElement('div');
            card.className = 'card';
            card.innerHTML = '<div class="row"><strong>' + esc(p.title) + '</strong>'
              + '<span class="chip ' + esc(p.status) + '">' + esc(p.status) + '</span>'
              + '<span class="chip">' + esc(p.riskLevel) + '</span>'
              + '<span class="muted">' + esc(p.proposalType) + ' · ' + esc(p.createdAt) + '</span></div>'
              + '<div class="muted">提案 ID：' + esc(p.id) + '</div>'
              + '<div class="row" style="margin-top:8px;"></div>';
            if (p.status === 'pending' || p.status === 'awaiting_second') {
              const actions = card.querySelector('.row:last-child');
              const approve = document.createElement('button');
              approve.className = 'primary'; approve.textContent = '批准';
              approve.onclick = () => decide(p.id, 'approve');
              const reject = document.createElement('button');
              reject.className = 'danger'; reject.textContent = '拒绝';
              reject.onclick = () => decide(p.id, 'reject');
              actions.append(approve, reject);
            } else if (p.status === 'applied') {
              card.insertAdjacentHTML('beforeend', '<pre>' + esc(JSON.stringify({domainObjectId:p.domainObjectId}, null, 2)) + '</pre>');
            }
            list.append(card);
          }
        }
        document.getElementById('refresh').onclick = load;
        document.getElementById('loginBtn').onclick = async () => {
          const r = await fetch('/api/auth/login', {
            method: 'POST',
            credentials: 'same-origin',
            headers: { 'content-type': 'application/json' },
            body: JSON.stringify({
              username: document.getElementById('username').value,
              password: document.getElementById('password').value,
            }),
          });
          if (!r.ok) { alert('登录失败：HTTP ' + r.status); return; }
          document.getElementById('password').value = '';
          await load();
        };
        load();
      </script></body></html>
      """;

  @GetMapping(value = "/approval", produces = MediaType.TEXT_HTML_VALUE)
  public String approvalPage() {
    return PAGE;
  }
}
