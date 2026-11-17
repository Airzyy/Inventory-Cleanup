package dev.airz.inventorycleanup;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

@EventBusSubscriber(modid = "inventorycleanup")
public final class InventoryCleanupEvents {
    private InventoryCleanupEvents() {}

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            CuriosMenuSlots.addToInventoryMenu(player, player.inventoryMenu);
        }
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            CuriosMenuSlots.addToInventoryMenu(player, player.inventoryMenu);
        }
    }
}
