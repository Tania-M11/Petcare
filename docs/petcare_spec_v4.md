# PETCARE — Especificación (v4, para IA)

> **Uso:** fuente única de verdad. Todo lo descrito aquí forma parte del producto y se implementa completo. Los IDs (RF, RNF) son estables: los de la v3 se conservan y los nuevos continúan la numeración. Lo que no esté aquí y no esté en la sección 8 ("Fuera de alcance") es una duda: pregunta antes de asumir.

---

## 1. Resumen

App móvil **Android nativo** (español, Colombia) para organizar los cuidados de mascotas de **cualquier especie**: recordatorios que funcionan sin internet, historial, documentos, QR de identificación y cuidadores autorizados mediante el código de la mascota. Incluye además un ecosistema de **profesionales**: **veterinarios** (con certificado y verificación) y **prestadores de servicios** (paseadores, baño y peluquería, cuidado temporal) que publican perfil y ubicación, reciben solicitudes, envían cotizaciones y reciben reseñas de los propietarios. Los pagos ocurren fuera de la app.

**Problema que resuelve:** olvido y mala organización de vacunas, medicamentos, higiene, comidas y citas; falta de historial; dificultad para coordinar a varias personas que cuidan la misma mascota; dificultad para encontrar, comparar y contratar veterinarios y prestadores de servicios.

---

## 2. Identidad, perfiles y roles

### 2.1 Principio
Una **identidad de usuario** es la base. Los roles y perfiles se manejan de forma independiente y **acumulable**: una misma cuenta puede ser propietaria, cuidadora, prestadora de servicios y veterinaria al mismo tiempo. No existen tipos de cuenta mutuamente excluyentes.

| Elemento | Naturaleza | Cómo se obtiene |
|---|---|---|
| `USUARIO` | Identidad base (Firebase Auth + `users/{uid}`) | Registro |
| `PROPIETARIO` | Rol **por mascota** | Se es propietario de toda mascota que se registra (no requiere activación) |
| `CUIDADOR` | Rol **por mascota** | El propietario aprueba una solicitud hecha con el código de la mascota |
| `PRESTADOR_SERVICIOS` | **Perfil profesional** opcional | El usuario lo activa desde su perfil y completa el formulario |
| `VETERINARIO` | **Perfil profesional** opcional, con verificación propia | El usuario lo activa desde su perfil, completa el formulario y sube su certificado |

- El cuidador **no** es un prestador de servicios contratado, ni viceversa. Son conceptos y permisos distintos.
- La verificación del veterinario (certificado, estados, insignia) es **independiente** de cualquier información o verificación de prestadores; no comparten campos, estados ni insignias.
- Un mismo usuario no puede contratarse a sí mismo: se bloquean solicitudes donde `ownerId == providerId/vetId`.

### 2.2 Documento de usuario
```
users/{uid} {
  nombre, correo, telefono?, whatsapp?, fotoMiniatura?,
  perfiles: { prestador: bool, veterinario: bool },
  schemaVersion: 2,
  tipoCuenta?  // campo legado v3 (solo lectura, ver 2.3)
  creadoEn, migradoEn?
}
```
- `telefono` y `whatsapp` son opcionales y solo se comparten con terceros si el propietario lo decide al solicitar un servicio (RF-36).
- Si `perfiles.prestador == true` debe existir `providers/{uid}`; si `perfiles.veterinario == true` debe existir `vets/{uid}`. Desactivar un perfil lo oculta de los directorios sin borrar historial ni reseñas.

### 2.3 Migración desde la v3 (cuentas existentes)
Aplica solo si ya hay cuentas creadas con el modelo anterior (`tipoCuenta` = `USUARIO` | `VETERINARIO`). Si el proyecto aún no tiene datos, se aplica directamente el modelo nuevo.

Estrategia segura y compatible, **sin cambiar nombres de colecciones ni mover datos**:
1. **Migración perezosa en el cliente:** al iniciar sesión, si `schemaVersion` no existe o es 1, la app ejecuta una transacción que establece `perfiles = {prestador: false, veterinario: (tipoCuenta == 'VETERINARIO')}`, `schemaVersion = 2` y `migradoEn`.
2. **No se borra `tipoCuenta`**: se conserva como campo legado para que versiones antiguas de la app sigan funcionando.
3. **Idempotente y reversible:** repetir la migración no cambia nada; no se elimina ni reescribe ningún dato clínico, mascota, solicitud o reseña.
4. Las cuentas `VETERINARIO` legadas conservan `vets/{uid}` intacto y ganan automáticamente los roles de propietario y cuidador (al ser implícitos).
5. Las cuentas `USUARIO` legadas quedan con ambos perfiles en `false` y pueden activarlos después.
6. **Compatibilidad de reglas:** durante la transición las reglas aceptan como veterinario a quien cumpla `perfiles.veterinario == true` **o** `tipoCuenta == 'VETERINARIO'`; cuando todas las cuentas estén migradas se elimina la segunda condición.
7. Las colecciones existentes (`vets`, `serviceRequests` —solicitudes a veterinarios—, `pets`, `petCodes`, etc.) **conservan su nombre y estructura**.
8. Pruebas obligatorias de migración: cuenta legada `USUARIO`, cuenta legada `VETERINARIO`, cuenta nueva, y repetición de la migración.

### 2.4 Activación de perfiles y modos de uso (RF-30)
- Perfil → "Activar perfil de prestador de servicios" y "Activar perfil de veterinario".
- Un usuario con perfiles profesionales alterna entre **Modo personal**, **Modo prestador** y **Modo veterinario** desde Perfil (selector visible en la barra superior). El modo activo se recuerda localmente y cada modo tiene su propia navegación (sección 10). Una sola sesión, sin cerrar y volver a entrar.
- Las notificaciones (RF-29) abren directamente el modo correspondiente.

---

## 3. Restricciones y principios

1. **Restricción ética:** la app **no diagnostica, no receta, no recomienda dosis, tratamientos, frecuencias, porciones, cantidades ni tipo de alimento**. Todo valor lo escribe el usuario o el veterinario; la app solo guarda, recuerda y muestra.
2. **Alimentación = solo recordatorios.** Sin cálculo de raciones ni calorías.
3. **Estado de cuidado** siempre muestra el aviso "no constituye un diagnóstico médico".
4. **Recordatorios offline:** las alarmas se programan localmente desde Room y se disparan sin internet.
5. **Motor único de cuidados:** todo cuidado programable usa `CareEvent`/`CareLog` (sección 9). No dupliques lógica de frecuencia ni de próxima fecha.
6. **Privacidad por mínimo necesario:** cada rol recibe solo los datos que necesita (sección 5). Los permisos se aplican **en las reglas de seguridad de Firestore**, no solo ocultando botones en la interfaz.
7. **Verificación de veterinarios** con insignia visible (RF-23). Los prestadores no tienen insignia de verificación en esta versión.
8. **Sin pagos en línea:** la coordinación económica y el pago se hacen directamente entre propietario y profesional, fuera de PetCare. Las pantallas de cotización muestran ese aviso.
9. **Servicios gratuitos sin tarjeta:** solo servicios disponibles sin facturación (sección 11). No usar Firebase Storage ni Cloud Functions.
10. **Contacto directo:** llamada y WhatsApp abren las apps del sistema (`ACTION_DIAL`, `https://wa.me/`), sin permisos especiales.

---

## 4. Especies

Catálogo cerrado + "otro" con texto libre. Se usa en mascotas, en las especies que atiende un veterinario y en las que atiende un prestador.

`PERRO · GATO · AVE · CONEJO · ROEDOR (hámster, cobayo, ratón, etc.) · REPTIL · PEZ · EQUINO · OTRO(texto libre)`

- `raza`: texto libre opcional. `tamaño` (pequeño | mediano | grande): opcional, informativo.
- `peso`: se guarda en **gramos** (entero); la UI permite ingresar kg o g. Opcional para peces.
- Los cuidados específicos de cada especie se crean como **actividades personalizadas** (RF-13); la app no sugiere cuáles ni cada cuánto.

---

## 5. Permisos por mascota

Matriz de acceso a la información. **Prestador** significa un prestador de servicios con una solicitud activa sobre esa mascota; su acceso nunca pasa por la ficha de la mascota (ver RF-36).

| Acción / dato | Propietario | Cuidador | Veterinario vinculado | Prestador (por solicitud) |
|---|---|---|---|---|
| Ficha básica, salud básica y alergias | Sí | Sí (lectura) | Sí (lectura) | Solo lo que el propietario comparta en la solicitud |
| Medicamentos y dosis | Sí | Lectura + registrar dosis | Lectura; crea los suyos | No |
| Vacunas y desparasitación | Sí | Lectura | Lectura; crea las suyas | No |
| Visitas veterinarias | Sí | No | Lectura; crea las suyas | No |
| Documentos | Sí | No | Lectura; sube los suyos | No |
| Peso e historial de peso | Sí | Lectura | Lectura | No |
| Contacto del veterinario de la mascota | Sí | Sí | — | No |
| Historial y calendario | Sí | Lectura de actividades y registros compartidos | Lectura | No |
| Registrar cumplimiento: **comidas, paseos (manual y GPS), higiene/baños, actividades personalizadas y dosis ya programadas** | Sí | **Sí** | No | No |
| Registrar peso, subir documentos | Sí | No | Sí (veterinario) | No |
| Editar perfil de la mascota, salud básica, comidas recordatorio | Sí | **No** | No | No |
| Crear/editar/eliminar tratamientos, dosis, vacunas, citas y calendarios médicos | Sí | **No** | Crea los suyos marcados "registrado por veterinario" | No |
| Ocultar/eliminar registros hechos por un veterinario | Sí | No | No | No |
| Autorizar a otros cuidadores, quitar cuidadores | Sí | **No** | No | No |
| Regenerar o invalidar el código de mascota | Sí | No | No | No |
| Vincular/desvincular veterinario | Sí | No | Puede desvincularse | No |
| Salir de la mascota | Sí* | Sí | Sí | — |
| Eliminar la mascota | Sí | **No** | No | No |
| Acceder a mascotas sin autorización | — | **Nunca** | **Nunca** | **Nunca** |

*Un propietario único no puede "salir"; solo eliminar la mascota (la transferencia de propiedad está fuera de alcance).

**Trazabilidad:** todo registro de dosis, comida, paseo, baño, peso o dato clínico guarda `registradoPor {userId, nombre, rol}` y `registradoEn`. La UI lo muestra ("Dado por Ana, 8:05 a. m.").

**Reglas de acceso por subcolección (para las reglas de Firestore):**
- `pets/{petId}`: lectura para `miembrosIds` (propietario y cuidadores) y `vetIds`. Los cuidadores ven una proyección limitada por reglas de subcolecciones; nunca los campos de gestión (`vetIds`, `configQR`, miembros).
- `careEvents`, `careLogs`, `walks`, `weights`: lectura para propietario, cuidadores y veterinarios vinculados; escritura de `careLogs` y `walks` para propietario y cuidadores; escritura de `careEvents` solo propietario y veterinario vinculado (`origen = VETERINARIO`).
- `visits`, `files`: lectura/escritura solo propietario y veterinarios vinculados (con las restricciones de la tabla). **Cuidadores sin acceso.**
- Un prestador de servicios **no está** en `miembrosIds` ni en `vetIds` y no tiene lectura sobre `pets/**`.

---

## 6. Requisitos funcionales

### 6.1 Cuenta

**RF-01 Autenticación**
- Registro (correo, contraseña, nombre; teléfono y WhatsApp opcionales), inicio y cierre de sesión, sesión persistente. **No se elige tipo de cuenta.**
- Sin recuperación de contraseña.
- Al cerrar sesión se limpia la caché local del usuario y se elimina su token FCM de este dispositivo.
- Todo usuario nuevo se crea con `schemaVersion = 2` y `perfiles = {prestador:false, veterinario:false}`.

**RF-30 Activación de perfiles y modos** (ver 2.4)
- Activar/desactivar perfil de prestador y de veterinario; selector de modo; navegación por modo.
- Activar el perfil veterinario inicia además el flujo de verificación (RF-23); activar el de prestador no exige verificación.

### 6.2 Mascotas (rol propietario)

**RF-02 CRUD de mascotas**
- Campos: nombre*, foto, especie*, raza, sexo (macho | hembra | desconocido), fechaNacimiento (edad calculada automáticamente; admite fecha aproximada), color, tamaño, peso*, esterilizado (bool), notas.
- **`codigoMascota`:** código único autogenerado de 8 caracteres de un alfabeto de 31 símbolos (A–Z y 2–9 sin caracteres ambiguos), generado con `SecureRandom`; la unicidad se garantiza creando `petCodes/{codigo}` en una transacción "crear solo si no existe". Detalle y controles en RF-20.
- Cambiar el peso crea un `WeightRecord` (RF-10).
- Eliminar pide confirmación y borra en cascada todo lo asociado (eventos, registros, archivos, vínculos, solicitudes de cuidador, código).
- Sin microchip.

**RF-03 Salud básica:** alergias, condiciones conocidas, medicamentos actuales (texto de referencia), observaciones importantes.

**RF-04 Veterinario de la mascota**
- Modo **vinculado** (elegido del directorio, RF-24/RF-26) o modo **manual** (nombre, teléfono, clínica, dirección, horario, observaciones).
- Botón **Llamar** (`ACTION_DIAL`).

### 6.3 Salud

**RF-05 Vacunas:** nombre, fecha de aplicación, próxima fecha, veterinario, clínica, lote, observaciones, foto del certificado. Estado calculado: **al día / próxima / vencida** (ventana de aviso configurable, por defecto 7 días). Genera recordatorio.

**RF-06 Desparasitación:** tipo (interna | externa), producto, fecha, frecuencia definida por el usuario, próxima aplicación = fecha + frecuencia. Genera recordatorio.

**RF-07 Medicamentos:** nombre, dosis indicada (texto libre, no validado), frecuencia, horarios, fecha inicio/fin, indicaciones, veterinario. Genera las **dosis del día**; cada una se marca **administrada | pendiente | omitida** con `registradoPor` (también lo pueden hacer los cuidadores sobre dosis ya programadas). Avisa cuando el tratamiento está por terminar (por defecto 2 días antes). Una dosis ya administrada por otro miembro se muestra como tal y exige confirmación para modificarla.

**RF-08 Higiene y cuidados periódicos:** baño, corte de pelo, cepillado, corte de uñas, limpieza dental, de oídos y de ojos; frecuencia configurable; al marcar realizado se calcula la próxima fecha y se reprograma el recordatorio.

**RF-09 Visitas veterinarias:** fecha, motivo, veterinario (vinculado o texto), clínica, tratamiento indicado, medicamentos (texto), documentos asociados (RF-17), próxima cita (genera recordatorio con aviso anticipado).

**RF-10 Peso:** registro periódico (fecha + peso) y **gráfica de evolución**. Al registrar uno nuevo se actualiza el peso actual de la mascota. Lo registran propietario y veterinario vinculado; el cuidador solo lo consulta.

### 6.4 Cuidados diarios

**RF-11 Alimentación (solo recordatorios)**
- El usuario (propietario) crea una o varias **comidas recordatorio** por mascota: nombre (ej. "Desayuno"), **hora**, días de la semana y un **texto libre opcional** escrito por él. La app no valida, no calcula ni sugiere nada.
- A la hora configurada suena la notificación "Hora de darle de comer a {mascota}", con acciones **Dada**, **Posponer** y **Omitir**; propietario y cuidadores pueden resolverla.
- Al marcarla queda en el historial con `registradoPor`.

**RF-12 Paseos y actividad**
- **Registro manual:** fecha, hora, duración, distancia, tipo (paseo, juego, ejercicio, entrenamiento).
- **Registro con GPS:** iniciar / pausar / reanudar / finalizar; ruta dibujada en el mapa en tiempo real; distancia y duración calculadas; se guarda en el historial con la ruta.
- Implementación GPS: Fused Location Provider + **servicio en primer plano** (tipo `location`) con notificación persistente + permisos `ACCESS_FINE_LOCATION`, `FOREGROUND_SERVICE_LOCATION` y `POST_NOTIFICATIONS`, con manejo de permiso denegado. Descartar puntos con precisión peor a ~30 m. Muestrear cada 5–10 s o cada ≥10 m. Guardar la ruta como **polilínea codificada** (string).
- El detalle del paseo muestra el recorrido en mapa. Propietario y cuidadores pueden registrar paseos.

**RF-13 Actividades personalizadas:** cuidado propio con nombre, frecuencia, hora, duración, observaciones y foto opcional (`CareEvent` tipo `PERSONALIZADO`). Propietario crea y edita; propietario y cuidadores registran su cumplimiento.

### 6.5 Recordatorios y seguimiento

**RF-14 Recordatorios y notificaciones locales**
- Aplican a todos los `CareEvent` (vacunas, desparasitación, medicamentos, comidas, higiene, citas, servicios contratados, pesaje, personalizados).
- Frecuencias: único, diario, semanal, mensual, anual, cada N días, personalizado.
- Aviso anticipado configurable para vacunas y citas.
- Acciones: marcar como realizado, posponer, omitir, editar, eliminar (según permisos de la sección 5).
- **Funcionan sin internet**; se reprograman tras reinicio del dispositivo y cambios de hora o zona horaria.
- **Cada miembro decide si recibe notificaciones de cada mascota** (interruptor por mascota); cada dispositivo programa sus propias alarmas desde Room.
- Permisos: `POST_NOTIFICATIONS` (Android 13+) y alarmas exactas (Android 12+), con aviso si se deniegan.

**RF-15 Hoy, calendario e historial**
- **Hoy:** actividades del día, dosis y comidas pendientes, próximos eventos (incluidos servicios contratados y citas), alertas (vacunas vencidas o próximas), agrupado por mascota.
- **Calendario:** vista mensual; al elegir una fecha se ve lo realizado y lo pendiente.
- **Historial:** línea de tiempo por mascota con filtros por tipo (dentro del detalle de la mascota). El cuidador ve solo actividades y registros compartidos (sección 5).

**RF-16 Estado de cuidado**
- Resumen por mascota de: vacunas, medicamentos, higiene, comidas, citas → **al día | próximo | pendiente**, calculado solo desde fechas y registros de cumplimiento.
- Siempre con el aviso: "Esto no constituye un diagnóstico médico."

### 6.6 Documentos y QR

**RF-17 Documentos**
- Subir imagen (cámara/galería) o PDF por mascota (propietario y veterinario vinculado). Campos: nombre, tipo (carné | fórmula | examen | informe | otro), fecha, `subidoPor`. Ver y eliminar según permisos. Enlazables a visitas o vacunas.
- Almacenamiento en Firestore (sección 11.1): imágenes comprimidas, PDF hasta 5 MB, fragmentados en chunks.
- Caché local de archivos ya descargados.

**RF-18 Ficha resumen y código QR**
- **Ficha resumen:** perfil, salud básica, veterinario, vacunas, medicamentos, últimas visitas, código (vista del propietario; los demás roles ven lo permitido por la sección 5).
- **QR con texto codificado directo** (ZXing core): funciona con cualquier cámara, sin servidor ni internet. Texto estructurado y legible (máx. ~800 caracteres, sin foto):
  ```
  PETCARE v1
  Nombre: ...
  Especie: ...
  Raza: ...
  Alergias: ...
  Condiciones: ...
  Contacto: ...
  Codigo: ABCD2345
  ```
- **Interruptores de privacidad:** el propietario elige qué campos incluir, incluido el `Codigo` (que por defecto aparece desactivado y debe activarse expresamente). El QR no se actualiza solo: se regenera al cambiar los datos o el código.
- Acciones: pantalla completa, guardar o compartir la imagen del QR.

**RF-19 Escáner de QR**
- Escáner dentro de la app (ML Kit Code Scanner o ZXing embedded) que interpreta el formato `PETCARE v1` y muestra la ficha pública del QR. Si el QR no es de PetCare, muestra el texto crudo.
- Si el QR incluye `Codigo`, ofrece el atajo "Quiero cuidar esta mascota" (inicia RF-20; **no otorga acceso**).

### 6.7 Cuidadores y sincronización

**RF-20 Cuidar una mascota por código**
- Perfil o Mascotas → "Cuidar otra mascota" → el usuario escribe el `codigoMascota` → ve **solo** una vista limitada (nombre, miniatura, especie) → pulsa **"Quiero cuidar esta mascota"**.
- Se crea una `JoinRequest` en estado `PENDIENTE`. El propietario la ve en su Inicio y **la aprueba o rechaza**; la aprobación es obligatoria para cada cuidador.
- Al aprobar, el solicitante queda como `CUIDADOR` de esa mascota y esta aparece en su lista marcada "Cuidas a…". Mientras esté pendiente no ve nada más.
- **Condiciones del código:**
  - Único, de 8 caracteres de un alfabeto de 31 símbolos, aleatorio (no derivado de datos de la mascota).
  - **No otorga acceso automático**, no es una contraseña ni un mecanismo de autenticación, y no expone datos privados: `petCodes/{codigo}` solo contiene `petId`, nombre, miniatura y especie.
  - Las reglas permiten **solo `get` por código exacto**, nunca listar ni consultar `petCodes`.
  - El propietario puede **regenerarlo** (el anterior queda inválido) o **invalidarlo/desactivarlo** (`codigoActivo = false`: la búsqueda responde "código no disponible" y se pueden reactivar generando uno nuevo). Estas acciones no expulsan a los cuidadores ya aprobados.
  - Anti-abuso: máximo 5 solicitudes pendientes por usuario; una solicitud rechazada no se puede repetir en 24 h; límite de intentos de búsqueda fallidos por sesión (bloqueo temporal en el cliente).
- **Revocación:** el propietario ve la lista de miembros y puede **retirar el acceso** a un cuidador en cualquier momento (se elimina de `miembros`/`miembrosIds`, cancelando sus alarmas en su dispositivo al sincronizar). El cuidador también puede salir.

**RF-21 Sincronización en la nube**
- Firestore = nube; Room = caché local y fuente de alarmas.
- Sincronización en tiempo real entre dispositivos y miembros; escrituras sin conexión se encolan; restauración completa al iniciar sesión en un dispositivo nuevo.
- Conflictos: **gana la última escritura**.

### 6.8 Veterinarios (perfil `VETERINARIO`)

Se conserva íntegramente el perfil especializado de la v3, ahora como **perfil activable** sobre una identidad de usuario.

**RF-22 Perfil de veterinario**
- Campos: nombre completo*, foto, **especies que atiende*** (multiselección), especialidades (etiquetas libres), clínica, teléfono*, WhatsApp, correo, descripción, servicios (consulta, vacunación, desparasitación, cirugía, otros), **atención a domicilio** (bool), horario por día de la semana, tarifa de consulta referencial (texto opcional), ciudad*, dirección*, **ubicación en mapa*** (marcador).
- Editable en cualquier momento.

**RF-23 Verificación del veterinario**
- Sección **"Verificación"** donde el veterinario **sube su certificado profesional** (imagen o PDF, hasta 5 MB; Firestore, sección 11.1; acceso exclusivo del propio veterinario y del proceso verificador).
- Campo `verificacion`:

| Estado | Mensaje en el modo veterinario | Insignia pública |
|---|---|---|
| `SIN_CERTIFICADO` | "Sube tu certificado para iniciar tu verificación." | No verificado |
| `EN_REVISION` | "Recibimos tu certificado. Dentro de unos días recibirás tu verificación." | No verificado |
| `VERIFICADO` | "Tu perfil está verificado." | **Verificado ✓** |
| `RECHAZADO` | "No pudimos verificar tu certificado. Sube uno nuevo." | No verificado |

- Al subir o reemplazar el certificado, el estado pasa a `EN_REVISION` (única transición permitida al cliente; las reglas impiden que el veterinario se asigne `VERIFICADO`).
- **Insignia visible** en lista, marcador de mapa (marcador distinto para verificados), encabezado del perfil y selector al solicitar servicio. Los no verificados muestran "No verificado" y no se ocultan. El directorio tiene filtro **"Solo verificados"**.
- La integración que decide `VERIFICADO`/`RECHAZADO` se implementará después (sección 12); mientras tanto el campo se cambia manualmente en la consola de Firestore.
- El cambio a `VERIFICADO` o `RECHAZADO` genera notificación (RF-29).
- **Esta verificación es independiente de los prestadores de servicios**: ningún dato, estado o insignia se comparte con `providers`.

**RF-24 Directorio y mapa de veterinarios (rol propietario)**
- Lista y **mapa** con filtros: especie, ciudad, atención a domicilio, servicio, solo verificados. Orden por **distancia** si el usuario concede ubicación; si no, por ciudad y nombre.
- Consultas acotadas (por ciudad y especie, `limit(100)`).
- Detalle: **Llamar**, **WhatsApp**, **Cómo llegar**, **Solicitar servicio**, insignia de verificación, calificaciones (RF-28).

**RF-25 Solicitud de servicio a veterinario ("contratar")**
- El dueño elige **mascota**, **veterinario**, **servicio**, **fecha y hora preferidas**, modalidad (consultorio | domicilio) y mensaje opcional. Si la especie no está entre las que atiende, se advierte sin bloquear.
- Estados: `PENDIENTE → ACEPTADA | RECHAZADA`; `ACEPTADA → COMPLETADA | CANCELADA`; el dueño puede `CANCELADA` mientras esté pendiente o aceptada. Solo el veterinario marca `COMPLETADA`.
- El veterinario acepta confirmando o ajustando fecha y hora, o rechaza con motivo opcional.
- Al **aceptar**: se crea un `CareEvent` tipo `CITA_VET` (con recordatorio) y el **vínculo veterinario–mascota** (RF-26).
- Sin pagos ni chat. Colección: `serviceRequests` (nombre conservado). Cada cambio notifica a la contraparte (RF-29).

**RF-26 Vinculación veterinario–mascota y panel del veterinario**
- El `VetLink` da acceso a la ficha clínica con los permisos de la sección 5.
- El propietario puede **desvincular** en cualquier momento; el veterinario también.
- Un veterinario vinculado puede ser el "veterinario principal" (RF-04).
- **Panel del veterinario:** bandeja de solicitudes, agenda, pacientes vinculados con su ficha, edición de perfil y verificación.

**RF-27 El veterinario registra datos en la ficha**
- El veterinario vinculado puede crear **visitas, vacunas, tratamientos y medicamentos**, y subir documentos.
- Cada registro queda marcado **"registrado por veterinario"** con su nombre y su insignia de verificación.
- El propietario puede ocultar o eliminar esos registros. Las dosis son las que el veterinario escribe; la app no las valida ni sugiere.
- Los recordatorios generados se programan en los dispositivos de los miembros de la mascota.

### 6.9 Reseñas

**RF-28 Calificaciones y reseñas (veterinarios y prestadores)**
- Un propietario puede calificar (1–5 estrellas + comentario de hasta 500 caracteres) a un veterinario o a un prestador **solo si tiene una solicitud `COMPLETADA`** con él; una reseña por solicitud; el autor puede editarla o eliminarla.
- El perfil muestra promedio, cantidad y lista paginada. Los agregados (`ratingSum`, `ratingCount`) se mantienen en `vets/{uid}` o `providers/{uid}` mediante transacción del cliente; las reglas validan que el cambio corresponda a una reseña propia válida (`getAfter`).
- Reseñas en: `vets/{uid}/reviews/{serviceRequestId}` y `providers/{uid}/reviews/{providerRequestId}`.
- Se reutiliza el mismo componente visual de reseñas para ambos.

### 6.10 Notificaciones push

**RF-29 Notificaciones push (FCM)**
- Eventos que generan push:
  - Veterinarios: nueva solicitud (→ veterinario); aceptada, rechazada, cancelada o completada (→ contraparte); cambio de `verificacion` (→ veterinario).
  - Prestadores: nueva solicitud (→ prestador); cotización o propuesta de cambio (→ propietario); aceptada, rechazada, cancelada o completada (→ contraparte).
  - Cuidadores: nueva solicitud de cuidador (→ propietario); aprobación o rechazo (→ solicitante).
- Cada dispositivo registra su token en `users/{uid}/fcmTokens/{token}` al iniciar sesión y en `onNewToken`; lo elimina al cerrar sesión.
- **Envío sin Cloud Functions:** un **Cloudflare Worker** (plan gratuito) expone `POST /notify`. El cliente que origina el evento envía el ID token de Firebase y `{tipo, refId}` → el Worker verifica el token, lee el documento referenciado por la REST API de Firestore con una cuenta de servicio, confirma que el emisor es parte legítima (`serviceRequests`, `providerRequests`, `joinRequests` o `vets`) y envía por FCM HTTP v1. La cuenta de servicio vive solo como secreto del Worker.
- Las alarmas de recordatorios (RF-14) **no** usan push.
- La app también refleja las solicitudes en tiempo real mediante listeners (insignia/bandeja): el push es complemento.
- Canales Android separados: "Recordatorios", "Solicitudes" y "Cuidadores".
- El push abre el modo correcto (RF-30) y la pantalla de la solicitud.

### 6.11 Prestadores de servicios (perfil `PRESTADOR_SERVICIOS`)

**RF-31 Perfil de prestador**
- Se activa desde la cuenta de usuario (RF-30) sin perder los roles de propietario o cuidador.
- Campos:
  - Nombre (persona o negocio)*, tipo (persona | negocio), foto, descripción*.
  - Teléfono*, WhatsApp, correo (opcional).
  - Ciudad*, dirección (opcional) y/o **zona de cobertura** (texto y/o radio en km)*.
  - **Ubicación geográfica en mapa*** (marcador que mueve el propio prestador; la app no calcula ni asigna ubicaciones por su cuenta).
  - **Modalidades:** `DOMICILIO`, `ESTABLECIMIENTO` o ambas.
  - **Especies que atiende*** (multiselección del catálogo).
  - **Horarios y disponibilidad:** horario semanal por día (rangos), fechas no disponibles y un interruptor "Recibiendo solicitudes".
  - **Galería de trabajos:** hasta 12 fotografías (sección 11.1).
  - Servicios (RF-32).
- Editable en cualquier momento; el prestador puede ocultar su perfil del directorio sin perder historial.
- Calificación promedio y reseñas mostradas en su perfil (RF-28).

**RF-32 Categorías y servicios**
- **Catálogo de categorías extensible:** las categorías viven en `serviceCategories/{id}` (`nombre`, `icono`, `orden`, `activa`) y se leen en tiempo de ejecución, de modo que agregar una categoría nueva (adiestramiento, etc.) **no requiere cambiar el código ni el esquema**: basta con crear el documento. Categorías iniciales:
  - `PASEADOR` (Paseadores de mascotas)
  - `BANO_PELUQUERIA` (Baño y peluquería)
  - `CUIDADO_TEMPORAL` (Cuidado temporal de mascotas)
- Un prestador elige una o varias categorías (la primera es la **categoría principal**) y define, dentro de cada una, uno o varios **servicios**:
```
Servicio {
  id, categoriaId, nombre, descripcion?,
  precio: { modo: FIJO | DESDE | COTIZACION, montoCOP?, unidad?: POR_SERVICIO|POR_HORA|POR_PASEO|POR_DIA|POR_NOCHE },
  especies?[]   // subconjunto de las que atiende
}
```
- Los precios son **orientativos**: "Desde $X", "$X" o "A cotizar" (modo `COTIZACION`).
- Las pantallas de cotización indican que las tarifas son orientativas y que el pago se coordina fuera de PetCare.

**RF-33 Directorio y mapa de prestadores (rol propietario)**
- Directorio **independiente** del de veterinarios, reutilizando los componentes de lista, mapa, perfil, filtros y reseñas.
- **Lista y mapa** con:
  - Búsqueda por **ciudad o zona** (ciudad normalizada + texto de zona).
  - Filtro por **categoría**, por **especie atendida** y por **modalidad** (domicilio | establecimiento).
  - Precios orientativos, horarios/disponibilidad y calificaciones visibles.
- Cada prestador aparece como **marcador** en el mapa (estilo distinto al de veterinarios, con el icono de su categoría principal).
- Al tocar un marcador se muestra una **tarjeta resumida**: nombre, foto, categoría principal, calificación (si tiene), ciudad o zona, precio orientativo (si existe) y botón **"Ver perfil"**.
- **Perfil completo:** nombre y foto; descripción; categorías y servicios; especies atendidas; teléfono con botón **Llamar**; botón **WhatsApp**; ubicación y zona de cobertura; botón **Cómo llegar** (abre la app de mapas con las coordenadas); horarios; tarifas; galería de trabajos; reseñas; botón **"Solicitar cotización / contratar"**.
- La ubicación mostrada es la que el prestador administra. La app pide **permiso de ubicación del usuario solo cuando es necesario** (ordenar por cercanía o "cerca de mí"), nunca al abrir el directorio, y funciona sin él.
- Consultas acotadas: por ciudad y categoría (`array-contains`), filtrando especie y modalidad en el cliente, `limit(100)`; los prestadores ocultos o sin "Recibiendo solicitudes" se indican o filtran.

**RF-34 Solicitud de cotización y contratación (propietario)**
- Desde el perfil del prestador (o desde la mascota), el propietario completa:
  - **Mascota**, **prestador**, **categoría**, **servicio específico**.
  - **Fecha y hora preferidas** (en cuidado temporal, fecha de inicio y fin).
  - **Modalidad:** domicilio o establecimiento (solo las que ofrezca el prestador).
  - **Descripción de lo que necesita**, mensaje adicional.
  - **Fotografías opcionales** (hasta 3, comprimidas; se guardan de forma segura en Firestore, accesibles solo para propietario y prestador, sección 11.1).
  - **Qué datos comparte con el prestador** (ver RF-36).
- Se advierte, sin bloquear, si la especie de la mascota no está entre las que atiende el prestador.
- El propietario ve el estado de todas sus solicitudes y citas (lista "Mis solicitudes") y puede **cancelar** mientras no esté `COMPLETADA`.
- Al `COMPLETADA`, puede **calificar** (RF-28).

**RF-35 Gestión de solicitudes (modo prestador)**
- Bandeja de solicitudes, agenda y historial de servicios.
- Para cada solicitud el prestador puede: **consultar** los datos permitidos de la mascota y la descripción (RF-36); **enviar una cotización** (precio en COP, observaciones, vigencia opcional); **aceptar**; **rechazar** (motivo opcional); **proponer otra fecha u horario**; **marcar como completada**; **cancelar**.
- **Estados:** `PENDIENTE → COTIZADA → ACEPTADA → COMPLETADA`, más `RECHAZADA` y `CANCELADA`.

| Transición | Quién | Condición |
|---|---|---|
| `PENDIENTE → ACEPTADA` | Prestador | Solo si el servicio tiene precio fijo/"desde" y **acepta exactamente la fecha, hora y modalidad pedidas**, sin cambios |
| `PENDIENTE → COTIZADA` | Prestador | Envía cotización (precio y observaciones) y/o **propone otra fecha u horario** |
| `COTIZADA → ACEPTADA` | **Propietario** | **Confirma** la cotización o las nuevas condiciones |
| `COTIZADA → COTIZADA` | Prestador | Reemplaza su propuesta; el historial conserva las anteriores |
| `PENDIENTE/COTIZADA → RECHAZADA` | Prestador | Motivo opcional |
| `COTIZADA → CANCELADA` | Propietario | Rechaza o abandona la propuesta |
| `PENDIENTE/ACEPTADA → CANCELADA` | Propietario o prestador | Motivo opcional |
| `ACEPTADA → COMPLETADA` | Prestador | Solo con fecha de servicio ya cumplida o en curso |

- **Aceptar con cotización o cambio de condiciones siempre requiere la confirmación del propietario.**
- Al pasar a `ACEPTADA` se crea en la mascota un `CareEvent` tipo `SERVICIO_CONTRATADO` (con fecha, prestador, servicio y recordatorio) y aparece en la agenda del prestador.
- Cada cambio se refleja en ambas cuentas en tiempo real y genera push (RF-29). Historial de eventos de la solicitud (`historial[]` con actor, estado, fecha).
- **Sin pagos en línea.** Para coordinar: botones **Llamar** y **WhatsApp** hacia la contraparte cuando los datos de contacto hayan sido compartidos (RF-36).

**RF-36 Acceso del prestador limitado a la solicitud (privacidad)**
- Un prestador **nunca** lee `pets/{petId}`, ni es cuidador, ni obtiene acceso permanente por haber sido contratado. Su acceso se limita a **la solicitud** y a lo que el propietario decidió compartir.
- Al crear la solicitud, el propietario marca qué comparte; la app guarda **copias (instantáneas)**, no referencias a la ficha:
  - `resumen` (en el documento de la solicitud, siempre compartido): nombre, miniatura, especie, raza, sexo, edad aproximada, tamaño y peso aproximado.
  - `privado/mascota` (opcional, según casillas): alergias, condiciones relevantes, notas de comportamiento e indicaciones especiales para el servicio. Legible por el prestador mientras la solicitud esté `PENDIENTE`, `COTIZADA` o `ACEPTADA`.
  - `privado/contacto` (opcional, según casillas): teléfono/WhatsApp del propietario y, si es a domicilio, la dirección. Legible por el prestador **solo mientras la solicitud esté `ACEPTADA`**.
- Al pasar a `COMPLETADA`, `RECHAZADA` o `CANCELADA`, las reglas **dejan de permitir** al prestador leer `privado/*` (el acceso expira automáticamente; no depende de borrar nada). El prestador conserva en su historial el `resumen`, el servicio, fechas, cotización y reseña.
- El propietario puede borrar `privado/*` en cualquier momento.
- El veterinario conserva su acceso clínico por `VetLink` (RF-26) y es independiente de lo anterior.
- Las reglas de Firestore verifican el estado de la solicitud con `get()` del documento padre; los botones ocultos en la interfaz no sustituyen estas reglas.

---

## 7. Notas de reutilización (componentes técnicos)

Para evitar duplicar trabajo entre veterinarios y prestadores, implementar como componentes compartidos:
- Lista/tarjeta de profesional, filtros y estado vacío/carga/error.
- Mapa con marcadores y tarjeta resumida (parámetros: tipo de marcador, icono, datos).
- Selector de ubicación con marcador (perfiles de veterinario y de prestador).
- Pantalla de perfil con acciones Llamar / WhatsApp / Cómo llegar.
- Componente de reseñas y estrellas.
- Flujo de solicitudes (chips de estado, historial, notificaciones) parametrizable por tipo de solicitud.
- Almacén de archivos en Firestore (RF-17, certificados, galería, fotos de solicitudes).

El **directorio de prestadores y el de veterinarios son independientes** (colecciones, reglas, pantallas y verificaciones separadas) aunque compartan componentes.

---

## 8. Fuera de alcance (no implementar)

- Recuperación de contraseña, microchip, reporte PDF (no usar PdfDocument API).
- Cálculo o recomendación de raciones, calorías, tipo de alimento, dosis, tratamientos o frecuencias.
- Estadísticas avanzadas (promedios de paseos, % de cumplimiento). Solo la gráfica de peso.
- Registro de síntomas y comportamiento.
- Chat entre usuarios; videollamada o telemedicina.
- **Pagos en línea**, facturación y contratos formales (el pago se coordina fuera de PetCare).
- Verificación de prestadores de servicios (los veterinarios sí tienen la suya).
- Categorías de prestadores distintas de las tres iniciales (el diseño permite agregarlas después sin cambios de código).
- Transferencia de propiedad de una mascota; roles por mascota distintos de Propietario y Cuidador.
- Que un prestador se convierta automáticamente en cuidador o conserve acceso tras el servicio.
- Ver en vivo el paseo de otro miembro; rutas sugeridas.
- Versión iOS.
- Uso de Firebase Storage, Cloud Functions, Google Maps SDK u osmdroid.

---

## 9. Modelo de datos (propuesta base)

### 9.1 `CareEvent` genérico
```
CareEvent {
  id, petId,
  tipo: VACUNA | DESPARASITACION_INTERNA | DESPARASITACION_EXTERNA | MEDICAMENTO |
        BANO | CORTE_PELO | CEPILLADO | CORTE_UNAS | LIMPIEZA_DENTAL | LIMPIEZA_OIDOS |
        LIMPIEZA_OJOS | CITA_VET | SERVICIO_CONTRATADO | PESAJE | COMIDA | PERSONALIZADO,
  titulo,
  frecuencia: { tipo: UNICO|DIARIO|SEMANAL|MENSUAL|ANUAL|CADA_N_DIAS|PERSONALIZADO, n?, diasSemana?, horarios[] },
  fechaInicio, fechaFin?, proximaFecha,
  avisoAnticipadoDias?,
  detalle: { ...campos del tipo; en COMIDA: textoLibreUsuario?; en SERVICIO_CONTRATADO: prestadorNombre, servicio, providerRequestId },
  origen: MANUAL | SOLICITUD_VET(serviceRequestId) | SOLICITUD_PRESTADOR(providerRequestId) | VETERINARIO(vetId),
  creadoPor{userId,nombre,rol}, oculto (bool), actualizadoEn
}

CareLog {
  id, petId, careEventId, fechaProgramada,
  estado: ADMINISTRADA|REALIZADA|OMITIDA|PENDIENTE|POSPUESTA,
  registradoPor{userId,nombre,rol}, registradoEn, notas?
}
```

### 9.2 Entidades
- **User:** ver 2.2. Subcolección `fcmTokens`.
- **Pet:** id, nombre, foto (Blob ≤ ~100 KB, 512 px), especie, especieOtro?, raza, sexo, fechaNacimiento, color, tamaño?, pesoGramos, esterilizado, notas, alergias, condiciones, medicamentosActualesTexto, `codigoMascota`, `codigoActivo`, `miembros{userId: PROPIETARIO|CUIDADOR}`, `miembrosIds[]`, `vetIds[]`, vetPrincipal{vetUid? | manual{...}}, configQR{campos visibles, incluirCodigo}.
- **PetCode:** codigo → {petId, nombre, miniatura (Blob ≤ ~15 KB), especie, activo}. Solo esos campos.
- **JoinRequest:** petId, solicitanteId, solicitanteNombre, estado (PENDIENTE|APROBADA|RECHAZADA), creadoEn, resueltoEn.
- **VetProfile (`vets/{uid}`):** como RF-22 + `verificacion`, `certificadoSubidoEn?`, `ratingSum`, `ratingCount`, `activo`.
- **ProviderProfile (`providers/{uid}`):**
```
{ uid, tipo: PERSONA|NEGOCIO, nombre, foto (Blob ≤ ~100 KB), descripcion, telefono, whatsapp?, correo?,
  ciudad, ciudadNorm, direccion?, zonaCobertura, radioKm?, lat, lng,
  modalidades: [DOMICILIO, ESTABLECIMIENTO],
  especiesAtendidas[], categorias[] (primera = principal), categoriaPrincipal,
  servicios[ Servicio ],
  horario{día→rangos[]}, fechasNoDisponibles[], recibiendoSolicitudes (bool),
  precioMinimoOrientativo? (derivado, para tarjetas),
  ratingSum, ratingCount, activo, creadoEn, actualizadoEn }
```
  Galería: `providers/{uid}/gallery/{id}` {miniatura (Blob ≤ ~25 KB), orden} y `providers/{uid}/galleryFull/{id}` {foto (Blob ≤ ~250 KB)}; la miniatura se lista, la foto completa se lee al abrirla.
- **ServiceCategory (`serviceCategories/{id}`):** nombre, icono, orden, activa.
- **ServiceRequest (a veterinarios, nombre conservado):** id, petId, petResumen, ownerId, ownerNombre, vetId, servicio, modalidad, fechaPreferida, fechaConfirmada?, mensaje?, motivoRechazo?, estado, creadoEn, actualizadoEn.
- **ProviderRequest (`providerRequests/{id}`):**
```
{ id, ownerId, ownerNombre, providerId, providerNombre,
  petId, resumen{nombre,miniatura,especie,raza,sexo,edadAprox,tamaño?,pesoAprox?},
  categoriaId, servicioId, servicioNombre, modalidad,
  fechaPreferida, fechaFin?, descripcion, mensaje?,
  cotizacion?{montoCOP, observaciones, vigenciaHasta?, fechaPropuesta?, modalidadPropuesta?, enviadaEn},
  estado: PENDIENTE|COTIZADA|ACEPTADA|COMPLETADA|RECHAZADA|CANCELADA,
  motivo?, canceladaPor?,
  historial[{actor, estado, nota?, fecha}],
  comparte{mascota: bool, contacto: bool},
  creadoEn, actualizadoEn }
providerRequests/{id}/privado/mascota   // alergias, condiciones, comportamiento, indicaciones
providerRequests/{id}/privado/contacto  // telefono, whatsapp, direccion
providerRequests/{id}/files/{n}         // hasta 3 fotos (Blob), ambas partes
```
- **VetLink:** petId, vetId, creadoEn, origen(serviceRequestId).
- **Review:** requestId, profesionalId, tipo (VET|PRESTADOR), ownerId, ownerNombre, estrellas, comentario, creadoEn, editadoEn?.
- **WeightRecord**, **VetVisit**, **Walk** {tipo, inicio, fin, distanciaM, duracionS, rutaPolyline?, esManual, registradoPor}.
- **FileMeta:** id, nombre, tipo, mime, tamañoBytes, fecha, subidoPor, nChunks. **FileChunk:** índice, datos (Blob ≤ 800 KB).

### 9.3 Estructura en Firestore
```
users/{uid}
users/{uid}/fcmTokens/{token}
vets/{uid}                              // lectura: cualquier autenticado
vetCertificates/{uid}                   // solo el propio veterinario y el proceso verificador
vetCertificates/{uid}/chunks/{n}
vets/{uid}/reviews/{serviceRequestId}
providers/{uid}                         // lectura: cualquier autenticado
providers/{uid}/gallery/{id}
providers/{uid}/galleryFull/{id}
providers/{uid}/reviews/{providerRequestId}
serviceCategories/{id}                  // lectura: autenticados; escritura: solo consola/administración
petCodes/{codigo}                       // solo get por código exacto
serviceRequests/{id}                    // lectura: ownerId o vetId
providerRequests/{id}                   // lectura: ownerId o providerId
providerRequests/{id}/privado/{doc}
providerRequests/{id}/files/{n}
pets/{petId}                            // lectura: miembrosIds o vetIds
pets/{petId}/careEvents/{id}
pets/{petId}/careLogs/{id}
pets/{petId}/weights/{id}
pets/{petId}/visits/{id}
pets/{petId}/walks/{id}
pets/{petId}/files/{fileId}
pets/{petId}/files/{fileId}/chunks/{n}
pets/{petId}/joinRequests/{uid}
```
- "Mis mascotas": `pets` con `miembrosIds` array-contains uid. "Mis pacientes": `vetIds` array-contains uid.

### 9.4 Reglas de seguridad de Firestore (obligatorias)
- **Usuarios:** cada uno lee/escribe su `users/{uid}`; `perfiles` solo lo cambia su dueño, y `schemaVersion` solo puede subir.
- **Pets:** según la sección 5. `miembros`/`miembrosIds`/`vetIds`/`codigoMascota` solo los modifica el propietario (la aprobación de un cuidador es acción del propietario). Un prestador no tiene ninguna regla de lectura sobre `pets/**`.
- **petCodes:** `get` permitido a autenticados; `list` y consultas denegados; crear/actualizar/eliminar solo el propietario de la mascota referenciada; no se puede leer si `activo == false`.
- **joinRequests:** lo crea cualquier autenticado para su propio uid (con límites anti-abuso); solo el propietario cambia el estado.
- **vets/{uid}:** escribe solo su dueño, sin poder asignarse `VERIFICADO` ni `RECHAZADO` (solo `EN_REVISION` al subir certificado).
- **providers/{uid}:** escribe solo su dueño y solo si `users/{uid}.perfiles.prestador == true`; agregados de reseñas solo vía reseña válida.
- **serviceRequests (veterinarios):** transiciones por actor según RF-25.
- **providerRequests:**
  - Crear: `ownerId == auth.uid`, `ownerId != providerId`, `providerId` con perfil prestador activo, `estado == PENDIENTE`, `petId` perteneciente al propietario (propietario o cuidador no pueden contratar con mascotas ajenas; solo propietario).
  - Actualizar: validar la tabla de transiciones de RF-35 (qué actor puede pasar de qué estado a cuál, y qué campos puede cambiar: el prestador solo `cotizacion`, `estado` permitido, `motivo`, `historial`; el propietario solo `estado` permitido, `motivo`, `historial`).
  - `privado/mascota`: leer prestador si `estado in [PENDIENTE, COTIZADA, ACEPTADA]`; propietario siempre. `privado/contacto`: leer prestador solo si `estado == ACEPTADA`.
  - Prestador no puede leer ni escribir nada fuera de sus solicitudes.
- **Reseñas:** crear solo el propietario de una solicitud `COMPLETADA` (vet o prestador) con ese profesional; una por solicitud.
- **serviceCategories:** lectura para autenticados; sin escritura desde el cliente.
- **Archivos (`files`, `chunks`, `vetCertificates`, `providerRequests/*/files`):** mismos permisos que su documento padre.
- Probar siempre los casos de **acceso denegado**: cuidador leyendo visitas o documentos, prestador leyendo `pets`, prestador leyendo `privado/contacto` en `COTIZADA`, prestador leyendo `privado/*` en `COMPLETADA`, veterinario asignándose `VERIFICADO`, usuario listando `petCodes`.

### 9.5 Room (caché local)
Réplica de `Pet`, `CareEvent`, `CareLog` y `ScheduledAlarm {id, careEventId, disparaEn, estado}` para programar alarmas sin internet. Los directorios y las solicitudes no requieren caché persistente.

---

## 10. Navegación

**Modo personal** (propietario y cuidador): **Inicio · Mascotas · Calendario · Explorar · Perfil**
- **Inicio:** "Hoy" (RF-15), solicitudes pendientes de cuidadores, estado de mis solicitudes (veterinarios y prestadores), alertas.
- **Mascotas:** lista (propias y "Cuidas a…") → detalle con Resumen (ficha + QR + código), Salud, Cuidados (comidas, higiene, paseos, personalizadas), Historial, Estado de cuidado, Documentos, Miembros y cuidadores, Veterinarios vinculados, Servicios contratados. Acciones: "Cuidar otra mascota" y escáner QR. Para una mascota donde el usuario es cuidador solo se muestran las secciones permitidas por la sección 5.
- **Calendario:** vista mensual + detalle del día.
- **Explorar:** dos pestañas: **Veterinarios** (directorio y mapa, RF-24) y **Prestadores** (directorio y mapa, RF-33); y "Mis solicitudes" (ambas clases).
- **Perfil:** datos personales (incluye teléfono y WhatsApp), activar perfiles profesionales y selector de modo, notificaciones por mascota, cerrar sesión.

**Modo prestador** (si `perfiles.prestador`): **Solicitudes · Agenda · Mi perfil · Perfil**
- Solicitudes (bandeja, RF-35), Agenda (servicios aceptados), Mi perfil (datos públicos, servicios, precios, horarios, galería, reseñas), Perfil (selector de modo, cerrar sesión).

**Modo veterinario** (si `perfiles.veterinario`): **Solicitudes · Agenda · Pacientes · Mi perfil**
- Solicitudes, Agenda, Pacientes (fichas y registro de datos), Mi perfil (datos públicos y **Verificación**). Aviso visible mientras `verificacion` no sea `VERIFICADO`.

---

## 11. Stack tecnológico y servicios gratuitos

| Capa | Tecnología |
|---|---|
| Lenguaje/UI | Kotlin + Jetpack Compose |
| Arquitectura | Clean Architecture + MVVM (Vista, ViewModel, Casos de uso, Repositorio) |
| DI | Dagger Hilt |
| BD local | Room (SQLite) |
| Backend | Firebase Auth + Cloud Firestore (plan Spark, sin tarjeta) |
| Archivos | **Cloud Firestore** (Blob + chunks), sección 11.1 |
| Alarmas | AlarmManager (exactas) + WorkManager; receptor `BOOT_COMPLETED` |
| Push | Firebase Cloud Messaging (gratis) + **Cloudflare Worker** (plan gratuito) |
| QR | ZXing core (generar); ML Kit Code Scanner o ZXing embedded (leer) |
| Mapas | **MapLibre Native Android** (`org.maplibre.gl:android-sdk`) + teselas vectoriales de **OpenFreeMap** (sin API key) |
| Ubicación | Fused Location Provider (orden por distancia y paseos GPS) |
| Imágenes | Coil + compresión previa |
| Gráficas | Vico o Canvas de Compose |
| Control de versiones | Git + GitHub |
| **No usar** | Firebase Storage, Cloud Functions, PdfDocument API, Google Maps SDK, osmdroid (archivado) |

### 11.1 Archivos en Firestore (sin Firebase Storage)
- **Fotos de perfil** (mascota, veterinario, prestador): Blob dentro del documento, 512 px, ≤ ~100 KB. Miniaturas (`petCodes`, `resumen`, galería) ≤ ~15–25 KB.
- **Galería del prestador:** hasta 12 fotos; miniatura en `gallery` y foto completa (≤ ~250 KB, lado mayor ≤ 1280 px) en `galleryFull`, cargada al abrirla.
- **Fotos de solicitudes:** hasta 3 por solicitud, ≤ ~150 KB cada una, en `providerRequests/{id}/files`.
- **Documentos, certificados de vacuna, certificado del veterinario:** imagen comprimida (lado mayor ≤ 1600 px, JPEG ~70 %) o PDF hasta **5 MB**, partido en chunks de ≤ 800 KB como `Blob` (el límite por documento de Firestore es 1 MiB). Subida con `WriteBatch`; `nChunks` valida integridad.
- **Cuotas del plan Spark:** 1 GiB almacenado, 50 000 lecturas y 20 000 escrituras al día. Mitigaciones: caché local, descarga bajo demanda, paginación, listeners acotados y `limit` en directorios.
- Eliminar un archivo elimina sus chunks.

### 11.2 Mapas
- Usos: directorio de veterinarios, directorio de prestadores, selector de ubicación en perfiles profesionales y recorrido de paseos.
- Marcadores diferenciados por tipo (veterinario con insignia de verificación; prestador con icono de categoría).
- Atribución obligatoria: "© OpenFreeMap © OpenMapTiles Data from OpenStreetMap".
- Confirmar la URL del estilo (por ejemplo `https://tiles.openfreemap.org/styles/liberty`) y la versión vigente de MapLibre al implementar.
- Permiso de ubicación del usuario solo cuando haga falta (cercanía, paseo GPS).

---

## 12. Pendiente por definir

- **Integración verificadora de certificados de veterinarios:** el proceso que revisa el certificado y cambia `verificacion` a `VERIFICADO` o `RECHAZADO` aún no está definido. Hasta entonces la app solo carga el certificado, muestra "dentro de unos días recibirás tu verificación" y se actualiza al cambiar el campo (manualmente en la consola de Firestore).
- **Verificación de prestadores de servicios:** no definida. Si se agrega, será un proceso independiente de la verificación veterinaria.

---

## 13. Requisitos no funcionales

| ID | Requisito |
|---|---|
| RNF-01 | Usabilidad: máximo 3 toques para registrar una dosis o comida desde "Hoy". |
| RNF-02 | Seguridad: autenticación obligatoria; permisos aplicados en reglas de Firestore (sección 9.4), no solo en la UI; QR solo con lo activado por el propietario; `petCodes` solo expone lo mínimo; certificados nunca públicos; cuenta de servicio de FCM solo en el Worker. |
| RNF-03 | Offline: recordatorios, consulta y registro funcionan sin internet. Directorios, solicitudes y push requieren conexión. |
| RNF-04 | Persistencia: respaldo en nube y restauración en instalación nueva. |
| RNF-05 | Ética: cumplir la sección 3 en todos los textos. |
| RNF-06 | Compatibilidad: **minSdk 26**. |
| RNF-07 | Rendimiento: `LazyColumn`, paginación, imágenes comprimidas, caché de archivos. |
| RNF-08 | Mantenibilidad: Clean Architecture, Hilt, componentes compartidos (sección 7), ramas por funcionalidad. |
| RNF-09 | Fechas: instantes en UTC (`Instant`), fechas de calendario como `LocalDate`; precios en COP como enteros. |
| RNF-10 | Cada pantalla que lee datos maneja estados de carga, vacío y error. |
| RNF-11 | Respetar las cuotas del plan Spark y los límites del plan gratuito de Cloudflare Workers; manejar errores de cuota con mensajes claros. |
| RNF-12 | Migración: segura, idempotente y compatible con las cuentas v3 (sección 2.3). |
| RNF-13 | Privacidad: cada rol recibe el mínimo de datos; el acceso de un prestador expira con el estado de la solicitud. |

---

## 14. Orden de implementación (por dependencias)

1. Proyecto base, Hilt, Firebase Auth, `users/{uid}` con `perfiles` y `schemaVersion`, migración perezosa (RF-01, 2.3), selector de modo (RF-30).
2. CRUD de mascotas con especies y código (RF-02) y almacén de archivos en Firestore (11.1).
3. Motor `CareEvent`/`CareLog` + recordatorios locales (RF-14), y sobre él RF-05 a RF-11, RF-13.
4. RF-15, RF-16 y RF-10.
5. Sincronización Firestore ↔ Room y reglas de seguridad base por rol (RF-21, 9.4).
6. Mapas con MapLibre; RF-12 (paseos manuales y GPS).
7. RF-17, RF-18, RF-19.
8. RF-20 (cuidadores por código, revocación, regenerar/invalidar).
9. Componentes compartidos de directorio, perfil, reseñas y solicitudes (sección 7).
10. RF-22 a RF-27 (perfil veterinario con verificación, directorio, solicitudes, vínculos, panel).
11. RF-31 a RF-36 (perfil de prestador, categorías, directorio y mapa, cotizaciones, privacidad por solicitud).
12. RF-28 (reseñas) y RF-29 (push con FCM y Worker).
13. Pruebas con cuatro cuentas (propietario, cuidador, prestador, veterinario), una cuenta con todos los roles a la vez, migración de cuentas legadas, alarmas con app cerrada, reinicio, modo avión y casos de acceso denegado.

---

## 15. Criterios de aceptación globales

- Todo funciona en dispositivo físico.
- Una misma cuenta es propietaria de una mascota, cuidadora de otra, prestadora y veterinaria a la vez, y alterna entre modos sin cerrar sesión.
- Las cuentas v3 migran sin perder datos, y repetir la migración no cambia nada.
- Una notificación local se dispara con la app cerrada, sin internet y tras reiniciar el teléfono.
- Una comida recordatorio suena a la hora configurada, muestra el texto libre del usuario sin ningún cálculo y queda registrada como dada, omitida o pospuesta con autor y hora.
- Con solo el código, un usuario ve nombre, miniatura y especie; no obtiene acceso hasta que el propietario aprueba; el propietario puede retirar el acceso, regenerar o invalidar el código.
- Un cuidador puede registrar comidas, paseos, baños y dosis programadas, y **no** puede editar la mascota, eliminarla, modificar tratamientos o autorizar a otros; no puede leer visitas ni documentos.
- Un veterinario sube su certificado, ve el mensaje de verificación pendiente y los dueños lo ven como "No verificado"; al pasar `verificacion` a `VERIFICADO` en Firestore aparece "Verificado ✓" en lista, mapa y perfil, y recibe push. Esa verificación no afecta a ningún prestador.
- Un prestador activa su perfil, define categorías, servicios, precios orientativos, horarios, ubicación en mapa y galería, y aparece en el directorio de prestadores con marcador y tarjeta resumida.
- Un propietario filtra prestadores por ciudad/zona, categoría, especie y modalidad, ve su perfil, llama o abre WhatsApp, y solicita una cotización eligiendo qué datos de su mascota comparte.
- El flujo `PENDIENTE → COTIZADA → ACEPTADA → COMPLETADA` funciona entre dos cuentas: el prestador cotiza o propone otra fecha, el propietario confirma, aparece el servicio en el calendario del propietario y la agenda del prestador, y ambos reciben notificaciones. También funcionan `RECHAZADA` y `CANCELADA`.
- Un prestador **no** puede leer `pets/**` ni convertirse en cuidador; no ve `privado/contacto` hasta `ACEPTADA` y pierde acceso a `privado/*` al terminar la solicitud.
- Solo el propietario de una solicitud `COMPLETADA` puede calificar al profesional.
- Un paseo con GPS dibuja la ruta, calcula distancia y duración y se guarda en el historial.
- El QR leído con una cámara cualquiera muestra texto legible; el escáner de la app lo interpreta.
- Ninguna pantalla diagnostica ni sugiere dosis, porciones, alimentos o frecuencias.
- Las pruebas de reglas de seguridad de la sección 9.4 pasan, incluidos todos los casos de acceso denegado.

---

## 16. Instrucciones para la IA

1. Implementa todo lo descrito; no agregues nada de la sección 8.
2. Ante ambigüedad, **pregunta** antes de implementar.
3. Sigue el orden de dependencias de la sección 14; entrega código compilable en módulos pequeños.
4. Modela la identidad como `users/{uid}` con perfiles independientes; **nunca** reintroduzcas un tipo de cuenta excluyente.
5. No renombres colecciones existentes; la migración es perezosa, idempotente y no borra `tipoCuenta`.
6. Todo cuidado programable pasa por `CareEvent`/`CareLog`.
7. Mantén Clean Architecture; todo acceso a datos por repositorios; reutiliza los componentes compartidos de la sección 7.
8. Nunca generes texto de la app que diagnostique, recete o sugiera dosis, porciones, alimentos o frecuencias.
9. No uses Firebase Storage, Cloud Functions, Google Maps SDK ni osmdroid.
10. Aplica los permisos en las **reglas de Firestore**, no solo en la UI; incluye pruebas de acceso denegado para cada rol.
11. Mantén separadas la verificación veterinaria y cualquier dato de prestadores.
12. Al terminar cada funcionalidad, indica qué RF cubre y cómo probarla.
