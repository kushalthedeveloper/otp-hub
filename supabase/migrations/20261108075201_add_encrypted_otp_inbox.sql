create table if not exists public.otp_hub_otp_inbox (
  account_id uuid primary key references public.otp_hub_accounts(id) on delete cascade,
  encrypted_payload jsonb not null,
  updated_at timestamptz not null default now(),
  constraint otp_hub_otp_inbox_payload_object check (jsonb_typeof(encrypted_payload) = 'object')
);

alter table public.otp_hub_otp_inbox enable row level security;
revoke all on public.otp_hub_otp_inbox from anon, authenticated, public;

create or replace function public.otp_hub_otp_inbox_get(p_token text)
returns jsonb
language plpgsql
security definer
set search_path to 'pg_catalog', 'public', 'extensions'
as $function$
declare
  v_account_id uuid;
  v_payload jsonb;
begin
  select s.account_id into v_account_id
  from public.otp_hub_sessions s
  where s.token_hash = encode(extensions.digest(p_token, 'sha256'), 'hex')
    and s.expires_at > now();
  if v_account_id is null then raise exception 'Session expired'; end if;

  select encrypted_payload into v_payload
  from public.otp_hub_otp_inbox
  where account_id = v_account_id;
  return v_payload;
end;
$function$;

create or replace function public.otp_hub_otp_inbox_save(p_token text, p_encrypted_payload jsonb)
returns boolean
language plpgsql
security definer
set search_path to 'pg_catalog', 'public', 'extensions'
as $function$
declare
  v_account_id uuid;
begin
  if jsonb_typeof(p_encrypted_payload) <> 'object'
     or not (p_encrypted_payload ? 'salt' and p_encrypted_payload ? 'iv' and p_encrypted_payload ? 'data')
     or pg_column_size(p_encrypted_payload) > 65536 then
    raise exception 'Invalid or oversized encrypted inbox payload';
  end if;

  select s.account_id into v_account_id
  from public.otp_hub_sessions s
  where s.token_hash = encode(extensions.digest(p_token, 'sha256'), 'hex')
    and s.expires_at > now();
  if v_account_id is null then raise exception 'Session expired'; end if;

  insert into public.otp_hub_otp_inbox(account_id, encrypted_payload, updated_at)
  values (v_account_id, p_encrypted_payload, now())
  on conflict (account_id) do update
    set encrypted_payload = excluded.encrypted_payload, updated_at = now();
  return true;
end;
$function$;

revoke all on function public.otp_hub_otp_inbox_get(text) from public;
revoke all on function public.otp_hub_otp_inbox_save(text, jsonb) from public;
grant execute on function public.otp_hub_otp_inbox_get(text) to anon;
grant execute on function public.otp_hub_otp_inbox_save(text, jsonb) to anon;
