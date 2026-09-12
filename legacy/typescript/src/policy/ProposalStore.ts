import crypto from 'node:crypto'
import fs from 'node:fs'
import path from 'node:path'

export interface CommunityProposal { readonly id: string; readonly action: string; readonly input: Record<string, unknown>; readonly createdAt: string; readonly expiresAt: string; readonly status: 'PENDING' | 'CLAIMED' | 'COMPLETED' | 'FAILED'; readonly signature: string; readonly result?: unknown; readonly actor?: string }

export class ProposalStore {
  private readonly items: CommunityProposal[] = []
  public constructor(private readonly file: string, private readonly secret: string) { this.load() }

  public create(action: string, input: Record<string, unknown>): CommunityProposal {
    const createdAt = new Date().toISOString()
    const expiresAt = new Date(Date.now() + 15 * 60_000).toISOString()
    const id = crypto.randomUUID()
    const signature = this.sign(id, action, input, expiresAt)
    const proposal: CommunityProposal = {id, action, input, createdAt, expiresAt, status: 'PENDING', signature}
    this.items.push(proposal); this.append(proposal); return proposal
  }

  public list(): readonly CommunityProposal[] { return this.items.map(item => ({...item, input: {...item.input}})) }
  public claim(id: string, actor: string): CommunityProposal {
    const item = this.require(id)
    if (item.status !== 'PENDING') throw new Error(`proposal ${id} is not pending`)
    if (Date.parse(item.expiresAt) <= Date.now()) throw new Error(`proposal ${id} is expired`)
    if (item.signature !== this.sign(item.id, item.action, item.input, item.expiresAt)) throw new Error(`proposal ${id} signature is invalid`)
    const claimed = {...item, status: 'CLAIMED' as const, actor}; this.replace(claimed); return claimed
  }
  public result(id: string, status: 'COMPLETED' | 'FAILED', result: unknown): CommunityProposal { const updated = {...this.require(id), status, result}; this.replace(updated); return updated }

  private require(id: string): CommunityProposal { const item = this.items.find(candidate => candidate.id === id); if (!item) throw new Error(`unknown proposal ${id}`); return item }
  private replace(updated: CommunityProposal): void { const index = this.items.findIndex(item => item.id === updated.id); this.items[index] = updated; this.append(updated) }
  private sign(id: string, action: string, input: Record<string, unknown>, expiresAt: string): string { return crypto.createHmac('sha256', this.secret).update(JSON.stringify({id, action, input, expiresAt})).digest('hex') }
  private append(value: CommunityProposal): void { fs.mkdirSync(path.dirname(this.file), {recursive: true, mode: 0o700}); fs.appendFileSync(this.file, `${JSON.stringify(value)}\n`, {mode: 0o600}) }
  private load(): void { if (!fs.existsSync(this.file)) return; for (const line of fs.readFileSync(this.file, 'utf8').split(/\r?\n/).filter(Boolean)) { try { const item = JSON.parse(line) as CommunityProposal; const index = this.items.findIndex(candidate => candidate.id === item.id); if (index >= 0) this.items[index] = item; else this.items.push(item) } catch { /* ignore corrupt audit lines; current proposal state remains fail-closed */ } } }
}
