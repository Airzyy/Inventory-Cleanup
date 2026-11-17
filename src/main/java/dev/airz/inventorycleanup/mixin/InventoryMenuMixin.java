package dev.airz.inventorycleanup.mixin;

import dev.airz.inventorycleanup.CuriosMenuSlots;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.InventoryMenu;\nimport net.minecraft.world.inventory.Slot;\nimport net.minecraft.world.item.ItemStack;\nimport net.minecraft.world.entity.player.Player;\nimport net.minecraft.world.inventory.AbstractContainerMenu;\nimport net.minecraft.world.inventory.ClickType;\nimport net.minecraft.world.inventory.Slot;\nimport net.minecraft.world.item.ItemStack;\nimport org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;\nimport top.theillusivec4.curios.api.CuriosApi;\nimport top.theillusivec4.curios.common.inventory.CurioSlot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InventoryMenu.class)
public abstract class InventoryMenuMixin {
    @Inject(method = "<init>", at = @At("TAIL"), remap = false)
    private void inventorycleanup$addCuriosSlots(Inventory inventory, boolean active, Player owner, CallbackInfo ci) {
        CuriosMenuSlots.addToInventoryMenu(owner, (InventoryMenu) (Object) this);
    }
}
