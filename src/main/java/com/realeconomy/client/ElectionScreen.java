package com.realeconomy.client;

import com.realeconomy.client.ui.Theme;
import com.realeconomy.client.ui.ThemedButton;
import com.realeconomy.client.ui.ThemedScreen;
import com.realeconomy.network.ElectionVotePayload;
import com.realeconomy.network.OpenElectionPayload;
import com.realeconomy.network.PartyVoteView;
import com.realeconomy.util.TimeUtil;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/** Interfaz de la urna electoral: lista los partidos del país y permite votar por uno. */
public class ElectionScreen extends ThemedScreen {

    private static final int BG_W = 230;
    private static final int BG_H = 240;
    private static final int ROW_H = 22;

    private OpenElectionPayload data;

    public ElectionScreen(OpenElectionPayload data) {
        super(Text.literal("Elecciones [" + data.countryTag() + "]"), BG_W, BG_H);
        this.data = data;
    }

    @Override
    protected void initContent() {
        int cl = contentLeft();
        int cw = contentWidth();
        int y = contentTop() + 30;

        boolean canVote = data.open() && !data.alreadyVoted();
        for (PartyVoteView p : data.parties()) {
            ThemedButton vote = ThemedButton.of(cl + cw - 54, y, 54, 18, Text.literal("Votar"),
                    b -> ClientPlayNetworking.send(new ElectionVotePayload(p.partyId())));
            vote.active = canVote;
            this.addDrawableChild(vote);
            y += ROW_H;
        }
    }

    @Override
    protected void renderContent(DrawContext ctx, int mouseX, int mouseY, float delta) {
        int cl = contentLeft();
        int cw = contentWidth();
        int y = contentTop() + 2;

        if (data.open()) {
            text(ctx, "Elección ABIERTA · cierra en " + TimeUtil.format(data.endsInMillis()), cl, y, Theme.TEXT_OK);
        } else {
            text(ctx, "Sin elección abierta", cl, y, Theme.TEXT_DIM);
        }
        text(ctx, data.statusNote(), cl, y + 12, Theme.TEXT_DIM);

        int ry = contentTop() + 30;
        for (PartyVoteView p : data.parties()) {
            Theme.slot(ctx, cl, ry, cw, 18);
            Formatting col = Formatting.byName(p.colorName());
            text(ctx, p.name(), cl + 6, ry + 5, (col == null ? Formatting.WHITE : col).getColorValue() | 0xFF000000);
            ry += ROW_H;
        }
        if (data.parties().isEmpty()) {
            centered(ctx, Text.literal("(sin partidos)"), left + bgW / 2, ry + 6, Theme.TEXT_DIM);
        }
    }

    public void refresh(OpenElectionPayload data) {
        this.data = data;
        this.clearAndInit();
    }
}
