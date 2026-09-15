/**
 * Internal AI Gateway Foundation for Joblivo.
 *
 * <h2>Why the Gateway Exists</h2>
 * The AI Gateway provides a single, provider-neutral abstraction layer for internal AI generation
 * and inference across Joblivo. It decouples higher-level domain services from provider-specific
 * protocol details, authentication schemes, SDK quirks, and payload structures.
 *
 * <h2>Why Business Services Must Not Call Providers Directly</h2>
 * <ul>
 *   <li><b>Vendor Lock-in Prevention:</b> Business logic (Truth Engine, Resume AI, Job Matching)
 *       depends solely on Joblivo's {@link com.joblivo.ai.AiGateway} and provider-neutral models
 *       ({@link com.joblivo.ai.model.AiRequest}, {@link com.joblivo.ai.model.AiResponse}).</li>
 *   <li><b>Centralized Policy & Routing:</b> Model routing, fallback policies, rate limiting, token tracking,
 *       and fail-closed controls are managed in one architectural location via {@link com.joblivo.ai.routing.ModelRouter}.</li>
 *   <li><b>Observability & Security:</b> Audit trails, correlation tracing, and telemetry are standardized
 *       without spreading logging or credential-handling concerns into domain features.</li>
 * </ul>
 *
 * <h2>How Future Providers Plug In</h2>
 * Future AI providers (AWS Bedrock, OpenAI, Google Gemini, etc.) implement the internal SPI contract
 * {@link com.joblivo.ai.provider.AiProviderClient} and are discovered and managed by
 * {@link com.joblivo.ai.provider.AiProviderRegistry}. Business services remain completely unaware of
 * which concrete provider client fulfills their request.
 */
package com.joblivo.ai;
