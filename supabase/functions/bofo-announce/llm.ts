// bofo-announce / llm.ts - OpenRouter call, same pattern as bofo-chat's callLLM (key + LLM_MODELS, first 3 models, timeouts, think blocks stripped).
// Pure TypeScript: the environment and fetch are passed in. NEVER logs prompts, field values or model output (only model id + HTTP status).
import { stripThinking } from "./clean.ts";

export type LlmFn = (system: string, user: string, opts: { maxTokens: number; temperature: number; accept: (text: string) => boolean }) => Promise<string | null>;
export interface LlmEnv { key: string; models: string[] }

export function readLlmEnv(get: (k: string) => string | undefined): LlmEnv {
  return { key: (get("OPENROUTER_API_KEY") ?? "").trim(), models: (get("LLM_MODELS") ?? "").split(",").map((m) => m.trim()).filter(Boolean) };
}

/** null when the secrets are missing (the caller then uses the template).
 *  v2.4.6: tries up to 6 models (free models are often rate-limited), and gives the ones that answered HTTP 429 ONE more try after a short pause.
 *  Total wait stays under the app's 40 s call limit (budgetMs). */
export function makeLlm(env: LlmEnv, fetchFn: typeof fetch = fetch, timeoutMs = 8_000, budgetMs = 32_000, retryPauseMs = 2_000): LlmFn | null {
  if (!env.key || !env.models.length) { console.error("bofo-announce: OPENROUTER_API_KEY or LLM_MODELS is not set -> template answers only"); return null; }
  return async (system, user, opts) => {
    const started = Date.now();
    let pending = env.models.slice(0, 6);
    for (let pass = 0; pass < 2 && pending.length; pass++) {
      if (pass === 1) await new Promise((res) => setTimeout(res, retryPauseMs));
      const again: string[] = [];
      for (const model of pending) {
        if (Date.now() - started > budgetMs - 2000) return null;
        const ctl = new AbortController();
        const timer = setTimeout(() => ctl.abort(), timeoutMs);
        try {
          const r = await fetchFn("https://openrouter.ai/api/v1/chat/completions", {
            method: "POST", signal: ctl.signal,
            headers: { "content-type": "application/json", authorization: `Bearer ${env.key}`, "X-Title": "BOFO Finder" },
            body: JSON.stringify({ model, max_tokens: opts.maxTokens, temperature: opts.temperature, reasoning: { exclude: true }, include_reasoning: false,
              messages: [{ role: "system", content: system }, { role: "user", content: user }] }),
          });
          if (!r.ok) { console.error(`bofo-announce: model ${model} -> HTTP ${r.status}`); if (r.status === 429) again.push(model); continue; }
          const out = await r.json();
          const raw = out?.choices?.[0]?.message?.content;
          const text = typeof raw === "string" ? stripThinking(raw) : "";
          if (text && opts.accept(text)) return text;
          console.error(`bofo-announce: model ${model} gave an unusable answer`);   // no content in the log
        } catch (_e) { console.error(`bofo-announce: model ${model} failed (timeout/network)`); } finally { clearTimeout(timer); }
      }
      pending = again;
    }
    return null;
  };
}
