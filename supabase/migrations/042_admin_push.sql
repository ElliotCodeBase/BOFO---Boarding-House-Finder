-- 042: BOFO Admin app push (Stage 4, part 4, v2.9.11). Needs 029 (push), 033 (admin push) and 039 (roles). Safe to run twice.
-- NOT applied live: run it yourself, then redeploy send-push (see docs/PUSH_SETUP.md, "Admin app push").
-- What it adds
--   * device_tokens.app ('user' | 'admin'): which app a token belongs to. Old rows become 'user'. Chat pushes (dm / group) only ever go to 'user' tokens.
--   * register_push_token(p_token, p_app_version, p_app): a 3rd argument that defaults to 'user', so the user app keeps calling it unchanged.
--     p_app = 'admin' is refused unless the caller is active staff (is_staff()).
--   * staff_has(user, permission): the permission check of has_permission() for ANY user id (no JWT needed). Service role only.
--   * push_admin_tokens(permissions[], exclude_user): the phones that should get an admin push: active staff who hold at least one of the permissions
--     (members: role preset + grants - denies, not suspended, not expired; legacy admins = users.role 'admin' without a member row).
--     A phone registered BEFORE "log out everywhere" is skipped. A person who has the admin app on a phone gets the push there only;
--     without an admin-app token the user-app token is used (so legacy admins keep getting pushes). Service role only.
--   * trigger push_approval on admin_approvals (AFTER INSERT): push_notify('approval', {id, requested_by}). Ids only.
-- Lock-screen safe: the function sends only the type or a count, never a name or ID data (same rule as 033).
-- Undo: drop trigger if exists push_approval on public.admin_approvals; drop function if exists public.trg_push_approval(), public.push_admin_tokens(text[], uuid), public.staff_has(uuid, text);
--       then re-create the 2-argument register_push_token from 029 and: alter table public.device_tokens drop column if exists app;  (delete admin-app tokens first)

-- ===================== 1) which app a token belongs to =====================
alter table public.device_tokens add column if not exists app text not null default 'user';
do $$ begin
  if not exists (select 1 from pg_constraint where conname = 'device_tokens_app_check') then
    alter table public.device_tokens add constraint device_tokens_app_check check (app in ('user', 'admin'));
  end if;
end $$;

-- ===================== 2) register_push_token with the app =====================
drop function if exists public.register_push_token(text, text);
create or replace function public.register_push_token(p_token text, p_app_version text default null, p_app text default 'user')
returns void language plpgsql security definer set search_path = public as $$
declare a text := coalesce(nullif(btrim(p_app), ''), 'user');
begin
  if auth.uid() is null then raise exception 'sign in first'; end if;
  if p_token is null or char_length(p_token) < 20 or char_length(p_token) > 4096 then raise exception 'bad token'; end if;
  if a not in ('user', 'admin') then raise exception 'bad app'; end if;
  if a = 'admin' and not public.is_staff() then raise exception 'admins only' using errcode = '42501'; end if;
  insert into public.device_tokens (token, user_id, app_version, app)
    values (p_token, auth.uid(), left(p_app_version, 20), a)
    on conflict (token) do update set user_id = auth.uid(), app_version = excluded.app_version, app = excluded.app, updated_at = now();
  -- keep the 5 newest phones per user and app (old reinstalls leave dead tokens behind)
  delete from public.device_tokens d
   where d.user_id = auth.uid() and d.app = a
     and d.token in (select token from public.device_tokens where user_id = auth.uid() and app = a order by updated_at desc offset 5);
end $$;
revoke all on function public.register_push_token(text, text, text) from public, anon;
grant execute on function public.register_push_token(text, text, text) to authenticated;

-- ===================== 3) who is staff and what they may do (any user id) =====================
create or replace function public.staff_has(p_user uuid, p text) returns boolean
language plpgsql stable security definer set search_path = public as $$
declare m public.admin_members; perms text[];
begin
  if p_user is null or p is null or not (p = any (public.admin_permission_keys())) then return false; end if;
  select * into m from public.admin_members where user_id = p_user;
  if found then                                                      -- a member row always wins over the legacy rule (same as has_permission)
    if m.status <> 'active' then return false; end if;
    if m.expires_at is not null and m.expires_at <= now() then return false; end if;
    if m.role = 'owner' then return true; end if;
    select r.permissions into perms from public.admin_roles r where r.key = m.role;
    return p = any (coalesce(perms, '{}') || m.extra_grant) and not (p = any (m.extra_deny));
  end if;
  return exists (select 1 from public.users u where u.id = p_user and u.role = 'admin');     -- LEGACY, removed in Stage 5
end $$;
revoke all on function public.staff_has(uuid, text) from public, anon, authenticated;
grant execute on function public.staff_has(uuid, text) to service_role;

create or replace function public.push_admin_tokens(p_perms text[], p_exclude uuid default null)
returns table (token text, user_id uuid, app text, perms text[])
language sql stable security definer set search_path = public as $$
  with cand as (
    select u.id as uid, array(select q from unnest(p_perms) q where public.staff_has(u.id, q)) as held
      from public.users u
     where (p_exclude is null or u.id <> p_exclude)
       and (u.role = 'admin' or exists (select 1 from public.admin_members m where m.user_id = u.id))
  )
  select t.token, t.user_id, t.app, c.held
    from cand c
    join public.device_tokens t on t.user_id = c.uid
    left join public.admin_members m on m.user_id = c.uid
   where cardinality(c.held) > 0
     and (m.revoked_before is null or t.updated_at > m.revoked_before)        -- signed out everywhere after this phone registered
     and (t.app = 'admin' or not exists (select 1 from public.device_tokens x where x.user_id = c.uid and x.app = 'admin'));
$$;
revoke all on function public.push_admin_tokens(text[], uuid) from public, anon, authenticated;
grant execute on function public.push_admin_tokens(text[], uuid) to service_role;

-- ===================== 4) "an approval needs you" =====================
create or replace function public.trg_push_approval() returns trigger
language plpgsql security definer set search_path = public as $$
begin
  if new.status = 'pending' then
    perform public.push_notify('approval', jsonb_build_object('id', new.id, 'requested_by', new.requested_by));
  end if;
  return new;
end $$;
drop trigger if exists push_approval on public.admin_approvals;
create trigger push_approval after insert on public.admin_approvals for each row execute function public.trg_push_approval();

notify pgrst, 'reload schema';
