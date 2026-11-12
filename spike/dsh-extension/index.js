/**
 * Mammoth M0 spike — Host half of a minimal out-of-tree DSH bundle.
 *
 * Purpose (disposable spike fixture, not product code):
 * - prove a workspace-authored Host plugin activates in the `desktop` profile;
 * - register one model-facing Agent tool through `ctx.tools` / `defineTool`;
 * - exercise a Host-side persistence round-trip that survives a restart;
 * - report whether the documented `ctx.storageDomain` seam is reachable.
 *
 * Spike finding recorded on 2026-11-12 (see docs/spikes/S1-DSH-EXTENSION-SPIKE.md):
 * an out-of-tree bundle's modules resolve Node packages from its own checkout, so
 * `import '@deepseek-ai/dsh-tools'` fails with ERR_MODULE_NOT_FOUND unless the bundle
 * declares and installs those packages itself. This fixture therefore imports only
 * Node built-ins and registers the same definition shape that `defineTool` produces.
 */

import { mkdirSync, readFileSync, writeFileSync } from 'node:fs'
import { join } from 'node:path'

/** Cordis plugin name. */
export const name = 'mammoth-spike'

/** Services that must be present before `apply` runs. */
export const inject = ['tools']

const PLUGIN_ID = '@local/mammoth-spike'
const STATE_FILE = 'mammoth-spike-probe.json'

/** Resolve the fixture's own state file under DSH_HOME so a restart can read it back. */
function statePath() {
  const home = process.env.DSH_HOME || join(process.env.USERPROFILE || '.', '.dsh')
  return join(home, 'cache', 'mammoth-spike', STATE_FILE)
}

/** Read the persisted counter; a missing or malformed file means zero. */
function readCount(path) {
  try {
    const parsed = JSON.parse(readFileSync(path, 'utf8'))
    return typeof parsed.count === 'number' && Number.isFinite(parsed.count) ? parsed.count : 0
  } catch {
    return 0
  }
}

/**
 * Register the spike probe tool for model and PTC callers.
 * @param {import('@deepseek-ai/cordis').Context} ctx - context providing the tool registry.
 */
export function apply(ctx) {
  ctx.tools.register({
    name: 'mammoth_spike_probe',
    description:
      'Report the Mammoth M0 spike plugin status and increment its Host-side counter. '
      + 'Read-only with respect to the workspace: it writes only its own state file under DSH_HOME.',
    parameters: {
      note: { type: 'string', description: 'Optional note echoed back with this probe call.' },
    },
    output: {
      schema: {
        type: 'object',
        additionalProperties: false,
        properties: {
          plugin: { type: 'string', description: 'Bundle identifier.' },
          persistence: { type: 'string', description: 'Where the counter is stored.' },
          persistencePath: { type: 'string', description: 'Absolute state file path.' },
          count: { type: 'number', description: 'Counter after this call.' },
          note: { type: 'string', description: 'Echo of the optional note.' },
        },
        required: ['plugin', 'persistence', 'persistencePath', 'count'],
      },
      render: (_args, value) => [{
        type: 'text',
        text: `mammoth-spike probe: persistence=${value.persistence} count=${value.count} (${value.persistencePath})`,
      }],
    },
    async execute(args) {
      const path = statePath()
      mkdirSync(join(path, '..'), { recursive: true })
      const count = readCount(path) + 1
      writeFileSync(path, JSON.stringify({ count, updatedAt: new Date().toISOString() }), 'utf8')
      return {
        plugin: PLUGIN_ID,
        persistence: 'plugin-json-file',
        persistencePath: path,
        count,
        ...(args && args.note === undefined ? {} : { note: args.note }),
      }
    },
  })
}
