# RealEconomy

Mod de Minecraft **1.21.1** con **Fabric** que implementa una economía completa para servidores, basada en grupos: monedas propias, bancos, ayuntamientos, tiendas de jugadores, impuestos, préstamos, trabajos y NPCs.

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

## Estado por partes

1. **Grupos** ✅ — siglas, color, líder, miembros, lista de jugadores con `[SIGLAS]` en color.
2. **Monedas (icono por URL) y cuentas** ✅ — item físico de moneda por divisa, icono descargado/cacheado/sincronizado, libro de saldos.
3. **Banco NPC con interfaz** ✅ — NPC aldeano, GUI vanilla con ingresar/retirar/transferir.
4. **Ayuntamiento y licencias** ✅ — NPC ayuntamiento, GUI de compra de licencias (consumibles).
5. **Tiendas de jugadores (venta y compra)** ✅ — NPC tienda, ofertas de venta/compra, almacén, GUI de compra.
6. **Impuestos y cuenta del grupo** ✅ — cuenta del grupo, impuestos (fijo/%) y sueldos recurrentes en tiempo real, subvenciones.
7. **Mercados de grupo (NPCs)** ✅ — NPC de mercado del grupo que compra/vende items por monedas físicas desde su caja; incluye el cambio esmeraldas↔monedas.
8. **Trabajos y sueldos** ✅ — minería/tala/agricultura/banca; el líder fija el sueldo por acción y se paga desde la cuenta del grupo.
9. **Banquero y préstamos** ✅ — el líder presta desde la cuenta del grupo (capital, interés, plazo real); al vencer se cobra solo del saldo y, si no llega, queda deuda.

## Parte 1 — Grupos (implementada)

Modelo (`com.realeconomy.group`):
- `Group` — datos de un grupo (id UUID estable, nombre, siglas, color `Formatting`, líder, mapa miembro→nombre). Serialización NBT.
- `GroupManager` — `PersistentState`, fuente de verdad. Índices jugador→grupo y gestión de siglas únicas. Refleja cada grupo en un **equipo de scoreboard** (`re_<id>`) para pintar el nombre del jugador con las siglas y el color del grupo de forma 100 % vanilla (tab list, placa sobre la cabeza y chat). Las invitaciones son en memoria (se pierden al reiniciar, por diseño).

Comandos (`/group`, alias `/grupo`):

| Comando | Quién | Qué hace |
|---|---|---|
| `/group create <siglas> <color> [nombre]` | cualquiera sin grupo | crea el grupo (siglas 2-4 letras únicas). Si no das nombre, usa las siglas |
| `/group invite <jugador>` | líder | invita a un jugador en línea |
| `/group accept [siglas]` | invitado | acepta la invitación (las siglas solo si tienes varias) |
| `/group deny <siglas>` | invitado | rechaza una invitación |
| `/group kick <jugador>` | líder | expulsa a un miembro |
| `/group leave` | miembro | sale del grupo (el líder no puede; debe transferir o disolver) |
| `/group transfer <jugador>` | líder | cede el liderazgo a un miembro |
| `/group disband` | líder | disuelve el grupo |
| `/group settag <siglas>` | líder | cambia las siglas |
| `/group setcolor <color>` | líder | cambia el color |
| `/group setname <nombre>` | líder | cambia el nombre |
| `/group info [siglas]` | cualquiera | info del grupo (propio o el indicado) |
| `/group list` | cualquiera | lista todos los grupos |

Colores aceptados (español o inglés): rojo, verde, azul, amarillo, dorado/naranja, cian, rosa, morado_oscuro, blanco, gris, negro, y variantes `_oscuro`.

### Cómo probar la parte 1

1. `./gradlew runServer` (acepta el EULA la primera vez: pon `eula=true` en `run/eula.txt` y vuelve a arrancar).
2. Conéctate con un cliente 1.21.1 (con Fabric + Fabric API) o usa `./gradlew runClient`.
3. En el juego:
   - `/group create ESP rojo España` → tu nombre pasa a verse `TuNombre [ESP]` en rojo en el tab (Tab), sobre la cabeza y en el chat.
   - Con un segundo jugador: `/group invite Jugador2`, luego el otro hace `/group accept`.
   - `/group info`, `/group list`, `/group setcolor azul`, `/group settag ES`.
   - Reinicia el servidor y comprueba que los grupos y colores persisten.

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
