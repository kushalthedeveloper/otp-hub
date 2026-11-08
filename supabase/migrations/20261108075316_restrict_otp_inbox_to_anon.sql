revoke all on function public.otp_hub_otp_inbox_get(text) from authenticated;
revoke all on function public.otp_hub_otp_inbox_save(text, jsonb) from authenticated;
grant execute on function public.otp_hub_otp_inbox_get(text) to anon;
grant execute on function public.otp_hub_otp_inbox_save(text, jsonb) to anon;
