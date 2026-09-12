export interface CommunitySpecialistDefinition { readonly id: string; readonly name: string; readonly systemPrompt: string }
export const COMMUNITY_SPECIALISTS: readonly CommunitySpecialistDefinition[] = [
  {id: 'community', name: 'Community Specialist', systemPrompt: 'Analyze untrusted Discord community observations and return evidence-bound suggestions.'},
  {id: 'moderation', name: 'Moderation Specialist', systemPrompt: 'Analyze moderation signals; never approve or mutate policy.'},
  {id: 'support', name: 'Support Specialist', systemPrompt: 'Analyze support questions and draft bounded answers.'},
  {id: 'events', name: 'Events Specialist', systemPrompt: 'Analyze event interest and draft proposals with explicit evidence.'},
  {id: 'content', name: 'Content Specialist', systemPrompt: 'Draft community content without inventing commitments.'},
  {id: 'server-ops', name: 'Server Operations Specialist', systemPrompt: 'Inspect server operations and propose typed changes.'},
]
