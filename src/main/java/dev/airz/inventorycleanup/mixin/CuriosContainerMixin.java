package dev.airz.inventorycleanup.mixin;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "top.theillusivec4.curios.common.inventory.container.CuriosContainer", remap = false)
public abstract class CuriosContainerMixin {
    @Inject(method = "setPage", at = @At("TAIL"), remap = false)
    private void inventorycleanup$addCosmeticArmorSlots(int page, CallbackInfo ci) {
        try {
            Field playerField = this.getClass().getDeclaredField("player");
            playerField.setAccessible(true);
            Player player = (Player) playerField.get(this);

            Class<?> armorClass = Class.forName("com.airzy.cosmeticslots.CosmeticArmor");
            EquipmentSlot[] equipmentSlots = (EquipmentSlot[]) armorClass.getField("SLOTS").get(null);
            Class<?> cosmeticSlotClass = Class.forName("com.airzy.cosmeticslots.CosmeticSlot");
            Constructor<?> constructor = cosmeticSlotClass.getConstructor(
                    Player.class, EquipmentSlot.class, int.class, int.class);
            Method addSlot = AbstractContainerMenu.class.getDeclaredMethod("addSlot", Slot.class);
            addSlot.setAccessible(true);

            for (int i = 0; i < equipmentSlots.length; i++) {
                Slot slot = (Slot) constructor.newInstance(player, equipmentSlots[i], 77, 8 + i * 18);
                addSlot.invoke(this, slot);
            }
        } catch (ClassNotFoundException ignored) {
            // Cosmetic Slots is optional.
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // Do not prevent Curios from opening if the cosmetic slot API changes.
        }
    }
}
