# RealEconomy

Mod de Minecraft **1.21.1** con **Fabric** que implementa una economía completa para servidores, basada en **países** (con partidos políticos dentro): monedas propias, bancos, ayuntamientos, tiendas de jugadores, impuestos, préstamos, trabajos y NPCs.

> **Nota de terminología (2ª tanda):** lo que antes eran "grupos" ahora son **PAÍSES**. Internamente las clases siguen llamándose `Group`/`GroupManager` (y el `PersistentState` sigue siendo `realeconomy_groups`) para **migrar los datos existentes sin perderlos**; de cara al jugador todo dice "país". El comando principal es `/pais` (alias `/paises`, `/grupo`, `/group`).

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
