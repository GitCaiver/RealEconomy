package com.realeconomy.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.realeconomy.economy.BalanceManager;
import com.realeconomy.economy.Currency;
import com.realeconomy.economy.CurrencyManager;
import com.realeconomy.finance.FinanceManager;
import com.realeconomy.group.Group;
import com.realeconomy.group.GroupManager;
import com.realeconomy.license.CustomLicenseManager;
import com.realeconomy.license.License;
import com.realeconomy.license.LicensePermission;
import com.realeconomy.townhall.TownhallEntities;
import com.realeconomy.util.TimeUtil;
import net.minecraft.command.CommandSource;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;
import java.util.Locale;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

/**
 * Comandos /licencia (alias /licencias): el gobierno crea/edita/borra licencias personalizadas
 * del país; los jugadores las compran en el ayuntamiento. Parte 5, 3ª tanda.
 */
public final class LicenseCommands {

    private static final List<String> PERMS = List.of("tienda", "subasta", "banquero", "mercado", "trabajo");

    private LicenseCommands() {
    }

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        var root = literal("licencia")
                .then(literal("crear")
                        .then(argument("nombre", StringArgumentType.word())
                                .then(argument("precio", LongArgumentType.longArg(0))
                                        .then(argument("moneda", StringArgumentType.word())
                                                .then(argument("duracion", StringArgumentType.word())
                                                        .then(argument("permiso", StringArgumentType.word())
                                                                .suggests((c, b) -> CommandSource.suggestMatching(PERMS, b))
                                                                .executes(LicenseCommands::create)))))))
                .then(literal("editar")
                        .then(argument("nombre", StringArgumentType.word())
                                .then(argument("campo", StringArgumentType.word())
                                        .suggests((c, b) -> CommandSource.suggestMatching(
                                                List.of("precio", "moneda", "duracion", "permiso", "renombrar"), b))
                                        .then(argument("valor", StringArgumentType.word())
                                                .executes(LicenseCommands::edit)))))
                .then(literal("borrar")
                        .then(argument("nombre", StringArgumentType.word())
                                .executes(LicenseCommands::delete)))
                .then(literal("lista").executes(LicenseCommands::list))
                .then(literal("comprar")
                        .then(argument("nombre", StringArgumentType.greedyString())
                                .executes(LicenseCommands::buy)))
                .then(literal("mias").executes(LicenseCommands::mine));

        var node = dispatcher.register(root);
        dispatcher.register(literal("licencias").redirect(node));
    }

    private static Group playerCountry(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
        Group g = GroupManager.get(ctx.getSource().getServer()).getByPlayer(player.getUuid());
        if (g == null) {
            ctx.getSource().sendError(Text.literal("No perteneces a ningún país."));
        }
        return g;
    }

    private static Group governmentCountry(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        Group g = playerCountry(ctx);
        if (g == null) {
            return null;
        }
        if (!g.isGovernment(ctx.getSource().getPlayerOrThrow().getUuid())) {
            ctx.getSource().sendError(Text.literal("Solo el gobierno (líder o ministros) puede gestionar las licencias."));
            return null;
        }
        return g;
    }

    private static int create(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        Group g = governmentCountry(ctx);
        if (g == null) {
            return 0;
        }
        MinecraftServer server = ctx.getSource().getServer();
        String name = StringArgumentType.getString(ctx, "nombre");
        long price = LongArgumentType.getLong(ctx, "precio");
        String moneda = StringArgumentType.getString(ctx, "moneda");
        String duracion = StringArgumentType.getString(ctx, "duracion");
        String permisoRaw = StringArgumentType.getString(ctx, "permiso");

        CustomLicenseManager clm = CustomLicenseManager.get(server);
        if (clm.defByName(g.getId(), name) != null) {
            ctx.getSource().sendError(Text.literal("Ya existe una licencia con ese nombre en tu país."));
            return 0;
        }
        Currency currency = currencyByTag(server, moneda);
        if (currency == null) {
            ctx.getSource().sendError(Text.literal("No existe una moneda con esas siglas (" + moneda + ")."));
            return 0;
        }
        long duration = parseDuration(duracion);
        if (duration < 0) {
            ctx.getSource().sendError(Text.literal("Duración no válida. Usa \"permanente\" o un periodo (30s, 10m, 2h, 1d)."));
            return 0;
        }
        LicensePermission perm = LicensePermission.byKey(permisoRaw);
        if (perm == null) {
            ctx.getSource().sendError(Text.literal("Permiso no válido. Usa: " + String.join(", ", PERMS) + "."));
            return 0;
        }
        License def = License.create(g.getId(), name, price, currency.getId(), duration, perm);
        clm.define(def);
        ctx.getSource().sendFeedback(() -> Text.literal("Licencia \"" + name + "\" creada: "
                + price + " " + currency.getName() + ", " + durationLabel(duration)
                + ", permite " + perm.description() + "."), true);
        return 1;
    }

    private static int edit(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        Group g = governmentCountry(ctx);
        if (g == null) {
            return 0;
        }
        MinecraftServer server = ctx.getSource().getServer();
        String name = StringArgumentType.getString(ctx, "nombre");
        String campo = StringArgumentType.getString(ctx, "campo").toLowerCase(Locale.ROOT);
        String valor = StringArgumentType.getString(ctx, "valor");
        CustomLicenseManager clm = CustomLicenseManager.get(server);
        License def = clm.defByName(g.getId(), name);
        if (def == null) {
            ctx.getSource().sendError(Text.literal("No existe una licencia con ese nombre en tu país."));
            return 0;
        }
        String msg;
        switch (campo) {
            case "precio" -> {
                long price;
                try {
                    price = Long.parseLong(valor);
                } catch (NumberFormatException e) {
                    ctx.getSource().sendError(Text.literal("Precio no válido."));
                    return 0;
                }
                if (price < 0) {
                    ctx.getSource().sendError(Text.literal("El precio no puede ser negativo."));
                    return 0;
                }
                def.setPrice(price);
                msg = "Precio de \"" + name + "\" ajustado a " + price + ".";
            }
            case "moneda" -> {
                Currency currency = currencyByTag(server, valor);
                if (currency == null) {
                    ctx.getSource().sendError(Text.literal("No existe una moneda con esas siglas (" + valor + ")."));
                    return 0;
                }
                def.setCurrencyId(currency.getId());
                msg = "Moneda de \"" + name + "\" ajustada a " + currency.getName() + ".";
            }
            case "duracion" -> {
                long duration = parseDuration(valor);
                if (duration < 0) {
                    ctx.getSource().sendError(Text.literal("Duración no válida. Usa \"permanente\" o un periodo."));
                    return 0;
                }
                def.setDurationMillis(duration);
                msg = "Duración de \"" + name + "\" ajustada a " + durationLabel(duration) + ".";
            }
            case "permiso" -> {
                LicensePermission perm = LicensePermission.byKey(valor);
                if (perm == null) {
                    ctx.getSource().sendError(Text.literal("Permiso no válido. Usa: " + String.join(", ", PERMS) + "."));
                    return 0;
                }
                def.setPermission(perm);
                msg = "\"" + name + "\" ahora permite " + perm.description() + ".";
            }
            case "renombrar" -> {
                if (clm.defByName(g.getId(), valor) != null) {
                    ctx.getSource().sendError(Text.literal("Ya existe una licencia con ese nombre."));
                    return 0;
                }
                def.setName(valor);
                msg = "Licencia renombrada a \"" + valor + "\".";
            }
            default -> {
                ctx.getSource().sendError(Text.literal("Campo no válido. Usa: precio, moneda, duracion, permiso, renombrar."));
                return 0;
            }
        }
        clm.markDirty();
        String fmsg = msg;
        ctx.getSource().sendFeedback(() -> Text.literal(fmsg), true);
        return 1;
    }

    private static int delete(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        Group g = governmentCountry(ctx);
        if (g == null) {
            return 0;
        }
        String name = StringArgumentType.getString(ctx, "nombre");
        if (!CustomLicenseManager.get(ctx.getSource().getServer()).removeDef(g.getId(), name)) {
            ctx.getSource().sendError(Text.literal("No existe una licencia con ese nombre en tu país."));
            return 0;
        }
        ctx.getSource().sendFeedback(() -> Text.literal("Licencia \"" + name + "\" eliminada (y retirada a quien la tuviera)."), true);
        return 1;
    }

    private static int list(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        Group g = playerCountry(ctx);
        if (g == null) {
            return 0;
        }
        MinecraftServer server = ctx.getSource().getServer();
        List<License> defs = CustomLicenseManager.get(server).defs(g.getId());
        if (defs.isEmpty()) {
            ctx.getSource().sendFeedback(() -> Text.literal("Tu país no tiene licencias personalizadas."), false);
            return 1;
        }
        ctx.getSource().sendFeedback(() -> {
            MutableText t = Text.literal("Licencias de ")
                    .append(Text.literal("[" + g.getTag() + "]").formatted(g.getColor())).append(":");
            for (License l : defs) {
                t.append(Text.literal("\n - " + l.getName() + ": " + l.getPrice() + " "
                        + currencyName(server, l.getCurrencyId()) + ", " + durationLabel(l.getDurationMillis())
                        + ", permite " + l.getPermission().description()));
            }
            t.append(Text.literal("\nCómprala con /licencia comprar <nombre> (junto a un ayuntamiento).").formatted(Formatting.GRAY));
            return t;
        }, false);
        return 1;
    }

    private static int buy(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
        Group g = playerCountry(ctx);
        if (g == null) {
            return 0;
        }
        if (!TownhallEntities.isNear(player)) {
            ctx.getSource().sendError(Text.literal("Acércate a un ayuntamiento para comprar una licencia."));
            return 0;
        }
        MinecraftServer server = ctx.getSource().getServer();
        String name = StringArgumentType.getString(ctx, "nombre").trim();
        CustomLicenseManager clm = CustomLicenseManager.get(server);
        License def = clm.defByName(g.getId(), name);
        if (def == null) {
            ctx.getSource().sendError(Text.literal("Tu país no ofrece una licencia llamada \"" + name + "\"."));
            return 0;
        }
        BalanceManager bm = BalanceManager.get(server);
        if (!bm.subtract(player.getUuid(), def.getCurrencyId(), def.getPrice())) {
            ctx.getSource().sendError(Text.literal("Saldo insuficiente. Cuesta " + def.getPrice() + " "
                    + currencyName(server, def.getCurrencyId()) + "."));
            return 0;
        }
        // El pago va a la cuenta del país que emite la licencia.
        bm.add(FinanceManager.accountUuid(g), def.getCurrencyId(), def.getPrice());
        clm.grant(player.getUuid(), def, System.currentTimeMillis());
        ctx.getSource().sendFeedback(() -> Text.literal("Has comprado la licencia \"" + def.getName() + "\" ("
                + durationLabel(def.getDurationMillis()) + "). Permite " + def.getPermission().description() + "."), false);
        return 1;
    }

    private static int mine(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
        Group g = playerCountry(ctx);
        if (g == null) {
            return 0;
        }
        MinecraftServer server = ctx.getSource().getServer();
        CustomLicenseManager clm = CustomLicenseManager.get(server);
        List<License> defs = clm.defs(g.getId());
        long now = System.currentTimeMillis();
        ctx.getSource().sendFeedback(() -> {
            MutableText t = Text.literal("Tus licencias:");
            boolean any = false;
            for (License l : defs) {
                long exp = clm.heldExpiry(player.getUuid(), l.getId());
                if (exp < 0) {
                    continue;
                }
                any = true;
                String when = exp == 0 ? "permanente"
                        : (exp > now ? "caduca en " + TimeUtil.format(exp - now) : "CADUCADA");
                t.append(Text.literal("\n - " + l.getName() + " (" + l.getPermission().description() + "): " + when));
            }
            if (!any) {
                t.append(Text.literal("\n (ninguna)").formatted(Formatting.GRAY));
            }
            return t;
        }, false);
        return 1;
    }

    // ---- Helpers ----

    private static Currency currencyByTag(MinecraftServer server, String tag) {
        Group g = GroupManager.get(server).getByTag(tag);
        if (g == null) {
            return null;
        }
        return CurrencyManager.get(server).getByGroup(g.getId());
    }

    private static String currencyName(MinecraftServer server, String currencyId) {
        Currency c = CurrencyManager.get(server).getById(currencyId);
        return c == null ? "¿?" : c.getName();
    }

    /** "permanente" -> 0; un periodo válido -> ms; negativo = inválido. */
    private static long parseDuration(String raw) {
        if (raw == null) {
            return -1;
        }
        String s = raw.trim().toLowerCase(Locale.ROOT);
        if (s.equals("permanente") || s.equals("permanent") || s.equals("0")) {
            return 0;
        }
        long parsed = TimeUtil.parsePeriod(s);
        return parsed > 0 ? parsed : -1;
    }

    private static String durationLabel(long duration) {
        return duration <= 0 ? "permanente" : "dura " + TimeUtil.format(duration);
    }
}
