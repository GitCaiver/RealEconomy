package com.realeconomy.politics;

import com.realeconomy.group.Group;
import com.realeconomy.group.GroupManager;
import com.realeconomy.network.CountryCreateActionPayload;
import com.realeconomy.network.OpenCountryCreatePayload;
import com.realeconomy.util.ColorUtil;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.Locale;

/** Lógica de servidor de la interfaz de creación de país (tema Art Déco, parte 1). */
public final class CreationActions {

    private CreationActions() {
    }

    /** Abre la interfaz de creación si el jugador no pertenece ya a un país. */
    public static void openFor(ServerPlayerEntity player) {
        GroupManager mgr = GroupManager.get(player.getServer());
        if (mgr.getByPlayer(player.getUuid()) != null) {
            player.sendMessage(Text.literal("Ya perteneces a un país. Sal de él con /pais leave antes de crear otro.")
                    .formatted(Formatting.RED), false);
            return;
        }
        ServerPlayNetworking.send(player, new OpenCountryCreatePayload(true));
    }

    /** Procesa la confirmación de creación desde la GUI. */
    public static void handle(ServerPlayerEntity player, CountryCreateActionPayload action) {
        MinecraftServer server = player.getServer();
        GroupManager mgr = GroupManager.get(server);

        if (mgr.getByPlayer(player.getUuid()) != null) {
            player.sendMessage(Text.literal("Ya perteneces a un país.").formatted(Formatting.RED), false);
            return;
        }

        String tagError = GroupManager.validateTag(action.tag());
        if (tagError != null) {
            player.sendMessage(Text.literal(tagError).formatted(Formatting.RED), false);
            return;
        }
        if (mgr.tagExists(action.tag())) {
            player.sendMessage(Text.literal("Ya existe un país con las siglas ["
                    + GroupManager.normalizeTag(action.tag()) + "].").formatted(Formatting.RED), false);
            return;
        }

        Formatting color = ColorUtil.parse(action.colorName());
        if (color == null) {
            player.sendMessage(Text.literal("Color no válido.").formatted(Formatting.RED), false);
            return;
        }

        FormType form = parseFormKey(action.formKey());
        if (form == null) {
            player.sendMessage(Text.literal("Forma política no válida.").formatted(Formatting.RED), false);
            return;
        }
        FormSubtype subtype = FormSubtype.NONE;
        if (form.requiresSubtype()) {
            subtype = FormSubtype.parse(action.subtypeKey());
            if (subtype == null || subtype == FormSubtype.NONE || !form.subtypes().contains(subtype)) {
                player.sendMessage(Text.literal("Debes elegir un subtipo válido de " + form.displayName() + ".")
                        .formatted(Formatting.RED), false);
                return;
            }
        }

        String name = (action.name() == null || action.name().isBlank())
                ? GroupManager.normalizeTag(action.tag()) : action.name().trim();
        Group g = mgr.createGroup(name, action.tag(), color, player, form, subtype);

        player.sendMessage(Text.literal("País creado: ")
                .append(Text.literal("[" + g.getTag() + "]").formatted(g.getColor()))
                .append(Text.literal(" · " + PoliticalForms.displayName(form, subtype)))
                .formatted(Formatting.GREEN), false);
        player.sendMessage(Text.literal("Moneda creada automáticamente: " + g.getName() + " [" + g.getTag()
                + "]. Cámbiale el nombre con /moneda nombre o el icono con /moneda icono <url>."), false);
    }

    private static FormType parseFormKey(String key) {
        if (key == null) {
            return null;
        }
        try {
            return FormType.valueOf(key.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return FormType.parse(key);
        }
    }
}
