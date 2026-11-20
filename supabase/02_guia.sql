-- PETCARE · Parte 1.2: "¿Qué te trae a PetCare?" y Primeros pasos
-- guia_respondida_en: cuándo respondió (null = aún no se le preguntó; se muestra tras registrarse)
-- pasos_omitidos: pasos de la guía que decidió saltar ('GUIA_CERRADA' = ya no mostrar la tarjeta)

alter table public.usuarios
  add column guia_respondida_en timestamptz,
  add column pasos_omitidos text[] not null default '{}'
    check (cardinality(pasos_omitidos) <= 30);

grant update (guia_respondida_en, pasos_omitidos) on public.usuarios to authenticated;
