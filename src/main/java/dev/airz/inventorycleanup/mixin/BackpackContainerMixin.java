package dev.airz.inventorycleanup.mixin;

import dev.airz.inventorycleanup.CuriosMenuSlots;
import java.lang.reflect.Field;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Adds live Curios slots to both the server and client backpack menus. The slots are positioned
 * just to the left of Sophisticated Backpacks' storage frame.
 */
@Pseudo
@Mixin(targets = "net.p3pp3rf1y.sophisticatedbackpacks.common.gui.BackpackContainer", remap = false)
public abstract class BackpackContainerMixin {
    @Inject(method = "<init>", at = @At("TAIL"), remap = false, require = 0)
    private void inventorycleanup$addCuriosSlots(CallbackInfo ci) {
        try {
            Field playerField = null;
            for (Class<?> type = getClass(); type != null && playerField == null; type = type.getSuperclass()) {
                try {
                    playerField = type.getDeclaredField("player");
                } catch (NoSuchFieldException ignored) {
                    // Continue up to StorageContainerMenuBase.
                }
            }
            if (playerField == null) return;
            playerField.setAccessible(true);
            Object value = playerField.get(this);
            if (value instanceof Player player) {
                CuriosMenuSlots.addToStorageMenu(player, (AbstractContainerMenu) (Object) this);
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // Optional integration: never prevent a backpack from opening.
        }
    }
}
