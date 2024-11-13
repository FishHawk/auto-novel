export interface WorkspaceJob {
  state: 'pending' | 'processing' | 'finished';
  name: string;
  descriptor: string;
  createAt: number;
  tasks: WorkspaceTask[];
}

export interface WorkspaceTask {
  state: 'pending' | 'processing' | 'success' | 'failed';
  name: string;
  descriptor: string;
  segs: WorkspaceSegment[];
}

export interface WorkspaceSegment {
  state: 'pending' | 'processing' | 'success' | 'fallback-success' | 'failed';
  src: string;
  dst: string;
  log: string[];
}
