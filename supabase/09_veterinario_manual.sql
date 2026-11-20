-- PETCARE · RF-04: veterinario de la mascota en modo manual (nombre, teléfono, clínica,
-- dirección, horario, observaciones). El modo "vinculado" llega con el directorio (fase 4).
-- Se puede ejecutar varias veces.
alter table public.mascotas add column if not exists veterinario jsonb
  check (veterinario is null or pg_column_size(veterinario) < 2000);

grant insert (veterinario), update (veterinario) on public.mascotas to authenticated;

notify pgrst, 'reload schema';
