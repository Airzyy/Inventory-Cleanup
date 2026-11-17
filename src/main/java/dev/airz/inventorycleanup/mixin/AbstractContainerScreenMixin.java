package dev.airz.inventorycleanup.mixin;

import java.lang.reflect.Array;
import java.lang.reflect.Method;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractContainerScreen.class)
public abstract class AbstractContainerScreenMixin {
    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true, remap = false)
    private void inventorycleanup$openCuriosFromInlineSlots(double mouseX, double mouseY, int button,
                                                              CallbackInfoReturnable<Boolean> cir) {
        if (button != 0 || !((Object) this instanceof InventoryScreen screen)) return;

        int x = (int) mouseX - screen.getGuiLeft() - 180;
        int y = (int) mouseY - screen.getGuiTop() - 8;
        if (x < 0 || x >= 54 || y < 0 || y >= 144) return;

        var player = Minecraft.getInstance().player;
        if (player == null) return;
        ItemStack carried = player.containerMenu.getCarried();
        try {
            player.containerMenu.setCarried(ItemStack.EMPTY);
            Class<?> packetClass = Class.forName("top.theillusivec4.curios.common.network.client.CPacketOpenCurios");
            Object packet = packetClass.getConstructor(ItemStack.class).newInstance(carried);
            Class<?> payloadClass = Class.forName("net.minecraft.network.protocol.common.custom.CustomPacketPayload");
            Object extraPayloads = Array.newInstance(payloadClass, 0);
            Class<?> distributor = Class.forName("net.neoforged.neoforge.network.PacketDistributor");
            Method send = distributor.getMethod("sendToServer", payloadClass, extraPayloads.getClass());
            send.invoke(null, packet, extraPayloads);
            cir.setReturnValue(true);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            player.containerMenu.setCarried(carried);
            // If Curios cannot open its menu, allow vanilla click handling to continue.
        }
    }
}
