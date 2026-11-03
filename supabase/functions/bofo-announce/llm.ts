// bofo-announce / llm.ts - OpenRouter call, same pattern as bofo-chat's callLLM (key + LLM_MODELS, first 3 models, timeouts, think blocks stripped).
// Pure TypeScript: the environment and fetch are passed in. NEVER logs prompts, field values or model output (only model id + HTTP status).
import { stripThinking } from "./clean.ts";

export type LlmFn = (system: string, user: string, opts: { maxTokens: number; temperature: number; accept: (text: string) => boolean }) => Promise<string | null>;
export interface LlmEnv { key: string; models: string[] }

export function readLlmEnv(get: (k: string) => string | undefined): LlmEnv {
  return { key: (get("OPENROUTER_API_KEY") ?? "").trim(), models: (get("LLM_MODELS") ?? "").split(",").map((m) => m.trim()).filter(Boolean) };
}

/** null when the secrets are missing (the caller then uses the template). */
export function makeLlm(env: LlmEnv, fetchFn: typeof fetch = fetch, timeoutMs = 10_000, budgetMs = 25_000): LlmFn | null {
  if (!env.key || !env.models.length) { console.error("bofo-announce: OPENROUTER_API_KEY or LLM_MODELS is not set -> template answers only"); return null; }
  return async (system, user, opts) => {
    const started = Date.now();
    for (const model of env.models.slice(0, 3)) {
      if (Date.now() - started > budgetMs - 2000) break;
      const ctl = new AbortController();
      const timer = setTimeout(() => ctl.abort(), timeoutMs);
      try {
        const r = await fetchFn("https://openrouter.ai/api/v1/chat/completions", {
          method: "POST", signal: ctl.signal,
          headers: { "content-type": "application/json", authorization: `Bearer ${env.key}`, "X-Title": "BOFO Finder" },
          body: JSON.stringify({ model, max_tokens: opts.maxTokens, temperature: opts.temperature, reasoning: { exclude: true }, include_reasoning: false,
            messages: [{ role: "system", content: system }, { role: "user", content: user }] }),
        });
        if (!r.ok) { console.error(`bofo-announce: model ${model} -> HTTP ${r.status}`); continue; }
        const out = await r.json();
        const raw = out?.choices?.[0]?.message?.content;
        const text = typeof raw === "string" ? stripThinking(raw) : "";
        if (text && opts.accept(text)) return text;
        console.error(`bofo-announce: model ${model} gave an unusable answer`);   // no content in the log
      } catch (_e) { console.error(`bofo-announce: model ${model} failed (timeout/network)`); } finally { clearTimeout(timer); }
    }
    return null;
  };
}
