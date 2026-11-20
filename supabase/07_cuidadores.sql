-- PETCARE · Parte 3.1: cuidar una mascota por código (RF-20) y permisos por rol (sección 5)
-- Se puede ejecutar varias veces.

-- 1) Miembros de cada mascota: el propietario y los cuidadores aprobados
create table if not exists public.miembros_mascota (
  mascota_id uuid not null references public.mascotas (id) on delete cascade,
  usuario_id uuid not null references public.usuarios (id) on delete cascade,
  rol text not null check (rol in ('PROPIETARIO', 'CUIDADOR')),
  nombre text, -- nombre del usuario al unirse (para la lista "Personas")
  creado_en timestamptz not null default now(),
  primary key (mascota_id, usuario_id)
);
create index if not exists miembros_usuario on public.miembros_mascota (usuario_id);

-- 2) Solicitudes "Quiero cuidar esta mascota"
create table if not exists public.solicitudes_cuidado (
  id uuid primary key default gen_random_uuid(),
  mascota_id uuid not null references public.mascotas (id) on delete cascade,
  solicitante_id uuid not null references public.usuarios (id) on delete cascade,
  solicitante_nombre text,
  estado text not null default 'PENDIENTE' check (estado in ('PENDIENTE', 'APROBADA', 'RECHAZADA')),
  creado_en timestamptz not null default now(),
  resuelto_en timestamptz
);
create unique index if not exists solicitudes_una_pendiente on public.solicitudes_cuidado (mascota_id, solicitante_id) where estado = 'PENDIENTE';
create index if not exists solicitudes_solicitante on public.solicitudes_cuidado (solicitante_id, estado);

-- 3) Funciones de permisos (esquema privado, fuera de la API)
create or replace function privado.es_miembro(mascota uuid)
returns boolean language sql stable security definer set search_path = '' as $$
  select exists (select 1 from public.mascotas m where m.id = mascota and m.propietario_id = (select auth.uid()))
      or exists (select 1 from public.miembros_mascota mm where mm.mascota_id = mascota and mm.usuario_id = (select auth.uid()))
$$;
revoke execute on function privado.es_miembro(uuid) from public, anon;
grant execute on function privado.es_miembro(uuid) to authenticated;

-- El propietario queda como miembro al crear la mascota (y las que ya existían)
create or replace function public.agregar_propietario_como_miembro()
returns trigger language plpgsql security definer set search_path = '' as $$
begin
  insert into public.miembros_mascota (mascota_id, usuario_id, rol, nombre)
  values (new.id, new.propietario_id, 'PROPIETARIO', (select u.nombre from public.usuarios u where u.id = new.propietario_id))
  on conflict do nothing;
  return new;
end;
$$;
revoke execute on function public.agregar_propietario_como_miembro() from public, anon, authenticated;
drop trigger if exists mascotas_propietario_miembro on public.mascotas;
create trigger mascotas_propietario_miembro after insert on public.mascotas
  for each row execute function public.agregar_propietario_como_miembro();
insert into public.miembros_mascota (mascota_id, usuario_id, rol, nombre)
select m.id, m.propietario_id, 'PROPIETARIO', u.nombre from public.mascotas m join public.usuarios u on u.id = m.propietario_id
on conflict do nothing;

-- El rol del autor de un registro ahora distingue propietario y cuidador
create or replace function public.marcar_autor_registro()
returns trigger language plpgsql security definer set search_path = '' as $$
begin
  new.registrado_por := auth.uid();
  new.registrado_por_nombre := (select u.nombre from public.usuarios u where u.id = auth.uid());
  new.registrado_por_rol := case
    when privado.es_propietario(new.mascota_id) then 'PROPIETARIO'
    when privado.es_miembro(new.mascota_id) then 'CUIDADOR'
  end;
  return new;
end;
$$;

-- 4) Reglas por rol (sección 5 de la spec)
-- Mascota: la ven propietario y cuidadores; solo el propietario la edita
drop policy if exists "Propietario ve sus mascotas" on public.mascotas;
drop policy if exists "Miembros ven la mascota" on public.mascotas;
create policy "Miembros ven la mascota" on public.mascotas for select to authenticated
  using ((select auth.uid()) = propietario_id or privado.es_miembro(id));

-- Cuidados: los ven los miembros; los crea y edita el propietario
drop policy if exists "Propietario ve cuidados" on public.eventos_cuidado;
drop policy if exists "Miembros ven cuidados" on public.eventos_cuidado;
create policy "Miembros ven cuidados" on public.eventos_cuidado for select to authenticated using (privado.es_miembro(mascota_id));

-- Registros de cumplimiento: los ven y crean propietario y cuidadores
drop policy if exists "Propietario ve registros" on public.registros_cuidado;
drop policy if exists "Propietario crea registros" on public.registros_cuidado;
drop policy if exists "Propietario edita registros" on public.registros_cuidado;
drop policy if exists "Miembros ven registros" on public.registros_cuidado;
drop policy if exists "Miembros crean registros" on public.registros_cuidado;
drop policy if exists "Miembros editan registros" on public.registros_cuidado;
create policy "Miembros ven registros" on public.registros_cuidado for select to authenticated using (privado.es_miembro(mascota_id));
create policy "Miembros crean registros" on public.registros_cuidado for insert to authenticated with check (
  privado.es_miembro(mascota_id)
  and exists (select 1 from public.eventos_cuidado e where e.id = evento_id and e.mascota_id = registros_cuidado.mascota_id)
);
create policy "Miembros editan registros" on public.registros_cuidado for update to authenticated
  using (privado.es_miembro(mascota_id)) with check (privado.es_miembro(mascota_id));

-- Peso: lo ven los miembros (el cuidador solo lo consulta); lo registra el propietario
drop policy if exists "Propietario ve pesos" on public.pesos;
drop policy if exists "Miembros ven pesos" on public.pesos;
create policy "Miembros ven pesos" on public.pesos for select to authenticated using (privado.es_miembro(mascota_id));
-- Visitas: solo propietario (los cuidadores no tienen acceso; sin cambios)

-- Fotos: los cuidadores aprobados ven la foto de la mascota ("{dueño}/{mascota}/archivo.jpg")
drop policy if exists "Fotos de mascotas: ver las de mis mascotas" on storage.objects;
create policy "Fotos de mascotas: ver las de mis mascotas" on storage.objects for select to authenticated
  using (
    bucket_id = 'mascotas'
    and (storage.foldername(name))[2] ~ '^[0-9a-f-]{36}$'
    and privado.es_miembro(((storage.foldername(name))[2])::uuid)
  );

-- Miembros y solicitudes: cada quien ve lo suyo; el propietario, lo de sus mascotas.
-- Se modifican solo con las funciones de abajo (sin permisos directos de escritura).
alter table public.miembros_mascota enable row level security;
alter table public.solicitudes_cuidado enable row level security;
drop policy if exists "Ver miembros" on public.miembros_mascota;
create policy "Ver miembros" on public.miembros_mascota for select to authenticated
  using (usuario_id = (select auth.uid()) or privado.es_propietario(mascota_id));
drop policy if exists "Ver solicitudes" on public.solicitudes_cuidado;
create policy "Ver solicitudes" on public.solicitudes_cuidado for select to authenticated
  using (solicitante_id = (select auth.uid()) or privado.es_propietario(mascota_id));
revoke all on public.miembros_mascota, public.solicitudes_cuidado from anon, authenticated;
grant select on public.miembros_mascota, public.solicitudes_cuidado to authenticated;

-- 5) Funciones de la API
-- Buscar por código exacto: solo nombre y especie, nunca datos privados ni listados
create or replace function public.buscar_mascota_por_codigo(p_codigo text)
returns json language plpgsql stable security definer set search_path = '' as $$
declare r json;
begin
  if auth.uid() is null then raise exception 'Sin sesión' using errcode = '28000'; end if;
  select json_build_object('mascota_id', m.id, 'nombre', m.nombre, 'especie', m.especie)
  into r
  from public.codigos_mascota c join public.mascotas m on m.id = c.mascota_id
  where c.codigo = upper(replace(btrim(p_codigo), ' ', '')) and c.activo and not m.eliminado;
  return r;
end;
$$;

-- Pedir cuidar: devuelve PENDIENTE, YA_MIEMBRO, PROPIA, NO_DISPONIBLE, LIMITE o RECHAZADA_RECIENTE
create or replace function public.solicitar_cuidado(p_codigo text)
returns text language plpgsql security definer set search_path = '' as $$
declare
  yo uuid := auth.uid();
  v_mascota uuid;
  v_propietario uuid;
begin
  if yo is null then raise exception 'Sin sesión' using errcode = '28000'; end if;
  select m.id, m.propietario_id into v_mascota, v_propietario
  from public.codigos_mascota c join public.mascotas m on m.id = c.mascota_id
  where c.codigo = upper(replace(btrim(p_codigo), ' ', '')) and c.activo and not m.eliminado;
  if v_mascota is null then return 'NO_DISPONIBLE'; end if;
  if v_propietario = yo then return 'PROPIA'; end if;
  if exists (select 1 from public.miembros_mascota where mascota_id = v_mascota and usuario_id = yo) then return 'YA_MIEMBRO'; end if;
  if exists (select 1 from public.solicitudes_cuidado where mascota_id = v_mascota and solicitante_id = yo and estado = 'PENDIENTE') then return 'PENDIENTE'; end if;
  if exists (select 1 from public.solicitudes_cuidado where mascota_id = v_mascota and solicitante_id = yo
             and estado = 'RECHAZADA' and resuelto_en > now() - interval '24 hours') then return 'RECHAZADA_RECIENTE'; end if;
  if (select count(*) from public.solicitudes_cuidado where solicitante_id = yo and estado = 'PENDIENTE') >= 5 then return 'LIMITE'; end if;
  insert into public.solicitudes_cuidado (mascota_id, solicitante_id, solicitante_nombre)
  values (v_mascota, yo, (select u.nombre from public.usuarios u where u.id = yo));
  return 'PENDIENTE';
end;
$$;

-- Aprobar o rechazar (solo el propietario)
create or replace function public.resolver_solicitud(p_id uuid, p_aprobar boolean)
returns void language plpgsql security definer set search_path = '' as $$
declare s public.solicitudes_cuidado;
begin
  select * into s from public.solicitudes_cuidado where id = p_id for update;
  if s.id is null or not privado.es_propietario(s.mascota_id) then raise exception 'No autorizado' using errcode = '42501'; end if;
  if s.estado <> 'PENDIENTE' then return; end if;
  update public.solicitudes_cuidado
     set estado = case when p_aprobar then 'APROBADA' else 'RECHAZADA' end, resuelto_en = now()
   where id = p_id;
  if p_aprobar then
    insert into public.miembros_mascota (mascota_id, usuario_id, rol, nombre)
    values (s.mascota_id, s.solicitante_id, 'CUIDADOR', s.solicitante_nombre)
    on conflict do nothing;
  end if;
end;
$$;

-- Quitar a un cuidador (propietario) o salir (el propio cuidador). El propietario no puede salir.
create or replace function public.quitar_miembro(p_mascota uuid, p_usuario uuid)
returns void language plpgsql security definer set search_path = '' as $$
begin
  if not (privado.es_propietario(p_mascota) or p_usuario = auth.uid()) then
    raise exception 'No autorizado' using errcode = '42501';
  end if;
  delete from public.miembros_mascota where mascota_id = p_mascota and usuario_id = p_usuario and rol = 'CUIDADOR';
end;
$$;

-- Regenerar (el anterior deja de servir) o desactivar el código (solo propietario). No expulsa a nadie.
create or replace function public.regenerar_codigo(p_mascota uuid)
returns text language plpgsql security definer set search_path = '' as $$
declare nuevo text;
begin
  if not privado.es_propietario(p_mascota) then raise exception 'No autorizado' using errcode = '42501'; end if;
  nuevo := public.nuevo_codigo_mascota();
  insert into public.codigos_mascota (codigo, mascota_id, activo) values (nuevo, p_mascota, true)
  on conflict (mascota_id) do update set codigo = excluded.codigo, activo = true, creado_en = now();
  return nuevo;
end;
$$;

create or replace function public.desactivar_codigo(p_mascota uuid)
returns void language plpgsql security definer set search_path = '' as $$
begin
  if not privado.es_propietario(p_mascota) then raise exception 'No autorizado' using errcode = '42501'; end if;
  update public.codigos_mascota set activo = false where mascota_id = p_mascota;
end;
$$;

revoke execute on function public.buscar_mascota_por_codigo(text), public.solicitar_cuidado(text),
  public.resolver_solicitud(uuid, boolean), public.quitar_miembro(uuid, uuid),
  public.regenerar_codigo(uuid), public.desactivar_codigo(uuid) from public, anon;
grant execute on function public.buscar_mascota_por_codigo(text), public.solicitar_cuidado(text),
  public.resolver_solicitud(uuid, boolean), public.quitar_miembro(uuid, uuid),
  public.regenerar_codigo(uuid), public.desactivar_codigo(uuid) to authenticated;

notify pgrst, 'reload schema';
