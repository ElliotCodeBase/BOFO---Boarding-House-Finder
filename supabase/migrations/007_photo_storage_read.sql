-- Run AFTER 006. Fixes: "uploading photo ... HTTP 400 (code AccessDenied) new row violates row-level security policy".
-- Uploads use upsert and Storage reads the new row back, which needs a SELECT policy on the bucket.
-- 001 only gave bh-photos INSERT/UPDATE/DELETE, so every photo upload was refused. The bucket is public, so reading is fine.
drop policy if exists bhp_read on storage.objects;
create policy bhp_read on storage.objects for select to authenticated
  using (bucket_id = 'bh-photos');
