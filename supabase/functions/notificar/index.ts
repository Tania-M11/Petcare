// PETCARE · Parte 5.1: envía por FCM (HTTP v1) un aviso de la tabla notificaciones (RF-29).
// La llama la base de datos (pg_net) al crear cada aviso. FCM solo transporta: el contenido
// sale de la base de datos y cada aviso se envía una sola vez, así que repetir la llamada
// no tiene efecto. Requiere el secreto FCM_SERVICE_ACCOUNT (JSON de la cuenta de servicio
// de Firebase); sin él, los avisos siguen llegando por Realtime y al sincronizar.
import { createClient } from "jsr:@supabase/supabase-js@2";

type Cuenta = { project_id: string; client_email: string; private_key: string };

const b64url = (datos: ArrayBuffer | Uint8Array | string) => {
  const bytes = typeof datos === "string" ? new TextEncoder().encode(datos) : new Uint8Array(datos);
  let s = "";
  bytes.forEach((b) => (s += String.fromCharCode(b)));
  return btoa(s).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
};

async function tokenGoogle(c: Cuenta): Promise<string> {
  const ahora = Math.floor(Date.now() / 1000);
  const encabezado = b64url(JSON.stringify({ alg: "RS256", typ: "JWT" }));
  const cuerpo = b64url(JSON.stringify({
    iss: c.client_email,
    scope: "https://www.googleapis.com/auth/firebase.messaging",
    aud: "https://oauth2.googleapis.com/token",
    iat: ahora,
    exp: ahora + 3600,
  }));
  const pem = c.private_key.replace(/-----[^-]+-----/g, "").replace(/\s+/g, "");
  const der = Uint8Array.from(atob(pem), (ch) => ch.charCodeAt(0));
  const llave = await crypto.subtle.importKey("pkcs8", der, { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" }, false, ["sign"]);
  const firma = await crypto.subtle.sign("RSASSA-PKCS1-v1_5", llave, new TextEncoder().encode(`${encabezado}.${cuerpo}`));
  const r = await fetch("https://oauth2.googleapis.com/token", {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({ grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer", assertion: `${encabezado}.${cuerpo}.${b64url(firma)}` }),
  });
  if (!r.ok) throw new Error(`OAuth ${r.status}`);
  return (await r.json()).access_token;
}

Deno.serve(async (req) => {
  if (req.method !== "POST") return new Response("Método no permitido", { status: 405 });
  const { id } = await req.json().catch(() => ({ id: null }));
  if (typeof id !== "string") return Response.json({ error: "Falta id" }, { status: 400 });

  const sb = createClient(Deno.env.get("SUPABASE_URL")!, Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!);
  // Solo avisos de los últimos 10 minutos que aún no se enviaron
  const desde = new Date(Date.now() - 10 * 60 * 1000).toISOString();
  const { data: n } = await sb.from("notificaciones").select("*").eq("id", id).is("push_enviado_en", null).gte("creado_en", desde).maybeSingle();
  if (!n) return Response.json({ enviados: 0, motivo: "ya enviado o inexistente" });
  await sb.from("notificaciones").update({ push_enviado_en: new Date().toISOString() }).eq("id", id);

  const secreto = Deno.env.get("FCM_SERVICE_ACCOUNT");
  if (!secreto) return Response.json({ enviados: 0, motivo: "FCM sin configurar" });
  const cuenta: Cuenta = JSON.parse(secreto);
  const { data: dispositivos } = await sb.from("dispositivos").select("token").eq("usuario_id", n.usuario_id);
  if (!dispositivos?.length) return Response.json({ enviados: 0, motivo: "sin dispositivos" });

  const acceso = await tokenGoogle(cuenta);
  let enviados = 0;
  for (const { token } of dispositivos) {
    // Mensaje de datos: la app lo muestra con sus propios canales y abre el modo correcto
    const r = await fetch(`https://fcm.googleapis.com/v1/projects/${cuenta.project_id}/messages:send`, {
      method: "POST",
      headers: { Authorization: `Bearer ${acceso}`, "Content-Type": "application/json" },
      body: JSON.stringify({
        message: {
          token,
          android: { priority: "high" },
          data: { id: n.id, tipo: n.tipo, titulo: n.titulo, cuerpo: n.cuerpo, modo: n.modo, destino: n.destino },
        },
      }),
    });
    if (r.ok) {
      enviados++;
    } else if (r.status === 404 || (await r.text()).includes("UNREGISTERED")) {
      await sb.from("dispositivos").delete().eq("token", token); // token viejo
    }
  }
  return Response.json({ enviados });
});
