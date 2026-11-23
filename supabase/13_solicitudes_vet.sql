-- PETCARE · Parte 4.3: solicitudes a veterinario (RF-25), vínculo veterinario–mascota (RF-26)
-- y registros clínicos del veterinario (RF-27). Se puede ejecutar varias veces.

-- 1) Vínculo: da acceso a la ficha clínica según la sección 5 de la spec
create table if not exists public.vinculos_vet (
  mascota_id uuid not null references public.mascotas (id) on delete cascade,
  veterinario_id uuid not null references public.veterinarios (id) on delete cascade,
  creado_en timestamptz not null default now(),
  primary key (mascota_id, veterinario_id)
);
create index if not exists vinculos_vet_veterinario on public.vinculos_vet (veterinario_id);

create or replace function privado.es_vet_vinculado(mascota uuid)
returns boolean language sql stable security definer set search_path = '' as $$
  select exists (select 1 from public.vinculos_vet v where v.mascota_id = mascota and v.veterinario_id = (select auth.uid()))
$$;
revoke execute on function privado.es_vet_vinculado(uuid) from public, anon;
grant execute on function privado.es_vet_vinculado(uuid) to authenticated;

alter table public.vinculos_vet enable row level security;
drop policy if exists "Ver vínculos" on public.vinculos_vet;
create policy "Ver vínculos" on public.vinculos_vet for select to authenticated
  using (veterinario_id = (select auth.uid()) or privado.es_propietario(mascota_id));
revoke all on public.vinculos_vet from anon, authenticated;
grant select on public.vinculos_vet to authenticated;

-- 2) Solicitudes de servicio ("contratar"), sin pagos ni chat
create table if not exists public.solicitudes_vet (
  id uuid primary key default gen_random_uuid(),
  mascota_id uuid not null references public.mascotas (id) on delete cascade,
  propietario_id uuid not null references public.usuarios (id) on delete cascade,
  veterinario_id uuid not null references public.veterinarios (id) on delete cascade,
  servicio text not null check (servicio in ('CONSULTA', 'VACUNACION', 'DESPARASITACION', 'CIRUGIA', 'OTROS')),
  modalidad text not null check (modalidad in ('CONSULTORIO', 'DOMICILIO')),
  -- Hora local (como los registros de cuidado)
  fecha_preferida timestamp not null,
  fecha_confirmada timestamp,
  mensaje text check (char_length(mensaje) <= 500),
  motivo_rechazo text check (char_length(motivo_rechazo) <= 300),
  estado text not null default 'PENDIENTE' check (estado in ('PENDIENTE', 'ACEPTADA', 'RECHAZADA', 'COMPLETADA', 'CANCELADA')),
  -- Copias para mostrar la bandeja sin abrir la ficha
  mascota_nombre text not null,
  mascota_especie text not null,
  propietario_nombre text,
  propietario_telefono text,
  veterinario_nombre text not null,
  cita_id uuid, -- cuidado CITA_VET creado al aceptar
  creado_en timestamptz not null default now(),
  actualizado_en timestamptz not null default now()
);
create index if not exists solicitudes_vet_veterinario on public.solicitudes_vet (veterinario_id, estado);
create index if not exists solicitudes_vet_propietario on public.solicitudes_vet (propietario_id, estado);
create unique index if not exists solicitudes_vet_una_pendiente on public.solicitudes_vet (mascota_id, veterinario_id) where estado = 'PENDIENTE';

drop trigger if exists solicitudes_vet_actualizado_en on public.solicitudes_vet;
create trigger solicitudes_vet_actualizado_en before update on public.solicitudes_vet
  for each row execute function public.tocar_actualizado_en();

alter table public.solicitudes_vet enable row level security;
drop policy if exists "Ver mis solicitudes a veterinarios" on public.solicitudes_vet;
create policy "Ver mis solicitudes a veterinarios" on public.solicitudes_vet for select to authenticated
  using (propietario_id = (select auth.uid()) or veterinario_id = (select auth.uid()));
-- Solo se crean y cambian con las funciones de abajo
revoke all on public.solicitudes_vet from anon, authenticated;
grant select on public.solicitudes_vet to authenticated;

create or replace function public.crear_solicitud_vet(
  p_mascota uuid, p_veterinario uuid, p_servicio text, p_modalidad text, p_fecha timestamp, p_mensaje text default null
)
returns uuid language plpgsql security definer set search_path = '' as $$
declare
  m public.mascotas%rowtype;
  v public.veterinarios%rowtype;
  u public.usuarios%rowtype;
  nuevo uuid;
begin
  if auth.uid() is null then raise exception 'Sin sesión' using errcode = '28000'; end if;
  if not privado.es_propietario(p_mascota) then raise exception 'Solo el propietario solicita servicios' using errcode = '42501'; end if;
  select * into m from public.mascotas where id = p_mascota and not eliminado;
  select * into v from public.veterinarios where id = p_veterinario and activo;
  if m.id is null or v.id is null or v.id = auth.uid() then raise exception 'Veterinario no disponible' using errcode = 'P0002'; end if;
  if cardinality(v.servicios) > 0 and not (p_servicio = any (v.servicios)) then raise exception 'Servicio no ofrecido' using errcode = '22023'; end if;
  if p_modalidad = 'DOMICILIO' and not v.domicilio then raise exception 'No atiende a domicilio' using errcode = '22023'; end if;
  if p_fecha < localtimestamp - interval '1 day' then raise exception 'La fecha ya pasó' using errcode = '22023'; end if;
  -- Anti-abuso: máximo 5 pendientes por dueño y una por mascota y veterinario
  if (select count(*) from public.solicitudes_vet where propietario_id = auth.uid() and estado = 'PENDIENTE') >= 5 then
    raise exception 'Demasiadas solicitudes pendientes' using errcode = '53400';
  end if;
  if exists (select 1 from public.solicitudes_vet where mascota_id = p_mascota and veterinario_id = p_veterinario and estado = 'PENDIENTE') then
    raise exception 'Ya hay una solicitud pendiente' using errcode = '23505';
  end if;
  select * into u from public.usuarios where id = auth.uid();
  insert into public.solicitudes_vet (mascota_id, propietario_id, veterinario_id, servicio, modalidad, fecha_preferida, mensaje,
                                      mascota_nombre, mascota_especie, propietario_nombre, propietario_telefono, veterinario_nombre)
  values (p_mascota, auth.uid(), p_veterinario, p_servicio, p_modalidad, p_fecha, nullif(btrim(p_mensaje), ''),
          m.nombre, m.especie, u.nombre, coalesce(u.whatsapp, u.telefono), v.nombre)
  returning id into nuevo;
  return nuevo;
end;
$$;

-- El veterinario acepta (confirmando o ajustando la hora) o rechaza con un motivo opcional
create or replace function public.resolver_solicitud_vet(p_id uuid, p_aceptar boolean, p_fecha timestamp default null, p_motivo text default null)
returns void language plpgsql security definer set search_path = '' as $$
declare
  s public.solicitudes_vet%rowtype;
  v public.veterinarios%rowtype;
  fecha timestamp;
  cita uuid;
begin
  select * into s from public.solicitudes_vet where id = p_id for update;
  if s.id is null or s.veterinario_id <> auth.uid() then raise exception 'No autorizado' using errcode = '42501'; end if;
  if s.estado <> 'PENDIENTE' then raise exception 'La solicitud ya no está pendiente' using errcode = 'P0001'; end if;
  if not p_aceptar then
    update public.solicitudes_vet set estado = 'RECHAZADA', motivo_rechazo = nullif(btrim(p_motivo), '') where id = p_id;
    return;
  end if;
  select * into v from public.veterinarios where id = s.veterinario_id;
  fecha := coalesce(p_fecha, s.fecha_preferida);
  insert into public.vinculos_vet (mascota_id, veterinario_id) values (s.mascota_id, s.veterinario_id) on conflict do nothing;
  -- Recordatorio en los teléfonos de los miembros (llega con la sincronización)
  cita := gen_random_uuid();
  insert into public.eventos_cuidado (id, mascota_id, tipo, titulo, frecuencia_tipo, horas, ancla, fecha_inicio, aviso_anticipado_dias, detalle, origen, origen_ref)
  values (
    cita, s.mascota_id, 'CITA_VET',
    left('Cita con ' || v.nombre, 80),
    'UNICO', array[to_char(fecha, 'HH24:MI')], 'CALENDARIO', fecha::date, 1,
    jsonb_strip_nulls(jsonb_build_object('veterinario', v.nombre, 'clinica', v.clinica,
      'indicaciones', case s.modalidad when 'DOMICILIO' then 'A domicilio' else 'En ' || coalesce(v.clinica, v.direccion) end)),
    'SOLICITUD_VET', s.id
  );
  update public.solicitudes_vet set estado = 'ACEPTADA', fecha_confirmada = fecha, cita_id = cita where id = p_id;
end;
$$;

-- El dueño cancela mientras esté pendiente o aceptada (se borra el recordatorio de la cita)
create or replace function public.cancelar_solicitud_vet(p_id uuid)
returns void language plpgsql security definer set search_path = '' as $$
declare s public.solicitudes_vet%rowtype;
begin
  select * into s from public.solicitudes_vet where id = p_id for update;
  if s.id is null or s.propietario_id <> auth.uid() then raise exception 'No autorizado' using errcode = '42501'; end if;
  if s.estado not in ('PENDIENTE', 'ACEPTADA') then raise exception 'La solicitud ya terminó' using errcode = 'P0001'; end if;
  update public.solicitudes_vet set estado = 'CANCELADA' where id = p_id;
  if s.cita_id is not null then update public.eventos_cuidado set eliminado = true where id = s.cita_id; end if;
end;
$$;

-- Solo el veterinario la marca como completada
create or replace function public.completar_solicitud_vet(p_id uuid)
returns void language plpgsql security definer set search_path = '' as $$
declare s public.solicitudes_vet%rowtype;
begin
  select * into s from public.solicitudes_vet where id = p_id for update;
  if s.id is null or s.veterinario_id <> auth.uid() then raise exception 'No autorizado' using errcode = '42501'; end if;
  if s.estado <> 'ACEPTADA' then raise exception 'Solo se completan citas aceptadas' using errcode = 'P0001'; end if;
  update public.solicitudes_vet set estado = 'COMPLETADA' where id = p_id;
end;
$$;

-- Desvincular: el propietario en cualquier momento; el veterinario, a sí mismo
create or replace function public.desvincular_vet(p_mascota uuid, p_veterinario uuid)
returns void language plpgsql security definer set search_path = '' as $$
begin
  if not (privado.es_propietario(p_mascota) or p_veterinario = auth.uid()) then
    raise exception 'No autorizado' using errcode = '42501';
  end if;
  delete from public.vinculos_vet where mascota_id = p_mascota and veterinario_id = p_veterinario;
end;
$$;

revoke execute on function public.crear_solicitud_vet(uuid, uuid, text, text, timestamp, text), public.resolver_solicitud_vet(uuid, boolean, timestamp, text),
  public.cancelar_solicitud_vet(uuid), public.completar_solicitud_vet(uuid), public.desvincular_vet(uuid, uuid) from public, anon;
grant execute on function public.crear_solicitud_vet(uuid, uuid, text, text, timestamp, text), public.resolver_solicitud_vet(uuid, boolean, timestamp, text),
  public.cancelar_solicitud_vet(uuid), public.completar_solicitud_vet(uuid), public.desvincular_vet(uuid, uuid) to authenticated;

-- 3) Autoría: el veterinario vinculado firma con su nombre profesional y su insignia
create or replace function privado.nombre_autor(mascota uuid)
returns text language sql stable security definer set search_path = '' as $$
  select case
    when not privado.es_propietario(mascota) and privado.es_vet_vinculado(mascota) then
      (select v.nombre || case when v.verificacion = 'VERIFICADO' then ' ✓' else '' end from public.veterinarios v where v.id = (select auth.uid()))
    else (select u.nombre from public.usuarios u where u.id = (select auth.uid()))
  end
$$;
create or replace function privado.rol_autor(mascota uuid)
returns text language sql stable security definer set search_path = '' as $$
  select case
    when privado.es_propietario(mascota) then 'PROPIETARIO'
    when privado.es_vet_vinculado(mascota) then 'VETERINARIO'
    when privado.es_miembro(mascota) then 'CUIDADOR'
  end
$$;

create or replace function public.marcar_autor_registro()
returns trigger language plpgsql security definer set search_path = '' as $$
begin
  new.registrado_por := auth.uid();
  new.registrado_por_nombre := privado.nombre_autor(new.mascota_id);
  new.registrado_por_rol := privado.rol_autor(new.mascota_id);
  return new;
end;
$$;

create or replace function public.marcar_autor_evento()
returns trigger language plpgsql security definer set search_path = '' as $$
begin
  if tg_op = 'INSERT' then
    new.creado_por := auth.uid();
    new.creado_por_nombre := privado.nombre_autor(new.mascota_id);
    new.creado_por_rol := privado.rol_autor(new.mascota_id);
  else
    new.creado_por := old.creado_por;
    new.creado_por_nombre := old.creado_por_nombre;
    new.creado_por_rol := old.creado_por_rol;
    new.creado_en := old.creado_en;
  end if;
  return new;
end;
$$;

-- 4) Permisos del veterinario vinculado (sección 5)
drop policy if exists "Veterinario ve a su paciente" on public.mascotas;
create policy "Veterinario ve a su paciente" on public.mascotas for select to authenticated using (privado.es_vet_vinculado(id));

-- Cuidados: lee todos; crea vacunas, desparasitaciones, medicamentos y citas, y edita solo los suyos
drop policy if exists "Veterinario ve cuidados" on public.eventos_cuidado;
drop policy if exists "Veterinario crea cuidados clínicos" on public.eventos_cuidado;
drop policy if exists "Veterinario edita sus cuidados" on public.eventos_cuidado;
create policy "Veterinario ve cuidados" on public.eventos_cuidado for select to authenticated using (privado.es_vet_vinculado(mascota_id));
create policy "Veterinario crea cuidados clínicos" on public.eventos_cuidado for insert to authenticated with check (
  privado.es_vet_vinculado(mascota_id)
  and tipo in ('VACUNA', 'DESPARASITACION_INTERNA', 'DESPARASITACION_EXTERNA', 'MEDICAMENTO', 'CITA_VET')
  and origen = 'VETERINARIO' and not oculto
);
create policy "Veterinario edita sus cuidados" on public.eventos_cuidado for update to authenticated
  using (privado.es_vet_vinculado(mascota_id) and creado_por = (select auth.uid()))
  with check (privado.es_vet_vinculado(mascota_id) and origen = 'VETERINARIO' and not oculto);

drop policy if exists "Veterinario ve registros" on public.registros_cuidado;
create policy "Veterinario ve registros" on public.registros_cuidado for select to authenticated using (privado.es_vet_vinculado(mascota_id));

-- Peso: lo ve y lo registra
drop policy if exists "Veterinario ve pesos" on public.pesos;
drop policy if exists "Veterinario registra pesos" on public.pesos;
create policy "Veterinario ve pesos" on public.pesos for select to authenticated using (privado.es_vet_vinculado(mascota_id));
create policy "Veterinario registra pesos" on public.pesos for insert to authenticated with check (privado.es_vet_vinculado(mascota_id));

-- Visitas y documentos: lee todo; crea los suyos y edita solo los suyos
drop policy if exists "Veterinario ve visitas" on public.visitas;
drop policy if exists "Veterinario crea visitas" on public.visitas;
drop policy if exists "Veterinario edita sus visitas" on public.visitas;
create policy "Veterinario ve visitas" on public.visitas for select to authenticated using (privado.es_vet_vinculado(mascota_id));
create policy "Veterinario crea visitas" on public.visitas for insert to authenticated with check (privado.es_vet_vinculado(mascota_id));
create policy "Veterinario edita sus visitas" on public.visitas for update to authenticated
  using (privado.es_vet_vinculado(mascota_id) and registrado_por = (select auth.uid()))
  with check (privado.es_vet_vinculado(mascota_id));

drop policy if exists "Veterinario ve documentos" on public.documentos;
drop policy if exists "Veterinario sube documentos" on public.documentos;
create policy "Veterinario ve documentos" on public.documentos for select to authenticated using (privado.es_vet_vinculado(mascota_id));
create policy "Veterinario sube documentos" on public.documentos for insert to authenticated with check (privado.es_vet_vinculado(mascota_id));

-- Archivos: "{autor}/{mascota}/…": el propietario ve los de sus mascotas (también los que subió el
-- veterinario) y el veterinario vinculado los de sus pacientes
drop policy if exists "Documentos: ver los de mis mascotas y pacientes" on storage.objects;
create policy "Documentos: ver los de mis mascotas y pacientes" on storage.objects for select to authenticated
  using (
    bucket_id = 'documentos'
    and (storage.foldername(name))[2] ~ '^[0-9a-f-]{36}$'
    and (privado.es_propietario(((storage.foldername(name))[2])::uuid) or privado.es_vet_vinculado(((storage.foldername(name))[2])::uuid))
  );
drop policy if exists "Fotos de mascotas: ver las de mis pacientes" on storage.objects;
create policy "Fotos de mascotas: ver las de mis pacientes" on storage.objects for select to authenticated
  using (
    bucket_id = 'mascotas'
    and (storage.foldername(name))[2] ~ '^[0-9a-f-]{36}$'
    and privado.es_vet_vinculado(((storage.foldername(name))[2])::uuid)
  );

-- 5) Tiempo real: la bandeja y "Mis solicitudes" se actualizan solas
do $$
begin
  if not exists (select 1 from pg_publication_tables where pubname = 'supabase_realtime' and tablename = 'solicitudes_vet') then
    alter publication supabase_realtime add table public.solicitudes_vet;
  end if;
  if not exists (select 1 from pg_publication_tables where pubname = 'supabase_realtime' and tablename = 'vinculos_vet') then
    alter publication supabase_realtime add table public.vinculos_vet;
  end if;
end $$;

notify pgrst, 'reload schema';
