// bofo-announce / llm.ts - v2.4.9. Free AI via Groq (no card needed) first, OpenRouter models as backup.
// Model list = secret ANNOUNCE_MODELS (else LLM_MODELS), comma separated. An entry "groq:<id>" is sent to Groq with GROQ_API_KEY;
// any other entry is an OpenRouter id sent with OPENROUTER_API_KEY. Entries whose key is missing are skipped.
// Pure TypeScript: the environment and fetch are passed in. NEVER logs prompts, field values or model output (only model id + HTTP status).
import { stripThinking } from "./clean.ts";

export type LlmFn = (system: string, user: string, opts: { maxTokens: number; temperature: number; accept: (text: string) => boolean }) => Promise<string | null>;
export interface LlmEnv { key: string; groqKey?: string; models: string[] }

export function readLlmEnv(get: (k: string) => string | undefined): LlmEnv {
  const list = ((get("ANNOUNCE_MODELS") ?? "").trim() || (get("LLM_MODELS") ?? ""));
  return {
    key: (get("OPENROUTER_API_KEY") ?? "").trim(),
    groqKey: (get("GROQ_API_KEY") ?? "").trim(),
    models: list.split(",").map((m) => m.trim()).filter(Boolean),
  };
}

const GROQ_URL = "https://api.groq.com/openai/v1/chat/completions";
const OPENROUTER_URL = "https://openrouter.ai/api/v1/chat/completions";

/** null when no usable model + key exists (the caller then uses the template).
 *  Tries up to 6 models in order; models that answered HTTP 429 get ONE more try after a short pause.
 *  Total wait stays under the app's 40 s call limit (budgetMs). */
export function makeLlm(env: LlmEnv, fetchFn: typeof fetch = fetch, timeoutMs = 8_000, budgetMs = 32_000, retryPauseMs = 2_000): LlmFn | null {
  const usable = env.models.filter((m) => (m.startsWith("groq:") ? !!env.groqKey : !!env.key)).slice(0, 6);
  if (!usable.length) { console.error("bofo-announce: no usable model (set GROQ_API_KEY + ANNOUNCE_MODELS=groq:..., or OPENROUTER_API_KEY + model ids) -> template answers only"); return null; }
  return async (system, user, opts) => {
    const started = Date.now();
    let pending = usable;
    for (let pass = 0; pass < 2 && pending.length; pass++) {
      if (pass === 1) await new Promise((res) => setTimeout(res, retryPauseMs));
      const again: string[] = [];
      for (const entry of pending) {
        if (Date.now() - started > budgetMs - 2000) return null;
        const isGroq = entry.startsWith("groq:");
        const model = isGroq ? entry.slice(5) : entry;
        const ctl = new AbortController();
        const timer = setTimeout(() => ctl.abort(), timeoutMs);
        try {
          const payload: Record<string, unknown> = { model, max_tokens: opts.maxTokens, temperature: opts.temperature, messages: [{ role: "system", content: system }, { role: "user", content: user }] };
          if (!isGroq) { payload.reasoning = { exclude: true }; payload.include_reasoning = false; }   // OpenRouter only (Groq rejects unknown fields)
          const r = await fetchFn(isGroq ? GROQ_URL : OPENROUTER_URL, {
            method: "POST", signal: ctl.signal,
            headers: { "content-type": "application/json", authorization: `Bearer ${isGroq ? env.groqKey : env.key}`, "X-Title": "BOFO Finder" },
            body: JSON.stringify(payload),
          });
          if (!r.ok) {
            let why = ""; try { const e = await r.json(); why = String(e?.error?.message ?? e?.error?.code ?? "").slice(0, 140); } catch (_e) { /* no body */ }   // provider error text only, never our prompt
            console.error(`bofo-announce: model ${entry} -> HTTP ${r.status} ${why}`); if (r.status === 429) again.push(entry); continue;
          }
          const out = await r.json();
          const raw = out?.choices?.[0]?.message?.content;
          const text = typeof raw === "string" ? stripThinking(raw) : "";
          if (text && opts.accept(text)) return text;
          console.error(`bofo-announce: model ${entry} gave an unusable answer`);   // no content in the log
        } catch (_e) { console.error(`bofo-announce: model ${entry} failed (timeout/network)`); } finally { clearTimeout(timer); }
      }
      pending = again;
    }
    return null;
  };
}