import type { StrandsAgentRuntimeConfig } from '@tjxjnoobie/strands-bridge'

import type { CommunityAgentConfig } from '../../config/data/CommunityAgentConfig.js'
import { COMMUNITY_AGENT_SYSTEM_PROMPT } from '../prompt/CommunityAgentSystemPrompt.js'
import type { CommunitySpecialistDefinition } from '../prompt/CommunitySpecialistPrompts.js'

export type CommunityAgentEnvironment = Readonly<Record<string, string | undefined>>
type AgentTools = StrandsAgentRuntimeConfig['agent']['tools']

export interface CommunityAgentRuntimeBuildRequest {
  config?: CommunityAgentConfig
  tools?: AgentTools
  specialist?: CommunitySpecialistDefinition
}

export class CommunityAgentRuntimeConfigBuilder {
  public constructor(
    private readonly environment: CommunityAgentEnvironment = process.env,
  ) {}

  public build(request: CommunityAgentRuntimeBuildRequest = {}): StrandsAgentRuntimeConfig {
    const modelId =
      request.config?.modelId ??
      this.optionalString(this.environment['COMMUNITY_AGENT_MODEL_ID'])

    const internalMcpUrl =
      request.config === undefined
        ? this.optionalString(this.environment['COMMUNITY_AGENT_MCP_URL'])
        : `http://${request.config.web.host}:${request.config.web.port}/mcp/agent`

    const internalAuthorization =
      request.config?.control.internalAgentToken ??
      this.optionalString(this.environment['COMMUNITY_AGENT_MCP_AUTHORIZATION'])

    const specialist = request.specialist
    const agentId = specialist?.id ?? 'discord-manager'
    const agentName = specialist?.name ?? 'Discord Manager'
    const systemPrompt = specialist?.systemPrompt ?? COMMUNITY_AGENT_SYSTEM_PROMPT

    return {
      agent: {
        id: agentId,
        name: agentName,
        systemPrompt,
        printer: false,
        traceAttributes: {
          product: 'community-agent',
          surface: 'discord-manager',
          role: specialist === undefined ? 'coordinator' : specialist.id,
        },
        ...(modelId === undefined ? {} : { model: modelId }),
        ...(request.tools === undefined ? {} : { tools: request.tools }),
      },
      ...(internalMcpUrl === undefined
        ? {}
        : {
            mcpServers: {
              product: {
                url: internalMcpUrl,
                ...(internalAuthorization === undefined
                  ? {}
                  : {
                      headers: {
                        Authorization: internalAuthorization.startsWith('Bearer ')
                          ? internalAuthorization
                          : `Bearer ${internalAuthorization}`,
                      },
                    }),
              },
            },
            mcpDefaults: {
              applicationName: 'community-agent',
              applicationVersion: '0.1.0',
            },
          }),
    }
  }

  private optionalString(value: string | undefined): string | undefined {
    if (value === undefined) {
      return undefined
    }
    const normalized = value.trim()
    return normalized.length === 0 ? undefined : normalized
  }
}
