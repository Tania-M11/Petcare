# PETCARE — Plan de producto y desarrollo

> Único documento de guía del proyecto. Parte de la idea en `Downloads\petcare_spec (1).md` (v4) y la reorganiza pensando en quien usa la app.
> Si algo de aquí contradice la spec, vale este plan.

---

## 1. Para quién es y qué necesita cada uno

| Usuario | Qué quiere lograr | Qué lo frustra hoy | Qué le da PetCare |
|---|---|---|---|
| **Dueño/a** | Que a su mascota no le falte nada: comida, medicina, vacunas, baño | Olvidos, papeles perdidos, no saber si alguien ya le dio la pastilla | Recordatorios que suenan sin internet, "Hoy" con 1 toque para marcar, carné digital con QR |
| **Cuidador/a** (familiar, vecino, roomie) | Ayudar sin equivocarse | No saber horarios ni qué ya se hizo | Ve el plan de la mascota y registra lo que hace; el dueño ve "Dado por Ana, 8:05" |
| **Veterinario/a** | Conseguir pacientes y tener su historia a mano | Dueños que llegan sin carné ni historial | Perfil verificado, solicitudes, ficha clínica de sus pacientes vinculados |
| **Prestador/a** (paseo, baño, cuidado temporal) | Clientes cerca y agenda ordenada | Depender de redes sociales y WhatsApp sueltos | Perfil con servicios y precios, mapa, cotizaciones y reseñas |

**Prioridad:** el dueño es el usuario principal (sin dueños no hay clientes para los profesionales). Todo el diseño parte de su día a día.

---

## 2. Qué hacen bien las apps del mercado y qué tomamos

| Referente | Lo que hace bien | Lo que adopta PetCare |
|---|---|---|
| **11pets / Pawtrack** (registro de salud) | Todo por mascota: vacunas, medicinas, peso, documentos | Detalle de mascota organizado en secciones; "carné" digital |
| **PetDesk** (dueño ↔ veterinaria) | Recordatorios y citas en un mismo lugar | Las citas aceptadas caen solas en la agenda del dueño |
| **Rover / Wag / DogHero** (marketplace) | Buscar por servicio y zona, perfiles con reseñas, solicitud → aceptación | Directorio con filtros, tarjeta resumida, flujo de cotización |
| **Airbnb** (huésped/anfitrión) | **Una sola cuenta** con botón "Cambiar a modo anfitrión" | Modos personal/profesional desde Perfil, sin cerrar sesión |
| **Finch / apps de bienestar** (como la referencia visual) | Un personaje que acompaña, celebra y guía; tono cálido | **Chopper como guía**: bienvenida, estados vacíos, celebraciones |
| **Google Calendar / Structured** | Tira de días de la semana arriba; el mes completo solo si lo pides | El calendario deja de ser pestaña y vive dentro de "Hoy" |

---

## 3. Navegación: 4 pestañas

La spec pedía 5 (Inicio · Mascotas · Calendario · Explorar · Perfil). **Calendario se integra en Hoy** porque el dueño casi siempre pregunta "¿qué toca hoy?" y solo a veces "¿qué toca el 12?".

### Modo personal (dueño y cuidador)

| Pestaña | Contenido |
|---|---|
| **Hoy** | Saludo + Chopper · **tira de la semana** (toca un día → su agenda; "Ver mes" abre el calendario completo) · fila de avatares de mascotas para filtrar · tarjetas de pendientes con botón **Hecho** (1 toque) · alertas (vacuna vencida, solicitud de cuidador) · botón flotante **+ Registrar** (comida, dosis, paseo, baño, peso) |
| **Mascotas** | Tarjetas de mis mascotas y "Cuidas a…" · botón **+** con: Agregar mascota · Cuidar con código · Escanear QR · Detalle por mascota en secciones: Resumen (carné + QR) · Salud · Cuidados · Historial · Documentos · Personas (cuidadores y veterinarios) |
| **Servicios** | Veterinarios y Prestadores (lista ↔ mapa) · **Mis solicitudes** arriba cuando hay alguna activa |
| **Perfil** | Mis datos · **Tarjeta "Trabaja con PetCare"** para activar perfil veterinario o prestador · **Cambiar a modo profesional** · Notificaciones por mascota · Acerca de (créditos) · Cerrar sesión / Eliminar cuenta |

### Modo profesional (veterinario o prestador; mismo esqueleto)

| Pestaña | Contenido |
|---|---|
| **Solicitudes** | Bandeja por estado (Nuevas · Cotizadas · Aceptadas) |
| **Agenda** | Tira semanal + citas/servicios aceptados |
| **Pacientes** / **Clientes** | Vet: fichas clínicas vinculadas. Prestador: historial de servicios |
| **Perfil** | Mi perfil público, servicios/precios u horarios, verificación (vet), reseñas · **Volver a modo personal** |

Si alguien es veterinario **y** prestador, al cambiar de modo elige cuál (chip en la barra superior).

---

## 4. Registro y componente novedoso: "Primeros pasos con Chopper"

**Problema:** la app tiene cuatro tipos de usuario, pero la spec no deja elegir "tipo de cuenta" (y está bien: los roles se acumulan). Si el registro no pregunta nada, el veterinario o el paseador no saben por dónde empezar; si pregunta demasiado, el dueño se va antes de crear su mascota.

**Solución: registro corto + intención + camino guiado.**

1. **Cuenta (1 pantalla):** nombre, correo, contraseña, aceptar privacidad. Nada más.
2. **"¿Qué te trae a PetCare?"** — Chopper pregunta y muestra 4 tarjetas ilustradas, **se pueden marcar varias**:
   - 🐾 Tengo mascotas
   - 🤝 Ayudo a cuidar la mascota de alguien
   - 🩺 Soy veterinario/a
   - ✂️ Ofrezco servicios (paseo, baño, cuidado temporal)
3. **Camino personalizado:** según lo marcado se arma una lista **"Primeros pasos"** en Hoy, con progreso y la pose de Chopper que corresponde:
   - Mascotas → crear primera mascota → primer recordatorio → permitir notificaciones
   - Cuidar → escribir el código de la mascota → esperar aprobación
   - Veterinario → completar perfil → ubicar consultorio en el mapa → subir certificado (estado "En revisión")
   - Servicios → elegir categorías → agregar servicios y precios → horario → aparecer en el mapa
4. Cada paso se puede **saltar** y retomar desde la lista o desde Perfil. Al completar un camino, Chopper celebra (pose "¡Éxito!") y la lista desaparece.

**Por qué es novedoso:** ninguna de las apps de referencia combina en una sola cuenta dueño + cuidador + profesional; aquí el registro **descubre la intención** sin encasillar y convierte la configuración en un recorrido corto y acompañado. La intención solo personaliza la guía: no otorga permisos (los perfiles profesionales se activan al completar su formulario).

**Chopper como sistema de estados** (con las poses de `icons/`):

| Situación | Pose |
|---|---|
| Bienvenida / registro | Saludando · Organizadora (portapapeles) |
| Lista vacía (sin mascotas, sin solicitudes) | Con mochila, invitando a agregar |
| Todo lo de hoy hecho | ¡Éxito! |
| Recordatorio / notificación | Con calendario · con cuenco de comida |
| Sección veterinaria y verificación | Doctor con bolso médico |
| Cuidadores / compartir | Abrazando a sus amigos |

---

## 5. Identidad visual

- **Tipografía:** **Kalnia** (títulos) · **Quicksand** (textos, botones y etiquetas). Ambas de Google Fonts, incluidas en `res/font`.
- **Paleta** (cálida y natural, de la referencia de bienestar y del logo lavanda/durazno; tokens en `ui/theme/Color.kt`):

| Rol | Color |
|---|---|
| Fondo | Crema `#FBF6F1` · tarjetas blancas |
| Acción principal | Naranja `#F08A4B` (degradado desde `#F7B183`) · Durazno `#FCDCC6` para fondos activos |
| Del logo | Lavanda `#A98EE0` → `#8F78D6` · Durazno `#FCB484` |
| Acentos | Cielo `#7BA7CF` → `#5B89BA` (veterinarios) · Sol `#F5D565` (cuidar, prestadores) |
| Apoyo | Menta `#9FD3B6` |
| Texto | Tinta `#2D2433` · secundario `#8C8394` |

- **Formas** (de la referencia de bienestar): tarjetas muy redondeadas (24–32 dp), botones en píldora, fondos con degradados suaves crema→durazno o lavanda, "blobs" orgánicos detrás de Chopper, tira de días como selector grande, barra inferior flotante redondeada.
- **Íconos de sección:** cuadrados redondeados de color pastel con ícono (Vacunas lavanda, Medicamentos menta, Comida durazno, Baño agua, Paseos amarillo), como en la hoja de estilo.
- **Logo:** `petcare_logo_sin_fondo.png`, versión lavanda y durazno (actualizada el 19-nov). La hoja de estilo de `icons/` hecha con IA **no** se usa como guía.
- **Acabado:** grano sutil sobre degradados y formas orgánicas (blobs) detrás de Chopper, como la referencia.
- **Créditos:** Tony Tony Chopper © Eiichiro Oda / Shueisha / Toei Animation; uso académico sin ánimo de lucro, con créditos en "Acerca de" y en el README. No se publica en tiendas con el personaje.

---

## 6. Decisiones técnicas

- **Stack:** Kotlin 2.4 + Compose (Material 3), Hilt, Room, **Supabase** (Auth, Postgres + RLS, Storage, Realtime, Edge Functions), WorkManager, MapLibre + OpenFreeMap, ZXing/ML Kit, Coil. minSdk 26.
- **Sin conexión:** Supabase no guarda datos en el teléfono → la agenda vive en **Room** y una cola sube los cambios al volver la red. Directorios y solicitudes requieren internet.
- **Convención de sincronización** (toda tabla offline): `id` uuid creado en el teléfono, `actualizado_en` puesto por trigger del servidor (cursor de descarga, con 2 min de margen), `eliminado` en vez de borrar; en Room `pendiente` + `cambioLocal`. Cada tabla nueva se registra con `@IntoSet TablaSincronizable` en `ModuloSincronizacion`.
- **SQL en el panel:** los scripts deben poder ejecutarse varias veces (`if not exists`, `drop … if exists`) y terminar con `notify pgrst, 'reload schema'`.
- **Funciones públicas (RPC) con `security definer`:** el revisor de Supabase las marca como aviso; son intencionales porque cada una verifica por dentro quién la llama (dueño, solicitante o la propia persona).
- **Seguridad en el servidor:** permisos por rol con RLS; cambios de estado de solicitudes con funciones RPC; promedio de reseñas por trigger; verificación veterinaria solo editable desde el panel.
- **Privacidad:** datos de gestión de la mascota (código, QR) en tabla solo del propietario; el prestador solo ve lo que el dueño comparte en la solicitud y lo pierde al terminar.
- **Archivos:** Supabase Storage (buckets privados).
- **Push:** Edge Function disparada por la base de datos + FCM solo como transporte.
- **Alarmas:** AlarmManager exactas con respaldo si Android niega el permiso; se reprograman al reiniciar.
- **Ética:** la app nunca sugiere dosis, porciones, alimentos ni frecuencias; solo guarda lo que escribe el usuario o el veterinario.
- **Ajustes a la spec:** sin migración v3; peso opcional; frecuencia "personalizada" = días + horas; el cuidador no ve visitas ni documentos y la cita veterinaria en su agenda solo dice fecha y hora; se agregan recuperar contraseña, eliminar cuenta, política de privacidad y reportar perfil/reseña.

---

## 7. Plan por partes

Supabase: proyecto `ffqupbshyavmibnmjpqu` (Claude tiene acceso directo desde el 19-nov y aplica los `supabase/NN_*.sql`; funciones auxiliares de RLS en el esquema `privado`); confirmación de correo apagada durante el desarrollo (revisar antes de entregar).

Reglas: una parte por sesión · compila y se prueba en el teléfono antes de seguir · un commit por parte en `github.com/Tania-M11/Petcare` · pruebas instrumentadas en `app/src/androidTest` que Claude corre en el teléfono conectado.

| Fase | Parte | Contenido | Estado |
|---|---|---|---|
| **0 Base** | 0.1 | Proyecto Android, Hilt, Compose, navegación provisional, pruebas instrumentadas | ✅ |
| | 0.2 | **Identidad visual**: fuentes, paleta, formas, logo, recortes de Chopper, barra de **4 pestañas** con Hoy/Mascotas/Servicios/Perfil vacíos pero con estilo | ✅ |
| | 0.3 | Supabase `petcare` + tabla de usuarios (`supabase/01_base.sql`) + cliente en la app | ✅ |
| **1 Cuenta** | 1.1 | Bienvenida, registro (con política de privacidad), inicio y cierre de sesión | ✅ |
| | 1.2 | **"¿Qué te trae a PetCare?"** + lista Primeros pasos (`supabase/02_guia.sql`) | ✅ |
| **2 Mascotas y agenda** | 2.1 | Room + sincronización con Supabase; tabla `mascotas` y `eliminar_mi_cuenta()` (`supabase/03_mascotas_sync.sql`) | ✅ |
| | 2.2 | Agregar/editar mascota (foto, especie, código) y lista de mascotas; fotos en Storage y códigos (`supabase/04_fotos_y_codigos.sql`) | ✅ |
| | 2.3 | Motor de cuidados (frecuencias, próxima fecha) con pruebas; cuidados y registros sincronizados (`supabase/05_cuidados.sql`) | ✅ |
| | 2.4 | Alarmas y notificaciones con acciones Hecho/Posponer/Omitir; reprogramación tras reinicio, cambio de hora y sincronización | ✅ |
| | 2.5 | Comidas, higiene y actividades personalizadas | ✅ |
| | 2.6 | Medicamentos, vacunas y desparasitación (mismo formulario por clase de cuidado) | ✅ |
| | 2.7 | **Hoy** completo: tira semanal, pendientes con un toque, filtro por mascota, alertas, calendario mensual, + Nuevo cuidado | ✅ |
| | 2.8 | Ficha con pestañas Resumen · Cuidados · Salud · Historial: estado de cuidado, peso con gráfica, visitas con próxima cita (`supabase/06_pesos_y_visitas.sql`) | ✅ |
| **3 Compartir** | 3.1 | Cuidar con código, aprobación, sección Personas, regenerar/desactivar código, permisos por rol (`supabase/07_cuidadores.sql`, prueba real con dos cuentas) | ✅ |
| | 3.2 | Sincronización en vivo entre miembros (Realtime, `supabase/08_tiempo_real.sql`) y recordatorios por mascota en cada teléfono | ✅ |
| | 3.3 | Carné con QR configurable y escáner; veterinario manual con Llamar (RF-04, `09_veterinario_manual.sql`); documentos imagen/PDF (`10_documentos.sql`) | ✅ |
| | 3.4 | Paseos manuales y con GPS: servicio en primer plano, ruta en MapLibre/OpenFreeMap, polilínea (`supabase/11_paseos.sql`) | ✅ |
| **4 Profesionales** | 4.1 | "Trabaja con PetCare", cambio de modo y esqueleto profesional | ✅ |
| | 4.2 | Perfil veterinario + verificación; directorio y mapa (`supabase/12_veterinarios.sql`; el panel cambia `verificacion` a VERIFICADO/RECHAZADO) | ✅ |
| | 4.3 | Solicitudes a veterinario, vínculo y registros clínicos | |
| | 4.4 | Perfil de prestador (categorías, servicios, horario, galería); directorio y mapa | |
| | 4.5 | Cotizaciones y datos compartidos con el prestador | |
| **5 Cierre** | 5.1 | Notificaciones push y reseñas (+ reportar) | |
| | 5.2 | Eliminar cuenta, recuperar contraseña (pospuesta por Tania), confirmación de correo y protección de contraseñas filtradas, pulido y pruebas con varias cuentas | |
