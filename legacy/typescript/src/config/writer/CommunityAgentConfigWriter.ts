import {chmod, mkdir, writeFile} from 'node:fs/promises'
import path from 'node:path'

import type {CommunityAgentConfig} from '../data/CommunityAgentConfig.js'

export class CommunityAgentConfigWriter {
  public async write(file: string, config: CommunityAgentConfig): Promise<void> {
    await mkdir(path.dirname(file), {recursive: true, mode: 0o700})
    await writeFile(file, `${JSON.stringify(config, null, 2)}\n`, {mode: 0o600})
    await chmod(file, 0o600)
  }
}
