-- PETCARE · Partes 4.1 y 4.2: perfil de veterinario (RF-22), verificación (RF-23) y directorio (RF-24)
-- Se puede ejecutar varias veces.

-- Normaliza ciudades para filtrar ("Pasto", "PASTO", "pastó" → "pasto")
create or replace function public.normalizar_texto(t text)
returns text language sql immutable set search_path = '' as $$
  select lower(btrim(translate(coalesce(t, ''), 'ÁÉÍÓÚÜÑáéíóúüñ', 'AEIOUUNaeiouun')))
$$;

create table if not exists public.veterinarios (
  id uuid primary key references public.usuarios (id) on delete cascade,
  nombre text not null check (char_length(btrim(nombre)) between 1 and 100),
  foto_url text,
  especies text[] not null check (cardinality(especies) between 1 and 9),
  especialidades text[] not null default '{}' check (cardinality(especialidades) <= 10),
  servicios text[] not null default '{}' check (servicios <@ array['CONSULTA', 'VACUNACION', 'DESPARASITACION', 'CIRUGIA', 'OTROS']),
  clinica text check (char_length(clinica) <= 100),
  telefono text not null check (telefono ~ '^\+?[0-9 ]{7,20}$'),
  whatsapp text check (whatsapp ~ '^\+?[0-9 ]{7,20}$'),
  correo text check (char_length(correo) <= 120),
  descripcion text check (char_length(descripcion) <= 1000),
  domicilio boolean not null default false,
  -- Horario por día: {"1": "8:00-12:00, 14:00-18:00", ...} (1 = lunes)
  horario jsonb not null default '{}' check (pg_column_size(horario) < 2000),
  tarifa text check (char_length(tarifa) <= 100),
  ciudad text not null check (char_length(btrim(ciudad)) between 2 and 60),
  ciudad_norm text generated always as (public.normalizar_texto(ciudad)) stored,
  direccion text not null check (char_length(btrim(direccion)) between 3 and 150),
  lat double precision not null check (lat between -90 and 90),
  lon double precision not null check (lon between -180 and 180),
  -- RF-23: solo el proceso verificador (panel de Supabase) pasa a VERIFICADO o RECHAZADO
  verificacion text not null default 'SIN_CERTIFICADO' check (verificacion in ('SIN_CERTIFICADO', 'EN_REVISION', 'VERIFICADO', 'RECHAZADO')),
  certificado_subido_en timestamptz,
  rating_suma integer not null default 0,
  rating_cantidad integer not null default 0,
  activo boolean not null default true,
  creado_en timestamptz not null default now(),
  actualizado_en timestamptz not null default now()
);
create index if not exists veterinarios_ciudad on public.veterinarios (ciudad_norm) where activo;

drop trigger if exists veterinarios_actualizado_en on public.veterinarios;
create trigger veterinarios_actualizado_en before update on public.veterinarios
  for each row execute function public.tocar_actualizado_en();

-- El perfil activo se refleja en usuarios.es_veterinario (RF-30)
create or replace function public.sincronizar_perfil_veterinario()
returns trigger language plpgsql security definer set search_path = '' as $$
begin
  update public.usuarios set es_veterinario = new.activo where id = new.id;
  return new;
end;
$$;
revoke execute on function public.sincronizar_perfil_veterinario() from public, anon, authenticated;
drop trigger if exists veterinarios_perfil on public.veterinarios;
create trigger veterinarios_perfil after insert or update of activo on public.veterinarios
  for each row execute function public.sincronizar_perfil_veterinario();

alter table public.veterinarios enable row level security;
drop policy if exists "Directorio de veterinarios" on public.veterinarios;
drop policy if exists "Veterinario crea su perfil" on public.veterinarios;
drop policy if exists "Veterinario edita su perfil" on public.veterinarios;
-- Lectura: cualquier usuario con sesión (directorio); el propio veterinario también si está inactivo
create policy "Directorio de veterinarios" on public.veterinarios for select to authenticated
  using (activo or id = (select auth.uid()));
create policy "Veterinario crea su perfil" on public.veterinarios for insert to authenticated
  with check (id = (select auth.uid()));
create policy "Veterinario edita su perfil" on public.veterinarios for update to authenticated
  using (id = (select auth.uid())) with check (id = (select auth.uid()));

revoke all on public.veterinarios from anon, authenticated;
grant select on public.veterinarios to authenticated;
-- Sin permiso sobre verificacion, certificado_subido_en ni calificaciones
grant insert (id, nombre, foto_url, especies, especialidades, servicios, clinica, telefono, whatsapp, correo, descripcion,
              domicilio, horario, tarifa, ciudad, direccion, lat, lon, activo),
      update (nombre, foto_url, especies, especialidades, servicios, clinica, telefono, whatsapp, correo, descripcion,
              domicilio, horario, tarifa, ciudad, direccion, lat, lon, activo)
  on public.veterinarios to authenticated;

-- Certificados: bucket privado; solo el propio veterinario (y el verificador con el panel)
insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values ('certificados', 'certificados', false, 5242880, array['image/jpeg', 'image/png', 'application/pdf'])
on conflict (id) do nothing;
drop policy if exists "Certificados: ver el propio" on storage.objects;
drop policy if exists "Certificados: subir el propio" on storage.objects;
drop policy if exists "Certificados: reemplazar el propio" on storage.objects;
drop policy if exists "Certificados: borrar el propio" on storage.objects;
create policy "Certificados: ver el propio" on storage.objects for select to authenticated
  using (bucket_id = 'certificados' and (storage.foldername(name))[1] = (select auth.uid())::text);
create policy "Certificados: subir el propio" on storage.objects for insert to authenticated
  with check (bucket_id = 'certificados' and (storage.foldername(name))[1] = (select auth.uid())::text);
create policy "Certificados: reemplazar el propio" on storage.objects for update to authenticated
  using (bucket_id = 'certificados' and (storage.foldername(name))[1] = (select auth.uid())::text);
-- Para reemplazarlo por otro formato y al eliminar la cuenta (5.2)
create policy "Certificados: borrar el propio" on storage.objects for delete to authenticated
  using (bucket_id = 'certificados' and (storage.foldername(name))[1] = (select auth.uid())::text);

-- Al subir o reemplazar el certificado: EN_REVISION (única transición que puede hacer la app)
create or replace function public.enviar_certificado()
returns void language plpgsql security definer set search_path = '' as $$
begin
  if auth.uid() is null then raise exception 'Sin sesión' using errcode = '28000'; end if;
  update public.veterinarios set verificacion = 'EN_REVISION', certificado_subido_en = now() where id = auth.uid();
  if not found then raise exception 'Primero completa tu perfil' using errcode = 'P0002'; end if;
end;
$$;

-- Directorio (RF-24): filtros y orden por distancia si hay ubicación; máximo 100 resultados
create or replace function public.buscar_veterinarios(
  p_ciudad text default null, p_especie text default null, p_domicilio boolean default null,
  p_servicio text default null, p_solo_verificados boolean default false,
  p_lat double precision default null, p_lon double precision default null
)
returns table (
  id uuid, nombre text, foto_url text, especies text[], especialidades text[], servicios text[], clinica text,
  telefono text, whatsapp text, correo text, descripcion text, domicilio boolean, horario jsonb, tarifa text,
  ciudad text, direccion text, lat double precision, lon double precision, verificacion text,
  rating_suma integer, rating_cantidad integer, distancia_km double precision
)
language sql stable security invoker set search_path = '' as $$
  select v.id, v.nombre, v.foto_url, v.especies, v.especialidades, v.servicios, v.clinica, v.telefono, v.whatsapp, v.correo,
         v.descripcion, v.domicilio, v.horario, v.tarifa, v.ciudad, v.direccion, v.lat, v.lon, v.verificacion,
         v.rating_suma, v.rating_cantidad,
         case when p_lat is null or p_lon is null then null else
           6371 * 2 * asin(sqrt(power(sin(radians(v.lat - p_lat) / 2), 2)
             + cos(radians(p_lat)) * cos(radians(v.lat)) * power(sin(radians(v.lon - p_lon) / 2), 2)))
         end as distancia_km
  from public.veterinarios v
  where v.activo
    and v.id <> (select auth.uid())
    and (p_ciudad is null or btrim(p_ciudad) = '' or v.ciudad_norm = public.normalizar_texto(p_ciudad))
    and (p_especie is null or p_especie = any (v.especies))
    and (p_domicilio is null or not p_domicilio or v.domicilio)
    and (p_servicio is null or p_servicio = any (v.servicios))
    and (not coalesce(p_solo_verificados, false) or v.verificacion = 'VERIFICADO')
  order by distancia_km nulls last, v.ciudad_norm, v.nombre
  limit 100
$$;

revoke execute on function public.enviar_certificado(), public.buscar_veterinarios(text, text, boolean, text, boolean, double precision, double precision) from public, anon;
grant execute on function public.enviar_certificado(), public.buscar_veterinarios(text, text, boolean, text, boolean, double precision, double precision) to authenticated;

notify pgrst, 'reload schema';
