package dev.airz.inventorycleanup;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

@EventBusSubscriber(modid = "inventorycleanup")
public final class CuriosMenuSyncHandler {
    private CuriosMenuSyncHandler() {}

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDatapackSync(OnDatapackSyncEvent event) {
        ServerPlayer player = event.getPlayer();
        if (player != null) addSlots(player);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onPlayerJoinLevel(EntityJoinLevelEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            addSlots(player);
        }
    }

    private static void addSlots(ServerPlayer player) {
        // Keep the initial vanilla 50-slot inventory packet untouched. Curios' own
        // sync packet is sent during login/join; the client mixin adds its slots after
        // applying that packet, while this runs after Curios' server-side sync event.
        if (player.containerMenu == player.inventoryMenu) {
            CuriosMenuSlots.addToInventoryMenu(player, player.inventoryMenu);
        }
    }
}
