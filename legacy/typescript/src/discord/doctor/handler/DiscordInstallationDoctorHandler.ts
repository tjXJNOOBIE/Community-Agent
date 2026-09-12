import type {CommunityAgentConfig} from '../../../config/data/CommunityAgentConfig.js'
import {DiscordApiClient} from '../../api/DiscordApiClient.js'
import type {DiscordInstallationDoctorResult, DiscordDoctorCheck} from '../data/DiscordInstallationDoctorResult.js'

export class DiscordInstallationDoctorHandler {
  public constructor(private readonly config: CommunityAgentConfig, private readonly token: string) {}

  public async inspect(): Promise<DiscordInstallationDoctorResult> {
    const checks: DiscordDoctorCheck[] = []
    const api = new DiscordApiClient(this.token, this.config.guildId)
    let botUserId: string | undefined
    let guildName: string | undefined
    try {
      const user = await api.currentUser() as Record<string, unknown>
      botUserId = typeof user['id'] === 'string' ? user['id'] : undefined
      checks.push({key: 'bot_authentication', status: 'PASS', message: 'Discord bot token authenticated.'})
    } catch (error) { checks.push({key: 'bot_authentication', status: 'FAIL', message: error instanceof Error ? error.message : String(error)}) }
    try {
      const guild = await api.guild() as Record<string, unknown>
      guildName = typeof guild['name'] === 'string' ? guild['name'] : undefined
      checks.push({key: 'guild_access', status: 'PASS', message: `Bot can access the configured guild${guildName ? ` (${guildName})` : ''}.`})
    } catch (error) { checks.push({key: 'guild_access', status: 'FAIL', message: error instanceof Error ? error.message : String(error)}) }
    checks.push({key: 'message_content_intent', status: 'WARN', message: 'Enable Message Content Intent in the Discord Developer Portal.'})
    checks.push({key: 'trusted_prompting', status: this.config.trustedDiscordInteraction.enabled ? 'WARN' : 'PASS', message: this.config.trustedDiscordInteraction.enabled ? 'Trusted Discord prompting is explicitly enabled by machine configuration.' : 'Discord messages remain observation-only by default.'})
    return {healthy: checks.every(check => check.status !== 'FAIL'), guildId: this.config.guildId, ...(guildName === undefined ? {} : {guildName}), applicationId: this.config.applicationId, ...(botUserId === undefined ? {} : {botUserId}), checks}
  }
}
