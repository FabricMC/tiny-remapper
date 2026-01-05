package net.fabricmc.tinyremapper.extension.mixin.integration.mixins;

import net.fabricmc.tinyremapper.extension.mixin.integration.targets.AmbiguousRemappedNameTarget;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AmbiguousRemappedNameTarget.class)
public class AmbiguousRemappedNameMixin {
	@Inject(method = "addString", at = @At("HEAD"))
	private void injectAddString(String string, CallbackInfo ci) {
	}
}
