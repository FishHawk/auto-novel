import type { DeltaSessionEvent, DeltaSessionSummary } from './session-summary';

/** Reuse each unchanged task's list so another task's save does not rerender it. */
export function groupDeltaSessions(
  sessions: DeltaSessionSummary[],
  previous?: ReadonlyMap<string, DeltaSessionSummary[]>,
): Map<string, DeltaSessionSummary[]> {
  const groups = new Map<string, DeltaSessionSummary[]>();
  for (const session of sessions) {
    const taskId = session.identity.taskId;
    const group = groups.get(taskId) ?? [];
    group.push(session);
    groups.set(taskId, group);
  }
  for (const [taskId, group] of groups) {
    const before = previous?.get(taskId);
    if (
      before?.length === group.length &&
      group.every((item, i) => item === before[i])
    )
      groups.set(taskId, before);
  }
  return groups;
}

/**
 * Apply a burst of save notifications with one array rewrite. Existing entries
 * keep their position, new chapters append, and a reset replaces the whole list.
 */
export function applyDeltaSessionEvents(
  sessions: DeltaSessionSummary[],
  events: DeltaSessionEvent[],
): DeltaSessionSummary[] {
  if (!events.length) return sessions;
  let index: Map<string, DeltaSessionSummary> | undefined;
  const mutable = () => {
    index ??= new Map(sessions.map((session) => [session.id, session]));
    return index;
  };
  for (const event of events) {
    if (event.type === 'reset') {
      index = new Map(event.sessions.map((session) => [session.id, session]));
      continue;
    }
    if (event.type === 'remove') {
      const current = mutable();
      for (const id of event.ids) current.delete(id);
      continue;
    }
    mutable().set(event.session.id, event.session);
  }
  return index ? [...index.values()] : sessions;
}
