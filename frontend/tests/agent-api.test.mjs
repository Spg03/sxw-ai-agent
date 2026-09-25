import assert from 'node:assert/strict'
import { test } from 'node:test'
import { readFileSync } from 'node:fs'
import { runInNewContext } from 'node:vm'
import { File } from 'node:buffer'
import ts from 'typescript'

// Compile the actual API module in memory, without adding a browser/test-runner dependency.
const source = readFileSync(new URL('../src/api/agent.ts', import.meta.url), 'utf8')
const { outputText } = ts.transpileModule(source, {
  compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 },
})
function apiWithFetch(fetch) {
  const module = { exports: {} }
  runInNewContext(outputText, {
    module, exports: module.exports,
    require: name => {
      assert.equal(name, './client')
      return { api: { getToken: () => 'test-token' } }
    },
    fetch, FormData, AbortController, TextDecoder,
    crypto: { randomUUID: () => 'test-request' },
  })
  return module.exports.agentApi
}

test('upload surfaces actionable server validation instead of just HTTP 400', async () => {
  const api = apiWithFetch(async () => Response.json({ message: '扫描件需先进行 OCR' }, { status: 400 }))
  await assert.rejects(api.uploadAttachment('chat', new File(['resume'], 'resume.pdf')), /扫描件需先进行 OCR/)
})

test('upload falls back safely when a gateway responds with HTML', async () => {
  const api = apiWithFetch(async () => new Response('<html>gateway failure</html>', { status: 502 }))
  await assert.rejects(api.uploadAttachment('chat', new File(['resume'], 'resume.txt')), /附件上传失败：502/)
})

test('stream preparation reports unavailable selected attachments', { timeout: 2000 }, async () => {
  const api = apiWithFetch(async () => Response.json({ message: '所选附件不可用或不属于当前会话' }, { status: 400 }))
  const message = await new Promise(resolve => {
    api.streamChat({ chatId: 'chat', profile: 'GENERAL', message: '分析简历' }, {
      onToken: () => assert.fail('No model output is expected after attachment rejection'),
      onError: resolve,
    })
  })
  assert.equal(message, '所选附件不可用或不属于当前会话')
})

test('successful upload preserves authentication and the returned attachment', async () => {
  const api = apiWithFetch(async (url, options) => {
    assert.equal(url, '/api/conversations/chat%2F1/attachments')
    assert.equal(options.headers.Authorization, 'Bearer test-token')
    assert.equal(options.body.get('file').name, 'resume.txt')
    return Response.json({ code: 0, data: { attachmentId: 'att_1', status: 'READY' } })
  })
  const result = await api.uploadAttachment('chat/1', new File(['resume'], 'resume.txt'))
  assert.equal(result.attachmentId, 'att_1')
  assert.equal(result.status, 'READY')
})
