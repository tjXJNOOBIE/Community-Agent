export type CommunityAutonomyMode = 'OBSERVE' | 'PROPOSE' | 'OPERATE'

export interface CommunityAgentConfig {
  readonly version: 1
  readonly guildId: string
  readonly applicationId: string
  readonly discordBotTokenEnvironmentVariable: string
  readonly modelId?: string
  readonly dataDirectory: string
  readonly control: {
    readonly operatorToken: string
    readonly internalAgentToken: string
    readonly proposalSigningSecret: string
  }
  readonly autonomy: {
    readonly defaultMode: CommunityAutonomyMode
    readonly overrides: Readonly<Record<string, CommunityAutonomyMode>>
  }
  readonly web: {
    readonly host: string
    readonly port: number
    readonly allowedHosts: readonly string[]
    readonly allowedOrigins: readonly string[]
  }
  readonly trustedDiscordInteraction: {
    readonly enabled: boolean
    readonly userIds: readonly string[]
    readonly roleIds: readonly string[]
    readonly channelIds: readonly string[]
    readonly requireMention: boolean
  }
}
