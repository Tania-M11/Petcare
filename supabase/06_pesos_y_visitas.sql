-- PETCARE · Parte 2.8: historial de peso (RF-10) y visitas veterinarias (RF-09)
-- Se puede ejecutar varias veces. Misma convención de sincronización que 03 y 05.

create table if not exists public.pesos (
  id uuid primary key,
  mascota_id uuid not null references public.mascotas (id) on delete cascade,
  fecha date not null,
  gramos integer not null check (gramos > 0 and gramos < 2000000),
  notas text check (char_length(notas) <= 300),
  registrado_por uuid,
  registrado_por_nombre text,
  registrado_por_rol text,
  registrado_en timestamptz not null default now(),
  eliminado boolean not null default false,
  actualizado_en timestamptz not null default clock_timestamp()
);
create index if not exists pesos_mascota on public.pesos (mascota_id, actualizado_en);
create index if not exists pesos_actualizado on public.pesos (actualizado_en);

create table if not exists public.visitas (
  id uuid primary key,
  mascota_id uuid not null references public.mascotas (id) on delete cascade,
  fecha date not null,
  motivo text not null check (char_length(btrim(motivo)) between 1 and 200),
  veterinario text check (char_length(veterinario) <= 100),
  clinica text check (char_length(clinica) <= 100),
  -- Lo que indicó el veterinario, escrito tal cual (la app no lo interpreta)
  tratamiento text check (char_length(tratamiento) <= 1000),
  medicamentos text check (char_length(medicamentos) <= 1000),
  notas text check (char_length(notas) <= 1000),
  -- Recordatorio creado para la próxima cita (cuidado CITA_VET)
  proxima_cita_id uuid,
  registrado_por uuid,
  registrado_por_nombre text,
  registrado_por_rol text,
  registrado_en timestamptz not null default now(),
  eliminado boolean not null default false,
  actualizado_en timestamptz not null default clock_timestamp()
);
create index if not exists visitas_mascota on public.visitas (mascota_id, actualizado_en);
create index if not exists visitas_actualizado on public.visitas (actualizado_en);

-- Autor (lo pone el servidor) y marca de tiempo de sincronización
drop trigger if exists pesos_autor on public.pesos;
create trigger pesos_autor before insert or update on public.pesos
  for each row execute function public.marcar_autor_registro();
drop trigger if exists pesos_actualizado_en on public.pesos;
create trigger pesos_actualizado_en before insert or update on public.pesos
  for each row execute function public.marcar_actualizado_en();

drop trigger if exists visitas_autor on public.visitas;
create trigger visitas_autor before insert or update on public.visitas
  for each row execute function public.marcar_autor_registro();
drop trigger if exists visitas_actualizado_en on public.visitas;
create trigger visitas_actualizado_en before insert or update on public.visitas
  for each row execute function public.marcar_actualizado_en();

-- Seguridad: por ahora el propietario (cuidadores y veterinarios llegan en 3.1 y 4.3)
alter table public.pesos enable row level security;
alter table public.visitas enable row level security;

drop policy if exists "Propietario ve pesos" on public.pesos;
drop policy if exists "Propietario crea pesos" on public.pesos;
drop policy if exists "Propietario edita pesos" on public.pesos;
create policy "Propietario ve pesos" on public.pesos for select to authenticated using (privado.es_propietario(mascota_id));
create policy "Propietario crea pesos" on public.pesos for insert to authenticated with check (privado.es_propietario(mascota_id));
create policy "Propietario edita pesos" on public.pesos for update to authenticated
  using (privado.es_propietario(mascota_id)) with check (privado.es_propietario(mascota_id));

drop policy if exists "Propietario ve visitas" on public.visitas;
drop policy if exists "Propietario crea visitas" on public.visitas;
drop policy if exists "Propietario edita visitas" on public.visitas;
create policy "Propietario ve visitas" on public.visitas for select to authenticated using (privado.es_propietario(mascota_id));
create policy "Propietario crea visitas" on public.visitas for insert to authenticated with check (privado.es_propietario(mascota_id));
create policy "Propietario edita visitas" on public.visitas for update to authenticated
  using (privado.es_propietario(mascota_id)) with check (privado.es_propietario(mascota_id));

revoke all on public.pesos from anon, authenticated;
revoke all on public.visitas from anon, authenticated;
grant select on public.pesos, public.visitas to authenticated;
grant insert (id, mascota_id, fecha, gramos, notas, registrado_en, eliminado),
      update (id, mascota_id, fecha, gramos, notas, registrado_en, eliminado)
  on public.pesos to authenticated;
grant insert (id, mascota_id, fecha, motivo, veterinario, clinica, tratamiento, medicamentos, notas, proxima_cita_id, registrado_en, eliminado),
      update (id, mascota_id, fecha, motivo, veterinario, clinica, tratamiento, medicamentos, notas, proxima_cita_id, registrado_en, eliminado)
  on public.visitas to authenticated;

notify pgrst, 'reload schema';
