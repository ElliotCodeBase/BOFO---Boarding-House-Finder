-- BOFO Finder: paid "Featured" boosts for BH owners (GCash via PayMongo). Run after 002.

create table public.boost_plans (
  id text primary key,
  name text not null,
  days int not null check (days between 1 and 90),
  price_php numeric(10,2) not null check (price_php > 0),
  active boolean not null default true,
  sort_order int not null default 0
);
-- Placeholder prices: edit freely (price changes never affect past payments, they store their own amount).
insert into public.boost_plans (id, name, days, price_php, sort_order) values
  ('starter', 'Starter · 3 days',   3,  49, 1),
  ('weekly',  'Weekly · 7 days',    7,  99, 2),
  ('monthly', 'Monthly · 30 days', 30, 299, 3)
on conflict (id) do nothing;

create table public.payments (
  id uuid primary key default gen_random_uuid(),
  owner_id uuid not null references public.users(id) on delete cascade,
  boarding_house_id uuid not null references public.boarding_houses(id) on delete cascade,
  plan_id text not null references public.boost_plans(id),
  amount_php numeric(10,2) not null,
  provider text not null default 'paymongo',
  method text not null default 'gcash',
  provider_session_id text unique,
  reference_number text not null unique,
  status text not null default 'pending' check (status in ('pending','paid','failed','expired','refunded')),
  created_at timestamptz not null default now(),
  paid_at timestamptz
);
create index on public.payments (owner_id, created_at desc);
create index on public.payments (status, created_at desc);

create table public.boosts (
  id uuid primary key default gen_random_uuid(),
  boarding_house_id uuid not null references public.boarding_houses(id) on delete cascade,
  owner_id uuid not null references public.users(id) on delete cascade,
  payment_id uuid not null unique references public.payments(id) on delete cascade,   -- unique = a payment can never boost twice
  plan_id text not null references public.boost_plans(id),
  starts_at timestamptz not null,
  ends_at timestamptz not null,
  check (ends_at > starts_at)
);
create index on public.boosts (boarding_house_id, ends_at desc);

alter table public.boost_plans enable row level security;
alter table public.payments enable row level security;
alter table public.boosts enable row level security;

create policy plans_read on public.boost_plans for select to authenticated using (active);
-- Payments/boosts are written ONLY by Edge Functions (service role). Clients can read their own.
create policy pay_read on public.payments for select to authenticated using (owner_id = (select auth.uid()) or (select public.is_admin()));
create policy boost_read on public.boosts for select to authenticated using (owner_id = (select auth.uid()) or (select public.is_admin()));

-- Idempotent activation: called by the webhook after a verified, amount-matched payment.
-- Boosts STACK: a second purchase starts when the current one ends.
create or replace function public.activate_boost(pid uuid) returns jsonb
language plpgsql security definer set search_path = public as $$
declare p public.payments; pl public.boost_plans; start_at timestamptz;
begin
  select * into p from public.payments where id = pid for update;
  if p.id is null then raise exception 'payment not found'; end if;
  if exists (select 1 from public.boosts where payment_id = pid) then return jsonb_build_object('already', true); end if;
  select * into pl from public.boost_plans where id = p.plan_id;
  select greatest(now(), coalesce(max(ends_at), now())) into start_at from public.boosts where boarding_house_id = p.boarding_house_id;
  update public.payments set status = 'paid', paid_at = now() where id = pid;
  insert into public.boosts (boarding_house_id, owner_id, payment_id, plan_id, starts_at, ends_at)
    values (p.boarding_house_id, p.owner_id, pid, p.plan_id, start_at, start_at + make_interval(days => pl.days));
  insert into public.audit_logs (actor_id, action, target_type, target_id, details)
    values (p.owner_id, 'boost_activated', 'boarding_house', p.boarding_house_id::text, jsonb_build_object('payment_id', pid, 'plan', p.plan_id, 'amount', p.amount_php));
  return jsonb_build_object('ok', true);
end $$;
revoke all on function public.activate_boost from public, anon, authenticated;   -- service role only

-- Admin: refund bookkeeping + end the boost (the money itself is refunded in the PayMongo dashboard).
create or replace function public.admin_refund_boost(pid uuid, reason text) returns jsonb
language plpgsql security definer set search_path = public as $$
begin
  if not public.is_admin() then raise exception 'admins only'; end if;
  update public.payments set status = 'refunded' where id = pid;
  update public.boosts set ends_at = least(ends_at, now()), starts_at = least(starts_at, now() - interval '1 second') where payment_id = pid;
  insert into public.audit_logs (actor_id, action, target_type, target_id, details)
    values (auth.uid(), 'boost_refunded', 'payment', pid::text, jsonb_build_object('reason', reason));
  return '{}'::jsonb;
end $$;
revoke all on function public.admin_refund_boost from public, anon;
grant execute on function public.admin_refund_boost to authenticated;

-- Featured flag on the card view (only verified owners + active listings can be featured).
drop view if exists public.bh_cards;
create view public.bh_cards as
  select b.id, b.owner_id, b.name, b.address, b.lat, b.lng, b.price, b.price_mode,
         b.free_water, b.free_electricity, b.free_wifi, b.status, b.created_at,
         coalesce(a.available_beds, 0) as available_beds,
         coalesce(a.avg_rating, 0)     as avg_rating,
         coalesce(a.review_count, 0)   as review_count,
         (select p.url from public.photos p where p.boarding_house_id = b.id order by p.sort_order limit 1) as photo_url,
         (u.role = 'owner' and u.is_verified) as owner_verified,
         coalesce(a.views + 3 * a.favorites_count + 5 * a.chats_count, 0) as hot_score,
         (u.role = 'owner' and u.is_verified and b.status = 'active' and bo.ends_at is not null) as is_featured,
         bo.ends_at as featured_until
  from public.boarding_houses b
  join public.users u on u.id = b.owner_id
  left join public.bh_activity a on a.boarding_house_id = b.id
  left join lateral (select max(x.ends_at) as ends_at from public.boosts x
                      where x.boarding_house_id = b.id and x.starts_at <= now() and x.ends_at > now()) bo on true
  where b.status = 'active' or b.owner_id = (select auth.uid()) or (select public.is_admin());
grant select on public.bh_cards to authenticated;
