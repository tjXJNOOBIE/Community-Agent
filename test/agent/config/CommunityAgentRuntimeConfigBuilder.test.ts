import assert from 'node:assert/strict'
import test from 'node:test'

import { CommunityAgentRuntimeConfigBuilder } from '../../../src/agent/config/CommunityAgentRuntimeConfigBuilder.js'
import { COMMUNITY_AGENT_SYSTEM_PROMPT } from '../../../src/agent/prompt/CommunityAgentSystemPrompt.js'

test('buildsStableProductIdentityAndPrompt', () => {
  const runtimeConfig = new CommunityAgentRuntimeConfigBuilder({}).build()

  assert.equal(runtimeConfig.agent.id, 'community-agent')
  assert.equal(runtimeConfig.agent.name, 'Community Agent')
  assert.equal(runtimeConfig.agent.systemPrompt, COMMUNITY_AGENT_SYSTEM_PROMPT)
  assert.equal(runtimeConfig.agent.printer, false)
  assert.equal(runtimeConfig.agent.model, undefined)
  assert.equal(runtimeConfig.mcpServers, undefined)
})

test('buildsConfiguredModelAndMcpBoundaryFromEnvironment', () => {
  const builder = new CommunityAgentRuntimeConfigBuilder({
    COMMUNITY_AGENT_MODEL_ID: ' model.example ',
    COMMUNITY_AGENT_MCP_URL: ' https://example.invalid/mcp ',
    COMMUNITY_AGENT_MCP_AUTHORIZATION: ' Bearer example ',
  })

  const runtimeConfig = builder.build()

  assert.equal(runtimeConfig.agent.model, 'model.example')
  assert.notEqual(typeof runtimeConfig.mcpServers, 'string')
  const mcpServers = typeof runtimeConfig.mcpServers === 'string'
    ? undefined
    : runtimeConfig.mcpServers
  assert.equal(mcpServers?.product?.url, 'https://example.invalid/mcp')
  assert.deepEqual(mcpServers?.product?.headers, {
    Authorization: 'Bearer example',
  })
  assert.equal(runtimeConfig.mcpDefaults?.applicationName, 'community-agent')
})

test('ignoresBlankOptionalEnvironmentValues', () => {
  const runtimeConfig = new CommunityAgentRuntimeConfigBuilder({
    COMMUNITY_AGENT_MODEL_ID: '   ',
    COMMUNITY_AGENT_MCP_AUTHORIZATION: '   ',
  }).build()

  assert.equal(runtimeConfig.agent.model, undefined)
})
