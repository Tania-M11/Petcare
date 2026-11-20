-- PETCARE · Parte 2.3: motor de cuidados (CareEvent / CareLog)
-- Se puede ejecutar varias veces: completa lo que falte sin duplicar nada.
-- Sigue la convención de sincronización de 03 (id del teléfono, actualizado_en del servidor, eliminado).

-- Funciones auxiliares de las reglas: en un esquema que la API no expone
create schema if not exists privado;
revoke all on schema privado from public, anon;
grant usage on schema privado to authenticated;

-- ¿El usuario actual es propietario de esta mascota? (en 3.1 se suman los cuidadores)
create or replace function privado.es_propietario(mascota uuid)
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
  select exists (select 1 from public.mascotas m where m.id = mascota and m.propietario_id = (select auth.uid()))
$$;
revoke execute on function privado.es_propietario(uuid) from public, anon;
grant execute on function privado.es_propietario(uuid) to authenticated;

-- 1) Cuidados programables
create table if not exists public.eventos_cuidado (
  id uuid primary key,
  mascota_id uuid not null references public.mascotas (id) on delete cascade,
  tipo text not null check (tipo in ('VACUNA', 'DESPARASITACION_INTERNA', 'DESPARASITACION_EXTERNA', 'MEDICAMENTO',
    'BANO', 'CORTE_PELO', 'CEPILLADO', 'CORTE_UNAS', 'LIMPIEZA_DENTAL', 'LIMPIEZA_OIDOS', 'LIMPIEZA_OJOS',
    'CITA_VET', 'SERVICIO_CONTRATADO', 'PESAJE', 'COMIDA', 'PERSONALIZADO')),
  titulo text not null check (char_length(btrim(titulo)) between 1 and 80),
  frecuencia_tipo text not null check (frecuencia_tipo in ('UNICO', 'DIARIO', 'SEMANAL', 'MENSUAL', 'ANUAL', 'CADA_N_DIAS', 'PERSONALIZADO')),
  frecuencia_n integer not null default 1 check (frecuencia_n between 1 and 365),
  dias_semana integer[] not null default '{}' check (dias_semana <@ array[1, 2, 3, 4, 5, 6, 7]),
  horas text[] not null default '{}' check (cardinality(horas) <= 12),
  ancla text not null check (ancla in ('CALENDARIO', 'ULTIMA_VEZ')),
  fecha_inicio date not null,
  fecha_fin date check (fecha_fin is null or fecha_fin >= fecha_inicio),
  aviso_anticipado_dias integer check (aviso_anticipado_dias between 0 and 90),
  -- Lo que escribe el usuario o el veterinario (dosis, producto, texto de la comida…); la app no lo interpreta
  detalle jsonb not null default '{}' check (pg_column_size(detalle) < 4000),
  origen text not null default 'MANUAL' check (origen in ('MANUAL', 'SOLICITUD_VET', 'SOLICITUD_PRESTADOR', 'VETERINARIO')),
  origen_ref uuid,
  -- Autor: lo pone el servidor (nadie puede firmar por otro)
  creado_por uuid,
  creado_por_nombre text,
  creado_por_rol text,
  oculto boolean not null default false,
  eliminado boolean not null default false,
  creado_en timestamptz not null default now(),
  actualizado_en timestamptz not null default clock_timestamp()
);
create index if not exists eventos_cuidado_mascota on public.eventos_cuidado (mascota_id, actualizado_en);
create index if not exists eventos_cuidado_actualizado on public.eventos_cuidado (actualizado_en);

-- 2) Registros de cumplimiento (id determinista: evento + fecha programada)
create table if not exists public.registros_cuidado (
  id uuid primary key,
  mascota_id uuid not null references public.mascotas (id) on delete cascade,
  evento_id uuid not null references public.eventos_cuidado (id) on delete cascade,
  fecha_programada timestamp not null, -- hora local de la toma
  estado text not null check (estado in ('ADMINISTRADA', 'REALIZADA', 'OMITIDA', 'POSPUESTA')),
  registrado_por uuid,
  registrado_por_nombre text,
  registrado_por_rol text,
  registrado_en timestamptz not null, -- cuándo lo marcó la persona (aunque suba después)
  notas text check (char_length(notas) <= 500),
  eliminado boolean not null default false,
  actualizado_en timestamptz not null default clock_timestamp()
);
create index if not exists registros_cuidado_mascota on public.registros_cuidado (mascota_id, actualizado_en);
create index if not exists registros_cuidado_actualizado on public.registros_cuidado (actualizado_en);

-- Autor de un cuidado: solo al crearlo
create or replace function public.marcar_autor_evento()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
  if tg_op = 'INSERT' then
    new.creado_por := auth.uid();
    new.creado_por_nombre := (select u.nombre from public.usuarios u where u.id = auth.uid());
    new.creado_por_rol := case when privado.es_propietario(new.mascota_id) then 'PROPIETARIO' end;
  else
    new.creado_por := old.creado_por;
    new.creado_por_nombre := old.creado_por_nombre;
    new.creado_por_rol := old.creado_por_rol;
    new.creado_en := old.creado_en;
  end if;
  return new;
end;
$$;

-- Autor de un registro: quien lo marca (o lo cambia) por última vez
create or replace function public.marcar_autor_registro()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
  new.registrado_por := auth.uid();
  new.registrado_por_nombre := (select u.nombre from public.usuarios u where u.id = auth.uid());
  new.registrado_por_rol := case when privado.es_propietario(new.mascota_id) then 'PROPIETARIO' end;
  return new;
end;
$$;
revoke execute on function public.marcar_autor_evento() from public, anon, authenticated;
revoke execute on function public.marcar_autor_registro() from public, anon, authenticated;

drop trigger if exists eventos_cuidado_autor on public.eventos_cuidado;
create trigger eventos_cuidado_autor before insert or update on public.eventos_cuidado
  for each row execute function public.marcar_autor_evento();
drop trigger if exists eventos_cuidado_actualizado_en on public.eventos_cuidado;
create trigger eventos_cuidado_actualizado_en before insert or update on public.eventos_cuidado
  for each row execute function public.marcar_actualizado_en();

drop trigger if exists registros_cuidado_autor on public.registros_cuidado;
create trigger registros_cuidado_autor before insert or update on public.registros_cuidado
  for each row execute function public.marcar_autor_registro();
drop trigger if exists registros_cuidado_actualizado_en on public.registros_cuidado;
create trigger registros_cuidado_actualizado_en before insert or update on public.registros_cuidado
  for each row execute function public.marcar_actualizado_en();

-- 3) Seguridad: por ahora solo el propietario de la mascota
alter table public.eventos_cuidado enable row level security;
alter table public.registros_cuidado enable row level security;

drop policy if exists "Propietario ve cuidados" on public.eventos_cuidado;
drop policy if exists "Propietario crea cuidados" on public.eventos_cuidado;
drop policy if exists "Propietario edita cuidados" on public.eventos_cuidado;
create policy "Propietario ve cuidados" on public.eventos_cuidado
  for select to authenticated using (privado.es_propietario(mascota_id));
create policy "Propietario crea cuidados" on public.eventos_cuidado
  for insert to authenticated with check (privado.es_propietario(mascota_id));
create policy "Propietario edita cuidados" on public.eventos_cuidado
  for update to authenticated using (privado.es_propietario(mascota_id)) with check (privado.es_propietario(mascota_id));

drop policy if exists "Propietario ve registros" on public.registros_cuidado;
drop policy if exists "Propietario crea registros" on public.registros_cuidado;
drop policy if exists "Propietario edita registros" on public.registros_cuidado;
create policy "Propietario ve registros" on public.registros_cuidado
  for select to authenticated using (privado.es_propietario(mascota_id));
create policy "Propietario crea registros" on public.registros_cuidado
  for insert to authenticated with check (
    privado.es_propietario(mascota_id)
    and exists (select 1 from public.eventos_cuidado e where e.id = evento_id and e.mascota_id = registros_cuidado.mascota_id)
  );
create policy "Propietario edita registros" on public.registros_cuidado
  for update to authenticated using (privado.es_propietario(mascota_id)) with check (privado.es_propietario(mascota_id));

revoke all on public.eventos_cuidado from anon, authenticated;
revoke all on public.registros_cuidado from anon, authenticated;
grant select on public.eventos_cuidado, public.registros_cuidado to authenticated;
grant insert (id, mascota_id, tipo, titulo, frecuencia_tipo, frecuencia_n, dias_semana, horas, ancla, fecha_inicio, fecha_fin,
              aviso_anticipado_dias, detalle, origen, origen_ref, oculto, eliminado),
      update (id, mascota_id, tipo, titulo, frecuencia_tipo, frecuencia_n, dias_semana, horas, ancla, fecha_inicio, fecha_fin,
              aviso_anticipado_dias, detalle, origen, origen_ref, oculto, eliminado)
  on public.eventos_cuidado to authenticated;
grant insert (id, mascota_id, evento_id, fecha_programada, estado, registrado_en, notas, eliminado),
      update (id, mascota_id, evento_id, fecha_programada, estado, registrado_en, notas, eliminado)
  on public.registros_cuidado to authenticated;

-- Versión anterior (estaba en el esquema público de la API)
drop function if exists public.es_propietario(uuid);

notify pgrst, 'reload schema';
