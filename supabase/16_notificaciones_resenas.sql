-- PETCARE · Parte 5.1: notificaciones (RF-29), reseñas (RF-28) y reportes
-- Se puede ejecutar varias veces.

-- 1) Bandeja de avisos por usuario. La app los muestra en vivo (Realtime) o al sincronizar;
--    con Firebase configurado, además llegan por push aunque la app esté cerrada.
create table if not exists public.notificaciones (
  id uuid primary key default gen_random_uuid(),
  usuario_id uuid not null references public.usuarios (id) on delete cascade,
  tipo text not null,
  titulo text not null,
  cuerpo text not null,
  -- Modo y pestaña que abre al tocarla
  modo text not null default 'PERSONAL' check (modo in ('PERSONAL', 'VETERINARIO', 'PRESTADOR')),
  destino text not null default 'HOY' check (destino in ('HOY', 'MASCOTAS', 'SERVICIOS', 'PERFIL', 'SOLICITUDES')),
  ref_id uuid,
  leida boolean not null default false,
  creado_en timestamptz not null default now()
);
create index if not exists notificaciones_usuario on public.notificaciones (usuario_id, creado_en desc);
alter table public.notificaciones enable row level security;
drop policy if exists "Ver mis notificaciones" on public.notificaciones;
drop policy if exists "Marcar mis notificaciones" on public.notificaciones;
create policy "Ver mis notificaciones" on public.notificaciones for select to authenticated using (usuario_id = (select auth.uid()));
create policy "Marcar mis notificaciones" on public.notificaciones for update to authenticated
  using (usuario_id = (select auth.uid())) with check (usuario_id = (select auth.uid()));
revoke all on public.notificaciones from anon, authenticated;
grant select on public.notificaciones to authenticated;
grant update (leida) on public.notificaciones to authenticated;

-- Teléfonos de cada usuario para FCM (FCM solo transporta; el contenido sale de esta base)
create table if not exists public.dispositivos (
  token text primary key check (char_length(token) between 20 and 4096),
  usuario_id uuid not null references public.usuarios (id) on delete cascade,
  actualizado_en timestamptz not null default now()
);
create index if not exists dispositivos_usuario on public.dispositivos (usuario_id);
alter table public.dispositivos enable row level security;
revoke all on public.dispositivos from anon, authenticated;

create or replace function public.registrar_dispositivo(p_token text)
returns void language plpgsql security definer set search_path = '' as $$
begin
  if auth.uid() is null then raise exception 'Sin sesión' using errcode = '28000'; end if;
  -- Un token pertenece al último usuario que inició sesión en ese teléfono
  insert into public.dispositivos (token, usuario_id) values (p_token, auth.uid())
  on conflict (token) do update set usuario_id = excluded.usuario_id, actualizado_en = now();
end;
$$;
create or replace function public.olvidar_dispositivo(p_token text)
returns void language plpgsql security definer set search_path = '' as $$
begin
  delete from public.dispositivos where token = p_token and usuario_id = auth.uid();
end;
$$;

create or replace function privado.notificar(p_usuario uuid, p_tipo text, p_titulo text, p_cuerpo text, p_modo text, p_destino text, p_ref uuid)
returns void language plpgsql security definer set search_path = '' as $$
begin
  if p_usuario is null then return; end if;
  insert into public.notificaciones (usuario_id, tipo, titulo, cuerpo, modo, destino, ref_id)
  values (p_usuario, p_tipo, left(p_titulo, 120), left(p_cuerpo, 300), p_modo, p_destino, p_ref);
end;
$$;
revoke execute on function privado.notificar(uuid, text, text, text, text, text, uuid) from public, anon, authenticated;

-- 2) Qué genera un aviso (RF-29)
-- Cuidadores: nueva solicitud (→ propietario); aprobación o rechazo (→ solicitante)
create or replace function privado.avisar_cuidado()
returns trigger language plpgsql security definer set search_path = '' as $$
declare nombre text;
begin
  select m.nombre into nombre from public.mascotas m where m.id = new.mascota_id;
  if tg_op = 'INSERT' then
    perform privado.notificar((select m.propietario_id from public.mascotas m where m.id = new.mascota_id), 'CUIDADO_SOLICITADO',
      'Quieren ayudarte con ' || coalesce(nombre, 'tu mascota'), coalesce(new.solicitante_nombre, 'Alguien') || ' pidió cuidar a ' || coalesce(nombre, 'tu mascota') || '.',
      'PERSONAL', 'HOY', new.id);
  elsif new.estado is distinct from old.estado and new.estado in ('APROBADA', 'RECHAZADA') then
    perform privado.notificar(new.solicitante_id, 'CUIDADO_' || new.estado,
      case new.estado when 'APROBADA' then 'Ya cuidas a ' || coalesce(nombre, 'la mascota') else 'Solicitud no aprobada' end,
      case new.estado when 'APROBADA' then 'Ahora verás sus cuidados y recordatorios.' else 'El dueño no aprobó tu solicitud para cuidar a ' || coalesce(nombre, 'la mascota') || '.' end,
      'PERSONAL', 'MASCOTAS', new.id);
  end if;
  return new;
end;
$$;
drop trigger if exists solicitudes_cuidado_aviso on public.solicitudes_cuidado;
create trigger solicitudes_cuidado_aviso after insert or update of estado on public.solicitudes_cuidado
  for each row execute function privado.avisar_cuidado();

-- Veterinarios: nueva (→ veterinario); aceptada, rechazada o completada (→ dueño); cancelada (→ veterinario)
create or replace function privado.avisar_solicitud_vet()
returns trigger language plpgsql security definer set search_path = '' as $$
begin
  if tg_op = 'INSERT' then
    perform privado.notificar(new.veterinario_id, 'VET_NUEVA', 'Nueva solicitud de cita',
      coalesce(new.propietario_nombre, 'Un dueño') || ' pide cita para ' || new.mascota_nombre || '.', 'VETERINARIO', 'SOLICITUDES', new.id);
  elsif new.estado is distinct from old.estado then
    if new.estado = 'CANCELADA' then
      perform privado.notificar(new.veterinario_id, 'VET_CANCELADA', 'Cita cancelada',
        coalesce(new.propietario_nombre, 'El dueño') || ' canceló la cita de ' || new.mascota_nombre || '.', 'VETERINARIO', 'SOLICITUDES', new.id);
    else
      perform privado.notificar(new.propietario_id, 'VET_' || new.estado,
        case new.estado when 'ACEPTADA' then 'Cita confirmada' when 'RECHAZADA' then 'Cita no disponible' else 'Cita completada' end,
        new.veterinario_nombre || case new.estado
          when 'ACEPTADA' then ' confirmó la cita de ' || new.mascota_nombre || ' para el ' || to_char(new.fecha_confirmada, 'DD/MM HH24:MI') || '.'
          when 'RECHAZADA' then ' no puede atender a ' || new.mascota_nombre || coalesce(': ' || new.motivo_rechazo, '.')
          else ' marcó como completada la cita de ' || new.mascota_nombre || '. Ya puedes calificarla.' end,
        'PERSONAL', 'SERVICIOS', new.id);
    end if;
  end if;
  return new;
end;
$$;
drop trigger if exists solicitudes_vet_aviso on public.solicitudes_vet;
create trigger solicitudes_vet_aviso after insert or update of estado on public.solicitudes_vet
  for each row execute function privado.avisar_solicitud_vet();

-- Prestadores: nueva (→ prestador); cotización o propuesta (→ dueño); aceptada, rechazada,
-- cancelada o completada (→ la contraparte de quien hizo el cambio)
create or replace function privado.avisar_solicitud_prestador()
returns trigger language plpgsql security definer set search_path = '' as $$
declare
  actor text := new.historial -> -1 ->> 'actor';
  mascota text := new.resumen ->> 'nombre';
begin
  if tg_op = 'INSERT' then
    perform privado.notificar(new.prestador_id, 'PRES_NUEVA', 'Nueva solicitud: ' || new.servicio_nombre,
      coalesce(new.propietario_nombre, 'Un dueño') || ' pide cotización para ' || mascota || '.', 'PRESTADOR', 'SOLICITUDES', new.id);
  elsif new.estado = 'COTIZADA' and jsonb_array_length(new.historial) > jsonb_array_length(old.historial) then
    perform privado.notificar(new.propietario_id, 'PRES_COTIZADA', 'Tienes una propuesta',
      new.prestador_nombre || ' respondió tu solicitud para ' || mascota || '. Revísala y confírmala.', 'PERSONAL', 'SERVICIOS', new.id);
  elsif new.estado is distinct from old.estado then
    if actor = 'PROPIETARIO' then
      perform privado.notificar(new.prestador_id, 'PRES_' || new.estado,
        case new.estado when 'ACEPTADA' then 'Servicio confirmado' else 'Solicitud cancelada' end,
        coalesce(new.propietario_nombre, 'El dueño') || case new.estado when 'ACEPTADA' then ' aceptó tu propuesta para ' else ' canceló el servicio de ' end || mascota || '.',
        'PRESTADOR', 'SOLICITUDES', new.id);
    else
      perform privado.notificar(new.propietario_id, 'PRES_' || new.estado,
        case new.estado when 'ACEPTADA' then 'Servicio aceptado' when 'RECHAZADA' then 'Solicitud no disponible'
          when 'CANCELADA' then 'Servicio cancelado' else 'Servicio completado' end,
        new.prestador_nombre || case new.estado when 'ACEPTADA' then ' aceptó el servicio para ' when 'RECHAZADA' then ' no puede atender a '
          when 'CANCELADA' then ' canceló el servicio de ' else ' completó el servicio de ' end || mascota
          || case when new.estado = 'COMPLETADA' then '. Ya puedes calificarlo.' else '.' end,
        'PERSONAL', 'SERVICIOS', new.id);
    end if;
  end if;
  return new;
end;
$$;
drop trigger if exists solicitudes_prestador_aviso on public.solicitudes_prestador;
create trigger solicitudes_prestador_aviso after insert or update on public.solicitudes_prestador
  for each row execute function privado.avisar_solicitud_prestador();

-- Verificación del veterinario (→ veterinario)
create or replace function privado.avisar_verificacion()
returns trigger language plpgsql security definer set search_path = '' as $$
begin
  if new.verificacion is distinct from old.verificacion and new.verificacion in ('VERIFICADO', 'RECHAZADO') then
    perform privado.notificar(new.id, 'VERIFICACION_' || new.verificacion,
      case new.verificacion when 'VERIFICADO' then '¡Perfil verificado!' else 'Verificación no aprobada' end,
      case new.verificacion when 'VERIFICADO' then 'Tu perfil ya muestra la insignia Verificado ✓.' else 'No pudimos verificar tu certificado. Sube uno nuevo.' end,
      'VETERINARIO', 'PERFIL', new.id);
  end if;
  return new;
end;
$$;
drop trigger if exists veterinarios_aviso_verificacion on public.veterinarios;
create trigger veterinarios_aviso_verificacion after update of verificacion on public.veterinarios
  for each row execute function privado.avisar_verificacion();

revoke execute on function privado.avisar_cuidado(), privado.avisar_solicitud_vet(), privado.avisar_solicitud_prestador(), privado.avisar_verificacion()
  from public, anon, authenticated;

-- 3) Push con FCM: cada aviso nuevo llama a la Edge Function "notificar" (si está configurada)
create extension if not exists pg_net with schema extensions;
create table if not exists privado.config (clave text primary key, valor text not null);
revoke all on privado.config from public, anon, authenticated;

-- La función envía cada aviso una sola vez y solo si es reciente (push_enviado_en),
-- así que volver a llamarla no tiene efecto. Solo necesita el secreto FCM_SERVICE_ACCOUNT.
alter table public.notificaciones add column if not exists push_enviado_en timestamptz;
insert into privado.config (clave, valor) values ('url_notificar', 'https://ffqupbshyavmibnmjpqu.supabase.co/functions/v1/notificar')
on conflict (clave) do update set valor = excluded.valor;

create or replace function privado.enviar_push()
returns trigger language plpgsql security definer set search_path = '' as $$
declare
  url text := (select valor from privado.config where clave = 'url_notificar');
begin
  if url is not null then
    perform net.http_post(url := url, body := jsonb_build_object('id', new.id),
      headers := jsonb_build_object('Content-Type', 'application/json'));
  end if;
  return new;
exception when others then
  return new; -- el aviso queda en la bandeja aunque el push falle
end;
$$;
revoke execute on function privado.enviar_push() from public, anon, authenticated;
drop trigger if exists notificaciones_push on public.notificaciones;
create trigger notificaciones_push after insert on public.notificaciones
  for each row execute function privado.enviar_push();

-- 4) Reseñas (RF-28): 1–5 estrellas y comentario; solo con una solicitud COMPLETADA, una por solicitud
create table if not exists public.resenas (
  id uuid primary key default gen_random_uuid(),
  solicitud_id uuid not null unique,
  tipo text not null check (tipo in ('VET', 'PRESTADOR')),
  profesional_id uuid not null,
  autor_id uuid not null references public.usuarios (id) on delete cascade,
  autor_nombre text,
  estrellas smallint not null check (estrellas between 1 and 5),
  comentario text check (char_length(comentario) <= 500),
  oculta boolean not null default false,
  creado_en timestamptz not null default now(),
  actualizado_en timestamptz not null default now()
);
create index if not exists resenas_profesional on public.resenas (profesional_id, creado_en desc) where not oculta;
alter table public.resenas enable row level security;
drop policy if exists "Ver reseñas" on public.resenas;
create policy "Ver reseñas" on public.resenas for select to authenticated using (not oculta or autor_id = (select auth.uid()));
revoke all on public.resenas from anon, authenticated;
grant select on public.resenas to authenticated;

create or replace function public.guardar_resena(p_solicitud uuid, p_tipo text, p_estrellas integer, p_comentario text default null)
returns void language plpgsql security definer set search_path = '' as $$
declare
  profesional uuid;
  nuevo boolean;
begin
  if auth.uid() is null then raise exception 'Sin sesión' using errcode = '28000'; end if;
  if p_tipo = 'VET' then
    select veterinario_id into profesional from public.solicitudes_vet where id = p_solicitud and propietario_id = auth.uid() and estado = 'COMPLETADA';
  elsif p_tipo = 'PRESTADOR' then
    select prestador_id into profesional from public.solicitudes_prestador where id = p_solicitud and propietario_id = auth.uid() and estado = 'COMPLETADA';
  end if;
  if profesional is null then raise exception 'Solo se califica un servicio completado' using errcode = '42501'; end if;
  nuevo := not exists (select 1 from public.resenas where solicitud_id = p_solicitud);
  insert into public.resenas (solicitud_id, tipo, profesional_id, autor_id, autor_nombre, estrellas, comentario)
  values (p_solicitud, p_tipo, profesional, auth.uid(), (select u.nombre from public.usuarios u where u.id = auth.uid()), p_estrellas, nullif(btrim(p_comentario), ''))
  on conflict (solicitud_id) do update set estrellas = excluded.estrellas, comentario = excluded.comentario, actualizado_en = now()
  where public.resenas.autor_id = auth.uid();
  if nuevo then
    perform privado.notificar(profesional, 'RESENA_NUEVA', 'Nueva reseña: ' || repeat('★', p_estrellas),
      coalesce((select u.nombre from public.usuarios u where u.id = auth.uid()), 'Un cliente') || ' calificó tu servicio.',
      case p_tipo when 'VET' then 'VETERINARIO' else 'PRESTADOR' end, 'PERFIL', p_solicitud);
  end if;
end;
$$;

create or replace function public.eliminar_resena(p_solicitud uuid)
returns void language plpgsql security definer set search_path = '' as $$
begin
  delete from public.resenas where solicitud_id = p_solicitud and autor_id = auth.uid();
end;
$$;

-- Promedio en el perfil: lo mantiene el servidor (la app no puede escribir las calificaciones)
create or replace function privado.recalcular_calificacion()
returns trigger language plpgsql security definer set search_path = '' as $$
declare
  prof uuid;
  clase text;
  suma integer;
  cantidad integer;
begin
  if tg_op = 'DELETE' then prof := old.profesional_id; clase := old.tipo; else prof := new.profesional_id; clase := new.tipo; end if;
  select coalesce(sum(estrellas), 0), count(*) into suma, cantidad from public.resenas where profesional_id = prof and tipo = clase and not oculta;
  if clase = 'VET' then
    update public.veterinarios set rating_suma = suma, rating_cantidad = cantidad where id = prof;
  else
    update public.prestadores set rating_suma = suma, rating_cantidad = cantidad where id = prof;
  end if;
  return null;
end;
$$;
revoke execute on function privado.recalcular_calificacion() from public, anon, authenticated;
drop trigger if exists resenas_calificacion on public.resenas;
create trigger resenas_calificacion after insert or update or delete on public.resenas
  for each row execute function privado.recalcular_calificacion();

-- 5) Reportar un perfil o una reseña; con 3 reportes distintos una reseña se oculta hasta revisarla
create table if not exists public.reportes (
  id uuid primary key default gen_random_uuid(),
  autor_id uuid not null references public.usuarios (id) on delete cascade,
  tipo text not null check (tipo in ('PERFIL_VET', 'PERFIL_PRESTADOR', 'RESENA')),
  ref_id uuid not null,
  motivo text not null check (motivo in ('SPAM', 'OFENSIVO', 'FALSO', 'OTRO')),
  detalle text check (char_length(detalle) <= 500),
  estado text not null default 'PENDIENTE' check (estado in ('PENDIENTE', 'REVISADO')),
  creado_en timestamptz not null default now(),
  unique (autor_id, tipo, ref_id)
);
alter table public.reportes enable row level security;
drop policy if exists "Crear mis reportes" on public.reportes;
drop policy if exists "Ver mis reportes" on public.reportes;
create policy "Crear mis reportes" on public.reportes for insert to authenticated with check (autor_id = (select auth.uid()) and ref_id <> (select auth.uid()));
create policy "Ver mis reportes" on public.reportes for select to authenticated using (autor_id = (select auth.uid()));
revoke all on public.reportes from anon, authenticated;
grant select on public.reportes to authenticated;
grant insert (autor_id, tipo, ref_id, motivo, detalle) on public.reportes to authenticated;

create or replace function privado.ocultar_resena_reportada()
returns trigger language plpgsql security definer set search_path = '' as $$
begin
  if new.tipo = 'RESENA' and (select count(*) from public.reportes where tipo = 'RESENA' and ref_id = new.ref_id) >= 3 then
    update public.resenas set oculta = true where id = new.ref_id;
  end if;
  return null;
end;
$$;
revoke execute on function privado.ocultar_resena_reportada() from public, anon, authenticated;
drop trigger if exists reportes_ocultar on public.reportes;
create trigger reportes_ocultar after insert on public.reportes for each row execute function privado.ocultar_resena_reportada();

revoke execute on function public.registrar_dispositivo(text), public.olvidar_dispositivo(text),
  public.guardar_resena(uuid, text, integer, text), public.eliminar_resena(uuid) from public, anon;
grant execute on function public.registrar_dispositivo(text), public.olvidar_dispositivo(text),
  public.guardar_resena(uuid, text, integer, text), public.eliminar_resena(uuid) to authenticated;

do $$
begin
  if not exists (select 1 from pg_publication_tables where pubname = 'supabase_realtime' and tablename = 'notificaciones') then
    alter publication supabase_realtime add table public.notificaciones;
  end if;
end $$;

notify pgrst, 'reload schema';
