# P3 integration notes

## ADR-0015 P3 integration (2026-11-16)

- The Host holds only the **propose** token (`config.agentWrite.token`); it creates proposals via
  `POST /api/agent/proposals` and lists its own proposals via `GET /api/agent/proposals/mine`.
- There is no Host-side approve/reject/apply path and no proposal stub/adapter. A human approves
  in the Domain browser page `GET /approval` (human session + CSRF); the isolated approval service
  (`java -jar ... --approval-service --watch ...`, apply token only) applies approved proposals.
- Verified end-to-end in the real DSH Desktop: Host tool propose -> `/approval` human click ->
  approval service auto-apply -> Domain `REQ-0006/0007/0008` with `createdBy` = human.
- Known follow-ups: expose proposal `title` in the API response; add the `/approval` link in the
  workbench proposals page; retire the legacy Spike adapter files and P2.1 dedicated task account.
