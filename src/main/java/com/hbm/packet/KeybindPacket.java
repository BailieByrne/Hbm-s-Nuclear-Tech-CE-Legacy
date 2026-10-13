package com.hbm.packet;

import com.hbm.handler.HbmKeybinds.EnumKeybind;
import com.hbm.handler.HbmKeybindsServer;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

public class KeybindPacket implements IMessage {

	int id;
	int key;
	boolean pressed;

	public KeybindPacket() { }

	public KeybindPacket(EnumKeybind key, boolean pressed) {
		this.key = key.ordinal();
		this.pressed = pressed;
		this.id = 0;
	}
	
	public KeybindPacket(int id) {
		this.id = id;
	}

	@Override
	public void fromBytes(ByteBuf buf) {
		key = buf.readInt();
		pressed = buf.readBoolean();
		id = buf.readInt();
	}

	@Override
	public void toBytes(ByteBuf buf) {
		buf.writeInt(key);
		buf.writeBoolean(pressed);
		buf.writeInt(id);
	}

	public static class Handler implements IMessageHandler<KeybindPacket, IMessage> {

		@Override
		public IMessage onMessage(KeybindPacket m, MessageContext ctx) {
			if(m.key < 0 || m.key >= EnumKeybind.VALUES.length) return null;
			EntityPlayerMP player = ctx.getServerHandler().player;
			player.getServerWorld().addScheduledTask(() ->
					HbmKeybindsServer.onPressedServer(player, EnumKeybind.VALUES[m.key], m.pressed));
			return null;
		}
	}
}