/**
 * Mammoth M0 spike — Host half of a minimal out-of-tree DSH bundle.
 *
 * Purpose (disposable spike fixture, not product code):
 * - prove a workspace-authored Host plugin activates in the `desktop` profile;
 * - register one model-facing Agent tool through `ctx.tools` / `defineTool`;
 * - exercise the documented Host-side persistence seam (`ctx.storageDomain`).
 *
 * S1b (2026-11-12): the bundle now declares exact `@deepseek-ai/*` dependencies and installs
 * them beside its own checkout, which is what makes `import '@deepseek-ai/dsh-tools'` resolve.
 * See docs/spikes/S1-DSH-EXTENSION-SPIKE.md for the verification record and limits.
 */

import { defineTool } from '@deepseek-ai/dsh-tools'
import { defineDomain, domainTable } from '@deepseek-ai/dsh-storage-domain'
import { z } from 'zod'

/** Cordis plugin name. */
export const name = 'mammoth-spike'

/** Services that must be present before `apply` runs. */
export const inject = ['tools', 'storageDomain']

const PLUGIN_ID = '@local/mammoth-spike'
const DOMAIN = 'mammoth_spike'
const TABLE = 'counters'
const KEY = 'probe'

/** Declare the fixture's own schema-validated KV domain (zod record schema). */
const counterSpec = defineDomain({
  name: DOMAIN,
  version: 1,
  tables: { [TABLE]: domainTable(z.object({ count: z.number() })) },
})

/**
 * Register the spike probe tool for model and PTC callers.
 * @param {import('@deepseek-ai/cordis').Context} ctx - context providing tools and storageDomain.
 */
export async function apply(ctx) {
  const status = { mode: 'unavailable', detail: '' }
  let domain = null
  try {
    domain = await ctx.storageDomain.open(counterSpec)
    status.mode = 'storage-domain'
    ctx.effect(() => () => {
      domain.close()
    })
  } catch (error) {
    status.detail = error instanceof Error ? error.message : String(error)
  }

  ctx.tools.register(defineTool({
    name: 'mammoth_spike_probe',
    description:
      'Report the Mammoth M0 spike plugin status and increment a Host-side counter. '
      + 'Read-only with respect to the workspace: it writes only its own storage-domain record.',
    parameters: {
      note: { type: 'string', description: 'Optional note echoed back with this probe call.' },
    },
    output: {
      schema: {
        type: 'object',
        additionalProperties: false,
        properties: {
          plugin: { type: 'string', required: true, description: 'Bundle identifier.' },
          persistence: { type: 'string', required: true, description: 'storage-domain, or unavailable with a reason.' },
          persistenceDetail: { type: 'string', description: 'Why the storage seam was unavailable.' },
          count: { type: 'number', required: true, description: 'Counter after this call.' },
          note: { type: 'string', description: 'Echo of the optional note.' },
        },
      },
      render: (_args, value) => [{
        type: 'text',
        text: `mammoth-spike probe: persistence=${value.persistence} count=${value.count}`,
      }],
    },
    async execute(args) {
      let count = 1
      if (domain === null) {
        return {
          plugin: PLUGIN_ID,
          persistence: status.mode,
          persistenceDetail: status.detail,
          count,
          ...(args.note === undefined ? {} : { note: args.note }),
        }
      }
      const table = domain.table(TABLE)
      const current = table.get(KEY)
      if (current === undefined) {
        await table.put(KEY, { count })
      } else {
        count = current.count + 1
        await table.update(KEY, (record) => ({ count: record.count + 1 }))
      }
      return {
        plugin: PLUGIN_ID,
        persistence: status.mode,
        count,
        ...(args.note === undefined ? {} : { note: args.note }),
      }
    },
  }))
}
