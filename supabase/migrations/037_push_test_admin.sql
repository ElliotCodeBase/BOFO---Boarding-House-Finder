-- 037 (v2.9.5): admin-only "Send test notification" in Settings. Nobody else can send a test or read the push server's answer.
-- send_test_push(): queues a test push to the CALLER's own phones and returns the pg_net request id.
-- push_test_result(rid): the edge function's answer (status + short text) for that request id.
-- Needs 029 (push_settings, pg_net). NOT applied live: run it yourself.
create or replace function public.send_test_push() returns jsonb
language plpgsql security definer set search_path = public, extensions as $$
declare u text; s text; rid bigint;
begin
  if auth.uid() is null or not public.is_admin() then raise exception 'admin only'; end if;
  select value into u from public.push_settings where key = 'url';
  select value into s from public.push_settings where key = 'secret';
  if u is null or s is null then return jsonb_build_object('stage', 'not_set_up'); end if;
  select net.http_post(
    url := u,
    body := jsonb_build_object('kind', 'test', 'record', jsonb_build_object('as', 'me', 'user_id', auth.uid())),
    headers := jsonb_build_object('Content-Type', 'application/json', 'x-bofo-push-secret', s)) into rid;
  return jsonb_build_object('stage', 'sent', 'request_id', rid);
end $$;

create or replace function public.push_test_result(rid bigint) returns jsonb
language plpgsql security definer set search_path = public, extensions as $$
declare r record;
begin
  if auth.uid() is null or not public.is_admin() then raise exception 'admin only'; end if;
  select status_code, left(content, 200) as answer, error_msg into r from net._http_response where id = rid;
  if not found then return jsonb_build_object('ready', false); end if;
  return jsonb_build_object('ready', true, 'status_code', r.status_code, 'answer', r.answer, 'error', r.error_msg);
end $$;

revoke all on function public.send_test_push, public.push_test_result from public, anon;
grant execute on function public.send_test_push, public.push_test_result to authenticated;
notify pgrst, 'reload schema';
