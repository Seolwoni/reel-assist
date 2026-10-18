package com.seolwoni.reelassist.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.seolwoni.reelassist.fishing.BobberFx;

import net.minecraft.world.entity.Entity;

@Mixin(Entity.class)
public class EntityMixin {
	@Inject(method = "getTeamColor", at = @At("HEAD"), cancellable = true)
	private void reelassist$bobberColor(CallbackInfoReturnable<Integer> cir) {
		Entity self = (Entity) (Object) this;
		if (BobberFx.shouldGlow(self)) cir.setReturnValue(BobberFx.color());
	}
}
