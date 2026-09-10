#!/usr/bin/env node

import { createInterface } from 'node:readline/promises'
import { stdin, stdout } from 'node:process'

import { CommunityApplicationBootstrap } from '../application/bootstrap/CommunityApplicationBootstrap.js'
import { CommunityAgentDefaultConfigBuilder } from '../config/builder/CommunityAgentDefaultConfigBuilder.js'
import { CommunityAgentPathResolver } from '../config/path/CommunityAgentPathResolver.js'
import { CommunityAgentConfigReader } from '../config/reader/CommunityAgentConfigReader.js'
import { CommunityAgentConfigWriter } from '../config/writer/CommunityAgentConfigWriter.js'
import { DiscordInstallationDoctorHandler } from '../discord/doctor/handler/DiscordInstallationDoctorHandler.js'
import type { DiscordInstallationDoctorResult } from '../discord/doctor/data/DiscordInstallationDoctorResult.js'
import { CommunityAgentCliHandler } from './CommunityAgentCliHandler.js'
import { CommunityAgentInstallHandler } from './install/CommunityAgentInstallHandler.js'

function option(arguments_: readonly string[], name: string): string | undefined {
  const index = arguments_.indexOf(name)
  if (index < 0) {
    return undefined
  }
  return arguments_[index + 1]
}

async function promptIfMissing(
  value: string | undefined,
  prompt: string,
): Promise<string> {
  if (value !== undefined && value.trim().length > 0) {
    return value.trim()
  }
  if (!stdin.isTTY) {
    throw new Error(`${prompt} is required when stdin is not interactive.`)
  }
  const readline = createInterface({ input: stdin, output: stdout })
  try {
    return (await readline.question(`${prompt}: `)).trim()
  } finally {
    readline.close()
  }
}

async function install(arguments_: readonly string[]): Promise<void> {
  const paths = new CommunityAgentPathResolver()
  const handler = new CommunityAgentInstallHandler(
    new CommunityAgentDefaultConfigBuilder(),
    new CommunityAgentConfigWriter(),
    paths,
  )
  const guildId = await promptIfMissing(
    option(arguments_, '--guild-id'),
    'Discord server/guild ID',
  )
  const applicationId = await promptIfMissing(
    option(arguments_, '--application-id'),
    'Discord application ID',
  )
  const result = await handler.install({
    guildId,
    applicationId,
    force: arguments_.includes('--force'),
  })

  stdout.write(
    [
      `Config: ${result.configFile}`,
      `Invite bot: ${result.inviteUrl}`,
      `Set the bot token in ${result.botTokenEnvironmentVariable}.`,
      'Discord prompting is disabled by default. Trusted users/channels/roles must be enabled from the machine/operator control layer.',
      'Verify setup with: community-agent doctor',
      'Then start with: community-agent serve',
      'First operator request: community-agent \"Analyze my Discord server.\"',
    ].join('\n') + '\n',
  )
}


function renderDoctor(result: DiscordInstallationDoctorResult): string {
  const lines = [
    `Discord Manager doctor: ${result.healthy ? 'PASS' : 'FAIL'}`,
    `Guild: ${result.guildName ?? result.guildId} (${result.guildId})`,
    `Application: ${result.applicationId}`,
    ...(result.botUserId === undefined ? [] : [`Bot user: ${result.botUserId}`]),
    '',
    ...result.checks.map(
      (check) => `[${check.status}] ${check.key}: ${check.message}`,
    ),
  ]

  return lines.join('\n')
}

async function doctor(arguments_: readonly string[]): Promise<void> {
  const paths = new CommunityAgentPathResolver()
  const config = await new CommunityAgentConfigReader().read(paths.configFile())
  const botToken = process.env[config.discordBotTokenEnvironmentVariable]?.trim()
  if (botToken === undefined || botToken.length === 0) {
    throw new Error(
      `Set ${config.discordBotTokenEnvironmentVariable} before running Discord Manager doctor.`,
    )
  }

  const result = await new DiscordInstallationDoctorHandler(
    config,
    botToken,
  ).inspect()

  if (arguments_.includes('--json')) {
    stdout.write(`${JSON.stringify(result, null, 2)}\n`)
  } else {
    stdout.write(`${renderDoctor(result)}\n`)
  }

  if (!result.healthy) {
    process.exitCode = 1
  }
}

async function serve(): Promise<void> {
  const paths = new CommunityAgentPathResolver()
  const config = await new CommunityAgentConfigReader().read(paths.configFile())
  const application = await new CommunityApplicationBootstrap(paths).create(config)

  stdout.write(
    `Discord Manager listening on http://${config.web.host}:${config.web.port}\n`,
  )

  const shutdown = async () => {
    await application.close()
    process.exitCode = 0
  }

  process.once('SIGINT', () => void shutdown())
  process.once('SIGTERM', () => void shutdown())

  await new Promise<void>(() => undefined)
}

async function oneShot(arguments_: readonly string[]): Promise<void> {
  const request = arguments_.join(' ').trim()
  const paths = new CommunityAgentPathResolver()
  const handler = new CommunityAgentCliHandler(
    new CommunityApplicationBootstrap(paths),
    new CommunityAgentConfigReader(),
    paths,
  )
  stdout.write(`${await handler.handle(request)}\n`)
}

async function main(): Promise<void> {
  const arguments_ = process.argv.slice(2)
  const command = arguments_[0]

  if (command === 'install') {
    await install(arguments_.slice(1))
    return
  }

  if (command === 'doctor') {
    await doctor(arguments_.slice(1))
    return
  }

  if (command === 'serve') {
    await serve()
    return
  }

  await oneShot(arguments_)
}

main().catch((error: unknown) => {
  const message = error instanceof Error ? error.message : String(error)
  process.stderr.write(`${message}\n`)
  process.exitCode = 1
})
