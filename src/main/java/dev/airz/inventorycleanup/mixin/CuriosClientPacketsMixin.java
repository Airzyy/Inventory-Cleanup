package dev.airz.inventorycleanup.mixin;

import dev.airz.inventorycleanup.CuriosMenuSlots;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.theillusivec4.curios.common.network.server.sync.SPacketSyncCurios;

@Mixin(targets = "top.theillusivec4.curios.common.network.client.CuriosClientPackets", remap = false)
public abstract class CuriosClientPacketsMixin {
    @Inject(method = "handle(Ltop/theillusivec4/curios/common/network/server/sync/SPacketSyncCurios;)V",
            at = @At("TAIL"), remap = false)
    private static void inventorycleanup$addSyncedCuriosSlots(SPacketSyncCurios packet, CallbackInfo ci) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || packet.entityId != player.getId()) return;
        if (player.containerMenu == player.inventoryMenu) {
            // Curios has now installed the authoritative client-side slot handlers.
            // Add the matching menu slots only after that sync packet is applied.
            CuriosMenuSlots.addToInventoryMenu(player, player.inventoryMenu);
        }
    }
}
