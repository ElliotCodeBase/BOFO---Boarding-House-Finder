-- 018: old app versions cannot use admin (v2.0.121). Run AFTER 017. Safe to run twice.
--
-- From v2.0.121 the app sends its version in the header 'x-bofo-app-version' on every request.
-- Once a minimum is set, is_admin() is false unless the request carries a version >= that minimum, so an old app
-- (no header, or a lower number) gets NO admin data and no admin action: ID-scan review, takedowns, AI chat log, boosts...
-- It sits on top of the 017 two-step check (both must pass). The SQL editor and the service role are not affected.
--
-- Honest limit: the header is set by the app, so this stops OLD APP BUILDS, not a person who writes their own requests.
-- The protection against people is the two-step code (017) and the admin role itself.
--
-- Turn ON  (only AFTER your own phone runs 2.0.121 or newer, or you lock yourself out of admin):
--     select public.set_min_admin_app_version('2.0.121');
-- Turn OFF (break-glass, SQL editor any time):
--     select public.set_min_admin_app_version('');

create table if not exists public.app_settings (
  key        text primary key,
  value      text not null default '',
  updated_at timestamptz not null default now()
);
alter table public.app_settings enable row level security;
revoke all on public.app_settings from anon, authenticated;       -- no policy, no grant: only the functions below (definer) read it
insert into public.app_settings (key, value) values ('min_admin_app_version', '') on conflict (key) do nothing;

-- true when no minimum is set, or the request's app version is at least the minimum
create or replace function public.app_version_ok() returns boolean
language plpgsql stable security definer set search_path = public as $$
declare minv text; hv text; a int[]; b int[];
begin
  select s.value into minv from public.app_settings s where s.key = 'min_admin_app_version';
  if coalesce(minv, '') = '' then return true; end if;
  begin
    hv := nullif(current_setting('request.headers', true), '')::json ->> 'x-bofo-app-version';
    if hv is null then return false; end if;
    a := string_to_array(regexp_replace(hv, '[^0-9.].*$', ''), '.')::int[];      -- "2.0.120-debug" -> {2,0,120}
    b := string_to_array(minv, '.')::int[];
    return a >= b;
  exception when others then
    return false;
  end;
end $$;
revoke all on function public.app_version_ok() from public, anon;
grant execute on function public.app_version_ok() to authenticated, service_role;

-- is_admin(): 017 rules + the version rule
create or replace function public.is_admin() returns boolean
language sql stable security definer set search_path = public as $$
  select exists (select 1 from public.users where id = auth.uid() and role = 'admin')
     and (
       not coalesce((select f.value from public.app_flags f where f.key = 'require_admin_mfa'), false)
       or coalesce(auth.jwt() ->> 'aal', 'aal1') = 'aal2'
     )
     and public.app_version_ok();
$$;
revoke all on function public.is_admin() from public, anon;
grant execute on function public.is_admin() to authenticated, service_role;

-- the switch: owner / SQL editor only (the app can never call it)
create or replace function public.set_min_admin_app_version(v text) returns text
language plpgsql security definer set search_path = public as $$
begin
  if coalesce(v, '') <> '' and v !~ '^[0-9]+\.[0-9]+\.[0-9]+$' then
    raise exception 'Use the form 2.0.120 (three numbers with dots), or an empty text to turn it off.';
  end if;
  insert into public.app_settings (key, value, updated_at) values ('min_admin_app_version', coalesce(v, ''), now())
    on conflict (key) do update set value = excluded.value, updated_at = now();
  insert into public.audit_logs (actor_id, action, target_type, details)
    values (auth.uid(), 'min_admin_app_version_set', 'app_settings', jsonb_build_object('value', coalesce(v, '')));
  return case when coalesce(v, '') = '' then 'Admin app-version check is OFF' else 'Admin needs app version ' || v || ' or newer' end;
end $$;
revoke all on function public.set_min_admin_app_version(text) from public, anon, authenticated;
notify pgrst, 'reload schema';
