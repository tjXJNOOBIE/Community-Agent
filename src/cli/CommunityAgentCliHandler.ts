import type { CommunityApplicationBootstrap } from '../application/bootstrap/CommunityApplicationBootstrap.js'
import type { CommunityAgentConfigReader } from '../config/reader/CommunityAgentConfigReader.js'
import type { CommunityAgentPathResolver } from '../config/path/CommunityAgentPathResolver.js'
import { CommunityAgentCliInputError } from './error/CommunityAgentCliInputError.js'

export class CommunityAgentCliHandler {
  public constructor(
    private readonly applicationBootstrap: CommunityApplicationBootstrap,
    private readonly configReader: CommunityAgentConfigReader,
    private readonly paths: CommunityAgentPathResolver,
  ) {}

  public async handle(request: string): Promise<string> {
    const normalizedRequest = request.trim()
    if (normalizedRequest.length === 0) {
      throw new CommunityAgentCliInputError()
    }

    const config = await this.configReader.read(this.paths.configFile())
    const application = await this.applicationBootstrap.create(config)

    try {
      return await application.invoke(normalizedRequest)
    } finally {
      await application.close()
    }
  }
}
