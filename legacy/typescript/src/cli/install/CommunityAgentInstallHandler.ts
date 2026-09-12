import {mkdir} from 'node:fs/promises'
import path from 'node:path'

import type {CommunityAgentDefaultConfigBuilder} from '../../config/builder/CommunityAgentDefaultConfigBuilder.js'
import type {CommunityAgentConfigWriter} from '../../config/writer/CommunityAgentConfigWriter.js'
import type {CommunityAgentPathResolver} from '../../config/path/CommunityAgentPathResolver.js'

export interface CommunityInstallResult { readonly configFile: string; readonly inviteUrl: string; readonly botTokenEnvironmentVariable: string }

export class CommunityAgentInstallHandler {
  public constructor(
    private readonly builder: CommunityAgentDefaultConfigBuilder,
    private readonly writer: CommunityAgentConfigWriter,
    private readonly paths: CommunityAgentPathResolver,
  ) {}

  public async install(input: {guildId: string; applicationId: string; force?: boolean}): Promise<CommunityInstallResult> {
    const configFile = this.paths.configFile()
    if (!input.force) {
      try { await import('node:fs/promises').then(fs => fs.access(configFile)); throw new Error(`Config already exists at ${configFile}; use --force to replace it.`) } catch (error) {
        if (error instanceof Error && !error.message.includes('ENOENT')) throw error
      }
    }
    const config = this.builder.build({guildId: input.guildId, applicationId: input.applicationId, dataDirectory: this.paths.dataDirectory()})
    await mkdir(path.dirname(configFile), {recursive: true, mode: 0o700})
    await this.writer.write(configFile, config)
    const permissions = BigInt(1 | 1024 | 2048 | 32768 | 65536 | 131072 | 268435456)
    const inviteUrl = `https://discord.com/oauth2/authorize?client_id=${encodeURIComponent(input.applicationId)}&scope=bot%20applications.commands&permissions=${permissions.toString()}`
    return {configFile, inviteUrl, botTokenEnvironmentVariable: config.discordBotTokenEnvironmentVariable}
  }
}
