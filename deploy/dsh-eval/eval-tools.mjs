import { defineTool } from '@deepseek-ai/dsh-tools'

export const name = 'agentforge-eval-tools'
export const inject = ['tools']

const FIXTURES = Object.freeze({
  'project.owner': 'AgentForge Evaluation Team',
  'project.runtime': 'Spring Boot 3.4.4 / Java 21',
  'policy.external_write': 'approval-required',
  'ticket.AF-101': 'RESOLVED',
})

const output = {
  schema: {
    type: 'object',
    additionalProperties: false,
    properties: {
      success: { type: 'boolean', required: true },
      content: { type: 'string', required: true },
    },
  },
  render: (_args, value) => [{ type: 'text', text: value.content }],
}

function register(ctx, name, description, parameters, execute) {
  ctx.tools.register(defineTool({
    name,
    description,
    parameters,
    output,
    execute,
    presentCall: args => ({ card: 'generic', title: name, kind: 'other', rawInput: args }),
  }))
}

export function apply(ctx) {
  register(ctx, 'fixture_lookup', 'Read a value from the immutable evaluation fixture.', {
    key: { type: 'string', required: true },
  }, args => {
    const value = FIXTURES[args.key]
    if (value === undefined) throw new Error(`fixture_not_found:${bounded(args.key)}`)
    return Promise.resolve({ success: true, content: value })
  })

  register(ctx, 'calculator', 'Evaluate a basic arithmetic expression.', {
    expression: { type: 'string', required: true },
  }, args => {
    try {
      return Promise.resolve({ success: true, content: formatNumber(new ArithmeticParser(args.expression).parse()) })
    } catch (error) {
      throw new Error(`invalid_expression:${safeMessage(error)}`)
    }
  })

  register(ctx, 'simulated_write', 'Record a simulated write without external side effects.', {
    target: { type: 'string', required: true },
    value: { type: 'string', required: true },
  }, args => Promise.resolve({
    success: true,
    content: `SIMULATED_WRITE_OK target=${bounded(args.target)} value=${bounded(args.value)}`,
  }))

  register(ctx, 'forced_failure', 'Always return a deterministic failure for recovery tests.', {
    reason: { type: 'string' },
  }, args => { throw new Error(`FORCED_FAILURE:${bounded(args.reason ?? 'requested')}`) })
}

function bounded(value) {
  return String(value).slice(0, 512)
}

function safeMessage(error) {
  return error instanceof Error ? error.message.slice(0, 200) : 'invalid input'
}

function formatNumber(value) {
  if (!Number.isFinite(value)) throw new Error('non-finite result')
  return Number.isInteger(value) ? String(value) : String(Number(value.toPrecision(15)))
}

// Deliberately supports only numbers, parentheses and + - * /. It never uses eval/Function.
class ArithmeticParser {
  constructor(text) {
    this.text = String(text ?? '').replaceAll(/\s/g, '')
    this.index = 0
    if (this.text.length > 256) throw new Error('expression too long')
  }

  parse() {
    const value = this.expression()
    if (this.index !== this.text.length) throw new Error(`unexpected token at ${this.index}`)
    return value
  }

  expression() {
    let value = this.term()
    while (this.index < this.text.length && ['+', '-'].includes(this.text[this.index])) {
      const op = this.text[this.index++]
      const right = this.term()
      value = op === '+' ? value + right : value - right
    }
    return value
  }

  term() {
    let value = this.factor()
    while (this.index < this.text.length && ['*', '/'].includes(this.text[this.index])) {
      const op = this.text[this.index++]
      const right = this.factor()
      if (op === '/' && right === 0) throw new Error('division by zero')
      value = op === '*' ? value * right : value / right
    }
    return value
  }

  factor() {
    if (this.text[this.index] === '(') {
      this.index++
      const value = this.expression()
      if (this.text[this.index++] !== ')') throw new Error('missing )')
      return value
    }
    const start = this.index
    if (['+', '-'].includes(this.text[this.index])) this.index++
    while (this.index < this.text.length && /[0-9.]/.test(this.text[this.index])) this.index++
    if (start === this.index) throw new Error('number expected')
    const token = this.text.slice(start, this.index)
    if (!/^[+-]?(?:\d+(?:\.\d*)?|\.\d+)$/.test(token)) throw new Error('invalid number')
    return Number(token)
  }
}
