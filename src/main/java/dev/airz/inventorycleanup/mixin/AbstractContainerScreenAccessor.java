package dev.airz.inventorycleanup.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = AbstractContainerScreen.class, remap = false)
public interface AbstractContainerScreenAccessor {
    @Accessor(value = "leftPos", remap = false)
    void inventorycleanup$setLeftPos(int leftPos);
}
