import crypto from 'node:crypto'
import path from 'node:path'

import type {CommunityAgentConfig} from '../data/CommunityAgentConfig.js'

export class CommunityAgentDefaultConfigBuilder {
  public build(input: {guildId: string; applicationId: string; dataDirectory: string}): CommunityAgentConfig {
    return {
      version: 1,
      guildId: input.guildId,
      applicationId: input.applicationId,
      discordBotTokenEnvironmentVariable: 'COMMUNITY_AGENT_DISCORD_BOT_TOKEN',
      ...(process.env['COMMUNITY_AGENT_MODEL_ID']?.trim() ? {modelId: process.env['COMMUNITY_AGENT_MODEL_ID'].trim()} : {}),
      dataDirectory: path.resolve(input.dataDirectory),
      control: {
        operatorToken: crypto.randomBytes(32).toString('hex'),
        internalAgentToken: crypto.randomBytes(32).toString('hex'),
        proposalSigningSecret: crypto.randomBytes(32).toString('hex'),
      },
      autonomy: {defaultMode: 'PROPOSE', overrides: {}},
      web: {host: '127.0.0.1', port: 3210, allowedHosts: ['127.0.0.1'], allowedOrigins: []},
      trustedDiscordInteraction: {enabled: false, userIds: [], roleIds: [], channelIds: [], requireMention: true},
    }
  }
}
