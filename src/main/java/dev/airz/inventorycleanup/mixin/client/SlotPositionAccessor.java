package dev.airz.inventorycleanup.mixin.client;

import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Slot.class)
public interface SlotPositionAccessor {
    @Mutable
    @Accessor("x")
    void inventorycleanup$setX(int x);

    @Mutable
    @Accessor("y")
    void inventorycleanup$setY(int y);
}
