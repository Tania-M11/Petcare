-- PETCARE · Parte 3.3: documentos de la mascota (RF-17)
-- Se puede ejecutar varias veces. Misma convención de sincronización que 03.

-- Archivos: bucket privado; carpeta "{dueño}/{mascota}/{documento}.ext"
insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values ('documentos', 'documentos', false, 5242880, array['image/jpeg', 'image/png', 'application/pdf'])
on conflict (id) do nothing;

drop policy if exists "Documentos: ver los propios" on storage.objects;
drop policy if exists "Documentos: subir a su carpeta" on storage.objects;
drop policy if exists "Documentos: reemplazar los propios" on storage.objects;
drop policy if exists "Documentos: borrar los propios" on storage.objects;
create policy "Documentos: ver los propios" on storage.objects for select to authenticated
  using (bucket_id = 'documentos' and (storage.foldername(name))[1] = (select auth.uid())::text);
create policy "Documentos: subir a su carpeta" on storage.objects for insert to authenticated
  with check (bucket_id = 'documentos' and (storage.foldername(name))[1] = (select auth.uid())::text);
create policy "Documentos: reemplazar los propios" on storage.objects for update to authenticated
  using (bucket_id = 'documentos' and (storage.foldername(name))[1] = (select auth.uid())::text);
create policy "Documentos: borrar los propios" on storage.objects for delete to authenticated
  using (bucket_id = 'documentos' and (storage.foldername(name))[1] = (select auth.uid())::text);

create table if not exists public.documentos (
  id uuid primary key,
  mascota_id uuid not null references public.mascotas (id) on delete cascade,
  nombre text not null check (char_length(btrim(nombre)) between 1 and 120),
  tipo text not null check (tipo in ('CARNE', 'FORMULA', 'EXAMEN', 'INFORME', 'OTRO')),
  fecha date not null,
  mime text not null check (mime in ('image/jpeg', 'image/png', 'application/pdf')),
  tamano_bytes integer not null check (tamano_bytes > 0 and tamano_bytes <= 5242880),
  ruta text, -- ruta en el bucket (null mientras el archivo no sube)
  visita_id uuid,
  evento_id uuid,
  registrado_por uuid,
  registrado_por_nombre text,
  registrado_por_rol text,
  registrado_en timestamptz not null default now(),
  eliminado boolean not null default false,
  actualizado_en timestamptz not null default clock_timestamp()
);
create index if not exists documentos_mascota on public.documentos (mascota_id, actualizado_en);
create index if not exists documentos_actualizado on public.documentos (actualizado_en);

drop trigger if exists documentos_autor on public.documentos;
create trigger documentos_autor before insert or update on public.documentos
  for each row execute function public.marcar_autor_registro();
drop trigger if exists documentos_actualizado_en on public.documentos;
create trigger documentos_actualizado_en before insert or update on public.documentos
  for each row execute function public.marcar_actualizado_en();

-- Solo el propietario (los cuidadores no ven documentos; veterinarios vinculados en la fase 4)
alter table public.documentos enable row level security;
drop policy if exists "Propietario ve documentos" on public.documentos;
drop policy if exists "Propietario crea documentos" on public.documentos;
drop policy if exists "Propietario edita documentos" on public.documentos;
create policy "Propietario ve documentos" on public.documentos for select to authenticated using (privado.es_propietario(mascota_id));
create policy "Propietario crea documentos" on public.documentos for insert to authenticated with check (privado.es_propietario(mascota_id));
create policy "Propietario edita documentos" on public.documentos for update to authenticated
  using (privado.es_propietario(mascota_id)) with check (privado.es_propietario(mascota_id));

revoke all on public.documentos from anon, authenticated;
grant select on public.documentos to authenticated;
grant insert (id, mascota_id, nombre, tipo, fecha, mime, tamano_bytes, ruta, visita_id, evento_id, registrado_en, eliminado),
      update (id, mascota_id, nombre, tipo, fecha, mime, tamano_bytes, ruta, visita_id, evento_id, registrado_en, eliminado)
  on public.documentos to authenticated;

notify pgrst, 'reload schema';
