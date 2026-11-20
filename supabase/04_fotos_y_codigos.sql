-- PETCARE · Parte 2.2: fotos de mascotas y código único
-- Se puede ejecutar varias veces: completa lo que falte sin duplicar nada.

-- 1) Fotos: bucket privado; cada usuario escribe solo en su carpeta "{su id}/..."
insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values ('mascotas', 'mascotas', false, 1048576, array['image/jpeg'])
on conflict (id) do nothing;

drop policy if exists "Fotos de mascotas: ver las propias" on storage.objects;
drop policy if exists "Fotos de mascotas: subir a su carpeta" on storage.objects;
drop policy if exists "Fotos de mascotas: reemplazar las propias" on storage.objects;
drop policy if exists "Fotos de mascotas: borrar las propias" on storage.objects;

create policy "Fotos de mascotas: ver las propias" on storage.objects
  for select to authenticated
  using (bucket_id = 'mascotas' and (storage.foldername(name))[1] = (select auth.uid())::text);
create policy "Fotos de mascotas: subir a su carpeta" on storage.objects
  for insert to authenticated
  with check (bucket_id = 'mascotas' and (storage.foldername(name))[1] = (select auth.uid())::text);
create policy "Fotos de mascotas: reemplazar las propias" on storage.objects
  for update to authenticated
  using (bucket_id = 'mascotas' and (storage.foldername(name))[1] = (select auth.uid())::text);
create policy "Fotos de mascotas: borrar las propias" on storage.objects
  for delete to authenticated
  using (bucket_id = 'mascotas' and (storage.foldername(name))[1] = (select auth.uid())::text);

-- 2) Código de la mascota (RF-02 / RF-20): 8 caracteres de 31 símbolos sin ambiguos
--    (sin I, L, O, 0, 1), aleatorio criptográfico. Lo genera el servidor al crear la mascota.
create table if not exists public.codigos_mascota (
  codigo text primary key check (codigo ~ '^[A-HJKMNP-Z2-9]{8}$'),
  mascota_id uuid not null unique references public.mascotas (id) on delete cascade,
  activo boolean not null default true,
  creado_en timestamptz not null default now()
);

alter table public.codigos_mascota enable row level security;

-- Solo el propietario ve el código (buscar por código llega en la parte 3.1, vía función)
drop policy if exists "Propietario ve el código" on public.codigos_mascota;
create policy "Propietario ve el código" on public.codigos_mascota
  for select to authenticated
  using (exists (
    select 1 from public.mascotas m
    where m.id = mascota_id and m.propietario_id = (select auth.uid())
  ));

revoke all on public.codigos_mascota from anon, authenticated;
grant select on public.codigos_mascota to authenticated;

create or replace function public.nuevo_codigo_mascota()
returns text
language plpgsql
volatile
set search_path = ''
as $$
declare
  alfabeto constant text := 'ABCDEFGHJKMNPQRSTUVWXYZ23456789'; -- 31 símbolos
  candidato text;
  b int;
begin
  loop
    candidato := '';
    while char_length(candidato) < 8 loop
      b := get_byte(extensions.gen_random_bytes(1), 0);
      if b < 248 then -- 248 = 31 × 8: descarta el sobrante para no sesgar
        candidato := candidato || substr(alfabeto, 1 + (b % 31), 1);
      end if;
    end loop;
    exit when not exists (select 1 from public.codigos_mascota c where c.codigo = candidato);
  end loop;
  return candidato;
end;
$$;

create or replace function public.asignar_codigo_mascota()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
  insert into public.codigos_mascota (codigo, mascota_id)
  values (public.nuevo_codigo_mascota(), new.id)
  on conflict (mascota_id) do nothing;
  return new;
end;
$$;

drop trigger if exists mascotas_asignar_codigo on public.mascotas;
create trigger mascotas_asignar_codigo
  after insert on public.mascotas
  for each row execute function public.asignar_codigo_mascota();

-- Mascotas creadas antes de esta parte también reciben su código
insert into public.codigos_mascota (codigo, mascota_id)
select public.nuevo_codigo_mascota(), m.id
from public.mascotas m
where not exists (select 1 from public.codigos_mascota c where c.mascota_id = m.id);

revoke execute on function public.nuevo_codigo_mascota() from public, anon, authenticated;
revoke execute on function public.asignar_codigo_mascota() from public, anon, authenticated;

notify pgrst, 'reload schema';
