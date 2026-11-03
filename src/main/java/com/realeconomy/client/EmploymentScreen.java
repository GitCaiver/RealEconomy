package com.realeconomy.client;

import com.realeconomy.client.ui.Theme;
import com.realeconomy.client.ui.ThemedButton;
import com.realeconomy.client.ui.ThemedScreen;
import com.realeconomy.client.ui.ThemedTextField;
import com.realeconomy.employment.VillagerJob;
import com.realeconomy.network.EmploymentActionPayload;
import com.realeconomy.network.OpenEmploymentPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

/** Interfaz Art Déco para emplear a un aldeano de MCA: trabajo, sueldo y periodo. */
public class EmploymentScreen extends ThemedScreen {

    private static final int BG_W = 220;
    private static final int BG_H = 210;

    private OpenEmploymentPayload data;
    private int selectedJob;
    private ThemedTextField wageField;
    private ThemedTextField periodField;

    public EmploymentScreen(OpenEmploymentPayload data) {
        super(Text.literal("Contratar aldeano"), BG_W, BG_H);
        this.data = data;
        this.selectedJob = Math.max(0, data.jobId());
    }

    private boolean canManage() {
        return data.mode() != OpenEmploymentPayload.MODE_NONE;
    }

    @Override
    protected void initContent() {
        int cl = contentLeft();
        int y = contentTop() + 54;

        if (canManage()) {
            // Botón de ciclado del trabajo.
            this.addDrawableChild(ThemedButton.of(cl, y, contentWidth(), 18, jobLabel(), b -> {
                selectedJob = (selectedJob + 1) % VillagerJob.values().length;
                b.setMessage(jobLabel());
            }));

            wageField = new ThemedTextField(this.textRenderer, cl + 70, y + 24, contentWidth() - 70, 16,
                    Text.literal("sueldo"));
            wageField.setText(String.valueOf(data.wage()));
            this.addDrawableChild(wageField);

            periodField = new ThemedTextField(this.textRenderer, cl + 70, y + 46, contentWidth() - 70, 16,
                    Text.literal("p. ej. 1h"));
            periodField.setText(data.period());
            this.addDrawableChild(periodField);

            this.addDrawableChild(ThemedButton.of(cl, y + 70, contentWidth() / 2 - 2, 20,
                    Text.literal(data.employed() ? "Actualizar" : "Contratar"), b -> hire()));

            if (data.employed()) {
                this.addDrawableChild(ThemedButton.of(cl + contentWidth() / 2 + 2, y + 70, contentWidth() / 2 - 2, 20,
                        Text.literal("Despedir"), b -> ClientPlayNetworking.send(
                                new EmploymentActionPayload(data.villager(), EmploymentActionPayload.FIRE, 0, 0L, "")) ));
            }
        }
        this.addDrawableChild(ThemedButton.of(cl, top + BG_H - 26, contentWidth(), 18,
                Text.literal("Cerrar"), b -> this.close()));
    }

    private Text jobLabel() {
        VillagerJob j = VillagerJob.byId(selectedJob);
        return Text.literal("Trabajo: " + (j == null ? "?" : j.display()));
    }

    private void hire() {
        long wage;
        try {
            wage = Long.parseLong(wageField.getText().trim());
        } catch (NumberFormatException e) {
            wage = 0;
        }
        String period = periodField.getText().trim();
        if (period.isEmpty()) {
            period = "1h";
        }
        ClientPlayNetworking.send(new EmploymentActionPayload(
                data.villager(), EmploymentActionPayload.HIRE, selectedJob, wage, period));
    }

    @Override
    protected void renderContent(DrawContext ctx, int mouseX, int mouseY, float delta) {
        int cl = contentLeft();
        int y = contentTop();
        text(ctx, Text.literal(data.villagerName()), cl, y, Theme.TITLE);

        String state;
        int stateColor;
        if (data.employed()) {
            state = data.active() ? "Empleado (trabajando)" : "Empleado (sin fondos: parado)";
            stateColor = data.active() ? Theme.TEXT_OK : Theme.TEXT_WARN;
        } else {
            state = "Sin empleo";
            stateColor = Theme.TEXT_DIM;
        }
        text(ctx, state, cl, y + 12, stateColor);

        if (data.employed()) {
            text(ctx, "Cartera: " + data.wallet() + " " + data.currencyName(), cl, y + 24, Theme.TEXT);
            String zone = "Zona: " + (data.hasZone() ? "asignada" : "sin asignar")
                    + " · Cofre: " + (data.hasChest() ? "vinculado" : "sin vincular");
            text(ctx, zone, cl, y + 36, Theme.TEXT_DIM);
        }

        if (!canManage()) {
            text(ctx, "(solo el empleador o el gobierno gestionan)", cl, y + 50, Theme.TEXT_WARN);
            return;
        }

        if (data.mode() == OpenEmploymentPayload.MODE_PUBLIC) {
            text(ctx, "Empleo público: paga la cuenta del país.", cl, y + 50, Theme.TEXT_DIM);
        } else {
            text(ctx, "Empleo privado: pagas de tu saldo.", cl, y + 50, Theme.TEXT_DIM);
        }

        // Etiquetas de los campos.
        int fieldsY = contentTop() + 54;
        text(ctx, "Sueldo:", cl, fieldsY + 28, Theme.TEXT);
        text(ctx, "Periodo:", cl, fieldsY + 50, Theme.TEXT);
        text(ctx, "Zona y cofre se asignan con /empleo zona y /empleo cofre.",
                cl, top + BG_H - 40, Theme.TEXT_DIM);
    }

    public void refresh(OpenEmploymentPayload data) {
        this.data = data;
        this.selectedJob = Math.max(0, data.jobId());
        this.clearAndInit();
    }
}
