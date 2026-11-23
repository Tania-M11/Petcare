-- PETCARE · Parte 4.4: perfil de prestador (RF-31), categorías y servicios (RF-32) y directorio (RF-33)
-- Se puede ejecutar varias veces.

-- 1) Catálogo extensible: agregar una categoría es insertar una fila (sin cambiar la app)
create table if not exists public.categorias_servicio (
  id text primary key check (id ~ '^[A-Z_]{3,40}$'),
  nombre text not null,
  icono text not null, -- emoji que se muestra en la lista y en el mapa
  orden integer not null default 0,
  activa boolean not null default true
);
insert into public.categorias_servicio (id, nombre, icono, orden) values
  ('PASEADOR', 'Paseadores de mascotas', '🦮', 1),
  ('BANO_PELUQUERIA', 'Baño y peluquería', '✂️', 2),
  ('CUIDADO_TEMPORAL', 'Cuidado temporal de mascotas', '🏠', 3)
on conflict (id) do nothing;
alter table public.categorias_servicio enable row level security;
drop policy if exists "Catálogo de categorías" on public.categorias_servicio;
create policy "Catálogo de categorías" on public.categorias_servicio for select to authenticated using (true);
revoke all on public.categorias_servicio from anon, authenticated;
grant select on public.categorias_servicio to authenticated;

-- 2) Perfil del prestador. Los servicios van en un arreglo JSON:
--    [{id, categoria, nombre, descripcion?, modo: FIJO|DESDE|COTIZACION, monto?, unidad?, especies?[]}]
create table if not exists public.prestadores (
  id uuid primary key references public.usuarios (id) on delete cascade,
  nombre text not null check (char_length(btrim(nombre)) between 1 and 100),
  tipo text not null default 'PERSONA' check (tipo in ('PERSONA', 'NEGOCIO')),
  foto_url text,
  descripcion text not null check (char_length(btrim(descripcion)) between 10 and 1000),
  telefono text not null check (telefono ~ '^\+?[0-9 ]{7,20}$'),
  whatsapp text check (whatsapp ~ '^\+?[0-9 ]{7,20}$'),
  correo text check (char_length(correo) <= 120),
  ciudad text not null check (char_length(btrim(ciudad)) between 2 and 60),
  ciudad_norm text generated always as (public.normalizar_texto(ciudad)) stored,
  direccion text check (char_length(direccion) <= 150),
  zona text check (char_length(zona) <= 120),
  zona_norm text generated always as (public.normalizar_texto(zona)) stored,
  radio_km integer check (radio_km between 1 and 100),
  lat double precision not null check (lat between -90 and 90),
  lon double precision not null check (lon between -180 and 180),
  modalidades text[] not null check (cardinality(modalidades) between 1 and 2 and modalidades <@ array['DOMICILIO', 'ESTABLECIMIENTO']),
  especies text[] not null check (cardinality(especies) between 1 and 9),
  -- La primera es la categoría principal
  categorias text[] not null check (cardinality(categorias) between 1 and 10),
  servicios jsonb not null default '[]' check (jsonb_typeof(servicios) = 'array' and jsonb_array_length(servicios) <= 30 and pg_column_size(servicios) < 30000),
  horario jsonb not null default '{}' check (pg_column_size(horario) < 2000),
  no_disponible date[] not null default '{}' check (cardinality(no_disponible) <= 60),
  recibiendo boolean not null default true,
  galeria text[] not null default '{}' check (cardinality(galeria) <= 12),
  rating_suma integer not null default 0,
  rating_cantidad integer not null default 0,
  activo boolean not null default true,
  creado_en timestamptz not null default now(),
  actualizado_en timestamptz not null default now(),
  -- Ciudad y además dirección o zona de cobertura (RF-31)
  constraint prestadores_cobertura check (coalesce(btrim(direccion), '') <> '' or coalesce(btrim(zona), '') <> '' or radio_km is not null)
);
create index if not exists prestadores_ciudad on public.prestadores (ciudad_norm) where activo;
create index if not exists prestadores_categorias on public.prestadores using gin (categorias) where activo;

-- Las categorías deben existir en el catálogo (un arreglo no admite llave foránea)
create or replace function public.validar_prestador()
returns trigger language plpgsql security definer set search_path = '' as $$
begin
  if exists (select 1 from unnest(new.categorias) c where not exists (select 1 from public.categorias_servicio s where s.id = c and s.activa)) then
    raise exception 'Categoría no válida' using errcode = '23503';
  end if;
  if exists (select 1 from jsonb_array_elements(new.servicios) s
             where not (s ->> 'categoria' = any (new.categorias))
                or coalesce(btrim(s ->> 'nombre'), '') = ''
                or not (s ->> 'modo' in ('FIJO', 'DESDE', 'COTIZACION'))
                or (s ->> 'modo' <> 'COTIZACION' and coalesce((s ->> 'monto')::numeric, 0) <= 0)) then
    raise exception 'Servicio no válido' using errcode = '22023';
  end if;
  return new;
end;
$$;
revoke execute on function public.validar_prestador() from public, anon, authenticated;
drop trigger if exists prestadores_validar on public.prestadores;
create trigger prestadores_validar before insert or update on public.prestadores
  for each row execute function public.validar_prestador();

drop trigger if exists prestadores_actualizado_en on public.prestadores;
create trigger prestadores_actualizado_en before update on public.prestadores
  for each row execute function public.tocar_actualizado_en();

-- El perfil activo se refleja en usuarios.es_prestador (RF-30)
create or replace function public.sincronizar_perfil_prestador()
returns trigger language plpgsql security definer set search_path = '' as $$
begin
  update public.usuarios set es_prestador = new.activo where id = new.id;
  return new;
end;
$$;
revoke execute on function public.sincronizar_perfil_prestador() from public, anon, authenticated;
drop trigger if exists prestadores_perfil on public.prestadores;
create trigger prestadores_perfil after insert or update of activo on public.prestadores
  for each row execute function public.sincronizar_perfil_prestador();

alter table public.prestadores enable row level security;
drop policy if exists "Directorio de prestadores" on public.prestadores;
drop policy if exists "Prestador crea su perfil" on public.prestadores;
drop policy if exists "Prestador edita su perfil" on public.prestadores;
create policy "Directorio de prestadores" on public.prestadores for select to authenticated
  using (activo or id = (select auth.uid()));
create policy "Prestador crea su perfil" on public.prestadores for insert to authenticated
  with check (id = (select auth.uid()));
create policy "Prestador edita su perfil" on public.prestadores for update to authenticated
  using (id = (select auth.uid())) with check (id = (select auth.uid()));
revoke all on public.prestadores from anon, authenticated;
grant select on public.prestadores to authenticated;
-- Las calificaciones no son editables por la app (RF-28 las mantiene con un trigger en 5.1)
grant insert (id, nombre, tipo, foto_url, descripcion, telefono, whatsapp, correo, ciudad, direccion, zona, radio_km, lat, lon,
              modalidades, especies, categorias, servicios, horario, no_disponible, recibiendo, galeria, activo),
      update (nombre, tipo, foto_url, descripcion, telefono, whatsapp, correo, ciudad, direccion, zona, radio_km, lat, lon,
              modalidades, especies, categorias, servicios, horario, no_disponible, recibiendo, galeria, activo)
  on public.prestadores to authenticated;

-- 3) Galería de trabajos: bucket público (es vitrina del perfil); cada quien escribe en su carpeta
insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values ('galeria', 'galeria', true, 5242880, array['image/jpeg', 'image/png'])
on conflict (id) do nothing;
drop policy if exists "Galería: ver las propias" on storage.objects;
drop policy if exists "Galería: subir a su carpeta" on storage.objects;
drop policy if exists "Galería: reemplazar las propias" on storage.objects;
drop policy if exists "Galería: borrar las propias" on storage.objects;
-- Ver por URL no necesita permiso (bucket público); listar y borrar sí
create policy "Galería: ver las propias" on storage.objects for select to authenticated
  using (bucket_id = 'galeria' and (storage.foldername(name))[1] = (select auth.uid())::text);
create policy "Galería: subir a su carpeta" on storage.objects for insert to authenticated
  with check (bucket_id = 'galeria' and (storage.foldername(name))[1] = (select auth.uid())::text);
create policy "Galería: reemplazar las propias" on storage.objects for update to authenticated
  using (bucket_id = 'galeria' and (storage.foldername(name))[1] = (select auth.uid())::text);
create policy "Galería: borrar las propias" on storage.objects for delete to authenticated
  using (bucket_id = 'galeria' and (storage.foldername(name))[1] = (select auth.uid())::text);

-- 4) Directorio (RF-33): ciudad o zona, categoría, especie y modalidad; distancia si hay ubicación
create or replace function public.buscar_prestadores(
  p_ciudad text default null, p_zona text default null, p_categoria text default null,
  p_especie text default null, p_modalidad text default null,
  p_lat double precision default null, p_lon double precision default null
)
returns table (
  id uuid, nombre text, tipo text, foto_url text, descripcion text, telefono text, whatsapp text, correo text,
  ciudad text, direccion text, zona text, radio_km integer, lat double precision, lon double precision,
  modalidades text[], especies text[], categorias text[], servicios jsonb, horario jsonb, no_disponible date[],
  recibiendo boolean, galeria text[], rating_suma integer, rating_cantidad integer, distancia_km double precision
)
language sql stable security invoker set search_path = '' as $$
  select p.id, p.nombre, p.tipo, p.foto_url, p.descripcion, p.telefono, p.whatsapp, p.correo,
         p.ciudad, p.direccion, p.zona, p.radio_km, p.lat, p.lon, p.modalidades, p.especies, p.categorias, p.servicios,
         p.horario, p.no_disponible, p.recibiendo, p.galeria, p.rating_suma, p.rating_cantidad,
         case when p_lat is null or p_lon is null then null else
           6371 * 2 * asin(sqrt(power(sin(radians(p.lat - p_lat) / 2), 2)
             + cos(radians(p_lat)) * cos(radians(p.lat)) * power(sin(radians(p.lon - p_lon) / 2), 2)))
         end as distancia_km
  from public.prestadores p
  where p.activo
    and p.id <> (select auth.uid())
    and (p_ciudad is null or btrim(p_ciudad) = '' or p.ciudad_norm = public.normalizar_texto(p_ciudad))
    and (p_zona is null or btrim(p_zona) = '' or p.zona_norm like '%' || public.normalizar_texto(p_zona) || '%')
    and (p_categoria is null or p_categoria = any (p.categorias))
    and (p_especie is null or p_especie = any (p.especies))
    and (p_modalidad is null or p_modalidad = any (p.modalidades))
  order by p.recibiendo desc, distancia_km nulls last, p.ciudad_norm, p.nombre
  limit 100
$$;
revoke execute on function public.buscar_prestadores(text, text, text, text, text, double precision, double precision) from public, anon;
grant execute on function public.buscar_prestadores(text, text, text, text, text, double precision, double precision) to authenticated;

notify pgrst, 'reload schema';
