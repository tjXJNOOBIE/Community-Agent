export class CommunityAgentCliInputError extends Error {
  public constructor() {
    super('Community Agent requires a non-blank request.')
    this.name = 'CommunityAgentCliInputError'
  }
}
