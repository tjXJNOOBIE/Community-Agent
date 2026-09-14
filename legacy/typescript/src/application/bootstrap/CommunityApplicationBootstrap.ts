import type {CommunityAgentConfig} from '../../config/data/CommunityAgentConfig.js'
import type {CommunityAgentPathResolver} from '../../config/path/CommunityAgentPathResolver.js'
import {CommunityApplication} from '../CommunityApplication.js'

export class CommunityApplicationBootstrap {
  public constructor(private readonly paths: CommunityAgentPathResolver) {}
  public async create(config: CommunityAgentConfig): Promise<CommunityApplication> {
    const application = new CommunityApplication(config, this.paths.proposalFile(), this.paths.auditFile())
    await application.start()
    return application
  }
}
