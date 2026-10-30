package com.realeconomy.shop;

import net.minecraft.inventory.SimpleInventory;

/** Almacén de una tienda (27 huecos) que avisa a la tienda cuando cambia. */
public class ShopStorage extends SimpleInventory {

    private Runnable onChanged = () -> {
    };

    public ShopStorage() {
        super(27);
    }

    public void setOnChanged(Runnable onChanged) {
        this.onChanged = onChanged;
    }

    @Override
    public void markDirty() {
        super.markDirty();
        onChanged.run();
    }
}
