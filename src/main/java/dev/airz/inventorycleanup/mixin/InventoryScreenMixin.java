package dev.airz.inventorycleanup.mixin;

import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InventoryScreen.class)
public abstract class InventoryScreenMixin {
    @Shadow @Final private RecipeBookComponent recipeBookComponent;

    @Inject(method = "init", at = @At("TAIL"))
    private void inventorycleanup$hidePlayerRecipeBook(CallbackInfo ci) {
        this.recipeBookComponent.setVisible(false);
    }
}
