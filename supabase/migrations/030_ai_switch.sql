-- 030: admin switch for the BOFO AI assistant (v2.3.2). Needs 014 (app_flags) and 017 (is_admin, audit_logs). Safe to run twice.
-- The flag row 'ai_enabled' (true = AI shown, false = AI hidden for everyone). Any signed-in user may read it (app_flags_read); only an admin can change it, through set_ai_enabled().
-- Turn it off/on from the SQL editor any time:  select public.set_ai_enabled(false);  /  select public.set_ai_enabled(true);   (the editor has no auth.uid(), so it is allowed)
insert into public.app_flags (key, value) values ('ai_enabled', true) on conflict (key) do nothing;

create or replace function public.set_ai_enabled(on_ boolean) returns void
language plpgsql security definer set search_path = public as $$
begin
  if auth.uid() is not null and not public.is_admin() then raise exception 'admins only'; end if;
  insert into public.app_flags (key, value, updated_at) values ('ai_enabled', on_, now())
    on conflict (key) do update set value = excluded.value, updated_at = now();
  begin
    insert into public.audit_logs (actor_id, action, target_type, details)
      values (auth.uid(), case when on_ then 'ai_enabled_on' else 'ai_enabled_off' end, 'app_flags', jsonb_build_object('value', on_));
  exception when others then null;   -- the audit line must never block the switch
  end;
end $$;
revoke all on function public.set_ai_enabled(boolean) from public, anon;
grant execute on function public.set_ai_enabled(boolean) to authenticated;
notify pgrst, 'reload schema';
-- Undo: drop function public.set_ai_enabled(boolean); delete from public.app_flags where key = 'ai_enabled';
