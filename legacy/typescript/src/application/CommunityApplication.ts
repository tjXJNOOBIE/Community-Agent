import http from 'node:http'

import {StrandsAgentRuntimeBootstrap} from '@tjxjnoobie/strands-bridge'
import type {CommunityAgentConfig} from '../config/data/CommunityAgentConfig.js'
import {CommunityAgentRuntimeConfigBuilder} from '../agent/config/CommunityAgentRuntimeConfigBuilder.js'
import {DiscordApiClient} from '../discord/api/DiscordApiClient.js'
import {ProposalStore} from '../policy/ProposalStore.js'

const text = (value: unknown): string => typeof value === 'string' ? value : ''

export class CommunityApplication {
  private server: http.Server | undefined
  private readonly proposals: ProposalStore
  private readonly discord: DiscordApiClient | undefined
  private closed = false

  public constructor(private readonly config: CommunityAgentConfig, private readonly proposalFile: string, private readonly auditFile: string) {
    this.proposals = new ProposalStore(proposalFile, config.control.proposalSigningSecret)
    const token = process.env[config.discordBotTokenEnvironmentVariable]?.trim()
    this.discord = token ? new DiscordApiClient(token, config.guildId) : undefined
  }

  public async start(): Promise<void> {
    this.server = http.createServer((request, response) => { void this.handle(request, response) })
    await new Promise<void>((resolve, reject) => { this.server?.once('error', reject); this.server?.listen(this.config.web.port, this.config.web.host, () => resolve()) })
  }

  public async invoke(request: string): Promise<string> {
    const runtime = await new StrandsAgentRuntimeBootstrap().createAgentRuntime(new CommunityAgentRuntimeConfigBuilder(process.env).build({config: this.config}))
    try { return (await runtime.invokeAgent(request)).toString() } finally { await runtime.close() }
  }

  public async close(): Promise<void> {
    if (this.closed) return
    this.closed = true
    if (this.server !== undefined) await new Promise<void>(resolve => this.server?.close(() => resolve()))
  }

  private async handle(request: http.IncomingMessage, response: http.ServerResponse): Promise<void> {
    response.setHeader('access-control-allow-origin', this.config.web.allowedOrigins.includes('*') ? '*' : this.config.web.allowedOrigins[0] ?? 'null')
    response.setHeader('access-control-allow-headers', 'authorization,content-type,mcp-protocol-version')
    response.setHeader('access-control-allow-methods', 'GET,POST,OPTIONS')
    response.setHeader('x-content-type-options', 'nosniff')
    if (request.method === 'OPTIONS') { response.statusCode = 204; response.end(); return }
    const url = new URL(request.url || '/', `http://${request.headers.host || 'localhost'}`)
    if (url.pathname === '/healthz' || url.pathname === '/readyz') { this.json(response, 200, {status: 'ok', name: 'Discord Manager', guildId: this.config.guildId}); return }
    if (url.pathname === '/') { this.json(response, 200, {name: 'Discord Manager', mcp: ['/mcp/operator', '/mcp/agent'], health: '/healthz'}); return }
    if (!['/mcp/operator', '/mcp/agent'].includes(url.pathname) || request.method !== 'POST') { response.statusCode = 404; response.end('Not found'); return }
    const operator = url.pathname === '/mcp/operator'
    const expected = operator ? this.config.control.operatorToken : this.config.control.internalAgentToken
    const provided = text(request.headers.authorization).replace(/^Bearer\s+/, '')
    if (provided !== expected) { response.statusCode = 401; response.setHeader('www-authenticate', 'Bearer'); this.json(response, 401, {error: 'authenticated Discord Manager access is required'}); return }
    try { this.json(response, 200, await this.rpc(await this.readJson(request), operator)) } catch (error) { this.json(response, 400, {jsonrpc: '2.0', id: null, error: {code: -32600, message: error instanceof Error ? error.message : String(error)}}) }
  }

  private async rpc(message: Record<string, unknown>, operator: boolean): Promise<Record<string, unknown>> {
    const id = message['id'] ?? null
    const method = message['method']
    if (method === 'initialize') return {jsonrpc: '2.0', id, result: {protocolVersion: '2025-11-25', capabilities: {tools: {listChanged: false}}, serverInfo: {name: 'discord-manager', version: '0.1.0'}}}
    if (method === 'tools/list') return {jsonrpc: '2.0', id, result: {tools: this.tools(operator)}}
    if (method !== 'tools/call') return {jsonrpc: '2.0', id, error: {code: -32601, message: 'method not found'}}
    const params = this.asRecord(message['params'])
    const name = text(params['name'])
    const input = this.asRecord(params['arguments'])
    return {jsonrpc: '2.0', id, result: await this.callTool(name, input, operator)}
  }

  private tools(operator: boolean): readonly Record<string, unknown>[] {
    const observe = {name: 'community_observe', description: 'Read configured Discord guild state and recent activity.', inputSchema: {type: 'object', additionalProperties: false, properties: {}}}
    const propose = {name: 'community_propose', description: 'Create a signed proposal; never approves or executes it.', inputSchema: {type: 'object', additionalProperties: false, properties: {action: {type: 'string'}, input: {type: 'object'}}, required: ['action', 'input']}}
    const operatorTools = [
      {name: 'operator_proposals', description: 'List pending and completed Discord Manager proposals.', inputSchema: {type: 'object', additionalProperties: false, properties: {}}},
      {name: 'operator_approve', description: 'Approve and execute exactly one signed proposal.', inputSchema: {type: 'object', additionalProperties: false, properties: {proposalId: {type: 'string'}}, required: ['proposalId']}},
    ]
    return operator ? [observe, propose, ...operatorTools] : [observe, propose]
  }

  private async callTool(name: string, input: Record<string, unknown>, operator: boolean): Promise<Record<string, unknown>> {
    if (name === 'community_observe') return this.result(await this.observe())
    if (name === 'community_propose') return this.result(this.proposals.create(text(input['action']), this.asRecord(input['input'])))
    if (name === 'operator_proposals' && operator) return this.result(this.proposals.list())
    if (name === 'operator_approve' && operator) {
      const claimed = this.proposals.claim(text(input['proposalId']), 'authenticated-operator')
      try {
        const action = claimed.action
        if (action !== 'send_message') throw new Error(`unsupported Discord action ${action}`)
        if (this.discord === undefined) throw new Error('Discord bot token is not configured')
        const result = await this.discord.sendMessage(text(claimed.input['channelId']), text(claimed.input['content']))
        return this.result(this.proposals.result(claimed.id, 'COMPLETED', result))
      } catch (error) { return this.result(this.proposals.result(claimed.id, 'FAILED', {error: error instanceof Error ? error.message : String(error)})) }
    }
    throw new Error('tool is unavailable on this control surface')
  }

  private async observe(): Promise<unknown> { if (this.discord === undefined) return {status: 'blocked', reason: 'Discord bot token is not configured'}; return {guild: await this.discord.guild(), channels: await this.discord.channels()} }
  private result(value: unknown): Record<string, unknown> { return {content: [{type: 'text', text: JSON.stringify(value)}], structuredContent: value} }
  private json(response: http.ServerResponse, status: number, value: unknown): void { response.statusCode = status; response.setHeader('content-type', 'application/json; charset=utf-8'); response.end(JSON.stringify(value)) }
  private async readJson(request: http.IncomingMessage): Promise<Record<string, unknown>> { const chunks: Buffer[] = []; for await (const chunk of request) chunks.push(Buffer.from(chunk as Uint8Array)); const value: unknown = JSON.parse(Buffer.concat(chunks).toString('utf8')); if (!this.record(value)) throw new Error('request must be an object'); return value }
  private asRecord(value: unknown): Record<string, unknown> { if (!this.record(value)) throw new Error('value must be an object'); return value }
  private record(value: unknown): value is Record<string, unknown> { return typeof value === 'object' && value !== null && !Array.isArray(value) }
}
