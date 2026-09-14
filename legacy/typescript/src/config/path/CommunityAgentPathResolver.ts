import os from 'node:os'
import path from 'node:path'

export class CommunityAgentPathResolver {
  public constructor(private readonly environment: Readonly<Record<string, string | undefined>> = process.env) {}

  public dataDirectory(): string {
    return this.environment['COMMUNITY_AGENT_DATA_DIR']?.trim() || path.join(os.homedir(), '.community-agent')
  }

  public configFile(): string {
    return path.join(this.dataDirectory(), 'config.json')
  }

  public proposalFile(): string {
    return path.join(this.dataDirectory(), 'proposals.jsonl')
  }

  public auditFile(): string {
    return path.join(this.dataDirectory(), 'audit.jsonl')
  }
}
