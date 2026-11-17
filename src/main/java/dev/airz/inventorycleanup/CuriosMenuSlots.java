package dev.airz.inventorycleanup;

import java.lang.reflect.Method;
import java.util.Map;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;
import top.theillusivec4.curios.api.type.inventory.IDynamicStackHandler;
import top.theillusivec4.curios.common.inventory.CurioSlot;

public final class CuriosMenuSlots {
    private CuriosMenuSlots() {}

    public static void addToInventoryMenu(Player player, InventoryMenu menu) {
        for (Slot slot : menu.slots) {
            if (slot instanceof CurioSlot) return;
        }

        CuriosApi.getCuriosInventory(player).ifPresent(handler -> {
            int visibleCount = handler.getVisibleSlots();
            if (visibleCount <= 0) return;

            int columns = Math.max(1, (int) Math.ceil(visibleCount / 8.0));
            int panelWidth = 14 + 18 * columns;
            int index = 0;

            try {
                Method addSlot = AbstractContainerMenu.class.getDeclaredMethod("addSlot", Slot.class);
                addSlot.setAccessible(true);

                for (Map.Entry<String, ICurioStacksHandler> entry : handler.getCurios().entrySet()) {
                    ICurioStacksHandler stacksHandler = entry.getValue();
                    if (!stacksHandler.isVisible()) continue;

                    IDynamicStackHandler stacks = stacksHandler.getStacks();
                    for (int slotIndex = 0; slotIndex < stacks.getSlots(); slotIndex++) {
                        int x = (index % columns) * 18 + 7 - panelWidth;
                        int y = 8 + (index / columns) * 18;
                        Slot curioSlot = new CurioSlot(
                                player, stacks, slotIndex, entry.getKey(), x, y,
                                stacksHandler.getRenders(), stacksHandler.getActiveStates(),
                                stacksHandler.canToggleRendering(), false, false);
                        addSlot.invoke(menu, curioSlot);
                        index++;
                    }
                }
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                // Keep the vanilla inventory usable if Curios changes its menu internals.
            }
        });
    }
}
