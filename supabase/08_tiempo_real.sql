-- PETCARE · Parte 3.2: cambios en vivo entre miembros (Supabase Realtime)
-- Realtime respeta las reglas RLS: cada teléfono solo recibe lo que puede ver.
-- Se puede ejecutar varias veces.
do $$
declare t text;
begin
  foreach t in array array['mascotas', 'eventos_cuidado', 'registros_cuidado', 'pesos', 'miembros_mascota', 'solicitudes_cuidado'] loop
    if not exists (select 1 from pg_publication_tables where pubname = 'supabase_realtime' and schemaname = 'public' and tablename = t) then
      execute format('alter publication supabase_realtime add table public.%I', t);
    end if;
  end loop;
end $$;
