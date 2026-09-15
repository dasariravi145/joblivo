/**
 * Centralized, type-safe frontend configuration.
 * Avoids hardcoding the API URL in business logic or presentation components.
 */
export const envConfig = {
  apiBaseUrl: process.env.NEXT_PUBLIC_API_BASE_URL || "http://localhost:8080",
} as const;
