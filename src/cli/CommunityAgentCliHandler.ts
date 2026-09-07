import type { IStrandsAgentRuntimeBootstrap } from '@tjxjnoobie/custom-strands-bridge'

import type { CommunityAgentRuntimeConfigBuilder } from '../agent/config/CommunityAgentRuntimeConfigBuilder.js'
import { CommunityAgentCliInputError } from './error/CommunityAgentCliInputError.js'

export class CommunityAgentCliHandler {
  private readonly agentRuntimeBootstrap: IStrandsAgentRuntimeBootstrap
  private readonly runtimeConfigBuilder: CommunityAgentRuntimeConfigBuilder

  public constructor(
    agentRuntimeBootstrap: IStrandsAgentRuntimeBootstrap,
    runtimeConfigBuilder: CommunityAgentRuntimeConfigBuilder,
  ) {
    this.agentRuntimeBootstrap = agentRuntimeBootstrap
    this.runtimeConfigBuilder = runtimeConfigBuilder
  }

  public async handle(request: string): Promise<string> {
    const normalizedRequest = request.trim()

    if (normalizedRequest.length === 0) {
      throw new CommunityAgentCliInputError()
    }

    const agentRuntime = await this.agentRuntimeBootstrap.createAgentRuntime(
      this.runtimeConfigBuilder.build(),
    )

    try {
      const result = await agentRuntime.invokeAgent(normalizedRequest)

      return result.toString()
    } finally {
      await agentRuntime.close()
    }
  }
}
