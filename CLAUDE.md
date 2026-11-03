# RealEconomy

Mod de Minecraft **1.21.1** con **Fabric** que implementa una economía completa para servidores, basada en **países** (con partidos políticos dentro): monedas propias, bancos, ayuntamientos, tiendas de jugadores, impuestos, préstamos, trabajos y NPCs.

> **Nota de terminología (2ª tanda):** lo que antes eran "grupos" ahora son **PAÍSES**. Internamente las clases siguen llamándose `Group`/`GroupManager` (y el `PersistentState` sigue siendo `realeconomy_groups`) para **migrar los datos existentes sin perderlos**; de cara al jugador todo dice "país". El comando principal es `/pais` (alias `/paises`, `/grupo`, `/group`).

> **REGLA DE PUBLICACIÓN LIMPIA (4ª tanda):** el mod se publica **limpio**: al instalarlo en un mundo nuevo **no hay ningún país, partido, moneda ni NPC creado**. Todo el estado vive en `PersistentState` por mundo y los NPCs los colocan comandos; **no se siembra ningún dato de prueba ni por defecto**. Cada servidor crea y configura sus países. (Los datos de ejemplo solo existen dentro de `/ecoadmin debug`, que es una herramienta de prueba, no estado del mundo.)

- **Mod id:** `realeconomy`
- **Loader:** Fabric
- **Minecraft:** 1.21.1 · **Java:** 21
- **Yarn mappings:** `1.21.1+build.3` · **Loader:** `0.20.1` · **Fabric API:** `0.116.17+1.21.1` · **Loom:** `1.11.8`

## Compilar y probar

```bash
./gradlew build          # compila y genera el .jar en build/libs/
./gradlew runServer      # arranca un servidor de pruebas con el mod cargado
./gradlew runClient      # arranca un cliente de pruebas
```

El `.jar` final queda en `build/libs/realeconomy-<version>.jar` (ignora el `-sources.jar`). Para un servidor real: Fabric Loader + Fabric API + este jar en `mods/`.

> La primera ejecución de Gradle descarga Minecraft, los mappings y las dependencias; puede tardar varios minutos.

## Arquitectura

- Paquete raíz: `com.realeconomy`
- `RealEconomy` — `ModInitializer`: registra comandos y eventos.
- **Persistencia:** cada subsistema que debe sobrevivir a reinicios usa un `PersistentState` guardado en el mundo (overworld), obtenido con `XxxManager.get(server)`. Nada se guarda en memoria salvo lo explícitamente temporal (p. ej. invitaciones de grupo pendientes).
- **Comandos:** un archivo por subsistema en `com.realeconomy.command`, registrados vía `CommandRegistrationCallback`.
- **Config:** (pendiente, parte transversal) archivo en `config/realeconomy.json`.

## Interfaces: tema visual único y `/ecoadmin debug`

Todas las pantallas del cliente (banco, ayuntamiento, tiendas, mercados, compradores) comparten un **tema único** con estética **Art Déco + steampunk elegante + neotecnología + opulencia burguesa**: **mármol negro**, **oro y latón**, cobre pulido, líneas geométricas simétricas, **marcos escalonados y abanicos Art Déco** en las esquinas, **remaches** de latón y un filo de **cian/turquesa neón** como acento tecnológico; bordes biselados; texto con la fuente de Minecraft (misma legibilidad que antes). Las texturas son **originales** (generadas de forma procedural, no copiadas de otros mods).

Código del tema en `com.realeconomy.client.ui`:
- `Theme` — paleta (oro/latón/mármol + acento `ACCENT` turquesa), identificadores de textura (resueltos desde la config, con fallback) y el **nine-slice** (`panel`, `button`, `slot`, `field`, `bar`) que escala las texturas sin deformarse; además `barFilled(...)` (barra con relleno proporcional, dorado o turquesa) y `separator(...)` (divisor Art Déco con rombo de latón).
- `ThemedScreen` — base de todas las pantallas: centra el panel de mármol, pinta la cabecera dorada con el título y un separador, y ofrece utilidades (`contentLeft/Top/Width`, `text`, `centered`). Las subclases implementan `initContent()` (widgets) y `renderContent()` (dibujo).
- `ThemedButton` — botón oscuro de latón con bisel dorado, texto claro con sombra, resalte turquesa al pasar el ratón y aspecto atenuado al estar deshabilitado.
- `ThemedSlider` — deslizador (barra de impuestos) con carril hundido (`bar`), relleno dorado y tirador del tema.
- `ThemedTextField` — campo de texto con fondo de mármol oscuro, marco de latón y filo turquesa al enfocar (reutilizable por todas las pantallas).

**Texturas** (en `assets/realeconomy/textures/gui/theme/`, nine-slice):
- `panel.png` (72×72, borde 14) — mármol negro + marco escalonado oro/latón + filo turquesa + abanicos y remaches.
- `button.png` (32×20, borde 6) — botón de latón oscuro con bisel dorado.
- `slot.png` (26×26, borde 6) — casilla hundida de mármol con bisel dorado (filas de los listados).
- `field.png` (24×24, borde 6) — campo de texto: mármol muy oscuro, marco de latón, filo turquesa.
- `bar.png` (24×12, borde 6) — carril hundido para sliders y barras de tendencia.

**Sustituibles por resource pack o config:** basta con sobrescribir esos identificadores en un resource pack, o cambiar `themePanelTexture` / `themeButtonTexture` / `themeSlotTexture` / `themeFieldTexture` / `themeBarTexture` en `config/realeconomy.json` (+ `/ecoadmin reload`). Las texturas se regeneran con el generador `tools/GenTheme.java` (no incluido en el jar; `javac -d <tmp> tools/GenTheme.java && java -cp <tmp> GenTheme <carpeta>`).

**`/ecoadmin debug <interfaz>` (op nivel 2):** abre cualquier interfaz **sin necesidad de NPC** ni de montar la economía, para probar las pantallas en local. `<interfaz>`: `banco`, `ayuntamiento`, `tienda`, `mercado`, `comprador`. El banco y el ayuntamiento muestran tus datos reales; tienda/mercado/comprador muestran ofertas/categorías de ejemplo (usan la moneda de tu país si tienes, si no un marcador `demo`).

> **Nota sobre "no se veían las interfaces":** se auditó todo el pipeline cliente↔servidor (registro de payloads S2C/C2S, receptores de cliente, apertura por clic derecho en NPC) y es correcto; el mod carga sin errores en `runClient`. Para que una GUI no aparezca hacían falta en la práctica: ser op, tener el mundo con trucos, haber creado el NPC y cumplir cercanía/moneda — o estar probando con un cliente **sin el mod** conectado a `runServer`. Ahora `/ecoadmin debug` abre cualquier pantalla al instante, y el clic derecho en NPC **registra en el log** cualquier error en vez de fallar en silencio.

## Estado por partes

1. **Países** (antes "Grupos") ✅ — siglas, color, líder, miembros, lista de jugadores con `[SIGLAS]` en color. **Partidos políticos** dentro de cada país (se fundan en el ayuntamiento pagando a la cuenta del país).
2. **Monedas (icono por URL) y cuentas** ✅ — item físico de moneda por divisa, icono descargado/cacheado/sincronizado, libro de saldos.
3. **Banco NPC con interfaz** ✅ — NPC aldeano, GUI vanilla con ingresar/retirar/transferir.
4. **Ayuntamiento y licencias** ✅ — NPC ayuntamiento, GUI de compra de licencias (consumibles).
5. **Tiendas de jugadores (venta y compra)** ✅ — NPC tienda, ofertas de venta/compra, almacén, GUI de compra.
6. **Impuestos y cuenta del país** ✅ — cuenta del país, impuestos (fijo/%) y sueldos recurrentes en tiempo real, subvenciones.
7. **Mercados de país (NPCs)** ✅ — NPC de mercado que compra/vende items por monedas físicas desde su caja; incluye el cambio esmeraldas↔monedas.
8. **Trabajos y sueldos** ✅ — minería/tala/agricultura/banca; el líder fija el sueldo por acción y se paga desde la cuenta del país.
9. **Banquero y préstamos** ✅ — el líder presta desde la cuenta del país (capital, interés, plazo real); al vencer se cobra solo del saldo y, si no llega, queda deuda.

### Segunda tanda (cambios y añadidos en curso)

1. **Países y partidos** ✅ — "grupos" → países; partidos políticos dentro de cada país.
2. **Moneda por defecto** ✅ — crear país crea su moneda automáticamente con item dorado propio.
3. **Gobierno y política monetaria** ✅ — imprimir/quemar dinero, devaluación por circulación, estadísticas, obras públicas.
4. **NPCs compradores por categoría** ✅ — oferta y demanda por categorías (tags); pagan desde la cuenta del país usando el valor de la moneda.
5. **Subastas** ✅ — licencia, pedestales, pujas en tiempo real, comisión al país, cobro offline.
   - (El "banquero y préstamos" original ya está hecho como parte 9.)

**Extra (2ª tanda):** barra de impuestos general del país **0–100 %** (slider en el ayuntamiento + `/gobierno impuestos`), que cobra ese % del saldo de cada miembro a la cuenta del país de forma periódica.

### Tercera tanda (cambios y añadidos en curso)

1. **Estética nueva de TODAS las interfaces** ✅ — tema único Art Déco + steampunk (mármol negro, oro/latón, acento turquesa neón); texturas originales `panel`/`button`/`slot`/`field`/`bar`; `ThemedTextField` y separadores Art Déco. Ver *Interfaces: tema visual único*.
2. **Gobierno y permisos** ✅ — ministros designados por el líder; solo el gobierno (líder+ministros) gestiona el país. Ver *Gobierno y permisos (3ª tanda)*.
3. **Banco: cambio de moneda y transferencias** ✅ — cambio origen→destino por valor actual con comisión al país; transferencias por GUI y comando. Ver *Banco: cambio de moneda (3ª tanda)*.
4. **Impuestos por tipo** ✅ — renta/ventatienda/cuotatienda/mercado/transferencia/cambio/subasta, cada uno configurable; panel editable en el ayuntamiento (gobierno). Ver *Impuestos por tipo (3ª tanda)*.
5. **Licencias personalizadas** ✅ — el gobierno define licencias (nombre, precio, moneda, caducidad, permiso); los jugadores las compran en el ayuntamiento; sin la licencia correcta, la acción se bloquea. Ver *Licencias personalizadas (3ª tanda)*.
6. **Mercado (NPC): solo vender** ✅ — el mercado solo compra (los jugadores venden); precio por oferta/demanda con tendencia (▲/▼). Ver *Mercado solo-venta (3ª tanda)*.
7. **Tiendas: icono real del item** ✅ — las ofertas muestran el **icono real** del item de Minecraft con su **brillo de encantamiento**, y un **tooltip** al pasar el ratón (nombre, encantamientos, durabilidad). Ver *Tiendas: icono real del item (3ª tanda)*.

## Cuarta tanda — Formas políticas (en curso)

Añade **formas políticas** a los países. Orden: (1) elegir forma al crear, (2) estructura de gobierno por forma, (3) elecciones, (4) leyes. Todo **data-driven**, configurable y guardado en el mundo.

### Parte 1 (4ª tanda) — Elegir forma política al crear el país — implementada

Al crear un país se elige una **forma política**: Ninguna, Democracia, República, Monarquía absoluta, Monarquía parlamentaria, Dictadura, Oligarquía, Teocracia, Tecnocracia o Anarquía. **Dictadura** exige subtipo (totalitarismo / autocracia / junta militar) y **Oligarquía** también (aristocracia / plutocracia).

- **Data-driven:** cada forma (y subtipo) se define en `config/realeconomy_forms.json`, generado con valores por defecto al arrancar y **editable sin tocar código**. Cada entrada tiene: `displayName`, `description`, `governmentMode` (quién gobierna), `howToPower` (cómo se llega al poder), `elections`, `partiesAllowed`, `partiesHavePower` y un bloque `laws` (impuesto máximo, si imprime dinero y cuánto, si hace falta votación, quién crea licencias, diezmo, tope de circulación, duración del mandato en domingos…). Si el archivo pierde entradas al actualizar el mod, se recompletan solas con los defaults. `/ecoadmin reload` recarga config **y** formas.
- **Modelo** (`com.realeconomy.politics`): `FormType` (enum de las 10 formas, con `requiresSubtype()`/`subtypes()`/`key()`), `FormSubtype` (enum de los 5 subtipos + `NONE`), `GovernmentMode` (SINGLE_LEADER, ELECTED_PARTY, ELECTED_PRESIDENT, MONARCH, PARLIAMENTARY, JUNTA, COUNCIL, PLUTOCRACY, THEOCRACY, NONE; se consume en la parte 2), `Laws`, `FormDefinition`, `PoliticalForms` (carga/guarda el JSON y resuelve `get`/`displayName`), `CreationActions` (lógica de servidor de la GUI de creación). `Group` guarda `form`+`subtype` (NBT `form`/`subtype`, por defecto NINGUNA → **migra países antiguos**). `GroupManager.createGroup(..., form, subtype)` y `setForm(...)`.
- **Interfaz de creación (tema Art Déco):** `/pais crear` abre `client.CountryCreateScreen` (siglas, nombre, color, forma y subtipo con ciclado, descripción de la forma y botón **Fundar país**). Payloads `OpenCountryCreatePayload` (S2C) y `CountryCreateActionPayload` (C2S); el servidor valida y crea.
- **Se muestra** en el **ayuntamiento** (línea "Forma: …" al pie, desde `OpenTownhallPayload.formName`) y en la **ficha del país** (`/pais info` → "Forma política: …").
- **Comandos** (`/pais`): `/pais crear` (GUI de creación con forma), `/pais forma` (ver forma y cómo se gobierna), `/pais forma <forma> [subtipo]` (el **líder** la cambia). El antiguo `/pais create <siglas> <color> [nombre]` sigue valiendo y crea con forma **Ninguna** (para elegir forma, usa `/pais crear` o `/pais forma`).

> **Nota:** la estructura real de gobierno de cada forma (quién puede gestionar el país) y las elecciones/leyes se implementan en las partes 2-4; en la parte 1 la forma se elige, se guarda, se muestra y queda lista para que las siguientes partes la consulten.

#### Cómo probar la parte 1 (4ª tanda)

1. `./gradlew runClient` (o conéctate a `runServer`).
2. **GUI:** `/pais crear` → se abre la interfaz Art Déco. Escribe siglas (2-4 letras) y nombre, cicla el color, cicla la **forma** (verás su descripción). Elige **Dictadura** → aparece el ciclador de **subtipo** (totalitarismo/autocracia/junta militar); igual con **Oligarquía**. Pulsa **Fundar país**.
3. Comprueba `/pais info` → "Forma política: …" y, en un ayuntamiento (`/ayuntamientocrear` como op, clic derecho), la línea "Forma: …" al pie.
4. **Por comando:** `/pais forma` muestra la forma actual y cómo se gobierna; `/pais forma teocracia`, `/pais forma dictadura junta_militar`, `/pais forma oligarquia plutocracia` (el líder la cambia).
5. **Data-driven:** edita `config/realeconomy_forms.json` (p. ej. cambia el `displayName` o una ley de `democracia`) y `/ecoadmin reload` → se refleja al instante.
6. **Limpio:** un mundo nuevo no trae ningún país; reinicia y comprueba que forma y subtipo persisten.

### Parte 2 (4ª tanda) — Estructura de gobierno por forma — implementada

Quién es "gobierno" (quién puede tocar impuestos, licencias, imprimir dinero, obras, sueldos, préstamos, moneda, mercado y la cuenta del país) **depende ahora de la forma política**. El resto solo ve. Lo centraliza `politics.Governance` (sustituye a `Group.isGovernment` en todos los comandos y botones de gobierno).

| Forma (modo) | Quién gobierna | Cómo se llega |
|---|---|---|
| Ninguna (SINGLE_LEADER) | líder + ministros | fundar / designar ministros |
| Dictadura·totalitarismo (SINGLE_LEADER) | líder (+ ministros) | sin elecciones; sin partidos |
| Dictadura·autocracia (SINGLE_LEADER) | líder (+ ministros) | partidos sin poder |
| Dictadura·junta militar (JUNTA) | líder + junta (ministros) | el líder compone la junta |
| Oligarquía·aristocracia (COUNCIL) | líder + consejo (ministros) | designados por el consejo/líder |
| Oligarquía·plutocracia (PLUTOCRACY) | los **N más ricos** del país | por riqueza (config `plutocracyGovernors`) |
| Teocracia (THEOCRACY) | sumo sacerdote (líder) + clero (ministros) | designa clérigos |
| Tecnocracia (COUNCIL) | líder + consejo de expertos (ministros) | designados |
| Monarquía absoluta (MONARCH) | rey (líder) + ministros | hereditario: `/pais sucesor` + `/pais abdicar` |
| Monarquía parlamentaria (PARLIAMENTARY) | **jefe de gobierno** del partido ganador (el rey NO toca la economía) | elecciones (parte 3) |
| Democracia (ELECTED_PARTY) | **fundador del partido ganador** | elecciones (parte 3) |
| República (ELECTED_PRESIDENT) | **presidente electo** | elecciones (parte 3) |
| Anarquía (NONE) | **nadie** | — |

- **Modelo:** `Group` gana `governingPartyId` (partido en el gobierno), `president` y `heir` (sucesor del rey), todos en NBT. `Governance.isGovernment(server, group, uuid)` resuelve según el `GovernmentMode` de la forma; `notGovernmentMessage(...)` da el **motivo claro** (anarquía, aún sin elecciones, solo los más ricos, etc.); `richestGovernors(...)` calcula la plutocracia desde el `BalanceManager`; `councilLabel(...)` adapta el nombre (junta/consejo/clero/ministros).
- **Elecciones (formas electivas):** en democracia/parlamentaria/república **no hay gobierno** hasta que unas elecciones fijen el partido/presidente ganador (`governingPartyId`/`president`). Eso lo hace la **parte 3**; mientras tanto esas formas quedan sin gobierno económico (mensaje que remite a `/ecoadmin elecciones`).
- **Monarquía:** `/pais sucesor <jugador>` (el rey designa heredero) y `/pais abdicar` (cede la corona al sucesor). En parlamentaria, el líder es el **rey** (jefe de Estado) y aparece así en `/pais gobierno`, sin poder económico.
- **GUI:** el ayuntamiento pinta el botón **Imprimir** / barra de impuestos solo si el jugador **es gobierno según la forma** (reusa `canPrint`). `/pais gobierno` muestra la forma y a los gobernantes según el modo.

> **Notas de diseño:** la "junta militar" y los "consejos" (aristocracia/tecnocracia/clero) se componen reutilizando el mecanismo de **ministros** (`/pais gobierno add/remove`): cualquier miembro del gobierno puede actuar (se simplifica el "decidir por mayoría" a "cualquiera del gobierno gestiona"). Los **NPCs compradores** (`/comprador`) siguen siendo solo del **líder** por diseño, independientemente de la forma.

#### Cómo probar la parte 2 (4ª tanda)

1. **Anarquía:** `/pais forma anarquia`. Intenta `/gobierno imprimir 100`, `/impuesto …`, `/tesoreria pagar …` → **bloqueado** con "en una anarquía nadie gobierna". Las vistas (`/pais info`, `/tesoreria ver`) siguen abiertas.
2. **Plutocracia:** `/pais forma oligarquia plutocracia`. Da saldo a dos miembros (`/ecoadmin saldo set A ESP 500`, `… B 100`). `/pais gobierno` muestra al más rico como gobierno; solo **A** puede `/impuesto …`. Súbele el saldo a B por encima de A y verás que pasa a gobernar B. Ajusta `plutocracyGovernors` + `/ecoadmin reload` para que gobiernen varios.
3. **Monarquía absoluta:** `/pais forma monarquia_absoluta`. `/pais sucesor Otro`, `/pais gobierno` (muestra rey y sucesor), `/pais abdicar` → el sucesor pasa a rey.
4. **Teocracia / junta / aristocracia / tecnocracia:** `/pais forma teocracia` (etc.). El líder designa clero/junta/consejo con `/pais gobierno add <jugador>`; `/pais gobierno` usa la etiqueta correcta. Esos designados ya pueden gestionar.
5. **Formas electivas:** `/pais forma democracia` → `/impuesto …` avisa de que no hay gobierno electo hasta las elecciones (parte 3).
6. Reinicia: forma, sucesor, gobierno y designados persisten.

### Parte 3 (4ª tanda) — Elecciones — implementada

En las formas electivas (democracia, monarquía parlamentaria, república) se celebran **elecciones** entre los **partidos** del país. Duran **todo el día de elecciones** (por defecto el **domingo**, 00:00–23:59 en tiempo real; día y zona horaria configurables). Se vota en un **NPC urna** y al terminar se cuenta, se anuncia el resultado y entra el nuevo gobierno. Todo se guarda en el mundo.

- **Modelo** (`com.realeconomy.election`): `Election` (ventana de tiempo, votos por partido, votantes; un voto por miembro), `ElectionRecord` (historial), `ElectionManager` (`PersistentState` `realeconomy_elections`: elecciones activas, `lastElectionDay` para no reabrir el mismo día, historial; `tick(server)` autolimitado a 1/seg). `UrnEntities` (NPC aldeano "🗳 Urna electoral", tag `realeconomy_urn`), `ElectionActions` (GUI de servidor).
- **Calendario:** el `tick` mira la hora en la zona configurada; si es el **día de elecciones** y el país es electivo y **tiene partidos** y no se ha celebrado ya ese día, **abre** una elección que cierra al final del día. Al cerrar, cuenta los votos, aplica el resultado y lo **anuncia en el chat** a los miembros conectados (y al log).
- **Resultado:** democracia/parlamentaria → el **partido ganador** pasa a `governingPartyId` (su fundador es jefe de gobierno/primer ministro); república → el fundador del partido ganador pasa a **presidente** (`president`). Sin votos → se mantiene el gobierno. Esto conecta con la **parte 2** (`Governance`), así que tras la elección el nuevo gobierno ya puede gestionar la economía.
- **Votar:** clic derecho en una urna abre `client.ElectionScreen` (lista de partidos con botón **Votar**, estado y cuenta atrás). También `/elecciones votar <partido>` **junto a una urna**. Cada miembro vota **una vez**; solo miembros del país. Payloads `OpenElectionPayload` (S2C) y `ElectionVotePayload` (C2S).
- **Urnas:** `/urnacrear` y `/urnaborrar` las coloca/quita el **gobierno del país o un op**.
- **Comandos** (`/elecciones`, alias `/eleccion`): `/elecciones` (estado y partidos), `/elecciones votar <partido>` (junto a urna), `/elecciones historial [siglas]`.
- **Pruebas sin esperar al domingo:** `/ecoadmin elecciones abrir` (fuerza abrir en todos los países electivos con partidos) y `/ecoadmin elecciones cerrar` (cuenta y aplica), solo op.
- **Config:** `electionDayOfWeek` (SUNDAY), `electionZoneId` ("" = la del sistema), `urnRange` (6.0), `electionHistoryMax` (20).

#### Cómo probar la parte 3 (4ª tanda)

1. País electivo con partidos: `/pais crear` (elige **Democracia**) o `/pais forma democracia`. Crea moneda y un ayuntamiento (op); funda 2 partidos con 2 jugadores (`/partido crear azul Progresistas`, `/partido crear rojo Conservadores`).
2. Coloca una urna: como gobierno/op, `/urnacrear` → "🗳 Urna electoral".
3. **Abre elección de prueba:** `/ecoadmin elecciones abrir`. Verás el aviso "¡Elecciones abiertas en [TAG]!".
4. **Vota:** clic derecho en la urna → GUI con los partidos → **Votar**. Con otros jugadores, vota distinto. Reintentar votar avisa "ya has votado". `/elecciones` muestra el estado.
5. **Cierra:** `/ecoadmin elecciones cerrar` → se anuncia el ganador y, en democracia, el **fundador del partido ganador** ya puede `/impuesto …`, `/gobierno imprimir …`, etc. (parte 2). En **república** (`/pais forma republica`) el ganador pasa a **presidente**.
6. **Historial:** `/elecciones historial`. Reinicia: elección activa, `lastElectionDay` e historial persisten.
7. **Domingo real:** deja la forma electiva y espera al día configurado (o cambia `electionDayOfWeek`/`electionZoneId` + `/ecoadmin reload`): la elección se abre sola ese día y se cierra al acabar.

### Parte 4 (4ª tanda) — Leyes — implementada

Cada forma tiene sus **leyes** (bloque `laws` en `config/realeconomy_forms.json`): impuesto máximo, si se puede imprimir y cuánto, tope de circulación, si las decisiones grandes necesitan votación, y quién puede crear licencias. **Por encima de todas están las LEYES DEL SERVIDOR** (`serverLaws` en `config/realeconomy.json`): si una ley de un país contradice al servidor, **gana el servidor**. Los comandos y botones de gobierno **comprueban la forma y las leyes antes de actuar** y, si no se puede, dan el **motivo claro**.

- **Modelo:** `politics.Laws` (POJO por forma), `config.ModConfig.ServerLaws` (límites globales), `politics.LawEnforcement` (resuelve el límite efectivo = el menor entre forma y servidor, y devuelve el motivo de bloqueo o `null`). Para "decisiones grandes": `politics.BigDecision` + `politics.BigDecisionManager` (`PersistentState` `realeconomy_decisions`).
- **Leyes aplicadas:**
  - **Impuesto máximo:** `/gobierno impuestos <n>` y la barra/botón "renta" del ayuntamiento no dejan pasar del máximo efectivo (p. ej. anarquía 0%, tecnocracia 60%); la barra se recorta sola y avisa.
  - **Imprimir dinero:** `/gobierno imprimir` y el botón **Imprimir** comprueban `canPrintMoney` (forma) y `allowPrintMoney` (servidor), el **máximo por emisión** (`maxPrintPerAction`) y el **tope de circulación** (`maxCirculation`). Si no se permite, mensaje claro.
  - **Licencias:** `/licencia crear|editar|borrar` comprueba `whoCreatesLicenses` (`GOVERNMENT`/`LEADER`/`NONE`) y `allowLicenses` del servidor.
  - **Anarquía:** al no haber gobierno (parte 2), ya no se puede tocar cuenta, impuestos, licencias ni obras (bloqueo con motivo).
- **Decisiones grandes con votación (repúblicas, `requiresVoteBigDecisions`):** si el gobierno intenta **subir el impuesto por encima de `bigTaxThreshold`** o **imprimir por encima de `bigPrintThreshold`**, no se aplica directamente: se crea una **propuesta** y se avisa a los **líderes de partido**, que votan con `/gobierno aprobar` o `/gobierno rechazar` (mayoría de los líderes actuales). Al aprobarse se aplica sola y se anuncia; al rechazarse se descarta. `/gobierno propuesta` muestra la pendiente. Persiste en el mundo y se limpia al disolver el país.
- **Config (`serverLaws`):** `maxTaxPercent` (100), `allowPrintMoney` (true), `maxPrintPerAction` (-1), `maxCirculation` (-1), `allowLicenses` (true). `/ecoadmin reload` las recarga.

> **Nota de alcance:** el tope de impuesto se aplica al **impuesto general ("renta")** y a la impresión de dinero, que son las palancas que citan las leyes; los **impuestos con nombre** (`/impuesto crear`) y los **tipos** (`/impuesto tipo …`) mantienen su propio rango 0-100. La votación de decisiones grandes se dispara desde los **comandos** `/gobierno`; el botón de la GUI aplica el tope duro pero no abre votación (usa el comando para el flujo con voto).

#### Cómo probar la parte 4 (4ª tanda)

1. **Tope de impuesto:** `/pais forma tecnocracia`. `/gobierno impuestos 80` → bloqueado ("la ley limita el impuesto al 60%"). `/gobierno impuestos 60` pasa. En el ayuntamiento, la barra no sube de 60.
2. **No imprimir:** `/pais forma anarquia` → `/gobierno imprimir 100` dice que en una anarquía nadie gobierna. Edita `forms.json` poniendo `canPrintMoney=false` en otra forma + `/ecoadmin reload` → esa forma no puede imprimir.
3. **Ley del servidor manda:** pon `serverLaws.maxTaxPercent = 30` en `config/realeconomy.json` + `/ecoadmin reload`. Aunque la forma permita 100, `/gobierno impuestos 50` se bloquea a 30. Igual con `allowPrintMoney=false` (nadie imprime) o `maxCirculation`.
4. **Licencias por ley:** en una forma con `whoCreatesLicenses=LEADER`, un ministro no puede `/licencia crear` (solo el líder); con `NONE`, nadie.
5. **Decisión grande con voto (república):** `/pais forma republica`, celebra una elección (parte 3) para tener presidente. Como presidente, `/gobierno impuestos 60` (supera el umbral 50) → se crea una **propuesta**; los **líderes de partido** reciben el aviso y votan `/gobierno aprobar`. Al llegar a la mayoría, se aplica. `/gobierno propuesta` muestra el recuento; `/gobierno rechazar` la tumba. Igual con `/gobierno imprimir 20000` (umbral de impresión).
6. Reinicia: leyes (config), propuestas pendientes y todo lo demás persiste.

## Quinta tanda — Compatibilidad con MCA Reborn y Towns & Towers (en curso)

Hace RealEconomy compatible con dos mods **externos y opcionales**: **MCA Reborn** (aldeanos con familia, para darles trabajos y llevarlos a la guerra) y **Towns and Towers** (solo para que sus pueblos/estructuras convivan con territorios y guerras). RealEconomy **funciona igual sin ellos**. No se copia código ni texturas de esos mods, y los **aldeanos vanilla no se tocan** (lo nuevo solo afecta a los aldeanos de MCA).

> **Prerrequisito:** la tanda necesita **territorios/provincias** y **guerras**, que no existían. Se construyen primero (abajo) como núcleo propio de RealEconomy, y las partes de MCA/T&T se apoyan en ellos.

### Parte 0 (5ª tanda) — Comprobación previa — hecha

- **Loader/versión de MCA Reborn:** MCA Reborn **sí tiene build de Fabric 1.21.1** (Modrinth `minecraft-comes-alive-reborn`, última `7.7.36+1.21.1`). El repo (`Luke100000/minecraft-comes-alive`, rama `1.21.1`) **no usa Architectury**: módulos `common/`+`fabric/`+`neoforge/`. ⇒ **RealEconomy se queda en Fabric 1.21.1**, no hace falta NeoForge ni Architectury.
- **Decisiones tomadas:** (1) construir un **núcleo mínimo de territorios/provincias + guerras** antes de MCA/T&T; (2) enlazar con MCA por **`modCompileOnly` + guardas `FabricLoader.isModLoaded("mca")`** en runtime (dependencia opcional).
- **Notas de integración de MCA** (de leer su código, rama 1.21.1; **Mojmap**, paquete `net.conczin.mca`, mod id `mca`): entidad `VillagerEntityMCA extends Villager` (tipos `mca:male_villager`/`mca:female_villager`; detectar con `instanceof`). Trabajos: `getVillagerBrain().assignJob(Chore, player)` (`Chore` = PROSPECT/HARVEST/CHOP/HUNT/FISH). Familia/dueño: `getRelationships().isMarriedTo(uuid)` / `getFamilyEntry().partner()/father()/mother()/streamParents()`. Guerra/seguir: `getVillagerBrain().setMoveState(MoveState.FOLLOW, player)` + profesión `ProfessionsMCA.GUARD`/`ARCHER`. Equipo: slots vanilla (`equipStack`/`getEquippedStack`), se renderiza. **MCA no tiene un modo pasivo/neutral/agresivo**, así que ese modo de actitud lo guarda y aplica **RealEconomy** por su cuenta.

### Parte A (5ª tanda) — Territorios y provincias — implementada

Posesión del terreno **por chunk**, por país y (opcionalmente) por **provincia**, en cualquier dimensión. Es la base para validar lugares de trabajo (deben estar en territorio propio o sin dueño) y para la **conquista** en guerras. Funciona con cualquier terreno lo genere quien lo genere (Towns & Towers incluido): solo depende de coordenadas de chunk; **no protege bloques de jugadores** por sí mismo (solo modela la propiedad).

- **Modelo** (`com.realeconomy.territory`): `Claim` (país + provincia del chunk), `Province` (id, país, nombre, color; NBT), `TerritoryManager` (`PersistentState` `realeconomy_territory`): claims por dimensión (`dimId → chunkLong → Claim`) y provincias. Métodos clave: `ownerCountry(world,pos)`, `provinceAt(...)`, `canWorkAt(world,pos,countryId)` (sin dueño o país propio), `claim/unclaim`, `createProvince/removeProvince`, `setClaimCountry` y `transferProvince` (conquista), `removeCountry` (al disolver el país, enganchado en `GroupManager.disband`).
- **Gobierno:** reclamar/abandonar y crear/borrar provincias es cosa del **gobierno** del país (usa `Governance`); las vistas son de cualquiera.
- **Comandos** (`/territorio`, alias `/territory`): `reclamar [provincia]` (reclama el chunk donde estás; opcionalmente lo asigna a una provincia), `abandonar`, `info` (dueño del chunk actual), `mapa` (minimapa ASCII de 13×9 chunks alrededor, coloreado por país), `lista` (chunks del país y por provincia). Un chunk ya reclamado por otro país **no** se puede reclamar (se consigue en una guerra).
- **Comandos** (`/provincia`, alias `/province`): `crear <nombre> [color]`, `borrar <nombre>` (sus chunks quedan en el país sin provincia), `color <nombre> <color>`, `lista`.
- **Config:** `maxClaimsPerCountry` (-1 = sin límite).

#### Cómo probar la parte A (5ª tanda)

1. País con gobierno (partes 1-2 de la 2ª tanda). `/territorio info` donde estás → "sin dueño".
2. **Provincia:** `/provincia crear Norte azul`. `/provincia lista` la muestra con 0 chunks.
3. **Reclamar:** `/territorio reclamar Norte` → el chunk actual pasa a tu país (provincia Norte). Muévete a otro chunk y `/territorio reclamar` (sin provincia). `/territorio info`, `/territorio lista` y `/territorio mapa` reflejan lo reclamado (tu chunk = `¤`, reclamados = `■` con el color del país).
4. **No pisar a otros:** con un 2º país, intenta `/territorio reclamar` en un chunk ya reclamado → bloqueado ("se consigue en una guerra").
5. **Abandonar/borrar:** `/territorio abandonar` libera el chunk. `/provincia borrar Norte` → sus chunks quedan en el país sin provincia.
6. **Límite:** pon `maxClaimsPerCountry: 2` + `/ecoadmin reload` → el 3er `reclamar` se bloquea.
7. **Persistencia/limpieza:** reinicia (claims y provincias persisten). Disuelve el país (`/pais disband`) → sus claims y provincias desaparecen.

### Parte B (5ª tanda) — Guerras — implementada

Guerras **país contra país** por una **provincia** del defensor. La **zona de guerra** son los chunks de esa provincia (territorios de la parte A). La victoria se decide por **jugadores vivos** (eliminación), por **rendición** o por **tiempo**. Si gana el atacante, **conquista la provincia** (y su territorio pasa a su país). No depende de qué mod generó el terreno (Towns & Towers incluido): la zona es geométrica.

- **Modelo** (`com.realeconomy.war`): `War` (atacante, defensor, provincia, inicio/fin, participantes por bando, eliminados, +conjuntos de aldeanos reservados para la parte 7; NBT) y `WarManager` (`PersistentState` `realeconomy_wars`: guerras activas + historial). Al declarar, se **alistan automáticamente** los miembros de cada país.
- **Zona y eliminación:** `isInWarZone` = la posición está en un chunk de la provincia contestada. Al **morir** un participante **dentro de la zona** (evento `ServerLivingEntityEvents.AFTER_DEATH`), queda **eliminado** de esa guerra. Si un bando se queda sin jugadores vivos (habiendo tenido), gana el otro. **Los aldeanos NO cuentan** para la victoria por defecto (se añade con config en la parte 7).
- **Fin:** `tick` (1/seg) cierra las guerras cuyo plazo venció → **gana el defensor** (el atacante no logró conquistar). Rendición/cancelación resuelven al instante. Al **disolver** un país en guerra, gana el otro bando. La conquista usa `TerritoryManager.transferProvince`. Historial acotado por `warHistoryMax`.
- **Avisos:** declaración, bajas y resultado se anuncian **en el chat** a los miembros de ambos países y en el log.
- **Leyes:** declarar exige ser **gobierno** (usa `Governance`) y que el servidor permita guerras (`serverLaws.allowWar`). (La prohibición de ejércitos por forma política se añade en la parte 7.)
- **Comandos** (`/guerra`, alias `/war`): `declarar <siglasDefensor> <provincia> [duracion]` (gobierno atacante), `estado` (guerras de tu país) / `lista` (todas), `unirse` (apúntate al bando de tu país), `rendirse` (gobierno defensor concede → gana atacante), `cancelar` (gobierno atacante abandona → gana defensor), `historial`.
- **Config:** `warDefaultDuration` (1d), `warMinDuration` (5m), `warHistoryMax` (20), `warBreakStructures` (true, si se pueden romper estructuras de T&T/terreno en zona de guerra; lo consume la parte 5), `serverLaws.allowWar` (true).

#### Cómo probar la parte B (5ª tanda)

1. Dos países con gobierno. El defensor crea una provincia y le reclama ≥1 chunk (parte A): `/provincia crear Frontera` + `/territorio reclamar Frontera`.
2. **Declarar:** como gobierno del atacante, `/guerra declarar <SIGLAS_DEF> Frontera 10m` → aviso "¡GUERRA!" a ambos países. `/guerra` y `/guerra lista` muestran la guerra (vivos y tiempo restante).
3. **Rendición:** como gobierno del defensor, `/guerra rendirse` → gana el atacante y la provincia Frontera pasa a su país (`/territorio info` en esos chunks lo confirma). `/guerra historial` lo registra.
4. **Eliminación:** declara otra guerra; que un miembro del defensor entre en la zona (sus chunks) y muera allí → "ha caído… queda eliminado". Si cae el único defensor vivo, gana el atacante y conquista.
5. **Cancelar:** `/guerra cancelar` (gobierno atacante) → gana el defensor, sin cambio de territorio.
6. **Tiempo:** declara con `5m` (o baja `warMinDuration`) y espera a que venza → gana el defensor automáticamente.
7. **Leyes:** pon `serverLaws.allowWar=false` + `/ecoadmin reload` → `/guerra declarar` se bloquea. Una anarquía no puede declarar (no hay gobierno).
8. **Persistencia:** reinicia con una guerra activa → persiste (participantes, eliminados, plazo). Disuelve un país en guerra → gana el otro.

### Parte 1 (5ª tanda) — Reglas de compatibilidad y detección — implementada

Los dos mods son **dependencias opcionales**: RealEconomy funciona igual sin ellos. **No hay dependencia de compilación**: el jar de Fabric de MCA usa **ClassTweaker** (archivo `.classtweaker`), que el Loom de este proyecto (1.11.8) no sabe parsear y rompe el *setup* de Minecraft. Por eso la integración con MCA se hace **por reflexión en runtime**, y el build no se acopla a su artefacto. Los **aldeanos vanilla no se tocan nunca**.

- **Detección** (`com.realeconomy.compat.ModCompat`): `isMcaLoaded()` / `isTownsLoaded()` (por `FabricLoader.isModLoaded`), con mod ids `mca` y `t_and_t`. `mcaEnabled()` / `townsEnabled()` = cargado **y** activado por config. Todo el código de integración se protege con estas comprobaciones.
- **Fachada MCA** (`com.realeconomy.compat.mca.Mca`): reflexión perezosa con caché de métodos y avisos-una-vez si una firma no existe (otra versión de MCA → devuelve valor neutro, no rompe). `isMcaVillager(entity)` detecta un aldeano **vivo** de MCA por el id de su tipo (`mca:male_villager`/`mca:female_villager`), sin tocar clases de MCA (seguro aunque MCA no esté). Las llamadas a métodos **vanilla** heredados por el aldeano se hacen con tipos vanilla normales (no por reflexión). Las firmas reales de MCA (rama 1.21.1, paquete `net.conczin.mca`) se conocen por lectura de su código.
- **Config:** `enableMcaIntegration` (true), `enableTownsIntegration` (true) — solo surten efecto si el mod correspondiente está instalado.
- **Estado:** `/ecoadmin compat` (op) muestra si cada mod está instalado y activo.

#### Cómo probar la parte 1 (5ª tanda)

1. **Sin los mods:** arranca RealEconomy solo. `/ecoadmin compat` → "MCA Reborn: no instalado", "Towns and Towers: no instalado". Todo lo demás del mod funciona igual que antes (no hay regresiones).
2. **Con MCA y/o T&T instalados:** `/ecoadmin compat` → "instalado y activo". Pon `enableMcaIntegration:false` + `/ecoadmin reload` → "instalado pero DESACTIVADO".
3. El build compila y arranca con y sin esos mods; no se declara ninguna dependencia de compilación hacia ellos.

### Parte 2 (5ª tanda) — Empleo de aldeanos de MCA — implementada

Los aldeanos **de MCA** (no los vanilla) se pueden **emplear**. **Agáchate + clic derecho** sobre uno abre la GUI de empleo Art Déco (el clic derecho normal deja la GUI propia de MCA intacta). El empleado tiene **cartera propia** y **cobra un sueldo periódico en la moneda del país desde la cuenta del empleador**; si el empleador se queda sin saldo, el aldeano **deja de trabajar** hasta que vuelva a haber. Se puede **despedir**.

- **Quién puede emplear** (`EmploymentActions.employerMode`): el **gobierno** del país → **empleo público** (paga la **cuenta del país**); o un **miembro con licencia de trabajo** → **empleo privado** (paga su **saldo**). La licencia se resuelve con el sistema de licencias personalizadas (`permits(country, player, TRABAJO)`): si el país no define licencia de trabajo, cualquier miembro puede emplear en privado; si la define, solo quien la tenga.
- **Modelo** (`com.realeconomy.employment`): `VillagerJob` (minero/leñador/agricultor/guardia/librero), `EmployedVillager` (empleador, país, público/privado, trabajo, sueldo+periodo, `lastPaid`, **cartera**, activo, **zona de trabajo** = centro+radio+dimensión, **cofre** vinculado; NBT), `EmploymentManager` (`PersistentState` `realeconomy_employment`: `tick` paga 1/seg en tiempo real; marca inactivo si no hay fondos y avisa al empleador; limpia los empleos públicos al disolver el país).
- **Detección de aldeano MCA** por id de tipo (`Mca.isMcaVillager`, parte 1), sin tocar clases de MCA cuando no está. El **comportamiento físico** de cada trabajo (picar, talar, cosechar, guardia, librero) es de las **partes 3-4**; esta parte establece la relación de empleo, el pago y la asignación de zona/cofre.
- **GUI** (`client.EmploymentScreen`): nombre y estado del aldeano, cartera, trabajo (ciclable), sueldo y periodo (campos de texto), botones **Contratar/Actualizar** y **Despedir**. Payloads `OpenEmploymentPayload` (S2C, por UUID del aldeano) y `EmploymentActionPayload` (C2S). Quien no es el empleador ni gobierno lo ve en solo lectura.
- **Comandos** (`/empleo`, alias `/jobsmca`): `info` (del aldeano empleado más cercano), `lista` (tus empleados), `despedir`, `zona <radio>` (fija la zona de trabajo = tu posición + radio), `cofre` (vincula el contenedor al que miras). Obras públicas podrán contratar aldeanos de MCA en partes siguientes.
- **Config:** `villagerWageDefaultPeriod` (1h), `maxEmployeesPerPlayer` (-1), `maxEmployeesPerCountry` (-1), `villagerWorkZoneMaxRadius` (32).

#### Cómo probar la parte 2 (5ª tanda)

> Requiere **MCA Reborn** instalado (es una función de sus aldeanos). Sin MCA no hay aldeanos de MCA; para ver solo la interfaz usa `/ecoadmin debug empleo`.

1. **UI sin MCA:** `/ecoadmin debug empleo` → abre la GUI de empleo Art Déco con un aldeano de ejemplo (cicla trabajo, escribe sueldo/periodo). No contrata nada real.
2. **Con MCA:** ten país con moneda y fondos (gobierno) o una licencia de trabajo. Haz que aparezca un aldeano de MCA. **Agáchate y clic derecho** sobre él → GUI de empleo. Elige **Minero**, sueldo `10`, periodo `30s`, **Contratar**. Mensaje de confirmación (paga el país o pagas tú).
3. **Sueldo:** espera los periodos → la cartera del aldeano sube y la cuenta/tu saldo baja (`/empleo info` junto a él). Vacía la cuenta/saldo → "sin saldo… deja de trabajar"; repón fondos → vuelve a cobrar.
4. **Zona y cofre:** junto al aldeano, `/empleo zona 8` (fija zona aquí) y mirando a un cofre `/empleo cofre`. `/empleo info` muestra zona y cofre.
5. **Gestión:** `/empleo lista`, `/empleo despedir` (o botón Despedir). Clic normal (sin agacharse) sigue abriendo la GUI propia de MCA. Reinicia: los empleos persisten. Disuelve el país → sus empleos públicos terminan.

### Parte 3 (5ª tanda) — Comportamiento físico de los trabajos — implementada

Los aldeanos empleados **trabajan de verdad** en su zona. Se implementa con un bucle propio del servidor (`employment.VillagerWorker`, enganchado al tick) que usa **API vanilla** (el aldeano de MCA es un `VillagerEntity`), **sin tocar la IA interna de MCA** (robusto ante cambios de versión). Se ejecuta **por lotes** y **solo cerca de jugadores / en chunks cargados** (rendimiento: solo se procesan aldeanos dentro de `villagerWorkActivationRange` de algún jugador conectado; nunca se simula en chunks descargados). Estado de trabajo (objetivo, progreso de rotura, carga) **transitorio**.

- **Minero:** busca el bloque minable más cercano de su zona, se acerca, **pica con animación y grietas** (tiempo según la dureza del bloque), se queda los drops (con la herramienta adecuada para que caigan) y, al llenar la carga, va al **cofre vinculado** y los deposita. Rompe cualquier **mena** (tag `c:ores`) y los bloques de `minerExtraBlocks` (piedra, deepslate, tierra, grava, arena por defecto).
- **Leñador:** rompe los **troncos** (tag `minecraft:logs`) de su zona (y las hojas si `loggerBreakLeaves`), **replanta un brote** del árbol talado sobre tierra/hierba si `loggerReplantSapling`, y deposita la madera.
- **Agricultor:** **cosecha** los cultivos maduros (`CropBlock`) de su zona y los **replanta** a edad 0; deposita la comida. (`farmerProduceTable` queda como gancho para producción extra configurable.)
- **Guardia:** se queda en su **puesto** (centro de la zona) y **ataca a los monstruos hostiles** (`HostileEntity`) que entren en su radio (ataque vanilla con enfriamiento); **nunca** ataca jugadores ni aldeanos fuera de una guerra. El equipo (parte 6) le sube el daño.
- **Seguridad:** solo trabaja **dentro de la zona asignada** y **solo en territorio propio o sin dueño** (`TerritoryManager.canWorkAt`, nunca en otro país); **nunca** rompe contenedores/bloques con entidad ni el cofre vinculado; respeta `villagerMaxBreaksPerMinute`. (La detección de "bloque colocado por jugador" se aproxima limitando a bloques naturales de la lista/tags; no hay registro de colocaciones.)
- **Config:** `villagerWorkActivationRange` (48), `villagerSearchRadius` (6), `villagerMaxBreaksPerMinute` (60), `villagerCarryCapacity` (128), `minerExtraBlocks`, `loggerBreakLeaves` (false), `loggerReplantSapling` (true), `farmerProduceTable` ({}), `guardAttackMonsters` (true), `villagerUsesToolDurability` (false, reservado).

> **Nota:** el movimiento usa la navegación vanilla; la IA propia de MCA puede competir por el control y hacer el desplazamiento algo irregular, pero la lógica de trabajo (picar/recoger/depositar/guardar) actúa correctamente cuando el aldeano está en rango. No se altera la IA de MCA a propósito.

#### Cómo probar la parte 3 (5ª tanda)

> Requiere **MCA Reborn**. Emplea un aldeano (parte 2) y asígnale zona y cofre.

1. **Minero:** emplea un aldeano de MCA como **Minero** cerca de una zona con piedra/menas. `/empleo zona 8` sobre la veta y `/empleo cofre` mirando a un cofre cercano. Quédate cerca → el aldeano va a los bloques, los pica (se ven las grietas y el brazo), y al llenarse vuelve y deja los items en el cofre.
2. **Leñador:** zona sobre un bosque → tala troncos, replanta brotes y deposita la madera.
3. **Agricultor:** zona sobre un campo de trigo maduro → cosecha y replanta; la comida acaba en el cofre.
4. **Guardia:** zona en un punto; de noche, un zombi que entre en el radio es atacado. No ataca a jugadores/aldeanos.
5. **Seguridad:** reclama con otro país un chunk dentro de la zona del minero → deja de romper ahí. Pon un cofre dentro de la zona → no lo rompe. Baja `villagerMaxBreaksPerMinute` a 5 → se nota el límite.
6. **Rendimiento:** aléjate >48 bloques → el aldeano deja de procesar trabajo (y retoma al volver). Sin fondos (parte 2) el aldeano para del todo.

### Parte 4 (5ª tanda) — Librero (venta de libros encantados) — implementada

Un aldeano de MCA empleado como **Librero** vende **libros encantados** a los jugadores. **Clic derecho normal** sobre él abre su tienda de libros Art Déco (el clic normal, reservado para el cliente; agáchate + clic sigue siendo la gestión del empleo). Los libros salen de una **lista configurable** (32 por defecto, de Irrompibilidad III a Propulsión acuática III) y el **stock rota cada 24 h** (tiempo real): cada librero pone a la venta N libros al azar con unidades limitadas por ciclo; se guarda cuándo toca la próxima rotación.

- **Precio:** se define en el config en **esmeraldas** y se convierte a la **moneda del país** del librero con el **valor actual** de la moneda (inflación/devaluación, `MonetaryManager.coinsForEmeralds`).
- **Pago:** se cobra del saldo del comprador; el dinero va a la **cuenta del empleador** del librero (o a la **cuenta del país** si `librarianRevenueToCountry`). Se aplica el **impuesto de venta de tienda** del país (`shopSalePercent`): el corte va a la cuenta del país y el resto al recaudador.
- **Icono real + tooltip:** cada libro viaja como `ItemStack` completo (`ItemStack.OPTIONAL_PACKET_CODEC`), así que la GUI pinta el **libro encantado real con su brillo** y su **tooltip** (encantamiento y nivel) al pasar el ratón.
- **Modelo** (`com.realeconomy.employment`): `LibrarianManager` (`PersistentState` `realeconomy_librarian`: stock por aldeano + `nextRotation`; `buildBook` crea el libro resolviendo el encantamiento por id en el registro dinámico, y **devuelve null si el id no existe** → ese libro se omite sin romper nada), `LibrarianActions` (abre la GUI y procesa la compra). `config.ModConfig.BookEntry` define cada libro (encantamiento, nivel, precio).
- **GUI** (`client.LibrarianScreen`): filas con icono+nombre del encantamiento, precio en monedas, unidades y botón **Comprar**; cuenta atrás para el cambio de stock. Payloads `OpenLibrarianPayload` (S2C, con los `ItemStack`) y `LibrarianActionPayload` (C2S).
- **Config:** `librarianBooks` (lista editable de 32), `librarianBooksPerCycle` (5), `librarianUnitsPerBook` (3), `librarianRotationHours` (24), `librarianRevenueToCountry` (false).

#### Cómo probar la parte 4 (5ª tanda)

1. **UI sin MCA:** `/ecoadmin debug librero` → GUI del librero con 5 libros de ejemplo (icono real, brillo, tooltip, precio en tu moneda si tienes país).
2. **Con MCA:** emplea un aldeano como **Librero** (parte 2, agáchate+clic → Contratar). Luego, **clic derecho normal** sobre él → tienda de libros. Compra uno con saldo suficiente → recibes el libro encantado y se cobra en tu moneda; la unidad baja. Sin saldo → aviso.
3. **Rotación:** baja `librarianRotationHours` a 0 (o espera) + `/ecoadmin reload`; al reabrir, el stock cambia. **Impuesto:** pon `ventatienda` (`/impuesto tipo ventatienda 10`) → parte del pago va a la cuenta del país.
4. **Recaudación:** por defecto el dinero va al empleador; pon `librarianRevenueToCountry:true` + reload → va a la cuenta del país.
5. Reinicia: el stock y la fecha de rotación persisten.

### Parte 5 (5ª tanda) — Towns and Towers (territorios y guerras) — implementada

Towns and Towers es un mod de **estructuras/pueblos**. RealEconomy **no toca nada suyo**: ni sus aldeanos, ni sus estructuras, ni les da trabajos/monedas, ni inyecta loot. Lo único que se garantiza es que sus pueblos **conviven** con territorios y guerras.

- **Agnóstico al terreno:** los territorios (parte A) y las guerras (parte B) trabajan **solo con coordenadas de chunk/provincia**, nunca inspeccionan qué mod generó el bloque ni el tipo de estructura. Por eso un pueblo de T&T puede quedar dentro de una provincia, y esa provincia se **reclama, amplía y conquista** con total normalidad, igual que cualquier terreno. No hay ningún caso especial para T&T que pueda fallar con sus estructuras.
- **Romper estructuras en guerra** (`warBreakStructures`, por defecto **true**): por defecto el terreno y las estructuras (T&T incluidas) se pueden romper/modificar con normalidad dentro de una zona de guerra. Si se pone en **false**, `war.WarProtection` + un `PlayerBlockBreakEvents.BEFORE` **impiden romper cualquier bloque dentro de una zona de guerra activa** (y el trabajador aldeano tampoco rompe ahí). No depende de qué mod hizo el bloque: protege toda la zona.
- **Detección:** `ModCompat.isTownsLoaded()` (mod id `t_and_t`) y `enableTownsIntegration`; estado en `/ecoadmin compat`.

#### Cómo probar la parte 5 (5ª tanda)

1. **Con T&T instalado:** genera mundo nuevo; localiza un pueblo de T&T (`/locate structure towns_and_towers:...`). Sus aldeanos y estructuras siguen **exactamente igual** (no tienen trabajos, ni monedas, ni GUIs de RealEconomy).
2. **Territorio:** ve a un pueblo de T&T y `/provincia crear Pueblo` + `/territorio reclamar Pueblo` en sus chunks → se reclaman sin problema. `/territorio mapa` los muestra.
3. **Guerra/conquista:** otro país `/guerra declarar <SIGLAS> Pueblo 10m`; al ganar el atacante, la provincia con el pueblo cambia de dueño (`/territorio info`).
4. **Protección:** `warBreakStructures:false` + `/ecoadmin reload`; dentro de una zona de guerra activa, romper un bloque (vanilla o de T&T) se cancela con aviso. Ponlo en `true` → se puede romper (por defecto).
5. Sin T&T instalado todo sigue igual; con o sin el mod, no hay errores.

### Parte 6 (5ª tanda) — Equipar aldeanos de MCA y modos de actitud — implementada

El **dueño** de un aldeano de MCA (su **familia jugadora**: pareja o padre/madre jugador) puede **equiparlo** y cambiarle el **modo de actitud**. Si no tiene familia jugadora, nadie puede equiparlo ni cambiarle el modo. Trabajo y actitud son **independientes**: un aldeano puede trabajar para el gobierno y tener como dueño a su pareja.

- **Dueño por familia** (`Mca.isOwner`, reflexión): comprueba `getRelationships().isMarriedTo(jugador)` o que el jugador sea `father()/mother()` en el `FamilyTreeNode` de MCA.
- **Equipar:** con el objeto (armadura/espada/arco/ballesta/tridente/maza/escudo/hacha) en la mano, el dueño **clic derecho** sobre el aldeano se lo pone (slot vanilla `equipStack`, se **renderiza** en el modelo por MCA). El objeto anterior vuelve al dueño. También desde la GUI (quitar) y, como se usa el equipo vanilla, persiste con la entidad.
- **Modos** (`attitude.AttitudeMode`, guardado por RealEconomy porque MCA no tiene este concepto; `AttitudeManager`, `PersistentState` `realeconomy_attitude`, por defecto **PASIVO**):
  - **Pasivo:** no ataca nunca.
  - **Neutral:** no ataca primero; si le pegan, se defiende (ataca a `getAttacker()`).
  - **Agresivo:** ataca a la vista. **Fuera de una guerra solo a monstruos hostiles**; a jugadores/aldeanos solo si `aggressiveAttacksPlayersOutsideWar` (por defecto **no**).
- **Combate** (`attitude.AttitudeCombat`, tick por lotes y solo cerca de jugadores): navega al objetivo y lo golpea con enfriamiento (ataque vanilla; el equipo sube el daño). El objetivo en **guerra** lo decide la parte 7 (reutiliza `engage`).
- **Muerte:** al morir un aldeano de MCA suelta su equipo si `villagerDropsEquipmentOnDeath` (por defecto no) y se limpian sus registros (actitud/empleo/librero).
- **GUI** (`client.OwnerScreen`): nombre, 6 slots de equipo (clic en un slot = quitarlo, con tooltip real), y 3 botones de modo (el actual marcado). Payloads `OpenOwnerPayload` (S2C, con los `ItemStack`) y `OwnerActionPayload` (C2S).
- **Comandos** (`/aldeano`, alias `/mcavillager`): `/aldeano` (abre la GUI del aldeano tuyo más cercano), `/aldeano modo <pasivo|neutral|agresivo>`, `/aldeano info`.
- **Config:** `villagerDropsEquipmentOnDeath` (false), `aggressiveAttacksPlayersOutsideWar` (false).

#### Cómo probar la parte 6 (5ª tanda)

1. **UI sin MCA:** `/ecoadmin debug aldeano` → GUI de dueño (slots vacíos y los 3 botones de modo).
2. **Con MCA:** cásate con una aldeana de MCA o ten un hijo (para ser su familia). Con una espada/armadura en la mano, **clic derecho** sobre ella → se la equipa (se ve en el modelo). Repite para casco/peto/etc.
3. **Modo:** `/aldeano` abre la GUI; pon **Agresivo**. De noche, ataca a los zombis que se acerquen; no ataca a jugadores (salvo que actives `aggressiveAttacksPlayersOutsideWar`). Pon **Neutral**: solo contraataca si le pegas. **Pasivo**: nunca ataca. También `/aldeano modo agresivo`.
4. **No-dueño:** otro jugador que no sea su familia no puede equiparla ni cambiarle el modo (aviso).
5. **Muerte:** con `villagerDropsEquipmentOnDeath:true`, al morir suelta el equipo. Reinicia: el modo persiste; el equipo va con la entidad.

### Parte 7 (5ª tanda) — Aldeanos de MCA en la guerra — implementada

Los aldeanos de MCA de un jugador cuyo país está en guerra pueden **luchar en el bando de ese país dentro de la zona de guerra**. En guerra, los **Agresivos** atacan a primera vista a los **participantes del bando enemigo** (jugadores) y a **sus aldeanos**; **Neutral y Pasivo se comportan como siempre**. Las **leyes** del país/servidor mandan: una forma política que prohíba ejércitos bloquea la guerra.

- **Alistar:** el **dueño** usa `/guerra alistar` junto a sus aldeanos de MCA → los mete en la guerra activa de su país, en su bando, con su dueño registrado. Límites `maxVillagersPerPlayerInWar` (5) y `maxVillagersPerCountryInWar` (20). Cada aldeano guarda su bando y su dueño en la guerra (NBT).
- **Combate** (`attitude.AttitudeCombat`): un aldeano **Agresivo** alistado y **dentro de la zona** busca el enemigo más cercano (jugador del bando contrario no eliminado, o aldeano enemigo) y lo ataca (reusa `engage`). Fuera de la zona o si no está alistado, se comporta según su modo normal (monstruos).
- **Victoria:** por defecto **los aldeanos NO cuentan** (la victoria sigue siendo por jugadores vivos). Con `villagersCountForVictory:true`, un bando solo cae cuando no le quedan ni jugadores ni aldeanos; el `tick` revisa la eliminación cada segundo.
- **Muerte:** si un aldeano muere, `WarManager.onVillagerDeath` lo quita de esa guerra (y de los registros de empleo/actitud/librero). Fuera de la guerra, reglas normales de MCA.
- **Leyes:** `/guerra declarar` y `/guerra alistar` comprueban `LawEnforcement.allowsArmy` (campo `allowArmy` en las leyes de la forma, editable en `realeconomy_forms.json`) y `serverLaws.allowWar`. `/guerra` muestra también el recuento de aldeanos por bando.
- **Config:** `maxVillagersPerPlayerInWar` (5), `maxVillagersPerCountryInWar` (20), `villagersCountForVictory` (false), ley de forma `allowArmy` (true por defecto).

#### Cómo probar la parte 7 (5ª tanda)

> Requiere **MCA Reborn**. Dos países con una provincia en disputa (parte A/B) y aldeanos de MCA de tu propiedad (parte 6).

1. Declara una guerra (parte B) entre tu país y otro por una provincia. `/guerra` muestra la guerra (y "aldeanos 0 vs 0").
2. Junto a tus aldeanos de MCA, `/guerra alistar` → se alistan en tu bando (hasta los límites). `/guerra` ahora muestra tu recuento de aldeanos.
3. Pon esos aldeanos en **Agresivo** (`/aldeano modo agresivo`, parte 6) y équipalos. Dentro de la **zona de guerra** (los chunks de la provincia), atacan a los jugadores/aldeanos del bando enemigo; fuera de la zona no.
4. **Victoria:** por defecto solo cuentan los jugadores. Pon `villagersCountForVictory:true` + `/ecoadmin reload`: si un bando pierde todos sus jugadores y aldeanos, pierde la guerra.
5. **Muerte:** si un aldeano alistado muere, sale de la guerra (`/guerra` baja el recuento).
6. **Leyes:** en `realeconomy_forms.json` pon `allowArmy:false` en una forma + `/ecoadmin reload`; un país con esa forma no puede `/guerra declarar` ni `/guerra alistar`.

### Parte 8 (5ª tanda) — Reglas transversales (config, leyes, tema, rendimiento) — implementada

Garantías que cumplen todas las partes anteriores (no añaden código nuevo, se verifican aquí):

- **Todo configurable y guardado en el mundo:** los conmutadores de mods (`enableMcaIntegration`, `enableTownsIntegration`), las tablas de producción y listas de bloques (`minerExtraBlocks`, `loggerBreakLeaves`, `farmerProduceTable`, `librarianBooks`…), los límites (`maxEmployeesPerPlayer/Country`, `maxVillagersPerPlayerInWar/Country`, `villagerMaxBreaksPerMinute`, `maxClaimsPerCountry`) y las opciones de guerra viven en `config/realeconomy.json` (+ `realeconomy_forms.json`), recargables con `/ecoadmin reload`. El estado (territorios, provincias, guerras, empleos, stock de libreros, actitudes) vive en `PersistentState` por mundo y persiste a reinicios. **Mundo limpio:** nada se siembra solo.
- **Las leyes del país y del servidor mandan por encima:** el impuesto/impresión siguen con `LawEnforcement` (3ª/4ª tanda); la guerra/ejércitos respetan `allowArmy` (forma) y `serverLaws.allowWar` (servidor).
- **Interfaces con el tema nuevo:** las GUIs nuevas (empleo, librero, dueño/equipo) usan `ThemedScreen`/`ThemedButton`/`ThemedTextField` y el tema Art Déco, con icono real + brillo + tooltip donde hay items.
- **Rendimiento:** los aldeanos empleados y los agresivos **solo calculan** su trabajo/combate si hay **jugadores cerca** (escaneo alrededor de los jugadores conectados) y en **chunks cargados** (solo se procesan entidades cargadas), **por lotes** (cada 5 ticks, no cada tick) y con **rango limitado**. La detección de enemigos de los agresivos solo corre cerca de jugadores. Si el chunk no está cargado, el aldeano no se simula y retoma al cargarse.
- **Aldeanos vanilla intactos:** todo lo nuevo se filtra por `Mca.isMcaVillager` (tipos `mca:*`); los aldeanos vanilla nunca reciben trabajos, armas, modos ni cartera. Lo previo (cambio de esmeraldas, NPCs del propio mod) no se toca.
- **Dependencias opcionales declaradas:** `mca` y `t_and_t` van en `suggests` de `fabric.mod.json`; no en `depends`.

#### Cómo probarlo con y sin los mods

- **Sin MCA ni T&T:** el mod carga y todo lo anterior (1ª-4ª tanda) funciona igual; `/ecoadmin compat` dice "no instalado". Las GUIs nuevas se previsualizan con `/ecoadmin debug empleo|librero|aldeano`. Territorios (`/territorio`, `/provincia`) y guerras (`/guerra`) funcionan solos (no dependen de MCA/T&T).
- **Con MCA:** aparecen aldeanos de MCA; `/ecoadmin compat` → "instalado y activo". Empleo (parte 2-3), librero (4), equipo/actitud (6) y guerra con aldeanos (7) operativos.
- **Con T&T:** sus pueblos conviven con territorios/guerras (parte 5); no se les añade nada.
- **Con ambos:** todo lo anterior a la vez; sin conflictos ni dependencias de compilación.

## Impuestos por tipo (3ª tanda, parte 4) — implementada

El impuesto único se sustituye por **varios tipos** que el **gobierno** configura por separado; cada uno se activa/desactiva poniéndolo a 0 (o a -1 = "por defecto de la config" en cambio/subasta). **Todo va a la cuenta del país.**

| Tipo | Qué grava | Dónde se cobra | A qué país |
|---|---|---|---|
| **renta** | % del saldo de cada miembro, periódico (`countryTaxPeriod`) | `FinanceManager.tick` (= impuesto general 0-100 ya existente) | propio |
| **ventatienda** | % de cada venta de una tienda de jugador | `ShopActions.buyFromShop` | país del dueño |
| **cuotatienda** | cantidad fija por tienda, periódica | `FinanceManager.collectShopFee` | país del dueño |
| **mercado** | % de lo que cobra un jugador al vender a un NPC comprador/mercado | `BuyerActions.sellItemType` | país del vendedor |
| **transferencia** | % de cada transferencia bancaria | `BankActions.transfer` / `/banco transferir` | país emisor de la moneda |
| **cambio** | comisión del cambio de moneda | `BankActions.quote` | país de la moneda destino |
| **subasta** | comisión al cerrarse una subasta con puja | `AuctionService` | país del vendedor |

- Modelo: `finance.TaxPolicy` (persistente dentro de `FinanceManager`/`GroupFinance`). `cambio` y `subasta` usan `-1 = usar config` (`bankExchangeCommissionPercent` / `auctionCommissionPercent`); `FinanceManager.effectiveExchangePercent/effectiveAuctionPercent` resuelven el valor. `applyTransactionTax(...)` mueve el corte a la cuenta del país.
- **Panel en el ayuntamiento** (`client.TaxScreen`, abierto con el botón "Impuestos por tipo…", solo gobierno): una fila por tipo con su valor y botones −/+ (−5/−1/+1/+5, y −10/+10 en la cuota). El resto de jugadores lo ve en solo lectura. Payloads `OpenTaxPolicyPayload` (S2C) y `TaxPolicyActionPayload` (C2S); acción `OPEN_TAXES` del ayuntamiento.
- **Comandos** (`/impuesto`, gobierno): `/impuesto tipos` (ver todos) y `/impuesto tipo <tipo> <valor> [periodo]`. Los impuestos **con nombre** (`/impuesto crear …`) y la **barra general** (= renta) siguen existiendo.

### Cómo probar la parte 4 (3ª tanda)

1. País con moneda y gobierno (partes 1-2). `/impuesto tipos` muestra todos los tipos a 0 / por defecto.
2. **Panel:** clic derecho en el ayuntamiento como gobierno → botón **Impuestos por tipo…** → ajusta "Venta de tienda" a 10% con +5/+1. Como no-gobierno, el panel es de solo lectura.
3. **Venta de tienda:** monta una tienda y que otro jugador compre → el dueño recibe el precio menos el 10% (va a `/tesoreria`).
4. **Transferencia:** `/impuesto tipo transferencia 5` → `/banco transferir Otro ESP 100` → el receptor recibe 95 y 5 van a la cuenta.
5. **Cambio/subasta:** `/impuesto tipo cambio 10` cambia la comisión del banco; `/impuesto tipo subasta 8` la de subastas. Pon `-1` para volver al valor de la config.
6. **Cuota de tienda:** `/impuesto tipo cuotatienda 50 1d` → cada día cada dueño paga 50 por tienda a la cuenta.
7. **Mercado:** `/impuesto tipo mercado 10` → al vender a un NPC comprador, el vendedor recibe un 10% menos (a la cuenta de su país). Reinicia: la política persiste.

## Licencias personalizadas (3ª tanda, parte 5) — implementada

El **gobierno** (líder + ministros) crea **licencias personalizadas** de su país; los jugadores las **compran en el ayuntamiento** (GUI o comando) y, **sin la licencia correcta, la acción se bloquea**. Conviven con las licencias consumibles antiguas de tienda/subasta (`townhall.LicenseManager`): el sistema nuevo es una capa de permisos **definida por cada país**.

Cada licencia tiene: **nombre**, **precio**, **moneda** (siglas de un país con moneda), **duración** (`permanente` o un periodo `30s/10m/2h/1d`) y **un permiso** de la lista:

| Permiso (clave) | Qué habilita | Dónde se aplica |
|---|---|---|
| `tienda` | montar una tienda | `/tienda crear` |
| `subasta` | colocar subastas | `/subasta crear` |
| `banquero` | conceder préstamos | `/prestamo conceder` |
| `mercado` | abrir un mercado | `/mercado crear` |
| `trabajo` | cobrar por trabajar | `jobs.Jobs.payWage` |

- **Regla de aplicación:** una acción solo se bloquea si el país del jugador **ha definido al menos una licencia** para ese permiso. Si no define ninguna, la acción sigue libre (compatibilidad con el comportamiento anterior). Un jugador sin país nunca queda bloqueado.
- **Modelo:** `license.LicensePermission` (enum), `license.License` (definición: id, país, nombre, precio, moneda, duración 0=permanente, permiso), `license.CustomLicenseManager` (`PersistentState` `realeconomy_customlicenses`): definiciones por país + licencias que posee cada jugador con su **caducidad** (`grant`, `holdsActive`, `permits(countryId, player, perm)`). Al disolver el país se borran sus licencias (`removeCountry`, enganchado en `GroupManager.disband`).
- **Pago:** el precio se cobra del **saldo** del comprador y va a la **cuenta del país** que emite la licencia.
- **GUI:** botón **Licencias del país…** en el ayuntamiento (abierto a todos) → `client.LicenseScreen` lista las licencias con su precio/duración/permiso, el estado de posesión y un botón **Comprar**. La creación/edición es por comando. Payloads `OpenLicensesPayload`/`LicenseView` (S2C) y `LicenseActionPayload` (C2S); acción `OPEN_LICENSES` del ayuntamiento.

Comandos (`/licencia`, alias `/licencias`):

| Comando | Quién | Qué hace |
|---|---|---|
| `/licencia crear <nombre> <precio> <siglasMoneda> <duracion> <permiso>` | gobierno | define una licencia (`duracion` = `permanente` o periodo) |
| `/licencia editar <nombre> <campo> <valor>` | gobierno | edita `precio`/`moneda`/`duracion`/`permiso`/`renombrar` |
| `/licencia borrar <nombre>` | gobierno | elimina la definición (y la retira a quien la tuviera) |
| `/licencia lista` | cualquiera | licencias que ofrece tu país |
| `/licencia comprar <nombre>` | miembro (junto a un ayuntamiento) | compra una licencia |
| `/licencia mias` | miembro | tus licencias y cuándo caducan |

### Cómo probar la parte 5 (3ª tanda)

1. País con moneda y gobierno (partes 1-2). Da saldo: `/ecoadmin saldo set TuNombre ESP 1000`.
2. **Crear (gobierno):** `/licencia crear Comercio 100 ESP permanente tienda` y `/licencia crear Temporal 50 ESP 2m subasta`. `/licencia lista` las muestra.
3. **Bloqueo:** sin tener la licencia, `/tienda crear` avisa de que tu país exige licencia. `/subasta crear …` igual.
4. **Comprar:** junto a un ayuntamiento (`/ayuntamientocrear`, op), clic derecho → **Licencias del país…** → **Comprar** (o `/licencia comprar Comercio`). Se te cobran 100 a tu saldo y van a `/tesoreria`. Ahora `/tienda crear` funciona.
5. **Caducidad:** compra la de `2m`; `/licencia mias` muestra la cuenta atrás. Al caducar, `/subasta crear` vuelve a bloquearse.
6. **Editar/borrar:** `/licencia editar Comercio precio 200`, `/licencia editar Comercio permiso mercado`, `/licencia borrar Temporal`.
7. **Sin definir:** un país que no cree ninguna licencia de `tienda` deja `/tienda crear` libre (como antes). Reinicia: definiciones y posesiones persisten; disuelve el país y se borran.

## Mercado solo-venta (3ª tanda, parte 6) — implementada

El **mercado del país** (`/mercado`, NPC "🏪 Mercado [SIGLAS]") pasa a ser **solo de venta**: los jugadores **solo pueden vender** sus items al mercado (el mercado **solo compra**). Se elimina la posibilidad de que el mercado venda al jugador. Las transacciones siguen usando **monedas físicas** de la **caja** del mercado (modelo de la parte 7, 1ª tanda), pero el precio ahora es **dinámico por oferta y demanda**.

- **Lista de lo que compra:** el gobierno define qué items compra el mercado con `/mercado comprar <moneda> [precio]`, con el item en la mano, junto al mercado. Si no se indica `precio`, se usa el **precio base de la config** (`marketBasePrice`), así que todas las ofertas pueden nacer con el **mismo precio base configurable**. La GUI del cliente lista esos items con su precio actual.
- **Oferta y demanda (por oferta/item):** cada oferta tiene un **multiplicador** (1.0 = base) que **baja al venderse** (`marketDropPerUnit` por unidad, suelo `marketMinMultiplier`) y se **recupera** hacia la base con el **tiempo real** (`marketRecoveryPerSecond`). El precio actual = `base × multiplicador` (mínimo 1). Se puede **desactivar** (`marketDynamicPricing=false` → precio fijo = base). El estado va **dentro de cada oferta** (persistido en el NBT del mercado; el reloj es real, sobrevive a reinicios).
- **Tendencia (▲/▼):** la GUI y `/mercado info` muestran la tendencia de cada oferta: **▼ roja** si hubo ventas en los últimos `marketTrendWindowSeconds` segundos (precio a la baja), **▲ verde** si se está recuperando por debajo de la base, **= gris** si está en la base.
- **Pago desde la caja:** vender entrega el item a la caja y paga al jugador con monedas físicas de la caja. Si la caja no tiene monedas o está llena, se vende solo lo que alcance.
- **Migración:** las ofertas antiguas en las que el mercado *vendía* al jugador (`type=SELL`) se **descartan** al cargar; las de compra se conservan (con precio base = su precio anterior).

Clases: `market.MarketOffer` (precio dinámico + tendencia), `market.MarketActions` (solo venta), `network.MarketOfferView` (vista con precio actual, base y tendencia), `client.MarketScreen`. Reutiliza `shop.ShopStorage` como caja.

Comandos del gobierno (`/mercado`, alias `/market`):

| Comando | Qué hace |
|---|---|
| `/mercado crear` | crea el NPC de mercado del país (máx `maxMarketsPerGroup`) |
| `/mercado comprar <moneda> [precio]` | añade qué item compra el mercado (precio = base de config si se omite) |
| `/mercado retirar <n>` | quita la oferta n (ver `/mercado info`) |
| `/mercado info` | lista lo que compra, con precio actual, base y tendencia |
| `/mercado borrar` | elimina el mercado y devuelve el contenido de la caja |

Config (parte 6): `marketDynamicPricing` (true), `marketBasePrice` (10), `marketDropPerUnit` (0.002), `marketRecoveryPerSecond` (0.001), `marketMinMultiplier` (0.2), `marketTrendWindowSeconds` (30). Siguen vigentes `maxMarketsPerGroup`, `marketRange`, `marketGuiTexture`.

### Cómo probar la parte 6 (3ª tanda)

1. País con moneda (partes 1-2) y **monedas físicas**: `/ecoadmin darmoneda TuNombre ESP 500`.
2. Gobierno: `/mercado crear` → aparece "🏪 Mercado [ESP]". Clic derecho (miembro) abre la **caja**; mete monedas (para que pueda pagar).
3. `/mercado comprar ESP` (precio base) con troncos en la mano, y `/mercado comprar ESP 8` con otro item. `/mercado info` muestra los dos a su precio base y tendencia `=`.
4. **Vender:** con **otro jugador** (sin ser del país), clic derecho en el mercado → GUI "El mercado compra:". Pulsa **Vender** (Shift = x64). Recibes monedas de la caja.
5. **Oferta/demanda:** vende muchas unidades seguidas de un item → su precio baja y la tendencia pasa a **▼**. Espera un rato sin vender → el precio sube hacia la base y la tendencia pasa a **▲**, hasta volver a `=`.
6. **Sin fondos:** vacía la caja de monedas → al vender avisa "El mercado no tiene monedas para pagar" (no imprime dinero).
7. **Desactivar:** `marketDynamicPricing=false` + `/ecoadmin reload` → precio fijo = base, tendencia `=`.
8. `/ecoadmin debug mercado` abre la GUI de ejemplo con las tres tendencias. Reinicia: mercado, ofertas y su estado de precio persisten.

## Tiendas: icono real del item (3ª tanda, parte 7) — implementada

Las ofertas de las tiendas de jugador (y, por coherencia, del mercado) se pintan con el **icono real del item de Minecraft** —el modelo que renderiza el propio juego— incluido el **brillo de encantamiento**, en vez de un emoji o icono genérico. Las ofertas de **venta y compra** se mantienen tal cual (misma lógica); solo cambia/mejora la presentación.

- **Datos completos por la red:** la oferta se crea con `hand.copyWithCount(1)`, que conserva **todos los componentes** del item (encantamientos, nombre personalizado, durabilidad...). El servidor guarda el `ItemStack` completo (`template.encode(lookup)`) y lo envía con `ItemStack.OPTIONAL_PACKET_CODEC`, así que el cliente recibe el item íntegro.
- **Icono + brillo:** `DrawContext.drawItem(stack, x, y)` pinta el modelo real del item; si el stack tiene encantamientos (`stack.hasGlint()`), el propio render añade el **brillo**. No hay que hacer nada extra para el glint: va incluido.
- **Tooltip al pasar el ratón:** `ThemedScreen` gana un hook `renderTooltips(...)` (se pinta por encima de todo, después de los widgets) y un helper `itemTooltip(ctx, stack, iconX, iconY, mouseX, mouseY)` que, si el ratón está sobre el icono 16×16, dibuja el **tooltip real** del item (nombre, encantamientos, durabilidad, lore...). Lo usan `client.ShopScreen` y `client.MarketScreen`.

Clases tocadas: `client.ui.ThemedScreen` (hook `renderTooltips` + `itemTooltip`), `client.ShopScreen`, `client.MarketScreen`. Sin cambios de red ni de persistencia (el `ItemStack` ya viajaba completo).

### Cómo probar la parte 7 (3ª tanda)

1. Consigue un item **encantado** (p. ej. `/give TuNombre minecraft:diamond_sword{Enchantments:[{id:"minecraft:sharpness",lvl:3}]} 1`, o encántalo en una mesa).
2. Monta una tienda (parte 5) y, con el item encantado en la mano, junto a la tienda: `/tienda vender 100 ESP` (y mete stock en el almacén) o `/tienda comprar 80 ESP 10`.
3. Con **otro jugador**, clic derecho en la tienda → en la fila de la oferta verás el **icono real** del item con el **brillo** de encantamiento.
4. Pasa el ratón por encima del icono → aparece el **tooltip** con el nombre, los encantamientos y la durabilidad.
5. Lo mismo vale para el **mercado** (`/mercado comprar ESP` con un item encantado → `/ecoadmin debug mercado` o clic derecho como cliente): icono real + brillo + tooltip.
6. Comprueba que **comprar y vender siguen funcionando igual** que antes (la lógica no cambió).

## Banco: cambio de moneda y transferencias (3ª tanda, parte 3) — implementada

**Cambio de moneda:** el jugador elige **moneda origen**, **moneda destino** y **cantidad**. Se calcula con el **valor actual** de cada moneda (inflación/devaluación, `MonetaryManager.value`): `esmeraldas = cantidad × valor(origen)`, `recibido = floor(esmeraldas / valor(destino)) − comisión`. La **comisión** (`bankExchangeCommissionPercent`, por defecto 2 %) va a la **cuenta del país que emite la moneda destino**. La GUI **muestra cuánto recibirá antes de confirmar** (previsualización local con los valores del snapshot); el servidor recalcula de forma autoritativa al confirmar. El cambio requiere estar **junto a un banco**.

**Transferencia:** el jugador indica **jugador**, **moneda** y **cantidad**; se valida que el jugador exista (caché de usuarios, funciona offline) y que haya saldo. Disponible por GUI del banco y por comando (sin necesidad de banco cerca; es un pago digital).

- Lógica en `bank.BankActions`: `exchange(...)` y `quote(...)` (cotización reutilizable, devuelve `Quote{net, commission, ok, error}`). `OpenBankPayload` lleva ahora `balances`, la lista `currencies` (`BankCurrencyInfo{id,name,tag,colorName,value}`) y `exchangeCommission`. `BankActionPayload.EXCHANGE` (3): `currencyId` = origen, `target` = id destino.
- GUI (`client.BankScreen`): sección "Cambio de moneda" con selector de destino (cicla), previsualización "Recibirás: …" y botón **Cambiar**.
- Comandos (`/banco`): `/banco transferir <jugador> <siglasMoneda> <cantidad>` y `/banco cambiar <siglasOrigen> <siglasDestino> <cantidad>` (este, junto a un banco).

Config (parte 3): `bankExchangeCommissionPercent` (2).

### Cómo probar la parte 3 (3ª tanda)

1. Dos países con moneda y valores distintos: crea `ESP` y `FRA`, imprime en uno para devaluarlo (`/gobierno imprimir 5000` como gobierno de ESP) → su valor baja.
2. Dale saldo: `/ecoadmin saldo set TuNombre ESP 1000`. Junto a un banco (`/bancocrear`, op), clic derecho → sección **Cambio de moneda**: selecciona la fila ESP (origen), cicla el destino a FRA, escribe `100` → "Recibirás: X FRA" según los valores; pulsa **Cambiar**.
3. Comprueba que la comisión aparece en la cuenta del país destino (`/tesoreria` como miembro de FRA).
4. Por comando: `/banco cambiar ESP FRA 100` (junto a un banco). Transferencia: `/banco transferir Otro ESP 50` (desde cualquier sitio) o por el botón **Transferir** de la GUI.

## Gobierno y permisos (3ª tanda, parte 2) — implementada

Cada país tiene un **gobierno** = el **líder** + los **ministros** que el líder designe. **Solo el gobierno** puede gestionar la economía del país; el resto de miembros (y ajenos) **solo pueden ver**.

- Modelo: `Group` guarda un conjunto `ministers` (NBT `ministers`). `Group.isGovernment(uuid)` = líder o ministro. Al salir del país o transferir el liderazgo, se limpian los ministros afectados. `GroupManager.addMinister/removeMinister` (persisten).
- **Acciones restringidas al gobierno** (antes solo líder): impuestos (`/impuesto`, `/gobierno impuestos`), tesorería y sueldos (`/tesoreria`), política monetaria imprimir/quemar (`/gobierno`), obras públicas (`/obra`), préstamos (`/prestamo`), sueldos de trabajos (`/trabajo sueldo`), moneda (`/moneda`), mercado del país (`/mercado`). Las vistas (`info`, `lista`, `stats`, `ver`...) siguen abiertas a cualquiera.
- **GUI del ayuntamiento:** el botón **Imprimir** y la **barra de impuestos** solo se muestran al gobierno (flag `canPrint` = `isGovernment`); a los demás aparece "(solo el gobierno gestiona)".
- **NPCs compradores:** siguen siendo **solo del líder** (`/comprador`), según lo pedido (no de los ministros).

Comandos de gobierno (bajo `/pais`, alias `/paises`/`/grupo`/`/group`):

| Comando | Quién | Qué hace |
|---|---|---|
| `/pais gobierno` o `/pais gobierno lista` | miembro | muestra líder + ministros del país |
| `/pais gobierno add <jugador>` | líder | nombra ministro a un miembro |
| `/pais gobierno remove <jugador>` | líder | destituye a un ministro |

`/pais info` lista también a los ministros.

> **Nota de diseño:** fundar/unirse a **partidos** (`/partido`) se mantiene abierto a cualquier miembro (pluralismo político / oposición), no restringido al gobierno, pese a que la lista de la tanda mencionaba "partidos". Si se prefiere que solo el gobierno gestione partidos, es un cambio de una línea en `PartyCommands`.
> **Nota de alcance:** el alta/baja de ministros es por **comando**; los botones de acción de gobierno ya se **ocultan** a quien no es gobierno. Un panel de gestión de gobierno con botones en el ayuntamiento se puede añadir si se quiere.

## Parte 1 (2ª tanda) — Países y partidos (implementada)

Los antiguos "grupos" son ahora **PAÍSES**. Para no perder datos, las clases internas siguen siendo `Group`/`GroupManager` y el `PersistentState` sigue siendo `realeconomy_groups`; **los países existentes se migran solos**. Todo el texto de cara al jugador dice "país".

Modelo de país (`com.realeconomy.group`):
- `Group` — datos de un país (id UUID estable, nombre, siglas, color `Formatting`, líder, mapa miembro→nombre). Serialización NBT.
- `GroupManager` — `PersistentState`, fuente de verdad. Índices jugador→país y gestión de siglas únicas. Refleja cada país en un **equipo de scoreboard** (`re_<id>`) para pintar el nombre con las siglas y el color del país de forma 100 % vanilla (tab, placa y chat). **Esto vale también para los miembros de un partido: siempre se ven las siglas del país** (ej. `Caiver [ESP]`). Las invitaciones son en memoria.

Modelo de partido (`com.realeconomy.party`):
- `Party` — un partido dentro de un país: id, countryId, nombre, color, fundador (líder del partido) y miembros. Un jugador solo puede estar en **un** partido, y de su propio país. NBT.
- `PartyManager` — `PersistentState` (`realeconomy_parties`). Índice jugador→partido; crear/unirse/salir/disolver; al disolver el país o al salir un miembro del país, se limpia su partido.

Comandos de país (`/pais`, alias `/paises`, `/grupo`, `/group`):

| Comando | Quién | Qué hace |
|---|---|---|
| `/pais create <siglas> <color> [nombre]` | sin país | crea el país (siglas 2-4 letras únicas) |
| `/pais invite\|accept\|deny\|kick\|leave\|transfer\|disband` | según rol | igual que antes (invitar, aceptar, expulsar, salir, transferir, disolver) |
| `/pais settag\|setcolor\|setname` | líder | cambia siglas / color / nombre |
| `/pais info [siglas]` | cualquiera | info del país (incluye sus partidos) |
| `/pais list` | cualquiera | lista todos los países |

Comandos de partido (`/partido`, alias `/party`):

| Comando | Quién | Qué hace |
|---|---|---|
| `/partido crear <color> <nombre>` | miembro de un país, junto al ayuntamiento | funda un partido pagando `partyPrice` a la cuenta del país |
| `/partido unirse <nombre>` | miembro del país | se une a un partido de tu país (libre) |
| `/partido salir` | miembro de partido | sale del partido |
| `/partido lista` | miembro | lista los partidos de tu país |
| `/partido info [nombre]` | miembro | info de un partido |
| `/partido color <color>` | fundador | cambia el color del partido |
| `/partido borrar` | fundador | disuelve el partido |

Config (partidos): `partyPrice` (100), `partyCurrencyTag` (vacío = moneda del propio país).

> El pago de fundar un partido requiere estar junto a un **ayuntamiento** (parte 4) y va a la **cuenta del país**. La moneda es la del propio país salvo que se configure otra en `partyCurrencyTag`.

Colores aceptados (español o inglés): rojo, verde, azul, amarillo, dorado/naranja, cian, rosa, morado_oscuro, blanco, gris, negro, y variantes `_oscuro`.

### Cómo probar la parte 1 (2ª tanda)

1. `./gradlew runServer` (EULA la primera vez) + cliente 1.21.1 con Fabric, o `./gradlew runClient`.
2. **País:** `/pais create ESP rojo España` → tu nombre se ve `TuNombre [ESP]` en rojo (tab, placa, chat). `/pais info`, `/pais list` siguen funcionando; `/grupo` y `/group` también (compatibilidad).
3. **Migración:** si ya tenías "grupos" de antes, al arrancar siguen existiendo como países (mismos datos, mismas siglas y colores).
4. **Partido:** necesitas moneda del país (`/moneda crear Peseta`) y un ayuntamiento cerca (`/ayuntamientocrear` como op). Dale saldo: `/ecoadmin saldo set TuNombre ESP 500`. Junto al ayuntamiento: `/partido crear azul Progresistas` → se cobran 100 a tu saldo y van a `/tesoreria` del país.
5. Con otro jugador del mismo país: `/partido unirse Progresistas`. Comprueba que **sigue viéndose `[ESP]`** sobre su cabeza (no el partido). `/partido lista`, `/partido info`.
6. `/partido salir`, `/partido borrar` (fundador). Sal del país (`/pais leave`) y verás que también sales del partido.
7. Reinicia el servidor: países y partidos persisten.

## Parte 2 (2ª tanda) — Moneda por defecto (implementada)

Al **crear un país se crea automáticamente su moneda**, sin tener que usar `/moneda crear`. Ya no hace falta ningún paso manual para tener divisa.

- **Moneda dorada por defecto:** el item de moneda (`realeconomy:coin`) usa su **propia textura dorada** (`assets/realeconomy/textures/item/coin.png`) y el **icono por defecto** del tooltip/GUIs es ahora una **moneda dorada** (`realeconomy/default_icon.png`), no el círculo gris anterior. Ambos son sustituibles por resource pack.
- **Nombre del item = nombre de la divisa + siglas del país,** con el color del país. Por defecto, al crear el país `España [ESP]`, la moneda se llama `España` y el item se titula **`España [ESP]`** en el color del país. El líder la renombra con `/moneda nombre <nombre>` (p. ej. a `Peseta`) y cambia el icono con `/moneda icono <url>`.
- **En el menú creativo** aparece el item de moneda (pestaña "RealEconomy"). Para dar monedas de una divisa concreta se usa el comando de admin `/ecoadmin darmoneda <jugador> <siglas> <cantidad>` (entrega monedas físicas con su `currency_id`).
- **Migración:** al arrancar el servidor, cualquier país antiguo que no tuviera moneda recibe la suya automáticamente (`SERVER_STARTED`), así que los mundos existentes quedan al día sin perder nada.

Detalles técnicos:
- `GroupManager.createGroup` llama a `ensureCurrency(g)` (crea la divisa con `CurrencyManager.createForGroup(g, g.getName(), null)` si no existe). `ensureCurrency` también se usa en la migración de arranque.
- `CoinItem.getName(stack)` resuelve el título vía `CoinItem.NAME_RESOLVER` (instalado por el cliente con los metadatos de `ClientCurrencyStore`); en el servidor no se pinta nombre. El tooltip ya no repite el nombre (va en el título); solo avisa "Moneda desconocida" si el cliente aún no conoce la divisa.

### Cómo probar la parte 2 (2ª tanda)

Necesitas **cliente** (el nombre/icono se ven en el cliente): `./gradlew runClient` o conéctate a `./gradlew runServer`.

1. `/pais create ESP rojo España` → además de crear el país, verás "Moneda creada automáticamente: España [ESP] (moneda dorada por defecto)".
2. Date monedas (op): `/ecoadmin darmoneda TuNombre ESP 100`. En el inventario el item se titula **`España [ESP]`** en rojo y se ve dorado; el tooltip muestra el icono dorado por defecto.
3. Renombra la divisa: `/moneda nombre Peseta` → el item pasa a titularse **`Peseta [ESP]`**. Cambia el icono: `/moneda icono <url>` (si falla la URL, se queda el dorado por defecto).
4. Abre el menú creativo → pestaña **RealEconomy**: aparece el item de moneda.
5. Reinicia: la moneda, su nombre e icono persisten. Un país creado antes de esta parte (sin moneda) recibe la suya al arrancar.

## Parte 3 (2ª tanda) — Gobierno y política monetaria (implementada)

El **líder gobierna la moneda** de su país: imprime o quema dinero, y el **valor de la moneda frente a la esmeralda** cambia según cuánto dinero haya en circulación. Además hay **obras públicas** (pedidos de materiales pagados desde la cuenta del país).

### Política monetaria

- **Imprimir dinero:** el líder crea dinero que entra en la **cuenta del país** (`/tesoreria`). Cada emisión se registra (cantidad, fecha, quién). Se hace con `/gobierno imprimir <cantidad>` **o** desde la GUI del ayuntamiento (botón *Imprimir*, solo el líder).
- **Quemar/retirar dinero:** `/gobierno quemar <cantidad>` saca dinero de la cuenta del país y lo retira de la circulación (sube el valor).
- **Devaluación (circulación → valor):** cada moneda tiene una **circulación** = dinero emitido por el país (impreso − quemado). El **valor** (esmeraldas por moneda) baja cuanto más dinero hay en circulación, con una **fórmula configurable**:
  `valor = currencyBaseValue × currencyBaseCirculation / (currencyBaseCirculation + circulación)`, acotado entre `currencyMinValue` y `currencyMaxValue`. Se puede desactivar (`devaluationEnabled=false` → valor fijo = `currencyBaseValue`).
- **Uso del valor:** las conversiones esmeralda↔moneda usan este valor actual (`MonetaryManager.coinsForEmeralds` / `emeraldsForCoins`). Lo consumirán los **NPCs compradores por categoría** de la parte 4.
- **Estadísticas:** `/gobierno stats` (circulación, valor actual, equivalencia con esmeralda, saldo de la cuenta) y `/gobierno historial` (últimas emisiones con su circulación y valor resultantes). La GUI del ayuntamiento también muestra circulación y valor del país del jugador.

### Obras públicas

El líder crea un **pedido de materiales** (item, cantidad, precio por unidad, moneda) con `/obra crear`. **Cualquier jugador** entrega ese item con `/obra entregar <id>` y **cobra por unidad desde la cuenta del país** (va a su saldo). La obra **se cierra al completarse** y se **pausa si el país no tiene fondos** (no se puede entregar hasta que vuelva a haber saldo). Los ids de obra son **globales** (únicos en el servidor), así que se entrega por id sin importar el país.

> Interpretación del punto "la cuenta del país puede comprar en tiendas/NPCs": se ha implementado la vía de **entrega por los jugadores** (los jugadores consiguen los materiales —incluso comprándolos en tiendas/mercados— y los entregan cobrando de la cuenta). No hay (todavía) un comprador automático que gaste la cuenta del país directamente en tiendas; se puede añadir si se quiere.

Clases: `economy.MonetaryManager` (circulación, valor, registro de emisiones; `PersistentState` `realeconomy_monetary`), `gov.PublicWork` + `gov.PublicWorksManager` (`realeconomy_publicworks`), `command.GovernmentCommands`, `command.PublicWorksCommands`. GUI: `OpenTownhallPayload`/`TownhallActionPayload`/`TownhallScreen`/`TownhallActions` ampliados.

Comandos de gobierno (`/gobierno`, alias `/gov`):

| Comando | Quién | Qué hace |
|---|---|---|
| `/gobierno imprimir <cantidad>` | líder | crea dinero en la cuenta del país (sube circulación, baja valor) |
| `/gobierno quemar <cantidad>` | líder | retira dinero de la cuenta y de la circulación (sube valor) |
| `/gobierno stats` | miembro | circulación, valor actual, equivalencia y saldo de la cuenta |
| `/gobierno historial` | miembro | últimas emisiones (imprimir/quemar) con circulación y valor |

Comandos de obras públicas (`/obra`, alias `/obras`):

| Comando | Quién | Qué hace |
|---|---|---|
| `/obra crear <cantidad> <precioUnidad> [siglasMoneda]` | líder | crea un pedido del item que lleva en la mano (moneda = la del país por defecto) |
| `/obra lista` | cualquiera | lista las obras abiertas del servidor (con progreso y si están sin fondos) |
| `/obra info <id>` | cualquiera | detalle de una obra |
| `/obra entregar <id> [cantidad]` | cualquiera | entrega el item y cobra por unidad desde la cuenta del país |
| `/obra cerrar <id>` | líder | cierra una obra de su país |

Config (parte 3): `devaluationEnabled` (true), `currencyBaseValue` (1.0), `currencyBaseCirculation` (1000), `currencyMinValue` (0.01), `currencyMaxValue` (100.0), `moneyLogMax` (50).

### Cómo probar la parte 3 (2ª tanda)

1. País con moneda (partes 1-2): `/pais create ESP rojo España`.
2. **Imprimir:** `/gobierno stats` (circulación 0, valor 1.000). `/gobierno imprimir 1000` → la cuenta sube (`/tesoreria`), circulación 1000 y valor baja a ~0.5. Imprime más y verás el valor caer. `/gobierno historial`.
3. **Quemar:** `/gobierno quemar 500` → circulación baja a 500 y el valor sube. (Necesitas saldo en la cuenta; imprime antes.)
4. **GUI:** como líder, clic derecho en un ayuntamiento (`/ayuntamientocrear`, op) → ves circulación/valor y el botón *Imprimir* con su casilla de cantidad.
5. **Obra pública:** con un item en la mano, `/obra crear 64 2` (pide 64 de ese item a 2/ud). Asegura saldo en la cuenta (imprime). Otro jugador con ese item: `/obra entregar <id> 10` → cobra 20 y la obra avanza. `/obra lista`, `/obra info <id>`.
6. **Pausa por fondos:** vacía la cuenta (o pide más caro que el saldo) → `/obra entregar` avisa "sin fondos (pausada)" hasta que vuelva a haber saldo.
7. Reinicia: circulación, historial y obras persisten. Disuelve el país: su política monetaria y obras se eliminan.

## Parte 4 (2ª tanda) — NPCs compradores por categoría (implementada)

El líder monta **NPC compradores** (aldeano "🛒 Comprador [SIGLAS]") que **compran items por categoría** (madera, minerales, comida, cultivos, piedra...). El pago sale de la **cuenta del país** y los precios se mueven por **oferta y demanda**.

- **Categorías por tags (configurables):** cada categoría es un **tag de items** de Minecraft o convencional (`c:...`). Por defecto: `madera`→`minecraft:logs`, `tablones`→`minecraft:planks`, `minerales`→`c:ores`, `carbon`→`minecraft:coals`, `lana`→`minecraft:wool`, `comida`→`c:foods`, `cultivos`→`c:crops`. Se editan/añaden en `buyerCategories` de la config. (Si un tag no existe en tu instalación, esa categoría simplemente no casa con nada hasta que lo cambies.)
- **Precio base igual para todo** (`buyerBasePrice`, en **esmeraldas**), con **override** por categoría (`buyerCategoryPrices`) o por item (`buyerItemPrices`).
- **Oferta y demanda:** cada categoría tiene un multiplicador (0–1) que **baja al vender** (`buyerDropPerUnit` por unidad, suelo `buyerMinMultiplier`) y **se recupera** hacia la base con el tiempo real (`buyerRecoveryPerSecond`). El estado es **global** del servidor por categoría (todo el mundo mueve el mismo mercado) y sobrevive a reinicios (reloj real).
- **Se puede desactivar** (`buyersDynamicPricing=false`) → precios fijos = base.
- **Precio en esmeraldas → monedas del país:** el precio final por unidad se paga en la **moneda del país** del comprador usando el **valor actual** de la parte 3 (`coinsForEmeralds`). Una moneda devaluada paga más monedas por el mismo valor en esmeraldas.
- **Pago desde la cuenta del país:** vender da saldo al jugador y descuenta de la cuenta del país. Si la cuenta se queda sin fondos, se vende solo lo que alcance. Los items vendidos se consumen (los absorbe el Estado).
- **Panel/GUI:** clic derecho en el comprador abre una GUI con el **precio actual por categoría** (y su % respecto a la base) y un botón **Vender inventario** (vende de golpe todo lo comprable que lleves).

Clases: `buyer.BuyerCategories` (resuelve categoría/precio por tags), `buyer.BuyerMarketManager` (`PersistentState` `realeconomy_buyers`: multiplicadores y recuperación), `buyer.BuyerEntities` (NPC), `buyer.BuyerActions` (venta + panel), `command.BuyerCommands`, `network.OpenBuyerPayload`/`BuyerActionPayload`/`BuyerCategoryView`, `client.BuyerScreen`.

Comandos (`/comprador`, alias `/buyer`):

| Comando | Quién | Qué hace |
|---|---|---|
| `/comprador crear` | líder | crea un NPC comprador de su país en su posición |
| `/comprador borrar` | líder | elimina el comprador de su país más cercano |
| `/comprador precios` | cualquiera | precio por categoría del comprador cercano (o de tu país) |
| `/comprador vender [cantidad]` | cualquiera | vende el item de la mano al comprador cercano |

Config (parte 4): `buyersDynamicPricing` (true), `buyerBasePrice` (1.0), `buyerDropPerUnit` (0.002), `buyerRecoveryPerSecond` (0.001), `buyerMinMultiplier` (0.2), `buyerRange` (6.0), `buyerGuiTexture`, `buyerCategories` (mapa), `buyerCategoryPrices`, `buyerItemPrices`.

### Cómo probar la parte 4 (2ª tanda)

1. País con moneda (partes 1-2) y **fondos en la cuenta** (parte 3: `/gobierno imprimir 5000`).
2. Como líder: `/comprador crear` → aparece "🛒 Comprador [ESP]".
3. `/comprador precios` → lista madera, minerales, etc. con su precio en tu moneda y el %.
4. Con troncos en la mano (categoría `madera`): `/comprador vender` cerca del NPC → cobras y la cuenta baja. Repite y mira en `/comprador precios` cómo baja el % de `madera` (oferta/demanda). Espera un rato y se recupera.
5. **GUI:** clic derecho en el comprador → ves el precio por categoría y el botón **Vender inventario** (vende todo lo comprable de golpe).
6. **Devaluación (parte 3):** imprime mucho (`/gobierno imprimir 20000`) y verás que el mismo material paga **más monedas** (moneda devaluada).
7. **Precios fijos:** pon `buyersDynamicPricing=false` + `/ecoadmin reload` → el % se queda al 100%.
8. Reinicia: los multiplicadores y los NPC persisten.

## Parte 5 (2ª tanda) — Subastas (implementada)

Sistema de subastas sobre **pedestales** con **puja en tiempo real**, comisión para el país y **cobro offline**.

- **Licencia de subasta:** se compra en el ayuntamiento con `/subasta licencia` (requiere estar cerca de uno). Precio y moneda en config (`auctionLicensePrice`, `auctionLicenseCurrencyTag`). Colocar una subasta **consume 1 licencia** (como las tiendas).
- **Pedestales (op):** `/subastacrear` coloca un pedestal (bloque de madera configurable) en el bloque al que miras; `/subastaborrar` elimina el pedestal más cercano (y devuelve la subasta si la había). El sitio se construye poniendo varios pedestales.
- **Colocar subasta:** con el item en la mano y junto a un pedestal **libre**, `/subasta crear <precio> <moneda> <duracion>` (duración en tiempo real: `30s`/`10m`/`2h`/`1d`, mínimo `auctionMinDuration`). El item sale de tu mano y queda en la subasta.
- **Display:** sobre el pedestal **flota y rota** el item subastado (entidad item sin gravedad ni recogida), con el **nombre vivo** encima (puja/estado y tiempo restante). El brillo de encantamientos se ve en el propio item; detalles completos (vendedor, puja, durabilidad, encantamientos, tiempo) con `/subasta info` o **clic derecho** en el pedestal.
- **Pujar:** `/subasta pujar <cantidad>` junto al pedestal (o clic derecho para ver la info y pujar por comando). La puja debe superar la actual (o el precio inicial). El dinero se **retiene** (se descuenta del saldo al pujar) y se **devuelve** al pujador anterior cuando alguien le supera.
- **Cierre (en tiempo real):** al vencer, el **mejor postor** recibe el item y el **vendedor** cobra la puja menos una **comisión** (`auctionCommissionPercent`) que va a la **cuenta del país del vendedor**. Si no hubo pujas, el item vuelve al vendedor. El vendedor puede **cancelar** (`/subasta cancelar`) solo si no hay pujas.
- **Cobro offline:** si ganas o te devuelven un item estando desconectado, queda **pendiente** y se te entrega al conectarte (o con `/subasta recoger`). Los reembolsos de puja son saldo digital (funcionan desconectado).
- **Persistencia:** pedestales, subastas (con su item), pujas retenidas y entregas pendientes sobreviven a reinicios; los plazos van por reloj real.

Clases: `auction.Auction`, `auction.AuctionManager` (`PersistentState` `realeconomy_auctions`), `auction.AuctionDisplay` (item flotante), `auction.AuctionService` (colocar/pujar/cancelar/cerrar/cobrar), `command.AuctionCommands`. Licencia en `townhall.LicenseManager` (contador aparte).

Comandos de subasta (`/subasta`, alias `/auction`):

| Comando | Quién | Qué hace |
|---|---|---|
| `/subasta licencia` | cualquiera (junto a ayuntamiento) | compra una licencia de subasta |
| `/subasta crear <precio> <moneda> <duracion>` | con licencia, junto a pedestal libre | subasta el item de la mano |
| `/subasta pujar <cantidad>` | cualquiera, junto al pedestal | puja (retiene el dinero; devuelve al anterior) |
| `/subasta cancelar` | vendedor, sin pujas | cancela y recupera el item |
| `/subasta info` | cualquiera | detalle de la subasta cercana |
| `/subasta lista` | cualquiera | todas las subastas activas |
| `/subasta recoger` | cualquiera | recoge items pendientes (offline) |
| `/subastacrear` / `/subastaborrar` | op | crea/elimina un pedestal |

Config (parte 5): `auctionLicensePrice` (100), `auctionLicenseCurrencyTag` (""), `auctionCommissionPercent` (5), `auctionMinDuration` ("30s"), `auctionRange` (4.0), `auctionPedestalBlock` ("minecraft:oak_fence"), `auctionGuiTexture`.

> Nota: la puja y la colocación son por comando (+ clic derecho muestra la info); la subasta cumple "GUI de Minecraft" con el item flotante y su nombre vivo. Se puede añadir una GUI de pujar al clic derecho si se quiere.

### Cómo probar la parte 5 (2ª tanda)

1. País con moneda y saldo (partes 1-3). Configura `auctionLicenseCurrencyTag` = siglas de un país con moneda (p. ej. `ESP`) + `/ecoadmin reload`.
2. Como op: mira a un bloque y `/subastacrear` (repite para varios pedestales). Da saldo a los jugadores.
3. `/subasta licencia` junto a un ayuntamiento → compra la licencia.
4. Con un item en la mano, junto a un pedestal libre: `/subasta crear 50 ESP 2m` → el item flota y rota sobre el pedestal.
5. Con otro jugador: `/subasta pujar 60` → se le descuenta 60; si otro puja 70, al primero se le devuelven 60. `/subasta info`, clic derecho en el pedestal.
6. Espera a que venza (o pon `30s`): el ganador recibe el item, el vendedor cobra 70 menos la comisión (que va a la cuenta de su país). Desconéctate antes de ganar y reconéctate → recoges el item (o `/subasta recoger`).
7. `/subasta cancelar` (sin pujas) devuelve el item. Reinicia: pedestales y subastas persisten.

## Impuesto general del país (barra 0–100, 2ª tanda)

Además de los impuestos con nombre (`/impuesto`, parte 6), el país tiene un **impuesto general** ajustable con una **barra de 0 a 100 %**: cobra ese porcentaje del **saldo de cada miembro** hacia la cuenta del país de forma periódica (`countryTaxPeriod`, por defecto `1h`).

- **Barra en el ayuntamiento:** el líder abre la GUI del ayuntamiento y mueve el **slider "Impuestos: X%"** (se aplica al soltar).
- **Por comando:** `/gobierno impuestos` (ver), `/gobierno impuestos <0-100>` (líder ajusta), `/gobierno impuestos cobrar` (líder fuerza el cobro ahora, para pruebas).
- Se guarda en `finance.FinanceManager` (campo `taxRate` por país) y se cobra en el `tick` cada `countryTaxPeriod`.

Cómo probar: `/gobierno impuestos 20` (o mueve la barra), da saldo a los miembros, `/gobierno impuestos cobrar` → cada miembro paga el 20% de su saldo a `/tesoreria`. Ajusta a 0 para desactivarlo.

## Parte 2 — Monedas y cuentas (implementada)

**Diseño del item:** una única moneda `realeconomy:coin` para todas las divisas; cada stack lleva el componente `realeconomy:currency_id`. Así las monedas de la misma divisa se apilan y las de divisas distintas no. El item usa un sprite base vanilla en inventario/mundo; el **icono descargado** se muestra en el tooltip de la moneda (y lo reutilizarán las GUIs del banco/tiendas en partes 3-5).

**Pipeline del icono por URL:** el líder da una URL → el servidor la descarga en un hilo aparte, valida que sea imagen, la normaliza a PNG 64×64 y la cachea en `world/realeconomy/icons/<id>.png` → se difunde a los clientes por red → el cliente la registra como textura dinámica. Si la URL falla, usa el icono por defecto incluido en el mod.

Clases:
- `economy.Currency` — datos de una divisa (id == groupId en esta versión, nombre, URL icono). NBT.
- `economy.CurrencyManager` — `PersistentState`. Crea/renombra/cambia icono, orquesta descarga y difusión.
- `economy.BalanceManager` — `PersistentState`. Libro de **saldos digitales** por jugador y moneda (`get/add/subtract/transfer`). Lo usarán banco, tiendas, impuestos, sueldos.
- `economy.IconCache` — descarga/validación/normalización/caché del icono; icono por defecto.
- `item.ModItems` — item `coin`, componente `currency_id`, grupo creativo. `createCoin(currencyId, count)`, `currencyOf(stack)`.
- `network.*` — payloads S2C `CurrencyListPayload` (metadatos) y `CurrencyIconPayload` (bytes PNG); `ModNetworking` envía al entrar y difunde al crear/cambiar.
- `client.*` — `ClientCurrencyStore` (metadatos + texturas registradas), `CoinTooltipComponent` (pinta el icono en el tooltip), `RealEconomyClient` (receptores de red + tooltips).

Comandos del líder (`/moneda`, alias `/currency`):

| Comando | Qué hace |
|---|---|
| `/moneda crear <nombre> [url]` | crea la moneda del grupo (una por grupo); sin url usa icono por defecto |
| `/moneda icono <url>` | cambia el icono (re-descarga) |
| `/moneda nombre <nombre>` | renombra la moneda |
| `/moneda info [siglas]` | info de la moneda (propia o del grupo indicado) |

Comandos de administrador (`/ecoadmin`, requiere op nivel 2):

| Comando | Qué hace |
|---|---|
| `/ecoadmin darmoneda <jugador> <siglas> <cantidad>` | entrega monedas **físicas** de esa divisa |
| `/ecoadmin saldo ver <jugador>` | muestra los saldos digitales |
| `/ecoadmin saldo set <jugador> <siglas> <cantidad>` | fija el saldo |
| `/ecoadmin saldo add <jugador> <siglas> <cantidad>` | suma/resta al saldo |
| `/ecoadmin reload` | recarga `config/realeconomy.json` sin reiniciar |
| `/ecoadmin debug <interfaz>` | abre una GUI sin NPC: `banco`, `ayuntamiento`, `tienda`, `mercado`, `comprador` |

> Nota: las monedas físicas (item) y el saldo digital (banco) son cosas distintas. El banco (parte 3) convertirá entre ambos (ingresar/retirar).

### Cómo probar la parte 2

Necesitas **cliente** (el icono se ve en el cliente). Usa `./gradlew runClient` + mundo abierto a LAN, o conéctate a `./gradlew runServer`.

1. `/group create ESP rojo España` (necesitas un grupo y ser líder).
2. `/moneda crear Peseta https://i.imgur.com/<algo>.png` → "descargando icono..." en el log del servidor verás "Icono de ... descargado y cacheado".
   - Prueba también una URL inválida: debe usar el icono por defecto sin romperse.
3. Date monedas (como op): `/ecoadmin darmoneda TuNombre ESP 100`.
4. Pon el ratón sobre las monedas en el inventario → el tooltip muestra el **icono descargado** y el nombre `[ESP] Peseta` en rojo. Las monedas de la misma divisa se apilan.
5. Guárdalas en un cofre, sácalas, pásaselas a otro jugador → se comportan como un item normal.
6. Saldos: `/ecoadmin saldo set TuNombre ESP 500`, `/ecoadmin saldo ver TuNombre`.
7. Reinicia el servidor: monedas (item), divisa e iconos cacheados y saldos persisten.

## Parte 3 — Banco NPC con interfaz (implementada)

**NPC:** un aldeano vanilla sin IA, invulnerable, silencioso y persistente, con nombre "🏦 Banco" y la scoreboard tag `realeconomy_bank`. Se guarda solo en el mundo (es una entidad). `UseEntityCallback` detecta el clic derecho en el servidor y abre la GUI.

**GUI (`BankScreen`, cliente):** una `Screen` propia dibujada con texturas y fuente vanilla. Fondo en `realeconomy:textures/gui/bank.png` (panel estilo contenedor) → **sustituible por resource pack** (mismo identificador) o cambiando `bankGuiTexture` en la config. Muestra una fila por moneda (icono descargado + nombre con color de grupo + saldo + monedas en inventario), con campos de cantidad y jugador y botones **Ingresar / Retirar / Transferir**.

**Flujo de red:** clic derecho → servidor envía `OpenBankPayload` (saldos) → cliente abre `BankScreen`. Cada acción manda `BankActionPayload` (C2S); el servidor valida (incluye que haya un banco a `bankRange`), aplica y responde con `OpenBankPayload` actualizado.

**Lógica (`BankActions`, servidor):**
- Ingresar: convierte monedas físicas del inventario en saldo (cantidad indicada o todas).
- Retirar: convierte saldo en monedas físicas (si hay saldo).
- Transferir: mueve saldo a otro jugador (resuelto por nombre vía caché; vale aunque esté desconectado).

Clases: `config.ModConfig`, `bank.BankEntities`, `bank.BankActions`, `command.BankCommands`, `network.OpenBankPayload`, `network.BankActionPayload`, `client.BankScreen`.

Comandos (solo op nivel 2):

| Comando | Qué hace |
|---|---|
| `/bancocrear` | crea un NPC banco en tu posición |
| `/bancoborrar` | elimina el banco más cercano |

> Nota: hice `/bancocrear` **solo op** (colocar NPCs es acción de administrador). Si quieres que cualquiera pueda crearlos, se quita el `requires`.

### Cómo probar la parte 3

Con `./gradlew runClient` (necesitas ver la GUI) y siendo op (en un servidor de pruebas lo eres por defecto en modo singleplayer con trucos / en runServer usa `/op`).

1. Ten un grupo y moneda (parte 2) y algunas monedas: `/ecoadmin darmoneda TuNombre ESP 200`.
2. `/bancocrear` → aparece el aldeano "🏦 Banco".
3. Clic derecho en el banco → se abre la interfaz con tus saldos y monedas.
4. Selecciona la moneda (clic en su fila), escribe una cantidad y pulsa **Ingresar** (item→saldo) y **Retirar** (saldo→item). Deja la cantidad vacía al ingresar para meter todas.
5. **Transferir:** escribe la cantidad y el nombre de otro jugador y pulsa Transferir.
6. Aléjate más de `bankRange` (config, 6 por defecto) e intenta una acción → "No hay ningún banco cerca".
7. `/bancoborrar` elimina el NPC. Reinicia el servidor: el banco (entidad) y los saldos persisten.
8. Resource pack / config: sustituye `assets/realeconomy/textures/gui/bank.png` en un pack o cambia `bankGuiTexture` en `config/realeconomy.json`.

## Parte 4 — Ayuntamiento y licencias (implementada)

**NPC** idéntico en mecánica al banco (aldeano marcado `realeconomy_townhall`, nombre "🏛 Ayuntamiento"). El helper de NPCs se extrajo a `npc.NpcUtil` y tanto banco como ayuntamiento lo reutilizan.

**Licencias consumibles:** comprar en el ayuntamiento da **+1 licencia disponible** (`LicenseManager`, persistente). Crear una tienda (parte 5) **consume 1 licencia**; una licencia usada no se reutiliza. Tope de tiendas: `maxShopsPerPlayer`.

**Precio y moneda configurables:** `licensePrice` y `licenseCurrencyTag` (siglas del grupo cuya moneda se usa). Si `licenseCurrencyTag` está vacío o no resuelve a una moneda, la compra se bloquea con aviso y la GUI muestra "Licencias no configuradas". Tras editar la config, `/ecoadmin reload` la recarga sin reiniciar.

**GUI (`TownhallScreen`):** panel vanilla (`realeconomy:textures/gui/townhall.png`, sustituible) con precio, moneda, licencias disponibles y botón **Comprar licencia** (deshabilitado si no está configurado).

Clases: `townhall.LicenseManager`, `townhall.TownhallEntities`, `townhall.TownhallActions`, `command.TownhallCommands`, `network.OpenTownhallPayload`, `network.TownhallActionPayload`, `client.TownhallScreen`, `npc.NpcUtil`.

Comandos (solo op): `/ayuntamientocrear`, `/ayuntamientoborrar`. Admin: `/ecoadmin reload`.

### Cómo probar la parte 4

1. Edita `config/realeconomy.json`: `licenseCurrencyTag` = siglas de un grupo con moneda (p. ej. `ESP`), `licensePrice` = p. ej. `50`. Luego `/ecoadmin reload` (o reinicia).
2. Dale saldo: `/ecoadmin saldo set TuNombre ESP 500`.
3. `/ayuntamientocrear` → aparece el NPC "🏛 Ayuntamiento".
4. Clic derecho → GUI con precio y licencias. Pulsa **Comprar licencia** → descuenta del saldo y sube "Licencias disponibles".
5. Sin saldo suficiente → aviso de saldo insuficiente. Sin `licenseCurrencyTag` → "Licencias no configuradas".
6. Reinicia: licencias y NPC persisten. (El consumo de licencias se verá al crear tiendas en la parte 5.)

## Parte 5 — Tiendas de jugadores (implementada)

**NPC tienda** por jugador (aldeano "🏪 Tienda de <dueño>", tag `realeconomy_shop` + tag con el id de la tienda). Crear una tienda **consume 1 licencia** (parte 4) y respeta `maxShopsPerPlayer`.

**Interacción con el NPC:**
- Dueño → abre el **almacén** (GUI de cofre vanilla 9×3, `GenericContainerScreenHandler`). Ahí mete stock para vender y recoge lo comprado.
- Cliente → abre la **GUI de compra** (`ShopScreen`): lista de ofertas con icono del item (render vanilla), precio, moneda (con color del grupo) y disponible; botón Comprar/Vender (Shift = x64).

**Ofertas** (se crean con el item en la mano, estando junto a tu tienda):
- Venta (`/tienda vender <precio> <moneda>`): clientes compran; pagan de su **saldo**, va al saldo del dueño; el item sale del almacén. Al llegar el stock a 0, la oferta se retira sola.
- Compra (`/tienda comprar <precio> <moneda> <cantidadMax>`): clientes venden; el pago sale del **saldo del dueño**, el item va al almacén. Al agotar saldo del dueño o la cantidad, la oferta se retira sola.

**Economía:** las transacciones de tienda usan el **saldo** (digital), coherente con el banco. Los jugadores consiguen saldo ingresando monedas físicas en el banco.

Clases: `shop.Shop`, `shop.Offer`, `shop.ShopStorage`, `shop.ShopManager`, `shop.ShopEntities`, `shop.ShopActions`, `command.ShopCommands`, `network.OfferView/OpenShopPayload/ShopActionPayload`, `client.ShopScreen`.

Comandos del dueño (`/tienda`, alias `/shop`): `crear`, `vender <precio> <moneda>`, `comprar <precio> <moneda> <cantidadMax>`, `retirar <n>`, `info`, `borrar`.

### Cómo probar la parte 5

1. Consigue una licencia (parte 4) y saldo. `/tienda crear` → aparece el NPC tienda (consume 1 licencia).
2. Con el item en la mano, junto a tu tienda: `/tienda vender 10 ESP` (vendes ese item a 10) y `/tienda comprar 8 ESP 32` (compras ese item a 8, máx 32).
3. Clic derecho en tu propia tienda → se abre el **almacén**; mete unidades del item que vendes.
4. Con **otro jugador**, clic derecho en la tienda → GUI de compra. Compra (paga de su saldo) y vende (cobra del saldo del dueño). Comprueba el auto-retiro al agotar stock/cantidad/saldo.
5. `/tienda info`, `/tienda retirar 1`, `/tienda borrar` (te devuelve el stock).
6. Reinicia: tiendas, ofertas y almacén persisten.

## Parte 6 — Impuestos y cuenta del grupo (implementada)

**Cuenta del grupo:** cada grupo tiene una cuenta que es, técnicamente, el saldo del `BalanceManager` indexado por la **UUID del grupo** (en la moneda del grupo, cuyo id == id del grupo). Así se reutilizan persistencia y transferencias. Al disolver el grupo, su cuenta y sus datos financieros se limpian.

**Impuestos** (`finance.Tax`): el líder crea impuestos que se cobran **a todos los miembros** (conectados o no) hacia la cuenta del grupo, con **periodicidad en tiempo real**:
- **Fijo:** una cantidad fija por miembro.
- **Porcentaje:** un % del saldo del miembro en la moneda del grupo.
- Si un miembro no tiene saldo suficiente, se le cobra lo que tenga (sin deuda en esta parte).

**Sueldos** (`finance.Salary`): el líder fija sueldos recurrentes que la **cuenta del grupo paga** a un miembro en tiempo real. Si la cuenta no tiene saldo, el pago se salta hasta el siguiente ciclo (avisa al líder).

**Subvenciones:** pago puntual desde la cuenta del grupo a un miembro.

**Procesamiento:** `FinanceManager.tick(server)` se registra en `ServerTickEvents.END_SERVER_TICK` y se autolimita a revisar una vez por segundo; cobra/paga lo vencido según `System.currentTimeMillis()`, de modo que los periodos transcurren aunque el servidor se reinicie entre medias (se basan en reloj real, no en ticks).

**Periodos:** texto tipo `30s`, `10m`, `2h`, `1d` (mínimo 5s).

Clases: `finance.Tax`, `finance.Salary`, `finance.FinanceManager`, `util.TimeUtil`, `command.TaxCommands`, `command.TreasuryCommands`.

Comandos del líder — impuestos (`/impuesto`, alias `/tax`):

| Comando | Qué hace |
|---|---|
| `/impuesto crear <nombre> fijo <cantidad> <periodo>` | impuesto de cantidad fija |
| `/impuesto crear <nombre> porcentaje <1-100> <periodo>` | impuesto porcentual sobre el saldo |
| `/impuesto lista` | lista impuestos (cualquier miembro) y cuándo toca el próximo cobro |
| `/impuesto borrar <nombre>` | elimina un impuesto |
| `/impuesto cobrar <nombre>` | fuerza el cobro inmediato (útil para probar) |

Comandos — tesorería (`/tesoreria`, alias `/cuenta`):

| Comando | Quién | Qué hace |
|---|---|---|
| `/tesoreria` o `/tesoreria ver` | miembro | muestra el saldo de la cuenta del grupo |
| `/tesoreria pagar <jugador> <cantidad>` | líder | subvención puntual desde la cuenta a un miembro |
| `/tesoreria sueldos` | miembro | lista los sueldos configurados |
| `/tesoreria sueldoset <jugador> <cantidad> <periodo>` | líder | fija/actualiza el sueldo recurrente de un miembro |
| `/tesoreria sueldoquitar <jugador>` | líder | elimina el sueldo de un miembro |

> `<jugador>` se resuelve entre los miembros del grupo por nombre, así que funciona aunque estén desconectados.

### Cómo probar la parte 6

1. Ten un grupo con moneda (partes 1-2) y dale saldo a varios miembros: `/ecoadmin saldo set Jugador1 ESP 1000`.
2. **Impuesto fijo:** `/impuesto crear iva fijo 50 10s` → cada 10s se cobran 50 a cada miembro hacia la cuenta. Mira `/tesoreria` subir.
3. **Impuesto %:** `/impuesto crear renta porcentaje 10 30s`. Fuerza uno con `/impuesto cobrar iva`.
4. `/impuesto lista` muestra el próximo cobro de cada uno.
5. **Sueldo:** `/tesoreria sueldoset Jugador2 20 15s` → cada 15s Jugador2 cobra 20 de la cuenta (si hay saldo). `/tesoreria sueldos` para verlo.
6. **Subvención:** `/tesoreria pagar Jugador2 100` (sale de la cuenta).
7. Reinicia el servidor: cuenta, impuestos y sueldos persisten; los periodos siguen contando en tiempo real.
8. Disuelve el grupo: la cuenta y sus impuestos/sueldos se eliminan.

## Parte 7 — Mercados de grupo / NPCs (implementada)

> **Actualizado en la 3ª tanda (parte 6):** el mercado pasó a ser **solo de venta** (el mercado solo compra; los jugadores solo venden) con **precio por oferta/demanda y tendencia**. Las ofertas de venta del mercado (`/mercado vender`) y el cambio esmeraldas↔monedas en esa dirección **ya no existen**. La descripción de abajo es la original; ver *Mercado solo-venta (3ª tanda, parte 6)* para el comportamiento vigente.

**Idea:** un grupo monta un **mercado** (NPC aldeano "🏪 Mercado [SIGLAS]") que compra y vende items a cambio de **monedas físicas** (el item moneda de la parte 2), no de saldo digital. Esto cierra el círculo de la economía física y da el **cambio esmeraldas↔monedas** como caso particular (basta una oferta cuyo item sea la esmeralda).

**Caja (till):** cada mercado tiene una caja (cofre vanilla 9×3) que guarda su stock de items y sus monedas. Cualquier **miembro del grupo** la abre con clic derecho; los **no miembros** ven la GUI de compra/venta.

**Ofertas** (las crea el **líder**, con el item en la mano, junto al mercado):
- Venta (`/mercado vender <precio> <moneda>`): el mercado vende ese item. El cliente paga monedas (entran a la caja) y recibe el item del stock. Límite: stock de la caja y monedas del cliente.
- Compra (`/mercado comprar <precio> <moneda>`): el mercado compra ese item. El cliente entrega el item (entra a la caja) y recibe monedas de la caja. Límite: monedas de la caja y espacio.

**Diferencia con las tiendas (parte 5):** la tienda es de un jugador y mueve **saldo digital**; el mercado es del **grupo** y mueve **monedas físicas** desde su caja. Como la caja es finita, el mercado no "imprime" dinero: hay que abastecerla (monedas para pagar, items para vender), lo que mantiene la economía autosostenible.

**Cambio esmeraldas↔monedas:** el líder crea dos ofertas con la esmeralda como item: `/mercado comprar 5 ESP` (el mercado compra esmeraldas y da monedas) y `/mercado vender 5 ESP` (el mercado vende esmeraldas por monedas). Así los jugadores entran/salen de la moneda del grupo.

Clases: `market.Market`, `market.MarketOffer`, `market.MarketManager`, `market.MarketEntities`, `market.MarketActions`, `command.MarketCommands`, `network.OpenMarketPayload`, `network.MarketActionPayload`, `client.MarketScreen`. Reutiliza `shop.ShopStorage` como caja y `network.OfferView`.

Comandos del líder (`/mercado`, alias `/market`):

| Comando | Qué hace |
|---|---|
| `/mercado crear` | crea el NPC de mercado del grupo (máx `maxMarketsPerGroup`) |
| `/mercado vender <precio> <moneda>` | oferta: el mercado **vende** el item de la mano |
| `/mercado comprar <precio> <moneda>` | oferta: el mercado **compra** el item de la mano |
| `/mercado retirar <n>` | quita la oferta n (ver `/mercado info`) |
| `/mercado info` | lista las ofertas del mercado |
| `/mercado borrar` | elimina el mercado y te devuelve el contenido de la caja |

Config: `maxMarketsPerGroup` (2), `marketRange` (6), `marketGuiTexture` (sustituible por resource pack).

### Cómo probar la parte 7

1. Grupo con moneda (partes 1-2) y monedas físicas: `/ecoadmin darmoneda TuNombre ESP 200`.
2. `/mercado crear` → aparece el NPC "🏪 Mercado [ESP]".
3. Clic derecho (como miembro) → abre la **caja**. Mete monedas (para poder comprar a jugadores) y/o items (para venderlos).
4. Junto al mercado, con un item en la mano: `/mercado vender 10 ESP` (lo vende a 10) y, con una esmeralda, `/mercado comprar 5 ESP` (compra esmeraldas por 5).
5. Con **otro jugador** (sin ser del grupo), clic derecho → GUI del mercado. Compra (paga monedas físicas) y vende (recibe monedas de la caja). Shift = x64.
6. Comprueba que el mercado se queda sin stock/monedas cuando se agota la caja (no imprime dinero).
7. Reinicia: mercado, ofertas y caja persisten.

## Parte 8 — Trabajos y sueldos (implementada)

Los miembros de un grupo **cobran por trabajar**. El **líder** fija el sueldo por acción de cada trabajo y el pago sale de la **cuenta del grupo** (la que alimentan los impuestos de la parte 6), de modo que el sistema **no genera inflación**: si la cuenta no tiene fondos, el trabajo no paga. Las ganancias van al **saldo** del trabajador (retirable en el banco).

Trabajos (`jobs.Job`):
- `mineria` — romper **minerales** (tag `c:ores`; no cuenta la piedra, para limitar el farmeo).
- `tala` — romper **troncos** (tag `minecraft:logs`).
- `agricultura` — cosechar **cultivos maduros** (`CropBlock` maduro).
- `banca` — realizar una operación en el banco (ingresar/retirar/transferir).

Se enganchan con `PlayerBlockBreakEvents.AFTER` (bloques) y desde `bank.BankActions` (banca). El aviso de cobro usa la **barra de acción** para no spamear el chat.

Clases: `jobs.Job`, `jobs.Jobs` (pago desde la cuenta), `jobs.JobEvents` (eventos de bloque), `command.JobCommands`. Los sueldos por trabajo se guardan en `finance.FinanceManager` (junto a impuestos y sueldos recurrentes).

Comandos (`/trabajo`, alias `/jobs`):

| Comando | Quién | Qué hace |
|---|---|---|
| `/trabajo sueldos` (o `info`) | miembro | lista el sueldo por acción de cada trabajo |
| `/trabajo sueldo <trabajo> <cantidad>` | líder | fija el sueldo (0 = desactiva). `<trabajo>`: mineria, tala, agricultura, banca |

### Cómo probar la parte 8

1. Grupo con moneda (partes 1-2) y **fondos en la cuenta** del grupo (p. ej. crea un impuesto y espera, o usa `/ecoadmin saldo add` sobre la UUID del grupo no es directo → lo más fácil: `/tesoreria` se llena con impuestos de la parte 6; para probar rápido, crea un impuesto fijo y fuérzalo con `/impuesto cobrar`).
2. Como líder: `/trabajo sueldo mineria 5`, `/trabajo sueldo tala 3`, `/trabajo sueldo agricultura 4`, `/trabajo sueldo banca 2`.
3. `/trabajo sueldos` para ver la tabla.
4. Rompe un mineral / un tronco / un cultivo maduro → ves "+5 Peseta (mineria)" en la barra de acción y tu saldo sube (y la cuenta del grupo baja).
5. Haz una operación en el banco → cobras el sueldo de `banca`.
6. Vacía la cuenta del grupo → el trabajo deja de pagar (sin inflación). Reinicia: los sueldos por trabajo persisten.

> Nota de diseño: pagar desde la cuenta del grupo cierra el bucle económico (impuestos → cuenta → sueldos/trabajos) y mantiene la economía autosostenible. Si prefieres que trabajar "cree" moneda nueva, se puede cambiar `jobs.Jobs.payWage` para abonar sin descontar de la cuenta.

## Parte 9 — Banquero y préstamos (implementada)

El **líder** actúa como banquero del grupo: concede **préstamos desde la cuenta del grupo** (la de la parte 6) a un miembro, con **capital, interés fijo y plazo en tiempo real**. El capital sale de la cuenta hacia el saldo del deudor; el total a devolver es `capital + capital*interés/100`.

**Cobro automático y deuda:** cuando llega el plazo, `FinanceManager.tick` descuenta lo pendiente del **saldo** del deudor (connectado o no) y lo devuelve a la cuenta del grupo. Si el saldo no llega, se cobra lo que haya y el resto **queda como deuda**; se sigue cobrando solo en ciclos posteriores conforme el deudor recupere saldo, hasta saldarlo. Se avisa al deudor al vencer y al quedar saldado.

**Autosostenible:** el dinero sale de la cuenta y vuelve a ella (más el interés, que también lo aportan los propios jugadores), así que no se imprime dinero. Un miembro solo puede tener **un préstamo activo** a la vez.

Clases: `finance.Loan`, `finance.FinanceManager` (mapa `loans` por deudor + `collectLoans` en el `tick`), `command.LoanCommands`. Persisten en el mismo `PersistentState` que impuestos y sueldos.

Comandos (`/prestamo`, alias `/loan`):

| Comando | Quién | Qué hace |
|---|---|---|
| `/prestamo lista` | miembro | lista los préstamos activos del grupo y cuándo vencen |
| `/prestamo mio` | miembro | muestra tu préstamo (pendiente, total, vencimiento) |
| `/prestamo conceder <jugador> <cantidad> <interes> <plazo>` | líder | presta desde la cuenta del grupo (interés 0-1000 %) |
| `/prestamo pagar [cantidad]` | deudor | devuelve anticipadamente (todo o una parte) desde tu saldo |
| `/prestamo perdonar <jugador>` | líder | cancela la deuda de un miembro |

> `<jugador>` se resuelve entre los miembros del grupo por nombre (funciona desconectado). `<plazo>`: `30s`, `10m`, `2h`, `1d` (mínimo 5s).

### Cómo probar la parte 9

1. Grupo con moneda (partes 1-2) y **saldo en la cuenta del grupo** (p. ej. con impuestos de la parte 6: `/impuesto crear iva fijo 100 5s` + `/impuesto cobrar iva`). Mira `/tesoreria`.
2. Como líder: `/prestamo conceder Jugador2 100 10 20s` → Jugador2 recibe 100 en su saldo; debe devolver 110 en 20s. La cuenta del grupo baja 100.
3. `/prestamo lista` (todos) y, como Jugador2, `/prestamo mio`.
4. **Pago anticipado:** como Jugador2, `/prestamo pagar 40` (parcial) o `/prestamo pagar` (todo) → vuelve a la cuenta del grupo.
5. **Vencimiento con saldo:** deja que pasen los 20s con saldo suficiente → se cobra solo y el préstamo se salda (aviso al deudor).
6. **Vencimiento sin saldo (deuda):** da poco saldo al deudor antes de vencer → al vencer se cobra lo que haya y el resto queda como deuda; dale saldo después y verás que se cobra solo hasta saldarlo.
7. `/prestamo perdonar Jugador2` cancela la deuda. Reinicia el servidor: préstamos y deudas persisten y los plazos siguen contando en tiempo real.

## Panel de progreso (local)

`dashboard/index.html` — página HTML autocontenida con el estado de las 9 partes, comandos y cómo probar. Ábrela en el navegador (`open dashboard/index.html`). Se actualiza al terminar cada parte.

## Convenciones

- Mensajes al jugador en español.
- Todo lo que deba persistir va en un `PersistentState` del overworld, nunca solo en memoria.
- IDs de grupo estables (UUID); las siglas son mutables y solo un índice único de cara al usuario.
