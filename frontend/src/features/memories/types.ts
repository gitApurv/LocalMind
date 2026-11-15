export interface Memory {
  id: string;
  memoryFact: string;
  pinned: boolean;
  createdAt: string;
}

export interface UpdateMemoryRequest {
  pinned: boolean;
}
