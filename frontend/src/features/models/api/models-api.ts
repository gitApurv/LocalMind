import { apiClient } from "@/lib/api-client";
import type { ModelListResponse } from "../types";

export const modelsApi = {
  list: () => apiClient.get<ModelListResponse>("/api/models"),
};
