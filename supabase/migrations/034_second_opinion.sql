-- v2.7.0 (Admin next steps, step 3): "needs a second opinion" flag on a waiting ID request.
-- Not run by the AI. Run it in the Supabase SQL editor, then the Queue long-press menu works.
alter table public.owner_verifications
  add column if not exists needs_second_opinion boolean not null default false,
  add column if not exists second_opinion_by uuid references public.users(id) on delete set null,
  add column if not exists second_opinion_at timestamptz;

create or replace function public.admin_flag_second_opinion(vid uuid, flag boolean) returns jsonb
language plpgsql security definer set search_path = public as $$
declare v public.owner_verifications;
begin
  if not public.is_admin() then raise exception 'admins only'; end if;
  select * into v from public.owner_verifications where id = vid and status = 'pending';
  if v.id is null then raise exception 'not pending'; end if;
  update public.owner_verifications
     set needs_second_opinion = flag,
         second_opinion_by = case when flag then auth.uid() else null end,
         second_opinion_at = case when flag then now() else null end
   where id = vid;
  insert into public.audit_logs (actor_id, action, target_type, target_id, details)
    values (auth.uid(), case when flag then 'verification_flagged' else 'verification_unflagged' end, 'owner_verification', vid::text,
            jsonb_build_object('owner_id', v.owner_id));
  return '{}'::jsonb;
end $$;

revoke all on function public.admin_flag_second_opinion(uuid, boolean) from public, anon;
grant execute on function public.admin_flag_second_opinion(uuid, boolean) to authenticated;
notify pgrst, 'reload schema';
