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
| **Finch / apps de bienestar** (como la referencia visual) | Un personaje que acompaña, celebra y guía; tono cálido | **Michi como guía**: bienvenida, estados vacíos, celebraciones |
| **Google Calendar / Structured** | Tira de días de la semana arriba; el mes completo solo si lo pides | El calendario deja de ser pestaña y vive dentro de "Hoy" |

---

## 3. Navegación: 4 pestañas

La spec pedía 5 (Inicio · Mascotas · Calendario · Explorar · Perfil). **Calendario se integra en Hoy** porque el dueño casi siempre pregunta "¿qué toca hoy?" y solo a veces "¿qué toca el 12?".

### Modo personal (dueño y cuidador)

| Pestaña | Contenido |
|---|---|
| **Hoy** | Saludo + Michi · **tira de la semana** (toca un día → su agenda; "Ver mes" abre el calendario completo) · fila de avatares de mascotas para filtrar · tarjetas de pendientes con botón **Hecho** (1 toque) · alertas (vacuna vencida, solicitud de cuidador) · botón flotante **+ Registrar** (comida, dosis, paseo, baño, peso) |
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

## 4. Registro y componente novedoso: "Primeros pasos con Michi"

**Problema:** la app tiene cuatro tipos de usuario, pero la spec no deja elegir "tipo de cuenta" (y está bien: los roles se acumulan). Si el registro no pregunta nada, el veterinario o el paseador no saben por dónde empezar; si pregunta demasiado, el dueño se va antes de crear su mascota.

**Solución: registro corto + intención + camino guiado.**

1. **Cuenta (1 pantalla):** nombre, correo, contraseña, aceptar privacidad. Nada más.
2. **"¿Qué te trae a PetCare?"** — Michi pregunta y muestra 4 tarjetas ilustradas, **se pueden marcar varias**:
   - 🐾 Tengo mascotas
   - 🤝 Ayudo a cuidar la mascota de alguien
   - 🩺 Soy veterinario/a
   - ✂️ Ofrezco servicios (paseo, baño, cuidado temporal)
3. **Camino personalizado:** según lo marcado se arma una lista **"Primeros pasos"** en Hoy, con progreso y la pose de Michi que corresponde:
   - Mascotas → crear primera mascota → primer recordatorio → permitir notificaciones
   - Cuidar → escribir el código de la mascota → esperar aprobación
   - Veterinario → completar perfil → ubicar consultorio en el mapa → subir certificado (estado "En revisión")
   - Servicios → elegir categorías → agregar servicios y precios → horario → aparecer en el mapa
4. Cada paso se puede **saltar** y retomar desde la lista o desde Perfil. Al completar un camino, Michi celebra (pose "¡Éxito!") y la lista desaparece.

**Por qué es novedoso:** ninguna de las apps de referencia combina en una sola cuenta dueño + cuidador + profesional; aquí el registro **descubre la intención** sin encasillar y convierte la configuración en un recorrido corto y acompañado. La intención solo personaliza la guía: no otorga permisos (los perfiles profesionales se activan al completar su formulario).

**Michi como sistema de estados** (con las poses de `icons/`):

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

- **Formas** (de la referencia de bienestar): tarjetas muy redondeadas (24–32 dp), botones en píldora, fondos con degradados suaves crema→durazno o lavanda, "blobs" orgánicos detrás de Michi, tira de días como selector grande, barra inferior flotante redondeada.
- **Íconos de sección:** cuadrados redondeados de color pastel con ícono (Vacunas lavanda, Medicamentos menta, Comida durazno, Baño agua, Paseos amarillo), como en la hoja de estilo.
- **Logo:** `petcare_logo_sin_fondo.png`, versión lavanda y durazno (actualizada el 19-nov). La hoja de estilo de `icons/` hecha con IA **no** se usa como guía.
- **Acabado:** grano sutil sobre degradados y formas orgánicas (blobs) detrás de Michi, como la referencia.
- **Mascota de la app (25-nov):** **Michi**, el gato de las ilustraciones de `icons/` (11 situaciones: saluda, celebra, duerme en su cojín, agenda, despertador, higiene, veterinario, salud, documentos, leyendo y con amigos) y es la mascota de toda la app, también en el ícono del instalador. Michi dormido en su cojín con el logo es la portada y la cabecera de crear cuenta / entrar.

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
| | 0.2 | **Identidad visual**: fuentes, paleta, formas, logo, recortes de Michi, barra de **4 pestañas** con Hoy/Mascotas/Servicios/Perfil vacíos pero con estilo | ✅ |
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
| | 4.3 | Solicitudes a veterinario, vínculo y registros clínicos (`supabase/13_solicitudes_vet.sql`) | ✅ |
| | 4.4 | Perfil de prestador (categorías, servicios, horario, galería); directorio y mapa (`supabase/14_prestadores.sql`; categorías nuevas = una fila en `categorias_servicio`) | ✅ |
| | 4.5 | Cotizaciones y datos compartidos con el prestador (`supabase/15_cotizaciones.sql`) | ✅ |
| **5 Cierre** | 5.1 | Avisos (tabla `notificaciones` por triggers → Realtime, revisión cada 15 min y push FCM con la Edge Function `notificar`), reseñas con promedio del servidor y reportes (`supabase/16_notificaciones_resenas.sql`) | ✅ |
| | 5.2 | Eliminar cuenta (archivos + datos), recuperar contraseña y confirmación de correo con enlaces `petcare://auth`, versión 1.0, pruebas con varias cuentas | ✅ |

### Pendiente en paneles (no se puede hacer desde la app ni con SQL)

1. **Supabase → Authentication → URL Configuration:** agregar `petcare://auth/**` en *Redirect URLs* (los enlaces de confirmar cuenta y recuperar contraseña vuelven a la app).
2. **Supabase → Authentication → Sign In / Providers → Email:** activar *Confirm email* antes de entregar (la app ya avisa "Te enviamos un correo").
3. **Supabase → Authentication → Attack Protection:** activar *Leaked password protection* (requiere plan Pro; en el plan gratuito queda la regla de mínimo 8 caracteres).
4. **Push con la app cerrada (opcional):** crear un proyecto en Firebase con el paquete `com.udenar.petcare`, poner `google-services.json` en `app/` y guardar el JSON de una cuenta de servicio en el secreto `FCM_SERVICE_ACCOUNT` de Edge Functions. Sin esto los avisos llegan igual con la app abierta y cada 15 minutos.
5. **Storage:** borrar los 3 archivos de prueba huérfanos (2 en `mascotas`, 1 en `galeria`).


---

## 8. Fase 6 — PetCare más vivo: diseño, formularios guiados y acompañamiento inteligente

**Diagnóstico (23-nov).** La app cumple la spec, pero se siente genérica: los formularios son listas largas de campos en columnas blancas (FormMascota, FormCuidado, FormVisita, perfil de veterinario y de prestador, cotización), la información se muestra como texto plano y casi nada responde a lo que el usuario hace. Marcar campos como "opcional" no lo resuelve: el problema es la forma de preguntar.

**Principios**

1. **Una pregunta a la vez.** Cada formulario se convierte en un recorrido corto, con tarjetas grandes e ilustradas, avance automático, barra de progreso y un resumen editable al final. Nada de pantallas con 15 campos.
2. **Primero lo que ya sabemos.** Prellenar con la última vez, con la ficha, con la cámara o con lo que el usuario dice en una frase. Escribir es el último recurso.
3. **Cada acción tiene respuesta.** Animaciones cortas, vibración suave, Michi reacciona, el progreso del día se llena.
4. **Ver para entender.** Gráficos simples (peso, cumplimiento, paseos) en lugar de listas.
5. **Guiar sin recetar.** La restricción ética de la spec sigue vigente: la app nunca sugiere dosis, porciones, tratamientos, diagnósticos ni frecuencias médicas. Las "recomendaciones inteligentes" son de **organización y seguimiento** (qué falta registrar, qué vence, qué cambió) y, cuando algo cambia en la salud, la única sugerencia es **hablar con el veterinario**, con un botón para pedir la cita.

**Herramientas del mercado que se usan**

| Necesidad | Herramienta | Por qué |
|---|---|---|
| Gráficos | **Canvas de Compose** (se descartó Vico) | La app ya dibujaba así la gráfica de peso; Vico solo tenía versiones preliminares y así no se agrega otra dependencia |
| Íconos | **Material Symbols** redondeados (Apache 2.0), de un solo color | Se ven más profesionales que los 3D tipo emoji (25-nov): cada familia lleva su tono de la paleta (salud cielo, actividad naranja, organización lavanda, higiene menta, celebración sol) sobre baldosas suaves |
| Escanear carné, etiqueta de vacuna, fórmula | **ML Kit Text Recognition v2** (en el teléfono, gratis, sin internet) | Prellena producto, lote y fechas desde una foto |
| Avatar recortado de la mascota | **ML Kit Subject Segmentation** | Recorta a la mascota de la foto y la muestra como "pegatina", con el estilo de los íconos de `icons/` |
| Animaciones | **Compose Animation** + **Lottie** (confeti, logros) y movimiento suave de Michi | Celebrar sin distraer |
| Widget en el inicio del teléfono | **Jetpack Glance** | Próximas tomas con botón **Hecho** sin abrir la app |
| Clima para paseos | **Open-Meteo** (gratis, sin clave) | "Hace 29 °C: mejor pasear temprano o al atardecer" |
| Registro por voz o texto libre y resumen semanal | **Claude** (Anthropic, modelo `claude-opus-5-5`) desde una Edge Function de Supabase, con salida estructurada | Convierte "Max comió a las 8 y le di la pastilla" en registros para confirmar. Solo ordena lo que la persona dijo; nunca aconseja. **Opcional:** requiere una clave de la API de Anthropic (≈ 1 centavo de dólar por registro hablado) |
| Voz | **SpeechRecognizer** de Android | Dictar en vez de escribir |

**Partes**

| Fase | Parte | Contenido | Estado |
|---|---|---|---|
| **6 Mejora** | 6.1 | **Sistema de diseño vivo:** componente `AsistentePasos` (una pregunta por pantalla, progreso, atrás/saltar, resumen editable), tarjetas de opción grandes con ilustración, selectores rápidos (hora con chips y rueda, peso con stepper, fecha "hoy / ayer / otra"), color propio por mascota, transiciones animadas, vibración y modo oscuro. **Hoy rediseñado:** anillo de progreso del día, línea de tiempo por horas, deslizar para marcar **Hecho** y celebración al completar el día |  ✅ modo oscuro en Perfil → Apariencia (empieza en claro hasta revisarlo en el teléfono) |
| | 6.2 | **Formularios guiados del dueño:** nueva mascota como historia (foto → especie con ilustraciones → nombre → edad aproximada con slider → listo), nuevo cuidado (¿qué? → ¿cuándo? → ¿hasta cuándo? → resumen en lenguaje natural "Todos los días a las 8:00 y 19:00"), visita, peso y documento. "Igual que la última vez" y autocompletado desde el historial. Plantillas de **qué se suele registrar** por especie (sin frecuencias ni dosis sugeridas) |  ✅ mascota, cuidado, visita y documento por pasos; peso con stepper |
| | 6.3 | **La cámara llena por ti:** escanear el carné o la etiqueta de la vacuna o del medicamento (ML Kit) para prellenar el formulario; la persona confirma cada dato. Avatar de la mascota recortado automáticamente |  ✅ |
| | 6.4 | **Gráficos y progreso:** peso con tendencia y rango del último año, cumplimiento semanal por cuidado (barras), paseos (km y minutos por semana, calendario de actividad), quién registró qué en la familia. Rachas y logros suaves con Michi ("7 días sin olvidar la cena"). Resumen semanal como notificación local |  ✅ incluye resumen semanal los domingos (se apaga en Perfil) |
| | 6.5 | **Recomendaciones inteligentes (reglas explicables, sin IA):** tarjetas "siguiente paso" en Hoy y en la ficha: vacuna o desparasitación vencida o próxima, medicamento que termina pronto, varias tomas omitidas seguidas, sin peso registrado en 3 meses, cambio de peso mayor al 10 % en 30 días → "Coméntalo con tu veterinario" + **Pedir cita**; ficha incompleta (alergias, veterinario de cabecera); clima para el paseo; cumpleaños y aniversario de adopción. Cada tarjeta dice **por qué** aparece y se puede descartar |  ✅ incluye clima con Open-Meteo |
| | 6.6 | **Pospuesta por decisión de Tania (23-nov): primero recomendaciones sin IA. Cuando se retome, empezar sin costo: reconocer frases con reglas en español + dictado de Android, y el plan gratuito de Gemini solo para lo que no se entienda.** Asistente con IA (opcional): botón de micrófono en "Registrar": se dicta o escribe una frase, Claude la convierte en registros y cuidados propuestos, y la persona confirma o corrige antes de guardar. Resumen semanal en lenguaje cercano. Reglas: salida estructurada, nunca consejos médicos, nada se guarda sin confirmación, funciona solo con internet y la app sigue completa sin esta parte |  Pospuesta |
| | 6.7 | **Formularios profesionales y solicitudes por pasos:** perfil de veterinario y de prestador con **vista previa en vivo** de cómo los verán los dueños; pedir cita o cotización en 4 pasos con **fotos opcionales (hasta 3, pendiente de RF-34)**; foto de perfil del veterinario y del prestador |  ✅ |
| | 6.9 | **Rediseño con referencias (25-nov):** portada con nube de color, Michi y botón oscuro para empezar; Hoy con saludo grande, píldoras con foto de cada mascota, carrusel ("Para Venus") con sus cuidados, próximo y paseos de la semana, **"¿Cómo está hoy?"** (ánimo anotado por el dueño con su semana en puntos; tras 2 días decaído sugiere consultar a su veterinario, sin diagnosticar), accesos rápidos en círculos y barra inferior oscura. **Paseo en vivo:** tarjeta con mapa en Hoy, barra del paseo en las demás pestañas, la notificación abre el mapa y el paseo activo es a pantalla completa con distancia, cronómetro y ritmo. Al elegir veterinario o servicios en "¿Qué te trae?" se abre su formulario | ✅ (falta verlo en el teléfono) |
| | 6.8 | **Pendientes y entrega:** ocultar (no solo borrar) registros del veterinario, ícono de la categoría en los marcadores del mapa, widget de Hoy (Glance), APK de entrega firmado con R8, pruebas de interfaz más estables, accesibilidad (TalkBack, contraste, tamaños de toque) y una ronda de pruebas con 3–5 personas reales |  Parcial: ✅ ocultar registros del veterinario, marcadores con ícono, widget de Hoy. Pospuestos por decisión de Tania (24-nov): APK firmado, accesibilidad, corrida completa y ronda con personas |

**Cómo se mide que mejoró:** tiempo para registrar una mascota (meta: menos de 1 minuto), toques para marcar una toma (meta: 1), cuidados registrados por semana, y lo que digan las personas en la ronda de pruebas de 6.8.

**Alcance:** los gráficos de 6.4 amplían la sección 7 de la spec ("solo la gráfica de peso"); se incluyen por decisión de Tania del 23-nov. Lo demás respeta la spec y las restricciones éticas.
