export type DiscordDoctorStatus = 'PASS' | 'FAIL' | 'WARN'
export interface DiscordDoctorCheck { readonly key: string; readonly status: DiscordDoctorStatus; readonly message: string }
export interface DiscordInstallationDoctorResult { readonly healthy: boolean; readonly guildId: string; readonly guildName?: string; readonly applicationId: string; readonly botUserId?: string; readonly checks: readonly DiscordDoctorCheck[] }
