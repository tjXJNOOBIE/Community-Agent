import type { CommunityApplicationBootstrap } from '../application/bootstrap/CommunityApplicationBootstrap.js'
import type { CommunityAgentConfigReader } from '../config/reader/CommunityAgentConfigReader.js'
import type { CommunityAgentPathResolver } from '../config/path/CommunityAgentPathResolver.js'
import type { IStrandsAgentRuntimeBootstrap } from '@tjxjnoobie/strands-bridge'
import type { CommunityAgentRuntimeConfigBuilder } from '../agent/config/CommunityAgentRuntimeConfigBuilder.js'
import { CommunityAgentCliInputError } from './error/CommunityAgentCliInputError.js'

export class CommunityAgentCliHandler {
  public constructor(
    private readonly applicationBootstrap: CommunityApplicationBootstrap | IStrandsAgentRuntimeBootstrap,
    private readonly configReader: CommunityAgentConfigReader | CommunityAgentRuntimeConfigBuilder,
    private readonly paths?: CommunityAgentPathResolver,
  ) {}

  public async handle(request: string): Promise<string> {
    const normalizedRequest = request.trim()
    if (normalizedRequest.length === 0) {
      throw new CommunityAgentCliInputError()
    }

    if (this.paths === undefined) {
      const runtime = await (this.applicationBootstrap as IStrandsAgentRuntimeBootstrap).createAgentRuntime(
        (this.configReader as CommunityAgentRuntimeConfigBuilder).build(),
      )
      try {
        return (await runtime.invokeAgent(normalizedRequest)).toString()
      } finally {
        await runtime.close()
      }
    }

    const config = await (this.configReader as CommunityAgentConfigReader).read(this.paths.configFile())
    const application = await (this.applicationBootstrap as CommunityApplicationBootstrap).create(config)

    try {
      return await application.invoke(normalizedRequest)
    } finally {
      await application.close()
    }
  }
}
