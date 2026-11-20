# PETCARE — Análisis, mejoras y plan de desarrollo (v2: Supabase)

> Basado en `docs/petcare_spec_v4.md`. Actualizado el 19-nov-2026: **el backend pasa de Firebase a Supabase**.
> Cuando este plan y la especificación no coincidan, **vale este plan** (sección 3 = cambios a la spec).

---

## 1. Qué se quiere construir

Una app Android (Kotlin + Compose) con **tres productos dentro de uno**:

| Bloque | Qué es | RF | Peso aprox. |
|---|---|---|---|
| **A. Agenda de cuidados** | Mascotas, salud, recordatorios offline, Hoy/calendario/historial, peso, paseos GPS, documentos, QR | RF-01 a RF-19 | ~45 % |
| **B. Cuidado compartido** | Cuidadores por código, permisos por rol, sincronización multi-dispositivo | RF-20, RF-21 | ~15 % |
| **C. Marketplace de profesionales** | Veterinarios verificados + prestadores, directorios con mapa, solicitudes/cotizaciones, reseñas, push | RF-22 a RF-36 | ~40 % |

El valor central está en **A** (que el recordatorio suene y quede registrado quién lo hizo). El plan construye A → B → C y cada fase deja una app usable.

---

## 2. Stack definitivo

| Capa | Tecnología |
|---|---|
| Lenguaje/UI | Kotlin 2.4 + Jetpack Compose (Material 3), minSdk 26, compileSdk 37 |
| Arquitectura | Clean Architecture + MVVM (ui / domain / data), un solo módulo `:app` con paquetes |
| DI | Dagger Hilt 2.60 + `hilt-lifecycle-viewmodel-compose` |
| BD local | Room (fuente de verdad offline de la agenda y de las alarmas) |
| Backend | **Supabase** (plan gratuito): Auth, Postgres + RLS, Storage, Realtime, Edge Functions |
| Cliente | `supabase-kt` 3.8 (auth, postgrest, storage, realtime) + Ktor OkHttp |
| Sincronización | Cola de cambios en Room + WorkManager (con red), Realtime para recibir cambios |
| Alarmas | AlarmManager (exactas con respaldo) + receptor `BOOT_COMPLETED` / cambio de hora |
| Push | Firebase Cloud Messaging **solo como transporte** + Edge Function de Supabase disparada por la base de datos |
| Archivos | **Supabase Storage** (buckets privados con políticas); se elimina el esquema de Blobs en chunks |
| Mapas | MapLibre Native 13.7 + OpenFreeMap (sin clave); distancias con **PostGIS** |
| Ubicación | Fused Location Provider (Play Services) |
| QR | ZXing core (generar) + ML Kit Code Scanner (leer) |
| Imágenes | Coil 3 + compresión previa |
| Gráficas | Canvas de Compose |

---

## 3. Cambios a la especificación

### 3.1 Por el paso a Supabase

| Spec v4 (Firebase) | Ahora (Supabase) | Ganancia |
|---|---|---|
| Sección 2.3: migración de cuentas v3 | **Se elimina.** Proyecto nuevo, sin datos previos | Un módulo menos |
| Firestore + reglas | Postgres con **RLS**; las transiciones de estado se hacen con **funciones RPC** (`security definer`) | Reglas verificables en SQL, transacciones reales |
| Cuidador no debe ver campos de gestión (imposible en Firestore) | Tabla aparte `mascota_gestion` (código, config QR) solo del propietario | Resuelto |
| `petCodes` con solo `get` | RPC `buscar_mascota_por_codigo(codigo)` que devuelve nombre, miniatura y especie | Imposible listar códigos |
| Límite "5 solicitudes pendientes" y "24 h tras rechazo" | Validado dentro de la RPC `solicitar_cuidado` | Anti-abuso real, no solo en el cliente |
| Agregados de reseñas por transacción del cliente | **Trigger** en la tabla de reseñas | El cliente no puede falsearlos |
| Verificación veterinaria protegida por reglas | Columna `verificacion` sin permiso de UPDATE para `authenticated`; se cambia en el panel de Supabase | Igual de simple, más seguro |
| Archivos Blob en chunks (Firestore) | Supabase Storage, buckets privados: `mascotas`, `documentos`, `certificados`, `galeria`, `solicitudes` | Sin límite de 1 MiB, URLs firmadas |
| Cloudflare Worker para push | **Edge Function** `notificar` disparada por *Database Webhook* al insertar/actualizar solicitudes | Un servicio menos; el cliente no decide a quién notifica |
| Orden por distancia en el cliente | PostGIS: `ST_DWithin` / `<->` en la RPC del directorio | Consultas por cercanía reales |
| Persistencia offline de Firestore | **Supabase no trabaja sin conexión** → Room es la fuente local de la agenda y una cola sube los cambios | RNF-03 se cumple de forma explícita |
| Conflictos "última escritura" | Columna `actualizado_en` + upsert solo si es más reciente | Igual a la spec |

### 3.2 Problemas técnicos que siguen vigentes

| # | Problema | Solución |
|---|---|---|
| T2 | La cita veterinaria (`CITA_VET`) es un evento que el cuidador ve; la visita no | El evento guarda solo fecha, hora y "Cita veterinaria"; el detalle clínico va en `visitas` |
| T4 | Alarmas exactas denegadas por defecto en Android 14+ | Pedir `SCHEDULE_EXACT_ALARM` con explicación; si se niega, `setAndAllowWhileIdle`. Guía de batería para Xiaomi/Samsung |
| T6 | Varios dispositivos suenan por la misma toma | Al llegar el registro por Realtime/sync, se cancela la notificación de esa toma |
| T8 | Peso obligatorio vs. opcional para peces | Peso opcional para todas las especies |
| T9 | Frecuencia `PERSONALIZADO` sin definir | = días de la semana + horas |
| T10 | "Registros compartidos" del cuidador sin definir | Todo registro de cuidado, paseo y peso; nunca visitas ni documentos |
| T11 | Plan gratuito: máximo 2 proyectos activos y pausa tras 7 días sin uso | Pausar un proyecto terminado al crear `petcare` y abrir la app al menos una vez por semana durante el desarrollo |

### 3.3 Simplificaciones

- **S2** Un solo **shell profesional** (Solicitudes · Agenda · Clientes/Pacientes · Mi perfil) para los modos veterinario y prestador.
- **S3** Un solo **motor de estados** (tabla de transiciones) para solicitudes a veterinarios y a prestadores; tablas separadas.
- **S4** Ciudades de una **lista cerrada** (municipios de Colombia, DANE).

### 3.4 Agregados

| # | Qué | Por qué |
|---|---|---|
| F1 | Eliminar cuenta (RPC que borra datos propios + Edge Function que borra el usuario de Auth) | Requisito de Google Play |
| F2 | Política de privacidad y aceptación al registrarse | Ley 1581 de 2012 |
| F3 | Recuperar contraseña (correo de Supabase Auth + enlace profundo) | Evita cuentas bloqueadas |
| F4 | Reportar perfil o reseña (tabla `reportes`, revisión en el panel) | Mínimo contra abuso en un directorio público |
| F5 | Bienvenida: crear primera mascota → primer recordatorio → permiso de notificaciones explicado | Sin recordatorios la app no aporta |
| F6 | Plantillas de **nombres** de cuidados (Baño, Desayuno…), sin frecuencias ni cantidades | Pocos toques sin violar la restricción ética |

### 3.5 Identidad visual

Se usa al reno Chopper (*One Piece*, © Eiichiro Oda / Shueisha / Toei Animation) como mascota de la app, **solo con fines académicos y no comerciales**, con los créditos en el README y en la pantalla "Acerca de". El crédito no equivale a una licencia: la app no se publica en tiendas con el personaje. La identidad completa entra en la parte 0.4.

---

## 4. Plan por fases y partes

Reglas de trabajo:
- **Una parte por sesión**; compila antes de pasar a la siguiente; **un commit por parte** en `github.com/Tania-M11/Petcare`.
- Cada parte termina con: RF cubiertos + cómo probarla. Las pruebas en el teléfono las hace Tania.
- El SQL vive en `supabase/NN_nombre.sql` y se aplica como migración.

### Fase 0 — Base
| Parte | Contenido | Estado |
|---|---|---|
| 0.1 | Proyecto Android: Gradle con catálogo, Hilt, Compose, Navigation, paquetes Clean, pantalla provisional | ✅ |
| 0.2 | Proyecto Supabase `petcare`, `01_base.sql` (perfiles de usuario + RLS), cliente `supabase-kt` con URL/clave en `local.properties` | |
| 0.3 | Componentes base: estados carga/vacío/error, selector de especie, fecha/hora, compresión de imágenes | |
| 0.4 | Tema y design system con la identidad visual de Tania | |

### Fase 1 — Agenda personal
| Parte | Contenido | RF |
|---|---|---|
| 1.1 | Registro, inicio/cierre de sesión, recuperar contraseña, aceptación de privacidad | RF-01, F2, F3 |
| 1.2 | Room + **motor de sincronización** (cola + WorkManager + descarga inicial) | RF-21 (base) |
| 1.3 | CRUD de mascotas con foto (Storage) y código único | RF-02 |
| 1.4 | Salud básica y veterinario manual con Llamar | RF-03, RF-04 |
| 1.5 | **Motor `CareEvent`/`CareLog`** con pruebas unitarias de frecuencias | 9.1 |
| 1.6 | **Alarmas** locales, reinicio, cambio de hora, canales, acciones en la notificación | RF-14 |
| 1.7 | Comidas recordatorio e higiene | RF-11, RF-08 |
| 1.8 | Medicamentos con dosis del día | RF-07 |
| 1.9 | Vacunas y desparasitación | RF-05, RF-06 |
| 1.10 | Actividades personalizadas y visitas veterinarias | RF-13, RF-09 |
| 1.11 | **Hoy** + bienvenida | RF-15, F5 |
| 1.12 | Calendario e historial | RF-15 |
| 1.13 | Estado de cuidado y peso con gráfica | RF-16, RF-10 |

### Fase 2 — Cuidado compartido, documentos y QR
| Parte | Contenido | RF |
|---|---|---|
| 2.1 | RLS completa de mascotas + **pruebas SQL de acceso denegado** por rol | 9.4 |
| 2.2 | Cuidar por código (RPC), solicitudes, aprobación, "Cuidas a…" | RF-20 |
| 2.3 | Miembros: revocar, salir, regenerar/invalidar código; UI limitada del cuidador; notificaciones por mascota | RF-20, RF-14 |
| 2.4 | Realtime entre miembros y cancelación de alarmas ya resueltas | RF-21, T6 |
| 2.5 | Documentos en Storage con caché local | RF-17 |
| 2.6 | Ficha resumen, QR con privacidad y escáner | RF-18, RF-19 |
| 2.7 | Eliminar cuenta | F1 |

### Fase 3 — Mapas y paseos
| 3.1 | MapLibre + OpenFreeMap y componente de mapa reutilizable |
|---|---|
| 3.2 | Paseos manuales (RF-12) |
| 3.3 | Paseo GPS con servicio en primer plano y polilínea (RF-12) |

### Fase 4 — Veterinarios
| Parte | Contenido | RF |
|---|---|---|
| 4.1 | Activación de perfiles, selector de modo y shell profesional | RF-30, S2 |
| 4.2 | Componentes compartidos de directorio, perfil y selector de ubicación | sección 7 |
| 4.3 | Perfil de veterinario y verificación con certificado | RF-22, RF-23 |
| 4.4 | Directorio y mapa (PostGIS) | RF-24 |
| 4.5 | Motor de estados + solicitud a veterinario | RF-25, S3 |
| 4.6 | Vínculo veterinario–mascota, panel y registros clínicos | RF-26, RF-27 |

### Fase 5 — Prestadores
| Parte | Contenido | RF |
|---|---|---|
| 5.1 | Categorías y perfil de prestador con servicios y precios | RF-31, RF-32 |
| 5.2 | Horarios, disponibilidad y galería | RF-31 |
| 5.3 | Directorio y mapa con tarjeta resumida | RF-33 |
| 5.4 | Solicitud de cotización con datos compartidos y fotos | RF-34, RF-36 |
| 5.5 | Bandeja del prestador: cotizar, proponer fecha, aceptar, completar | RF-35 |
| 5.6 | RLS de solicitudes y pruebas de acceso que expira | RF-36 |

### Fase 6 — Push y reseñas
| 6.1 | Firebase (solo FCM) + tabla de tokens + Edge Function `notificar` + webhooks (RF-29) |
|---|---|
| 6.2 | Canales y apertura en el modo correcto (RF-29, RF-30) |
| 6.3 | Reseñas con trigger de agregados + reportar (RF-28, F4) |

### Fase 7 — Cierre
| 7.1 | Pruebas con cuatro cuentas + una con todos los roles; modo avión; reinicio; acceso denegado |
|---|---|
| 7.2 | Pulido visual, accesibilidad y revisión de textos contra la restricción ética |

**Total:** 38 partes en 8 fases.
