/*
 * Copyright (c) 2016, 2018, Player, asie
 * Copyright (c) 2025, FabricMC
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package net.fabricmc.tinyremapper.extension.mixin.integration.mixins;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.fabricmc.tinyremapper.extension.mixin.integration.targets.NonObfuscatedOverrideTarget;

@Mixin(NonObfuscatedOverrideTarget.class)
public class NonObfuscatedOverrideMixin {
	@Inject(method = "callAdd", at = @At(value = "INVOKE", target = "Lnet/fabricmc/tinyremapper/extension/mixin/integration/targets/NonObfuscatedOverrideTarget;add(Ljava/lang/Object;)Z"))
	private void overridingTarget(Object add, CallbackInfo ci) {
	}

	@Inject(method = "callAdd", at = @At(value = "INVOKE", target = "Lnet/fabricmc/tinyremapper/extension/mixin/integration/targets/NonObfuscatedOverrideTarget;addAll(Ljava/util/Collection;)Z"))
	private void superTarget(Object add, CallbackInfo ci) {
	}
}
