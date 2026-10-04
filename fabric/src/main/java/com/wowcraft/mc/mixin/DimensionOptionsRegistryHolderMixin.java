package com.wowcraft.mc.mixin;

import com.mojang.serialization.Lifecycle;
import com.wowcraft.mc.WowCraftMod;
import net.minecraft.registry.RegistryKey;
import net.minecraft.world.dimension.DimensionOptions;
import net.minecraft.world.dimension.DimensionOptionsRegistryHolder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Vanilla marks every non-vanilla level stem as "experimental", which makes the game warn on world creation and ask for a
 * backup on every load. The instance dimension is part of the mod, so its stem counts as stable; other mods' dimensions
 * keep their own lifecycle (entries still mark the registry experimental).
 */
@Mixin(DimensionOptionsRegistryHolder.class)
public abstract class DimensionOptionsRegistryHolderMixin {
    @Inject(method = "getLifecycle", at = @At("HEAD"), cancellable = true)
    private static void wowcraft$stableInstanceDimension(RegistryKey<DimensionOptions> key, DimensionOptions options, CallbackInfoReturnable<Lifecycle> cir) {
        if (WowCraftMod.ID.equals(key.getValue().getNamespace())) cir.setReturnValue(Lifecycle.stable());
    }

    /** The registry's base lifecycle only reflects "more stems than vanilla"; per-entry lifecycles still apply on top. */
    @ModifyArg(method = "toConfig", require = 0,
            at = @At(value = "INVOKE", target = "Lnet/minecraft/registry/SimpleRegistry;<init>(Lnet/minecraft/registry/RegistryKey;Lcom/mojang/serialization/Lifecycle;)V"), index = 1)
    private Lifecycle wowcraft$stableBase(Lifecycle lifecycle) {
        return Lifecycle.stable();
    }

    @ModifyArg(method = "toConfig", require = 0,
            at = @At(value = "INVOKE", target = "Lnet/minecraft/registry/SimpleRegistry;<init>(Lnet/minecraft/registry/RegistryKey;Lcom/mojang/serialization/Lifecycle;Z)V"), index = 1)
    private Lifecycle wowcraft$stableBaseIntrusive(Lifecycle lifecycle) {
        return Lifecycle.stable();
    }
}
