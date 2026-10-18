package com.seolwoni.reelassist.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.seolwoni.reelassist.fishing.BobberFx;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;

@Mixin(ClientPacketListener.class)
public class ClientPacketListenerMixin {
	@Inject(method = "handleParticleEvent", at = @At("HEAD"))
	private void reelassist$watchWake(ClientboundLevelParticlesPacket packet, CallbackInfo ci) {
		// The handler first runs on the network thread and re-queues itself; only act on the main thread.
		if (Minecraft.getInstance().isSameThread()) BobberFx.onParticle(packet);
	}
}
