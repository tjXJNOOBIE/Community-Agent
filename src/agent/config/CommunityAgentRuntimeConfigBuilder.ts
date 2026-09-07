import type { StrandsAgentRuntimeConfig } from '@tjxjnoobie/custom-strands-bridge'

import { COMMUNITY_AGENT_SYSTEM_PROMPT } from '../prompt/CommunityAgentSystemPrompt.js'

export type CommunityAgentEnvironment = Readonly<Record<string, string | undefined>>

export class CommunityAgentRuntimeConfigBuilder {
  private readonly environment: CommunityAgentEnvironment

  public constructor(environment: CommunityAgentEnvironment = process.env) {
    this.environment = environment
  }

  public build(): StrandsAgentRuntimeConfig {
    const modelId = this.optionalString(this.environment['COMMUNITY_AGENT_MODEL_ID'])
    const mcpUrl = this.optionalString(this.environment['COMMUNITY_AGENT_MCP_URL'])

    const authorization = this.optionalString(
      this.environment['COMMUNITY_AGENT_MCP_AUTHORIZATION'],
    )

    const runtimeConfig: StrandsAgentRuntimeConfig = {
      agent: {
        id: 'community-agent',
        name: 'Community Agent',
        systemPrompt: COMMUNITY_AGENT_SYSTEM_PROMPT,
        printer: false,
        traceAttributes: {
          product: 'community-agent',
          hackathonTrack: 'Good Neighbor',
        },
        ...(modelId === undefined ? {} : { model: modelId }),
      },
      ...(mcpUrl === undefined
        ? {}
        : {
            mcpServers: {
              product: {
                url: mcpUrl,
                ...(authorization === undefined
                  ? {}
                  : { headers: { Authorization: authorization } }),
              },
            },
            mcpDefaults: {
              applicationName: 'community-agent',
              applicationVersion: '0.1.0',
            },
          }),
    }

    return runtimeConfig
  }

  private optionalString(value: string | undefined): string | undefined {
    if (value === undefined) {
      return undefined
    }

    const normalizedValue = value.trim()

    return normalizedValue.length === 0 ? undefined : normalizedValue
  }
}
