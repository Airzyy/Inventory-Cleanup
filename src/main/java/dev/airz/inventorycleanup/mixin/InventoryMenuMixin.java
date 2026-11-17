package dev.airz.inventorycleanup.mixin;

import dev.airz.inventorycleanup.CuriosMenuSlots;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.common.inventory.CurioSlot;

@Mixin(InventoryMenu.class)
public abstract class InventoryMenuMixin {
    @Inject(method = "<init>", at = @At("TAIL"), remap = false)
    private void inventorycleanup$addCuriosSlots(Inventory inventory, boolean active, Player owner, CallbackInfo ci) {
        // The integrated server constructs its menu before the client has necessarily
        // received Curios' initial capability sync. Build the authoritative server layout
        // here; the client mirrors it when the inventory screen is first initialized.
        if (!owner.level().isClientSide) {
            CuriosMenuSlots.addToInventoryMenu(owner, (InventoryMenu) (Object) this);
        }
    }

    @Inject(method = "quickMoveStack", at = @At("HEAD"), cancellable = true, remap = false)
    private void inventorycleanup$quickMoveCurios(Player player, int index,
                                                   CallbackInfoReturnable<ItemStack> cir) {
        InventoryMenu menu = (InventoryMenu) (Object) this;
        if (index < 0 || index >= menu.slots.size()) return;

        Slot source = menu.slots.get(index);
        if (source instanceof CurioSlot) {
            ItemStack sourceStack = source.getItem();
            if (sourceStack.isEmpty()) return;
            ItemStack result = sourceStack.copy();
            if (moveStack(menu, sourceStack, 9, 45, true)) {
                if (sourceStack.isEmpty()) source.set(ItemStack.EMPTY);
                source.setChanged();
                source.onTake(player, result);
                cir.setReturnValue(result);
            } else {
                cir.setReturnValue(ItemStack.EMPTY);
            }
            return;
        }

        // InventoryMenu uses 9..35 for main inventory, 36..44 for hotbar,
        // and 45 for offhand. Curios slots are appended after those vanilla slots.
        if (index >= 9 && index <= 45 && source.hasItem()) {
            ItemStack sourceStack = source.getItem();
            if (!CuriosApi.getItemStackSlots(sourceStack, player.level()).isEmpty()) {
                ItemStack result = sourceStack.copy();
                int curioStart = 0;
                while (curioStart < menu.slots.size()
                        && !(menu.slots.get(curioStart) instanceof CurioSlot)) {
                    curioStart++;
                }
                if (curioStart < menu.slots.size()
                        && moveStack(menu, sourceStack, curioStart, menu.slots.size(), false)) {
                    if (sourceStack.isEmpty()) source.set(ItemStack.EMPTY);
                    source.setChanged();
                    source.onTake(player, result);
                    cir.setReturnValue(result);
                }
            }
        }
    }

    private static boolean moveStack(InventoryMenu menu, ItemStack source,
                                     int start, int end, boolean reverse) {
        int before = source.getCount();
        int i = reverse ? end - 1 : start;
        int step = reverse ? -1 : 1;

        while (!source.isEmpty() && i >= start && i < end) {
            Slot target = menu.slots.get(i);
            if (target.isActive() && target.mayPlace(source)) {
                ItemStack existing = target.getItem();
                int limit = Math.min(target.getMaxStackSize(source), source.getMaxStackSize());

                if (existing.isEmpty()) {
                    int amount = Math.min(source.getCount(), limit);
                    if (amount > 0) {
                        target.set(source.split(amount));
                        target.setChanged();
                    }
                } else if (ItemStack.isSameItemSameComponents(existing, source)) {
                    int space = limit - existing.getCount();
                    int amount = Math.min(source.getCount(), space);
                    if (amount > 0) {
                        existing.grow(amount);
                        source.shrink(amount);
                        target.setChanged();
                    }
                }
            }
            i += step;
        }
        return source.getCount() < before;
    }
}
