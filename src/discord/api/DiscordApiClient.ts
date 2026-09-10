export class DiscordApiClient {
  private readonly base = 'https://discord.com/api/v10'
  public constructor(private readonly token: string, private readonly guildId: string) {}

  public async request(path: string, init: RequestInit = {}): Promise<unknown> {
    const response = await fetch(`${this.base}${path}`, {
      ...init,
      headers: {'authorization': `Bot ${this.token}`, 'content-type': 'application/json', ...(init.headers || {})},
    })
    const text = await response.text()
    const value = text.length === 0 ? null : JSON.parse(text)
    if (!response.ok) throw new Error(`Discord API ${response.status}: ${typeof value === 'object' && value !== null && 'message' in value ? String(value.message) : 'request failed'}`)
    return value
  }

  public guild(): Promise<unknown> { return this.request(`/guilds/${encodeURIComponent(this.guildId)}`) }
  public channels(): Promise<unknown> { return this.request(`/guilds/${encodeURIComponent(this.guildId)}/channels`) }
  public currentUser(): Promise<unknown> { return this.request('/users/@me') }
  public sendMessage(channelId: string, content: string): Promise<unknown> { return this.request(`/channels/${encodeURIComponent(channelId)}/messages`, {method: 'POST', body: JSON.stringify({content})}) }
}
