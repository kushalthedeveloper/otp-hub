create table if not exists public.otp_hub_devices (
  device_id uuid primary key,
  account_id uuid not null references public.otp_hub_accounts(id) on delete cascade,
  device_label text not null default 'Android phone',
  remote_scan_enabled boolean not null default false,
  last_seen_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);
create index if not exists otp_hub_devices_account_seen_idx
  on public.otp_hub_devices(account_id, last_seen_at desc);

create table if not exists public.otp_hub_remote_commands (
  id uuid primary key default gen_random_uuid(),
  account_id uuid not null references public.otp_hub_accounts(id) on delete cascade,
  device_id uuid not null references public.otp_hub_devices(device_id) on delete cascade,
  command_type text not null check (command_type in ('scan_sms')),
  status text not null default 'pending' check (status in ('pending','running','completed','failed','expired')),
  encrypted_result jsonb,
  error_code text,
  created_at timestamptz not null default now(),
  claimed_at timestamptz,
  completed_at timestamptz,
  expires_at timestamptz not null default (now() + interval '30 minutes')
);
create index if not exists otp_hub_remote_commands_device_status_idx
  on public.otp_hub_remote_commands(device_id, status, created_at desc);

alter table public.otp_hub_devices enable row level security;
alter table public.otp_hub_remote_commands enable row level security;
revoke all on public.otp_hub_devices from anon, authenticated, public;
revoke all on public.otp_hub_remote_commands from anon, authenticated, public;

create or replace function public.otp_hub_device_register(p_token text,p_device_id uuid,p_device_label text)
returns jsonb language plpgsql security definer
set search_path to 'pg_catalog','public','extensions'
as $function$
declare v_account_id uuid;
begin
  select account_id into v_account_id from public.otp_hub_sessions
  where token_hash=encode(extensions.digest(p_token,'sha256'),'hex') and expires_at>now();
  if v_account_id is null then raise exception 'Session expired'; end if;
  insert into public.otp_hub_devices(device_id,account_id,device_label,last_seen_at,updated_at)
  values(p_device_id,v_account_id,left(coalesce(nullif(p_device_label,''),'Android phone'),60),now(),now())
  on conflict(device_id) do update set device_label=excluded.device_label,last_seen_at=now(),updated_at=now()
  where public.otp_hub_devices.account_id=excluded.account_id;
  if not exists(select 1 from public.otp_hub_devices where device_id=p_device_id and account_id=v_account_id) then
    raise exception 'Device is already paired to another account';
  end if;
  return jsonb_build_object('device_id',p_device_id,'remote_scan_enabled',
    (select remote_scan_enabled from public.otp_hub_devices where device_id=p_device_id));
end;
$function$;

create or replace function public.otp_hub_device_set_remote_scan(p_token text,p_device_id uuid,p_enabled boolean)
returns boolean language plpgsql security definer
set search_path to 'pg_catalog','public','extensions'
as $function$
declare v_account_id uuid;
begin
  select account_id into v_account_id from public.otp_hub_sessions
  where token_hash=encode(extensions.digest(p_token,'sha256'),'hex') and expires_at>now();
  if v_account_id is null then raise exception 'Session expired'; end if;
  update public.otp_hub_devices set remote_scan_enabled=p_enabled,updated_at=now(),last_seen_at=now()
  where device_id=p_device_id and account_id=v_account_id;
  if not found then raise exception 'Paired device not found'; end if;
  if not p_enabled then
    update public.otp_hub_remote_commands set status='expired'
    where device_id=p_device_id and status in ('pending','running');
  end if;
  return true;
end;
$function$;

create or replace function public.otp_hub_remote_scan_request(p_token text)
returns jsonb language plpgsql security definer
set search_path to 'pg_catalog','public','extensions'
as $function$
declare v_account_id uuid; v_device_id uuid; v_command_id uuid;
begin
  select account_id into v_account_id from public.otp_hub_sessions
  where token_hash=encode(extensions.digest(p_token,'sha256'),'hex') and expires_at>now();
  if v_account_id is null then raise exception 'Session expired'; end if;
  select device_id into v_device_id from public.otp_hub_devices
  where account_id=v_account_id and remote_scan_enabled=true and last_seen_at>now()-interval '30 minutes'
  order by last_seen_at desc limit 1;
  if v_device_id is null then raise exception 'No online device has Remote SMS Scan enabled'; end if;
  if (select count(*) from public.otp_hub_remote_commands
      where account_id=v_account_id and command_type='scan_sms'
      and created_at>now()-interval '5 minutes' and status in ('pending','running','completed'))>=2 then
    raise exception 'Please wait before requesting another SMS scan';
  end if;
  insert into public.otp_hub_remote_commands(account_id,device_id,command_type)
  values(v_account_id,v_device_id,'scan_sms') returning id into v_command_id;
  return jsonb_build_object('command_id',v_command_id,'status','pending');
end;
$function$;

create or replace function public.otp_hub_remote_command_poll(p_token text,p_device_id uuid)
returns jsonb language plpgsql security definer
set search_path to 'pg_catalog','public','extensions'
as $function$
declare v_account_id uuid; v_commands jsonb;
begin
  select account_id into v_account_id from public.otp_hub_sessions
  where token_hash=encode(extensions.digest(p_token,'sha256'),'hex') and expires_at>now();
  if v_account_id is null then raise exception 'Session expired'; end if;
  update public.otp_hub_devices set last_seen_at=now(),updated_at=now()
  where device_id=p_device_id and account_id=v_account_id and remote_scan_enabled=true;
  if not found then return '[]'::jsonb; end if;
  with claimed as (
    update public.otp_hub_remote_commands set status='running',claimed_at=now()
    where id in (
      select id from public.otp_hub_remote_commands
      where device_id=p_device_id and account_id=v_account_id and status='pending' and expires_at>now()
      order by created_at asc limit 2 for update skip locked
    ) returning id,command_type
  )
  select coalesce(jsonb_agg(jsonb_build_object('id',id,'type',command_type)),'[]'::jsonb)
  into v_commands from claimed;
  update public.otp_hub_remote_commands set status='expired'
  where device_id=p_device_id and status='pending' and expires_at<=now();
  return v_commands;
end;
$function$;

create or replace function public.otp_hub_remote_command_complete(
  p_token text,p_device_id uuid,p_command_id uuid,p_status text,p_encrypted_result jsonb,p_error_code text default null
) returns boolean language plpgsql security definer
set search_path to 'pg_catalog','public','extensions'
as $function$
declare v_account_id uuid;
begin
  select account_id into v_account_id from public.otp_hub_sessions
  where token_hash=encode(extensions.digest(p_token,'sha256'),'hex') and expires_at>now();
  if v_account_id is null then raise exception 'Session expired'; end if;
  if p_status not in ('completed','failed') then raise exception 'Invalid command status'; end if;
  if p_status='completed' and (
    jsonb_typeof(p_encrypted_result)<>'object'
    or not (p_encrypted_result ? 'salt' and p_encrypted_result ? 'iv' and p_encrypted_result ? 'data')
    or pg_column_size(p_encrypted_result)>16384
  ) then raise exception 'Invalid encrypted result'; end if;
  update public.otp_hub_remote_commands
  set status=p_status,encrypted_result=case when p_status='completed' then p_encrypted_result else null end,
      error_code=case when p_status='failed' then left(coalesce(p_error_code,'scan_failed'),40) else null end,
      completed_at=now()
  where id=p_command_id and device_id=p_device_id and account_id=v_account_id and status='running' and expires_at>now();
  if not found then raise exception 'Command not found or expired'; end if;
  return true;
end;
$function$;

create or replace function public.otp_hub_remote_command_status(p_token text,p_command_id uuid)
returns jsonb language plpgsql security definer
set search_path to 'pg_catalog','public','extensions'
as $function$
declare v_account_id uuid; v_result jsonb;
begin
  select account_id into v_account_id from public.otp_hub_sessions
  where token_hash=encode(extensions.digest(p_token,'sha256'),'hex') and expires_at>now();
  if v_account_id is null then raise exception 'Session expired'; end if;
  select jsonb_build_object('status',status,'encrypted_result',encrypted_result,'error_code',error_code)
  into v_result from public.otp_hub_remote_commands where id=p_command_id and account_id=v_account_id;
  if v_result is null then raise exception 'Command not found'; end if;
  return v_result;
end;
$function$;

revoke all on function public.otp_hub_device_register(text,uuid,text) from public,authenticated;
revoke all on function public.otp_hub_device_set_remote_scan(text,uuid,boolean) from public,authenticated;
revoke all on function public.otp_hub_remote_scan_request(text) from public,authenticated;
revoke all on function public.otp_hub_remote_command_poll(text,uuid) from public,authenticated;
revoke all on function public.otp_hub_remote_command_complete(text,uuid,uuid,text,jsonb,text) from public,authenticated;
revoke all on function public.otp_hub_remote_command_status(text,uuid) from public,authenticated;
grant execute on function public.otp_hub_device_register(text,uuid,text) to anon;
grant execute on function public.otp_hub_device_set_remote_scan(text,uuid,boolean) to anon;
grant execute on function public.otp_hub_remote_scan_request(text) to anon;
grant execute on function public.otp_hub_remote_command_poll(text,uuid) to anon;
grant execute on function public.otp_hub_remote_command_complete(text,uuid,uuid,text,jsonb,text) to anon;
grant execute on function public.otp_hub_remote_command_status(text,uuid) to anon;
