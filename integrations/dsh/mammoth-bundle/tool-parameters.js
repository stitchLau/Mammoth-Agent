/**
 * Raw JSON Schema `parameters` for every Mammoth model-facing tool.
 *
 * These are raw schemas passed straight to `ctx.tools.register()`, not the
 * @deepseek-ai/dsh-tools author DSL used by `defineTool()`. Consequence:
 * required parameters are declared once, as a root-level `required: [...]`
 * string array; a per-property `required: true` is invalid here and is rejected
 * by the model provider on every request (see 2026-11-13 incident).
 *
 * Keeping the schemas in one module lets `registerTool()` validate them at
 * activation and `scripts/check-tool-schemas.mjs` validate them before reload.
 */

/** @type {Record<string, {type: 'object', additionalProperties: boolean, properties: Record<string, unknown>, required?: string[]}>} */
export const TOOL_PARAMETERS = {
  mammoth_list_projects: {
    type: 'object',
    additionalProperties: false,
    properties: {
      limit: { type: 'integer', description: 'Maximum number of projects to return (default 20).' },
    },
  },
  mammoth_get_project_context: {
    type: 'object',
    additionalProperties: false,
    properties: {
      projectId: { type: 'string', description: 'Optional project id; defaults to the project bound to this session.' },
    },
  },
  mammoth_propose_work_item: {
    type: 'object',
    additionalProperties: false,
    properties: {
      title: { type: 'string', description: 'Proposed work-item title.' },
      summary: { type: 'string', description: 'Proposed work-item summary/background.' },
      priority: {
        type: 'string',
        enum: ['low', 'normal', 'high', 'critical'],
        description: 'Priority; defaults to normal.',
      },
      projectId: { type: 'string', description: 'Project id; defaults to the project bound to this session.' },
      rationale: { type: 'string', description: 'Why this change is proposed (evidence/rationale shown to the approver).' },
    },
    required: ['title', 'summary'],
  },
}
