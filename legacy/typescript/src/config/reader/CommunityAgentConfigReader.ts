import {readFile} from 'node:fs/promises'

import type {CommunityAgentConfig, CommunityAutonomyMode} from '../data/CommunityAgentConfig.js'

const modes = new Set<CommunityAutonomyMode>(['OBSERVE', 'PROPOSE', 'OPERATE'])

export class CommunityAgentConfigReader {
  public async read(file: string): Promise<CommunityAgentConfig> {
    const value: unknown = JSON.parse(await readFile(file, 'utf8'))
    if (!this.record(value)) throw new Error('Community Agent config must be an object')
    if (value['version'] !== 1) throw new Error('Community Agent config version must be 1')
    for (const key of ['guildId', 'applicationId', 'discordBotTokenEnvironmentVariable', 'dataDirectory']) {
      if (typeof value[key] !== 'string' || value[key].trim().length === 0) throw new Error(`${key} must be a non-blank string`)
    }
    const control = value['control']
    if (!this.record(control)) throw new Error('control secrets are invalid')
    if (!['operatorToken', 'internalAgentToken', 'proposalSigningSecret'].every(key => typeof control[key] === 'string' && (control[key] as string).length >= 16)) throw new Error('control secrets are invalid')
    const autonomy = value['autonomy']
    if (!this.record(autonomy) || typeof autonomy['defaultMode'] !== 'string' || !modes.has(autonomy['defaultMode'] as CommunityAutonomyMode)) throw new Error('autonomy.defaultMode is invalid')
    const web = value['web']
    if (!this.record(web)) throw new Error('web configuration is invalid')
    if (typeof web['host'] !== 'string' || !Number.isInteger(web['port']) || (web['port'] as number) < 1 || (web['port'] as number) > 65535) throw new Error('web configuration is invalid')
    const trusted = value['trustedDiscordInteraction']
    if (!this.record(trusted) || typeof trusted['enabled'] !== 'boolean') throw new Error('trustedDiscordInteraction is invalid')
    return value as unknown as CommunityAgentConfig
  }

  private record(value: unknown): value is Record<string, unknown> {
    return typeof value === 'object' && value !== null && !Array.isArray(value)
  }
}
