-- PETCARE · Parte 2.1: mascotas sincronizables y eliminar cuenta
--
-- Convención de sincronización (todas las tablas que el teléfono guarda sin conexión):
--   * id uuid generado en el teléfono (se puede crear sin internet)
--   * actualizado_en lo pone SIEMPRE el servidor (trigger): es el cursor de descarga
--   * eliminado = true en vez de borrar, para que los demás dispositivos se enteren
--
-- Se puede ejecutar varias veces: completa lo que falte sin duplicar nada.

create or replace function public.marcar_actualizado_en()
returns trigger
language plpgsql
set search_path = ''
as $$
begin
  new.actualizado_en := clock_timestamp();
  return new;
end;
$$;
revoke execute on function public.marcar_actualizado_en() from public, anon, authenticated;

create table if not exists public.mascotas (
  id uuid primary key,
  propietario_id uuid not null default auth.uid() references public.usuarios (id) on delete cascade,
  nombre text not null check (char_length(btrim(nombre)) between 1 and 40),
  especie text not null check (especie in ('PERRO', 'GATO', 'AVE', 'CONEJO', 'ROEDOR', 'REPTIL', 'PEZ', 'EQUINO', 'OTRO')),
  especie_otro text check (char_length(especie_otro) <= 40),
  raza text check (char_length(raza) <= 60),
  sexo text not null default 'DESCONOCIDO' check (sexo in ('MACHO', 'HEMBRA', 'DESCONOCIDO')),
  fecha_nacimiento date,
  nacimiento_aproximado boolean not null default false,
  color text check (char_length(color) <= 40),
  tamano text check (tamano in ('PEQUENO', 'MEDIANO', 'GRANDE')),
  peso_gramos integer check (peso_gramos > 0 and peso_gramos < 2000000),
  esterilizado boolean,
  notas text check (char_length(notas) <= 1000),
  -- Salud básica (RF-03): texto escrito por el usuario, la app no lo interpreta
  alergias text check (char_length(alergias) <= 500),
  condiciones text check (char_length(condiciones) <= 500),
  medicamentos_actuales text check (char_length(medicamentos_actuales) <= 500),
  observaciones text check (char_length(observaciones) <= 500),
  foto_url text,
  eliminado boolean not null default false,
  creado_en timestamptz not null default now(),
  actualizado_en timestamptz not null default clock_timestamp()
);

create index if not exists mascotas_propietario_actualizado on public.mascotas (propietario_id, actualizado_en);

drop trigger if exists mascotas_actualizado_en on public.mascotas;
create trigger mascotas_actualizado_en
  before insert or update on public.mascotas
  for each row execute function public.marcar_actualizado_en();

-- Seguridad: por ahora solo el propietario (los cuidadores se suman en la parte 3.1)
alter table public.mascotas enable row level security;

drop policy if exists "Propietario ve sus mascotas" on public.mascotas;
drop policy if exists "Propietario crea mascotas" on public.mascotas;
drop policy if exists "Propietario edita sus mascotas" on public.mascotas;

create policy "Propietario ve sus mascotas" on public.mascotas
  for select to authenticated using ((select auth.uid()) = propietario_id);
create policy "Propietario crea mascotas" on public.mascotas
  for insert to authenticated with check ((select auth.uid()) = propietario_id);
create policy "Propietario edita sus mascotas" on public.mascotas
  for update to authenticated
  using ((select auth.uid()) = propietario_id) with check ((select auth.uid()) = propietario_id);

-- El propietario lo pone el servidor y nunca cambia; no hay DELETE (se usa eliminado = true)
revoke all on public.mascotas from anon, authenticated;
grant select on public.mascotas to authenticated;
grant insert (id, nombre, especie, especie_otro, raza, sexo, fecha_nacimiento, nacimiento_aproximado, color, tamano,
              peso_gramos, esterilizado, notas, alergias, condiciones, medicamentos_actuales, observaciones, foto_url, eliminado)
  on public.mascotas to authenticated;
grant update (id, nombre, especie, especie_otro, raza, sexo, fecha_nacimiento, nacimiento_aproximado, color, tamano,
              peso_gramos, esterilizado, notas, alergias, condiciones, medicamentos_actuales, observaciones, foto_url, eliminado)
  on public.mascotas to authenticated;

-- Eliminar la propia cuenta (requisito de Google Play): borra el usuario y, en cascada, sus datos
create or replace function public.eliminar_mi_cuenta()
returns void
language plpgsql
security definer
set search_path = ''
as $$
begin
  if auth.uid() is null then
    raise exception 'Sin sesión' using errcode = '28000';
  end if;
  delete from auth.users where id = auth.uid();
end;
$$;
revoke execute on function public.eliminar_mi_cuenta() from public, anon;
grant execute on function public.eliminar_mi_cuenta() to authenticated;

-- Avisar a la API (PostgREST) que hay tablas y funciones nuevas
notify pgrst, 'reload schema';
