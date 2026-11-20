-- PETCARE · Parte 0.3: identidad base
-- Una cuenta de Supabase Auth = un usuario. Los roles por mascota (propietario, cuidador)
-- llegan con las mascotas; los perfiles profesionales se activan desde la app (fase 4).

-- Fecha de actualización automática (se reutiliza en todas las tablas)
create or replace function public.tocar_actualizado_en()
returns trigger
language plpgsql
set search_path = ''
as $$
begin
  new.actualizado_en := now();
  return new;
end;
$$;

create table public.usuarios (
  id uuid primary key references auth.users (id) on delete cascade,
  nombre text not null check (char_length(btrim(nombre)) between 1 and 60),
  telefono text check (telefono ~ '^\+?[0-9 ]{7,20}$'),
  whatsapp text check (whatsapp ~ '^\+?[0-9 ]{7,20}$'),
  foto_url text,
  -- Respuesta a "¿Qué te trae a PetCare?": solo personaliza la guía, no da permisos
  intenciones text[] not null default '{}'
    check (intenciones <@ array['MASCOTAS', 'CUIDAR', 'VETERINARIO', 'SERVICIOS']),
  -- Perfiles profesionales: se activan por funciones del servidor al completar su formulario
  es_prestador boolean not null default false,
  es_veterinario boolean not null default false,
  acepto_privacidad_en timestamptz not null,
  creado_en timestamptz not null default now(),
  actualizado_en timestamptz not null default now()
);

create trigger usuarios_actualizado_en
  before update on public.usuarios
  for each row execute function public.tocar_actualizado_en();

-- Seguridad por filas: cada quien ve y edita solo su propio registro
alter table public.usuarios enable row level security;

create policy "Cada quien ve su usuario" on public.usuarios
  for select to authenticated using ((select auth.uid()) = id);

create policy "Cada quien edita su usuario" on public.usuarios
  for update to authenticated
  using ((select auth.uid()) = id) with check ((select auth.uid()) = id);

-- La app solo cambia sus datos personales; nunca los perfiles profesionales ni la aceptación
revoke all on public.usuarios from anon, authenticated;
grant select on public.usuarios to authenticated;
grant update (nombre, telefono, whatsapp, foto_url, intenciones) on public.usuarios to authenticated;

-- Al registrarse se crea el usuario con el nombre del formulario.
-- Sin aceptar la política de privacidad (metadato acepto_privacidad = true) el registro falla.
create or replace function public.al_crear_cuenta()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
  if coalesce((new.raw_user_meta_data ->> 'acepto_privacidad')::boolean, false) is not true then
    raise exception 'Debe aceptar la política de privacidad' using errcode = 'P0001';
  end if;
  insert into public.usuarios (id, nombre, acepto_privacidad_en)
  values (
    new.id,
    left(coalesce(nullif(btrim(new.raw_user_meta_data ->> 'nombre'), ''), 'Usuario'), 60),
    now()
  );
  return new;
end;
$$;

create trigger al_crear_cuenta
  after insert on auth.users
  for each row execute function public.al_crear_cuenta();

revoke execute on function public.al_crear_cuenta() from public, anon, authenticated;
revoke execute on function public.tocar_actualizado_en() from public, anon, authenticated;

-- Comprobación de conexión desde la app (no expone datos)
create or replace function public.ping()
returns text
language sql
stable
set search_path = ''
as $$ select 'petcare'::text $$;

grant execute on function public.ping() to anon, authenticated;
