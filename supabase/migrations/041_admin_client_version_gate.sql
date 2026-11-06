-- 041: the admin app gets its OWN minimum version (Stage 4). Run AFTER 039 and 040. Safe to run twice.
--
-- Before: app_version_ok() compared every request with ONE minimum (min_admin_app_version), so the user app and the new admin app,
-- which will have different version numbers, could not be gated separately.
-- Now: a request with header x-bofo-client = 'admin' (sent by the BOFO Admin app) is compared with 'min_admin_client_version';
-- every other request (the old app inside the user app: no such header) is compared with 'min_admin_app_version' exactly as before.
-- Nothing changes until you set a value. Honest limit (same as 018): the header stops OLD BUILDS, not a person who writes their own requests.
--
-- Turn ON (only after your own phone runs the admin app version you name, or you lock yourself out):
--     select public.admin_set_min_admin_client_version('2.9.9');   -- as Owner in the app's SQL role, or via the SQL editor: select public.set_min_admin_client_version('2.9.9');
-- Turn OFF: set it to '' (empty text).
--
-- ROLLBACK: re-run the function body from 018_min_admin_app_version.sql (app_version_ok) and
--     drop function if exists public.set_min_admin_client_version(text), public.admin_set_min_admin_client_version(text);
--     delete from public.app_settings where key = 'min_admin_client_version';

insert into public.app_settings (key, value) values ('min_admin_client_version', '') on conflict (key) do nothing;

create or replace function public.app_version_ok() returns boolean
language plpgsql stable security definer set search_path = public as $$
declare minv text; hv text; client text; a int[]; b int[];
begin
  begin
    client := nullif(current_setting('request.headers', true), '')::json ->> 'x-bofo-client';
  exception when others then client := null; end;
  select s.value into minv from public.app_settings s
   where s.key = case when client = 'admin' then 'min_admin_client_version' else 'min_admin_app_version' end;
  if coalesce(minv, '') = '' then return true; end if;
  begin
    hv := nullif(current_setting('request.headers', true), '')::json ->> 'x-bofo-app-version';
    if hv is null then return false; end if;
    a := string_to_array(regexp_replace(hv, '[^0-9.].*$', ''), '.')::int[];      -- "2.9.9-debug" -> {2,9,9}
    b := string_to_array(minv, '.')::int[];
    return a >= b;
  exception when others then
    return false;
  end;
end $$;
revoke all on function public.app_version_ok() from public, anon;
grant execute on function public.app_version_ok() to authenticated, service_role;

create or replace function public.set_min_admin_client_version(v text) returns text
language plpgsql security definer set search_path = public as $$
begin
  if coalesce(v, '') <> '' and v !~ '^[0-9]+\.[0-9]+\.[0-9]+$' then
    raise exception 'Use the form 2.9.9 (three numbers with dots), or an empty text to turn it off.';
  end if;
  insert into public.app_settings (key, value, updated_at) values ('min_admin_client_version', coalesce(v, ''), now())
    on conflict (key) do update set value = excluded.value, updated_at = now();
  perform public.admin_log('min_admin_client_version_set', 'app_settings', 'min_admin_client_version', jsonb_build_object('value', coalesce(v, '')));
  return case when coalesce(v, '') = '' then 'Admin app version check is OFF' else 'The admin app needs version ' || v || ' or newer' end;
end $$;
revoke all on function public.set_min_admin_client_version(text) from public, anon, authenticated;

create or replace function public.admin_set_min_admin_client_version(v text) returns text
language plpgsql security definer set search_path = public as $$
begin
  perform public.require_permission('app_config');
  return public.set_min_admin_client_version(v);
end $$;
revoke all on function public.admin_set_min_admin_client_version(text) from public, anon;
grant execute on function public.admin_set_min_admin_client_version(text) to authenticated;
notify pgrst, 'reload schema';
