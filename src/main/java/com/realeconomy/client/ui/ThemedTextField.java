package com.realeconomy.client.ui;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

/**
 * Campo de texto con la estética del tema: fondo de mármol oscuro con marco de latón
 * y filo turquesa ({@link Theme#field}). Reutilizable por todas las pantallas del mod.
 */
public class ThemedTextField extends TextFieldWidget {

    public ThemedTextField(TextRenderer tr, int x, int y, int width, int height, Text placeholder) {
        super(tr, x, y, width, height, placeholder);
        this.setDrawsBackground(false); // dibujamos nosotros el fondo temático
    }

    @Override
    public void renderWidget(DrawContext ctx, int mouseX, int mouseY, float delta) {
        if (this.visible) {
            Theme.field(ctx, getX(), getY(), getWidth(), getHeight());
            if (isFocused()) {
                // Resalte turquesa del borde al enfocar.
                ctx.drawBorder(getX(), getY(), getWidth(), getHeight(), 0x8835E0D8);
            }
        }
        super.renderWidget(ctx, mouseX, mouseY, delta);
    }
}
