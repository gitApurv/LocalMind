/** Mirrors the backend `ModelInfo`. */
export interface ModelInfo {
  name: string;
  model?: string;
  size?: number;
  modifiedAt?: string;
  supportsTools?: boolean;
}

/** Mirrors the backend `ModelResponse`. */
export interface ModelListResponse {
  models: ModelInfo[];
}

/** One entry of the model dropdown. */
export interface ModelOption {
  id: string;
  label: string;
  badge: string;
}
