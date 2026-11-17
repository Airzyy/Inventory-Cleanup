package dev.airz.inventorycleanup.mixin;

import dev.airz.inventorycleanup.client.BackpackCompat;
import dev.airz.inventorycleanup.client.InventoryTabButton;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Sophisticated Core sizes its storage screens to fill the whole window height, which would leave
 * no room above the frame for the tab row. For backpack screens only, this makes it size itself
 * as if the window were shorter by the tab row's height above <em>and</em> below, so the screen
 * stays centered with the row visible. Extra rows simply fall into the screen's own scroll area.
 *
 * <p>Targeted by name so Sophisticated Core is not needed at compile time. {@code require = 0}
 * means a future rename inside Sophisticated Core just loses the spacing instead of crashing.
 */
@Mixin(targets = "net.p3pp3rf1y.sophisticatedcore.client.gui.StorageScreenBase", remap = false)
public abstract class StorageScreenBaseMixin {
    @ModifyVariable(method = "updateDimensionsAndSlotPositions", at = @At("HEAD"), argsOnly = true,
            remap = false, require = 0)
    private int inventorycleanup$leaveRoomForTabs(int height) {
        if (!((Object) this instanceof Screen screen) || !BackpackCompat.isBackpackScreen(screen)) return height;

        // The tab row is not added in creative, so don't waste space there.
        Player player = Minecraft.getInstance().player;
        if (player == null || player.isCreative()) return height;

        int tabRowHeight = -InventoryTabButton.ROW_OFFSET_Y;
        return height - 2 * tabRowHeight;
    }
}
