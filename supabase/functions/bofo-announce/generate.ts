// bofo-announce / generate.ts - LLM first, template when the model is missing / failing / unusable. Never throws because of the model.
import type { AnnounceRequest } from "./clean.ts";
import { assemble, effectiveOptions, finalizeModelOutput } from "./clean.ts";
import type { LlmFn } from "./llm.ts";
import { SYSTEM_PROMPT, buildUserPrompt } from "./prompt.ts";
import { buildTemplate } from "./templates.ts";

export interface AnnounceResult { title: string; message: string; notes?: string; degraded: boolean }

export function templateResult(req: AnnounceRequest): AnnounceResult {
  const { tone, length } = effectiveOptions(req);
  const t = buildTemplate({ type: req.type, lang: req.lang, tone, length, emoji: req.emoji, fields: req.fields });
  return { ...assemble(t.title, t.message, req, length, t.notes), degraded: true };
}

export async function generate(req: AnnounceRequest, llm: LlmFn | null): Promise<AnnounceResult> {
  if (llm) {
    const { length } = effectiveOptions(req);
    try {
      let final: ReturnType<typeof finalizeModelOutput> = null;
      const text = await llm(SYSTEM_PROMPT, buildUserPrompt(req), {
        maxTokens: 700, temperature: req.adjust === "again" ? 0.8 : 0.3,
        accept: (t) => { final = finalizeModelOutput(t, req, length); return final !== null; },
      });
      if (text && final) return { ...(final as NonNullable<typeof final>), degraded: false };
    } catch (_e) { console.error("bofo-announce: model path failed -> template"); }
  }
  return templateResult(req);
}
