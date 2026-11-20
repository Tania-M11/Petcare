# PetCare

App Android para organizar los cuidados de mascotas de cualquier especie: recordatorios que funcionan sin internet, historial, documentos, QR de identificación, cuidadores autorizados por código y un directorio de veterinarios y prestadores de servicios.

Proyecto académico — Universidad de Nariño.

## Documentación

- [`PLAN_PETCARE.md`](PLAN_PETCARE.md): análisis, cambios a la especificación, stack y **plan por partes** (estado de avance).
- [`docs/petcare_spec_v4.md`](docs/petcare_spec_v4.md): especificación funcional original (v4). Donde difiera, vale el plan.

## Stack

Kotlin · Jetpack Compose · Hilt · Room · Supabase (Auth, Postgres + RLS, Storage, Realtime, Edge Functions) · WorkManager · MapLibre + OpenFreeMap · ZXing / ML Kit.

## Compilar

1. Android Studio (JDK incluido) y Android SDK 37.
2. Crear `local.properties` con `sdk.dir`, y desde la parte 0.2:
   ```
   SUPABASE_URL=https://<ref>.supabase.co
   SUPABASE_PUBLISHABLE_KEY=sb_publishable_...
   ```
3. `./gradlew assembleDebug`

## Créditos y derechos de autor

- **Tony Tony Chopper** y el universo de *One Piece* © Eiichiro Oda / Shueisha / Toei Animation. El personaje se usa como mascota ilustrativa **solo con fines académicos y sin ánimo de lucro**; PetCare no está afiliado ni respaldado por sus titulares, y la app no se distribuye comercialmente con el personaje.
- Mapas: © OpenFreeMap © OpenMapTiles, datos de © colaboradores de OpenStreetMap.
