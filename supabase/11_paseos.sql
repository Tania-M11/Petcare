-- PETCARE · Parte 3.4: paseos manuales y con GPS (RF-12)
-- Se puede ejecutar varias veces. Los registran propietario y cuidadores (sección 5).
create table if not exists public.paseos (
  id uuid primary key,
  mascota_id uuid not null references public.mascotas (id) on delete cascade,
  tipo text not null check (tipo in ('PASEO', 'JUEGO', 'EJERCICIO', 'ENTRENAMIENTO')),
  inicio timestamptz not null,
  fin timestamptz not null check (fin >= inicio),
  duracion_s integer not null check (duracion_s >= 0 and duracion_s <= 86400),
  distancia_m integer check (distancia_m >= 0 and distancia_m <= 200000),
  ruta_polyline text check (char_length(ruta_polyline) <= 100000), -- ruta GPS codificada (null si es manual)
  es_manual boolean not null default false,
  notas text check (char_length(notas) <= 500),
  registrado_por uuid,
  registrado_por_nombre text,
  registrado_por_rol text,
  registrado_en timestamptz not null default now(),
  eliminado boolean not null default false,
  actualizado_en timestamptz not null default clock_timestamp()
);
create index if not exists paseos_mascota on public.paseos (mascota_id, actualizado_en);
create index if not exists paseos_actualizado on public.paseos (actualizado_en);

drop trigger if exists paseos_autor on public.paseos;
create trigger paseos_autor before insert or update on public.paseos
  for each row execute function public.marcar_autor_registro();
drop trigger if exists paseos_actualizado_en on public.paseos;
create trigger paseos_actualizado_en before insert or update on public.paseos
  for each row execute function public.marcar_actualizado_en();

alter table public.paseos enable row level security;
drop policy if exists "Miembros ven paseos" on public.paseos;
drop policy if exists "Miembros registran paseos" on public.paseos;
drop policy if exists "Miembros editan paseos" on public.paseos;
create policy "Miembros ven paseos" on public.paseos for select to authenticated using (privado.es_miembro(mascota_id));
create policy "Miembros registran paseos" on public.paseos for insert to authenticated with check (privado.es_miembro(mascota_id));
create policy "Miembros editan paseos" on public.paseos for update to authenticated
  using (privado.es_miembro(mascota_id)) with check (privado.es_miembro(mascota_id));

revoke all on public.paseos from anon, authenticated;
grant select on public.paseos to authenticated;
grant insert (id, mascota_id, tipo, inicio, fin, duracion_s, distancia_m, ruta_polyline, es_manual, notas, registrado_en, eliminado),
      update (id, mascota_id, tipo, inicio, fin, duracion_s, distancia_m, ruta_polyline, es_manual, notas, registrado_en, eliminado)
  on public.paseos to authenticated;

-- Cambios en vivo entre miembros
do $$
begin
  if not exists (select 1 from pg_publication_tables where pubname = 'supabase_realtime' and schemaname = 'public' and tablename = 'paseos') then
    alter publication supabase_realtime add table public.paseos;
  end if;
end $$;

notify pgrst, 'reload schema';
