/**
 * Local guard for the raw JSON Schema `parameters` passed to `ctx.tools.register()`.
 *
 * Why this file exists (2026-11-13 incident):
 * - DSH has two schema forms. `defineTool()` accepts the @deepseek-ai/dsh-tools
 *   author DSL, where a property may declare `required: true`; the compiler turns
 *   that into a root-level `required: [...]` array. A bundle that registers a
 *   hand-written raw JSON Schema through `ctx.tools.register()` must use the raw
 *   form directly and can never use `required: true`.
 * - In DSH 0.2.2-rc.2, `ToolRegistry.register()` asserts only `output.schema`
 *   (`@deepseek-ai/dsh-tools/lib/index.js`), not `definition.parameters`.
 * - The model provider validates the tool schema on every request. One invalid
 *   `parameters` schema therefore fails every request of every session that sees
 *   the global tool, not just the one call.
 *
 * The rules below mirror `assertSupportedJsonSchema()` from the same DSH version
 * (`@deepseek-ai/dsh-tools/lib/shared-DGx6D7zk.js`) plus the object-root
 * requirement providers impose on function parameters. Re-check them when DSH is
 * upgraded; this bundle targets DSH 0.2.2-rc.2 (`engines.dsh`).
 */

/** Scalar schema types accepted by the DSH raw subset. */
const SCALAR_TYPES = ['string', 'number', 'integer', 'boolean', 'null']

/** Every schema type accepted by the DSH raw subset. */
const SCHEMA_TYPES = [...SCALAR_TYPES, 'array', 'object']

/** Non-validating annotation keywords. */
const ANNOTATION_KEYS = new Set(['description', 'title', 'default', 'examples'])

/** Validating keywords accepted by the DSH raw subset. */
const CONSTRAINT_KEYS = new Set([
  'type', 'oneOf', 'properties', 'required', 'additionalProperties', 'items', 'enum', 'const',
])

/** Keywords that may not appear beside `oneOf`. */
const ONE_OF_SIBLING_KEYS = ['properties', 'required', 'additionalProperties', 'items', 'enum', 'const']

/** Keywords that are valid only on the listed declared types. */
const TYPE_BOUND_KEYS = {
  properties: ['object'],
  required: ['object'],
  additionalProperties: ['object'],
  items: ['array'],
  enum: SCALAR_TYPES,
  const: SCALAR_TYPES,
}

/** @param {unknown} value */
const isRecord = value => typeof value === 'object' && value !== null && !Array.isArray(value)

/** @param {unknown} value */
const isFiniteNumber = value => typeof value === 'number' && Number.isFinite(value)

/**
 * Whether one literal matches the declared scalar type.
 * @param {string} type
 * @param {unknown} value
 */
function scalarMatches(type, value) {
  switch (type) {
    case 'string': return typeof value === 'string'
    case 'number': return isFiniteNumber(value)
    case 'integer': return isFiniteNumber(value) && Number.isInteger(value)
    case 'boolean': return typeof value === 'boolean'
    case 'null': return value === null
    default: return false
  }
}

/**
 * Explain the most common form of this bug before falling back to a generic message.
 * @param {string} path
 * @param {unknown} value
 */
function requiredMessage(path, value) {
  if (value === true) {
    return `${path} must be an array of strings in a raw JSON Schema; "required: true" is only valid`
      + ' inside the dsh-tools author DSL used by defineTool(), not in a schema passed to ctx.tools.register()'
  }
  return `${path} must be an array of strings naming properties of the same object`
}

/**
 * Validate one raw JSON Schema node against the DSH subset.
 * @param {unknown} node
 * @param {string} path
 * @param {string[]} violations
 * @param {Set<object>} seen
 */
function checkNode(node, path, violations, seen) {
  if (!isRecord(node)) {
    violations.push(`${path} must be a schema object`)
    return
  }
  if (seen.has(node)) {
    violations.push(`${path} is circular`)
    return
  }
  seen.add(node)

  for (const key of Object.keys(node)) {
    if (CONSTRAINT_KEYS.has(key) || ANNOTATION_KEYS.has(key)) continue
    violations.push(`${path}.${key} is not a supported keyword (subset: type/oneOf/properties/required/additionalProperties/items/enum/const + annotations)`)
  }
  if (Object.hasOwn(node, 'description') && typeof node.description !== 'string') {
    violations.push(`${path}.description must be a string`)
  }
  if (Object.hasOwn(node, 'title') && typeof node.title !== 'string') {
    violations.push(`${path}.title must be a string`)
  }

  const hasType = Object.hasOwn(node, 'type')
  const hasOneOf = Object.hasOwn(node, 'oneOf')
  if (hasType && hasOneOf) {
    violations.push(`${path} cannot declare both type and oneOf`)
    return
  }
  if (!hasType && !hasOneOf) {
    for (const key of ONE_OF_SIBLING_KEYS) {
      if (Object.hasOwn(node, key)) violations.push(`${path}.${key} requires type or oneOf`)
    }
    return
  }
  if (hasOneOf) {
    for (const key of ONE_OF_SIBLING_KEYS) {
      if (Object.hasOwn(node, key)) violations.push(`${path}.${key} is not supported beside oneOf`)
    }
    if (!Array.isArray(node.oneOf) || node.oneOf.length < 2) {
      violations.push(`${path}.oneOf must be an array of at least two schemas`)
      return
    }
    node.oneOf.forEach((branch, index) => checkNode(branch, `${path}.oneOf[${index}]`, violations, seen))
    return
  }

  const type = node.type
  if (typeof type !== 'string' || !SCHEMA_TYPES.includes(type)) {
    violations.push(Array.isArray(type)
      ? `${path}.type must be a single type string (type arrays are not supported)`
      : `${path}.type must be one of ${SCHEMA_TYPES.join('/')}`)
    return
  }
  for (const [key, allowed] of Object.entries(TYPE_BOUND_KEYS)) {
    if (!Object.hasOwn(node, key)) continue
    // Point at the actual mistake first: a per-property `required: true` belongs
    // to the defineTool() author DSL, never to a raw schema.
    if (key === 'required' && node.required === true) {
      violations.push(requiredMessage(`${path}.required`, node.required))
    } else if (!allowed.includes(type)) {
      violations.push(`${path}.${key} is not supported on type "${type}"`)
    }
  }

  if (type === 'object') {
    if (Object.hasOwn(node, 'additionalProperties') && typeof node.additionalProperties !== 'boolean') {
      violations.push(`${path}.additionalProperties must be a boolean`)
    }
    const properties = Object.hasOwn(node, 'properties') ? node.properties : undefined
    if (properties !== undefined && !isRecord(properties)) {
      violations.push(`${path}.properties must be an object of schemas`)
    } else if (properties !== undefined) {
      for (const [key, value] of Object.entries(properties)) {
        checkNode(value, `${path}.properties.${key}`, violations, seen)
      }
    }
    if (Object.hasOwn(node, 'required')) {
      const required = node.required
      if (!Array.isArray(required) || required.some(entry => typeof entry !== 'string')) {
        violations.push(requiredMessage(`${path}.required`, required))
      } else {
        const declared = properties !== undefined && isRecord(properties) ? properties : {}
        for (const key of required) {
          if (!Object.hasOwn(declared, key)) {
            violations.push(`${path}.required names "${key}" which is not in properties`)
          }
        }
      }
    }
    return
  }

  if (type === 'array') {
    if (Object.hasOwn(node, 'items')) checkNode(node.items, `${path}.items`, violations, seen)
    return
  }

  if (Object.hasOwn(node, 'enum')) {
    const allowed = node.enum
    if (!Array.isArray(allowed) || allowed.length === 0 || !allowed.every(entry => scalarMatches(type, entry))) {
      violations.push(`${path}.enum must be a non-empty array of ${type} values`)
    }
  }
  if (Object.hasOwn(node, 'const') && !scalarMatches(type, node.const)) {
    violations.push(`${path}.const must be a ${type} value`)
  }
}

/**
 * Collect every violation in one tool's raw parameter schema.
 * @param {unknown} parameters
 * @returns {string[]} Path-qualified violations; empty means valid.
 */
export function validateToolParameters(parameters) {
  const violations = []
  checkNode(parameters, 'parameters', violations, new Set())
  if (isRecord(parameters) && parameters.type !== 'object') {
    violations.push('parameters.type must be "object": providers require function parameters to be an object schema')
  }
  return violations
}

/**
 * Fail plugin activation before an invalid schema can reach the model provider.
 * A thrown error leaves the rest of the profile usable; an invalid schema would
 * otherwise fail every request of every session.
 * @param {string} name
 * @param {unknown} parameters
 */
export function assertToolParameters(name, parameters) {
  const violations = validateToolParameters(parameters)
  if (violations.length === 0) return
  throw new Error(`tool "${name}" has an invalid parameter schema:\n- ${violations.join('\n- ')}`)
}
