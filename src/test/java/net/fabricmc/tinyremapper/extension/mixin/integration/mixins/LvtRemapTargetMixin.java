/*
 * Copyright (c) 2016, 2018, Player, asie
 * Copyright (c) 2026, FabricMC
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

import com.llamalad7.mixinextras.sugar.Local;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import net.fabricmc.tinyremapper.extension.mixin.integration.targets.LvtRemapTarget;

import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LvtRemapTarget.class)
public class LvtRemapTargetMixin {
	@ModifyVariable(method = "target", at = @At("HEAD"), name = "str3")
	private String modifyStr3(String str3) {
		return "noice";
	}

	@ModifyVariable(at = @At("HEAD"), name = "str3", method = "target")
	private String modifyStr3Reordered(String str3) {
		return "noice";
	}

	@Inject(method = "target", at = @At("HEAD"))
	private void captureStr3WithLocal(CallbackInfo ci, @Local(name = "str3", argsOnly = true) String str3) {
	}
}
