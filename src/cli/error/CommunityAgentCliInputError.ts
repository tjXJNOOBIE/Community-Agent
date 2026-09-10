export class CommunityAgentCliInputError extends Error {
  public constructor() {
    super('Community Agent request cannot be blank.')
    this.name = 'CommunityAgentCliInputError'
  }
}
