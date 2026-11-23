-- PETCARE · Parte 4.5: cotizaciones y contratación (RF-34, RF-35) y datos compartidos con el prestador (RF-36)
-- Se puede ejecutar varias veces. Sin pagos: el valor se acuerda fuera de PetCare.

create table if not exists public.solicitudes_prestador (
  id uuid primary key default gen_random_uuid(),
  -- La mascota puede borrarse: el prestador conserva el resumen en su historial
  mascota_id uuid references public.mascotas (id) on delete set null,
  propietario_id uuid not null references public.usuarios (id) on delete cascade,
  prestador_id uuid not null references public.prestadores (id) on delete cascade,
  categoria_id text not null references public.categorias_servicio (id),
  servicio_id text,
  servicio_nombre text not null,
  precio_modo text not null default 'COTIZACION' check (precio_modo in ('FIJO', 'DESDE', 'COTIZACION')),
  precio_referencia integer,
  modalidad text not null check (modalidad in ('DOMICILIO', 'ESTABLECIMIENTO')),
  -- Hora local; fin solo en cuidado temporal
  fecha_inicio timestamp not null,
  fecha_fin timestamp check (fecha_fin is null or fecha_fin >= fecha_inicio),
  descripcion text not null check (char_length(btrim(descripcion)) between 3 and 1000),
  mensaje text check (char_length(mensaje) <= 500),
  estado text not null default 'PENDIENTE' check (estado in ('PENDIENTE', 'COTIZADA', 'ACEPTADA', 'RECHAZADA', 'COMPLETADA', 'CANCELADA')),
  -- Siempre compartido: nombre, especie, raza, sexo, edad, tamaño y peso aproximados
  resumen jsonb not null,
  -- Propuesta vigente del prestador (las anteriores quedan en el historial)
  cotizacion_precio integer check (cotizacion_precio between 0 and 100000000),
  cotizacion_observaciones text check (char_length(cotizacion_observaciones) <= 500),
  cotizacion_vigencia date,
  propuesta_inicio timestamp,
  propuesta_fin timestamp,
  motivo text check (char_length(motivo) <= 300),
  historial jsonb not null default '[]',
  propietario_nombre text,
  prestador_nombre text not null,
  evento_id uuid, -- cuidado SERVICIO_CONTRATADO creado al aceptar
  creado_en timestamptz not null default now(),
  actualizado_en timestamptz not null default now()
);
create index if not exists solicitudes_prestador_prestador on public.solicitudes_prestador (prestador_id, estado);
create index if not exists solicitudes_prestador_propietario on public.solicitudes_prestador (propietario_id, estado);
create unique index if not exists solicitudes_prestador_una_abierta on public.solicitudes_prestador (mascota_id, prestador_id)
  where estado in ('PENDIENTE', 'COTIZADA');

drop trigger if exists solicitudes_prestador_actualizado_en on public.solicitudes_prestador;
create trigger solicitudes_prestador_actualizado_en before update on public.solicitudes_prestador
  for each row execute function public.tocar_actualizado_en();

alter table public.solicitudes_prestador enable row level security;
drop policy if exists "Ver mis solicitudes a prestadores" on public.solicitudes_prestador;
create policy "Ver mis solicitudes a prestadores" on public.solicitudes_prestador for select to authenticated
  using (propietario_id = (select auth.uid()) or prestador_id = (select auth.uid()));
revoke all on public.solicitudes_prestador from anon, authenticated;
grant select on public.solicitudes_prestador to authenticated;

-- Datos opcionales (copias, nunca referencias a la ficha). El acceso del prestador expira solo:
--   SALUD    → mientras esté PENDIENTE, COTIZADA o ACEPTADA
--   CONTACTO → solo mientras esté ACEPTADA
create table if not exists public.solicitudes_compartido (
  solicitud_id uuid not null references public.solicitudes_prestador (id) on delete cascade,
  tipo text not null check (tipo in ('SALUD', 'CONTACTO')),
  datos jsonb not null check (jsonb_typeof(datos) = 'object' and pg_column_size(datos) < 4000),
  primary key (solicitud_id, tipo)
);
alter table public.solicitudes_compartido enable row level security;
drop policy if exists "Ver lo compartido" on public.solicitudes_compartido;
drop policy if exists "Propietario borra lo compartido" on public.solicitudes_compartido;
create policy "Ver lo compartido" on public.solicitudes_compartido for select to authenticated using (
  exists (
    select 1 from public.solicitudes_prestador s
    where s.id = solicitud_id
      and (s.propietario_id = (select auth.uid())
           or (s.prestador_id = (select auth.uid())
               and ((tipo = 'SALUD' and s.estado in ('PENDIENTE', 'COTIZADA', 'ACEPTADA'))
                    or (tipo = 'CONTACTO' and s.estado = 'ACEPTADA'))))
  )
);
create policy "Propietario borra lo compartido" on public.solicitudes_compartido for delete to authenticated using (
  exists (select 1 from public.solicitudes_prestador s where s.id = solicitud_id and s.propietario_id = (select auth.uid()))
);
revoke all on public.solicitudes_compartido from anon, authenticated;
grant select, delete on public.solicitudes_compartido to authenticated;

-- Agrega una entrada al historial (actor, estado, fecha y nota)
create or replace function privado.historial(h jsonb, actor text, estado text, nota text default null)
returns jsonb language sql stable set search_path = '' as $$
  select h || jsonb_build_array(jsonb_strip_nulls(jsonb_build_object('actor', actor, 'estado', estado, 'fecha', now(), 'nota', nota)))
$$;

-- Hora de Colombia (las fechas de las solicitudes son hora local)
create or replace function privado.ahora_local()
returns timestamp language sql stable set search_path = '' as $$ select (now() at time zone 'America/Bogota') $$;

-- Al quedar ACEPTADA: recordatorio SERVICIO_CONTRATADO para los miembros de la mascota
create or replace function privado.crear_cuidado_servicio(s public.solicitudes_prestador)
returns uuid language plpgsql security definer set search_path = '' as $$
declare
  nuevo uuid := gen_random_uuid();
  inicio timestamp := coalesce(s.propuesta_inicio, s.fecha_inicio);
begin
  if s.mascota_id is null then return null; end if;
  insert into public.eventos_cuidado (id, mascota_id, tipo, titulo, frecuencia_tipo, horas, ancla, fecha_inicio, aviso_anticipado_dias, detalle, origen, origen_ref)
  values (
    nuevo, s.mascota_id, 'SERVICIO_CONTRATADO', left(s.servicio_nombre || ' · ' || s.prestador_nombre, 80),
    'UNICO', array[to_char(inicio, 'HH24:MI')], 'CALENDARIO', inicio::date, 1,
    jsonb_strip_nulls(jsonb_build_object(
      'prestador', s.prestador_nombre,
      'indicaciones', case s.modalidad when 'DOMICILIO' then 'A domicilio' else 'En el establecimiento' end,
      'texto', case when s.cotizacion_precio is not null then 'Precio acordado: $' || to_char(s.cotizacion_precio, 'FM999G999G999') end)),
    'SOLICITUD_PRESTADOR', s.id
  );
  return nuevo;
end;
$$;
revoke execute on function privado.crear_cuidado_servicio(public.solicitudes_prestador) from public, anon, authenticated;

create or replace function public.crear_solicitud_prestador(
  p_mascota uuid, p_prestador uuid, p_categoria text, p_servicio text, p_modalidad text,
  p_inicio timestamp, p_fin timestamp, p_descripcion text, p_mensaje text default null,
  p_salud jsonb default null, p_contacto jsonb default null
)
returns uuid language plpgsql security definer set search_path = '' as $$
declare
  m public.mascotas%rowtype;
  p public.prestadores%rowtype;
  u public.usuarios%rowtype;
  serv jsonb;
  nuevo uuid;
  edad text;
begin
  if auth.uid() is null then raise exception 'Sin sesión' using errcode = '28000'; end if;
  if not privado.es_propietario(p_mascota) then raise exception 'Solo el propietario solicita servicios' using errcode = '42501'; end if;
  select * into m from public.mascotas where id = p_mascota and not eliminado;
  select * into p from public.prestadores where id = p_prestador and activo;
  if m.id is null or p.id is null or p.id = auth.uid() then raise exception 'Prestador no disponible' using errcode = 'P0002'; end if;
  if not p.recibiendo then raise exception 'No está recibiendo solicitudes' using errcode = 'P0002'; end if;
  if not (p_categoria = any (p.categorias)) or not (p_modalidad = any (p.modalidades)) then
    raise exception 'Categoría o modalidad no ofrecida' using errcode = '22023';
  end if;
  if p_servicio is not null then
    select x into serv from jsonb_array_elements(p.servicios) x where x ->> 'id' = p_servicio and x ->> 'categoria' = p_categoria;
    if serv is null then raise exception 'Servicio no ofrecido' using errcode = '22023'; end if;
  end if;
  if p_inicio < privado.ahora_local() - interval '1 hour' then raise exception 'La fecha ya pasó' using errcode = '22023'; end if;
  if (select count(*) from public.solicitudes_prestador where propietario_id = auth.uid() and estado in ('PENDIENTE', 'COTIZADA')) >= 5 then
    raise exception 'Demasiadas solicitudes abiertas' using errcode = '53400';
  end if;
  if exists (select 1 from public.solicitudes_prestador where mascota_id = p_mascota and prestador_id = p_prestador and estado in ('PENDIENTE', 'COTIZADA')) then
    raise exception 'Ya hay una solicitud abierta' using errcode = '23505';
  end if;
  select * into u from public.usuarios where id = auth.uid();
  edad := case when m.fecha_nacimiento is null then null
    when age(m.fecha_nacimiento) < interval '1 year' then extract(month from age(m.fecha_nacimiento))::int || ' meses'
    else extract(year from age(m.fecha_nacimiento))::int || ' años' end;
  insert into public.solicitudes_prestador (
    mascota_id, propietario_id, prestador_id, categoria_id, servicio_id, servicio_nombre, precio_modo, precio_referencia,
    modalidad, fecha_inicio, fecha_fin, descripcion, mensaje, resumen, historial, propietario_nombre, prestador_nombre)
  values (
    p_mascota, auth.uid(), p_prestador, p_categoria, p_servicio,
    coalesce(serv ->> 'nombre', (select c.nombre from public.categorias_servicio c where c.id = p_categoria)),
    coalesce(serv ->> 'modo', 'COTIZACION'), (serv ->> 'monto')::integer,
    p_modalidad, p_inicio, p_fin, btrim(p_descripcion), nullif(btrim(p_mensaje), ''),
    -- Instantánea: el prestador nunca lee la ficha (RF-36)
    jsonb_strip_nulls(jsonb_build_object('nombre', m.nombre, 'especie', m.especie, 'raza', m.raza, 'sexo', m.sexo, 'edad', edad,
      'tamano', m.tamano, 'peso_gramos', m.peso_gramos, 'foto_url', m.foto_url)),
    privado.historial('[]', 'PROPIETARIO', 'PENDIENTE'), u.nombre, p.nombre)
  returning id into nuevo;
  if p_salud is not null and p_salud <> '{}'::jsonb then
    insert into public.solicitudes_compartido (solicitud_id, tipo, datos) values (nuevo, 'SALUD', p_salud);
  end if;
  if p_contacto is not null and p_contacto <> '{}'::jsonb then
    insert into public.solicitudes_compartido (solicitud_id, tipo, datos) values (nuevo, 'CONTACTO', p_contacto);
  end if;
  return nuevo;
end;
$$;

-- Prestador: cotiza y/o propone otra fecha (PENDIENTE|COTIZADA → COTIZADA)
create or replace function public.cotizar_solicitud(
  p_id uuid, p_precio integer, p_observaciones text default null, p_vigencia date default null,
  p_inicio timestamp default null, p_fin timestamp default null
)
returns void language plpgsql security definer set search_path = '' as $$
declare s public.solicitudes_prestador%rowtype;
begin
  select * into s from public.solicitudes_prestador where id = p_id for update;
  if s.id is null or s.prestador_id <> auth.uid() then raise exception 'No autorizado' using errcode = '42501'; end if;
  if s.estado not in ('PENDIENTE', 'COTIZADA') then raise exception 'La solicitud ya no admite cotizaciones' using errcode = 'P0001'; end if;
  if p_precio is null and p_inicio is null then raise exception 'Escribe un precio o propone otra fecha' using errcode = '22023'; end if;
  update public.solicitudes_prestador set
    estado = 'COTIZADA', cotizacion_precio = p_precio, cotizacion_observaciones = nullif(btrim(p_observaciones), ''),
    cotizacion_vigencia = p_vigencia, propuesta_inicio = p_inicio, propuesta_fin = p_fin,
    historial = privado.historial(s.historial, 'PRESTADOR', 'COTIZADA',
      concat_ws(' · ', case when p_precio is not null then '$' || to_char(p_precio, 'FM999G999G999') end,
                case when p_inicio is not null then 'propone ' || to_char(p_inicio, 'YYYY-MM-DD HH24:MI') end))
  where id = p_id;
end;
$$;

-- Prestador: acepta sin cambios (solo precio fijo o "desde" y exactamente lo pedido)
create or replace function public.aceptar_solicitud_prestador(p_id uuid)
returns void language plpgsql security definer set search_path = '' as $$
declare s public.solicitudes_prestador%rowtype;
begin
  select * into s from public.solicitudes_prestador where id = p_id for update;
  if s.id is null or s.prestador_id <> auth.uid() then raise exception 'No autorizado' using errcode = '42501'; end if;
  if s.estado <> 'PENDIENTE' then raise exception 'Solo se aceptan directamente las solicitudes pendientes' using errcode = 'P0001'; end if;
  if s.precio_modo = 'COTIZACION' then raise exception 'Este servicio requiere cotización' using errcode = 'P0001'; end if;
  s.estado := 'ACEPTADA';
  update public.solicitudes_prestador set estado = 'ACEPTADA', evento_id = privado.crear_cuidado_servicio(s),
    historial = privado.historial(s.historial, 'PRESTADOR', 'ACEPTADA')
  where id = p_id;
end;
$$;

-- Propietario: confirma la cotización o las nuevas condiciones (COTIZADA → ACEPTADA)
create or replace function public.confirmar_cotizacion(p_id uuid)
returns void language plpgsql security definer set search_path = '' as $$
declare s public.solicitudes_prestador%rowtype;
begin
  select * into s from public.solicitudes_prestador where id = p_id for update;
  if s.id is null or s.propietario_id <> auth.uid() then raise exception 'No autorizado' using errcode = '42501'; end if;
  if s.estado <> 'COTIZADA' then raise exception 'No hay una cotización por confirmar' using errcode = 'P0001'; end if;
  if s.cotizacion_vigencia is not null and s.cotizacion_vigencia < privado.ahora_local()::date then
    raise exception 'La cotización ya venció' using errcode = 'P0001';
  end if;
  update public.solicitudes_prestador set estado = 'ACEPTADA',
    fecha_inicio = coalesce(s.propuesta_inicio, s.fecha_inicio), fecha_fin = coalesce(s.propuesta_fin, s.fecha_fin),
    evento_id = privado.crear_cuidado_servicio(s),
    historial = privado.historial(s.historial, 'PROPIETARIO', 'ACEPTADA')
  where id = p_id;
end;
$$;

-- Prestador: rechaza (PENDIENTE|COTIZADA → RECHAZADA)
create or replace function public.rechazar_solicitud_prestador(p_id uuid, p_motivo text default null)
returns void language plpgsql security definer set search_path = '' as $$
declare s public.solicitudes_prestador%rowtype;
begin
  select * into s from public.solicitudes_prestador where id = p_id for update;
  if s.id is null or s.prestador_id <> auth.uid() then raise exception 'No autorizado' using errcode = '42501'; end if;
  if s.estado not in ('PENDIENTE', 'COTIZADA') then raise exception 'La solicitud ya no se puede rechazar' using errcode = 'P0001'; end if;
  update public.solicitudes_prestador set estado = 'RECHAZADA', motivo = nullif(btrim(p_motivo), ''),
    historial = privado.historial(s.historial, 'PRESTADOR', 'RECHAZADA', nullif(btrim(p_motivo), ''))
  where id = p_id;
end;
$$;

-- Cancelar: propietario (PENDIENTE, COTIZADA o ACEPTADA) o prestador (PENDIENTE o ACEPTADA)
create or replace function public.cancelar_solicitud_prestador(p_id uuid, p_motivo text default null)
returns void language plpgsql security definer set search_path = '' as $$
declare
  s public.solicitudes_prestador%rowtype;
  actor text;
begin
  select * into s from public.solicitudes_prestador where id = p_id for update;
  if s.id is null then raise exception 'No autorizado' using errcode = '42501'; end if;
  if s.propietario_id = auth.uid() and s.estado in ('PENDIENTE', 'COTIZADA', 'ACEPTADA') then actor := 'PROPIETARIO';
  elsif s.prestador_id = auth.uid() and s.estado in ('PENDIENTE', 'ACEPTADA') then actor := 'PRESTADOR';
  elsif s.propietario_id = auth.uid() or s.prestador_id = auth.uid() then raise exception 'La solicitud ya no se puede cancelar' using errcode = 'P0001';
  else raise exception 'No autorizado' using errcode = '42501';
  end if;
  update public.solicitudes_prestador set estado = 'CANCELADA', motivo = nullif(btrim(p_motivo), ''),
    historial = privado.historial(s.historial, actor, 'CANCELADA', nullif(btrim(p_motivo), ''))
  where id = p_id;
  if s.evento_id is not null then update public.eventos_cuidado set eliminado = true where id = s.evento_id; end if;
end;
$$;

-- Prestador: completa (ACEPTADA, con la fecha del servicio ya cumplida o en curso)
create or replace function public.completar_solicitud_prestador(p_id uuid)
returns void language plpgsql security definer set search_path = '' as $$
declare s public.solicitudes_prestador%rowtype;
begin
  select * into s from public.solicitudes_prestador where id = p_id for update;
  if s.id is null or s.prestador_id <> auth.uid() then raise exception 'No autorizado' using errcode = '42501'; end if;
  if s.estado <> 'ACEPTADA' then raise exception 'Solo se completan servicios aceptados' using errcode = 'P0001'; end if;
  if s.fecha_inicio > privado.ahora_local() then raise exception 'El servicio aún no empieza' using errcode = 'P0001'; end if;
  update public.solicitudes_prestador set estado = 'COMPLETADA', historial = privado.historial(s.historial, 'PRESTADOR', 'COMPLETADA')
  where id = p_id;
end;
$$;

revoke execute on function
  public.crear_solicitud_prestador(uuid, uuid, text, text, text, timestamp, timestamp, text, text, jsonb, jsonb),
  public.cotizar_solicitud(uuid, integer, text, date, timestamp, timestamp),
  public.aceptar_solicitud_prestador(uuid), public.confirmar_cotizacion(uuid),
  public.rechazar_solicitud_prestador(uuid, text), public.cancelar_solicitud_prestador(uuid, text),
  public.completar_solicitud_prestador(uuid)
from public, anon;
grant execute on function
  public.crear_solicitud_prestador(uuid, uuid, text, text, text, timestamp, timestamp, text, text, jsonb, jsonb),
  public.cotizar_solicitud(uuid, integer, text, date, timestamp, timestamp),
  public.aceptar_solicitud_prestador(uuid), public.confirmar_cotizacion(uuid),
  public.rechazar_solicitud_prestador(uuid, text), public.cancelar_solicitud_prestador(uuid, text),
  public.completar_solicitud_prestador(uuid)
to authenticated;

do $$
begin
  if not exists (select 1 from pg_publication_tables where pubname = 'supabase_realtime' and tablename = 'solicitudes_prestador') then
    alter publication supabase_realtime add table public.solicitudes_prestador;
  end if;
end $$;

notify pgrst, 'reload schema';
