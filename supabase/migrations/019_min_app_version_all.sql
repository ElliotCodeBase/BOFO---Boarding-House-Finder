-- 019: old app versions are refused for EVERYONE, not only admins (v2.0.122). Run AFTER 018. Safe to run twice.
--
-- Same header as 018 ('x-bofo-app-version', sent by the app since v2.0.120). A "pre-request" function runs before every
-- REST / RPC request. When a minimum is set and the request has no header or a lower version, the request is refused (426).
-- NOT affected: the SQL editor, the service role, and the login pages (/auth/v1 is not a REST request).
-- Edge functions (delete-account, create-boost-checkout, bofo-chat) forward the app's header to the database since v2.0.122,
-- so a new app works through them and an old app is refused by them too.
--
-- The switch is OFF after this migration (empty text). Hooking it up is a SEPARATE step (see the bottom), so nothing changes by accident.
-- Turn ON  (only AFTER your own phone runs the minimum version or newer, or you lock yourself out):
--     select public.set_min_app_version('2.0.122');
-- Turn OFF (break-glass, SQL editor any time):
--     select public.set_min_app_version('');
-- Honest limit: the header is set by the app, so this stops OLD APP BUILDS, not someone who writes their own requests.

insert into public.app_settings (key, value) values ('min_app_version', '') on conflict (key) do nothing;

create or replace function public.enforce_min_app_version() returns void
language plpgsql stable security definer set search_path = public as $$
declare minv text; hv text; a int[]; b int[]; r text;
begin
  select s.value into minv from public.app_settings s where s.key = 'min_app_version';
  if coalesce(minv, '') = '' then return; end if;                       -- switch is off
  r := coalesce(nullif(current_setting('request.jwt.claims', true), '')::json ->> 'role', '');
  if r = 'service_role' then return; end if;                            -- server side / SQL editor tools
  begin
    hv := nullif(current_setting('request.headers', true), '')::json ->> 'x-bofo-app-version';
    if hv is not null then
      a := string_to_array(regexp_replace(hv, '[^0-9.].*$', ''), '.')::int[];   -- "2.0.122-debug" -> {2,0,122}
      b := string_to_array(minv, '.')::int[];
      if a >= b then return; end if;
    end if;
  exception when others then null;                                      -- unreadable header = treated as too old
  end;
  raise sqlstate 'PT426' using message = 'Please update the BOFO app to continue.', detail = 'min_app_version=' || minv;
end $$;
revoke all on function public.enforce_min_app_version() from public;
grant execute on function public.enforce_min_app_version() to anon, authenticated, service_role, authenticator;

create or replace function public.set_min_app_version(v text) returns text
language plpgsql security definer set search_path = public as $$
begin
  if coalesce(v, '') <> '' and v !~ '^[0-9]+\.[0-9]+\.[0-9]+$' then
    raise exception 'Use the form 2.0.122 (three numbers with dots), or an empty text to turn it off.';
  end if;
  insert into public.app_settings (key, value, updated_at) values ('min_app_version', coalesce(v, ''), now())
    on conflict (key) do update set value = excluded.value, updated_at = now();
  insert into public.audit_logs (actor_id, action, target_type, details)
    values (auth.uid(), 'min_app_version_set', 'app_settings', jsonb_build_object('value', coalesce(v, '')));
  return case when coalesce(v, '') = '' then 'App-version check is OFF for everyone' else 'Everyone needs app version ' || v || ' or newer' end;
end $$;
revoke all on function public.set_min_app_version(text) from public, anon, authenticated;

-- STEP 2 (run by hand, only after the app 2.0.122 is installed on your phone). Hooks the check into every request:
--     alter role authenticator set pgrst.db_pre_request = 'public.enforce_min_app_version';
--     notify pgrst, 'reload config';
-- Undo (break-glass, SQL editor):
--     alter role authenticator reset pgrst.db_pre_request;
--     notify pgrst, 'reload config';
notify pgrst, 'reload schema';
