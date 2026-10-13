package com.hbm.packet.toserver;

import com.hbm.interfaces.IKeypadHandler;
import com.hbm.util.Keypad;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

public class KeypadServerPacket implements IMessage {

	public int x, y, z;
	//0 is button clicked, with data as the button id.
	public int type;
	public int data;
	
	public KeypadServerPacket() {
	}
	
	public KeypadServerPacket(BlockPos pos, int type, int data) {
		this.x = pos.getX();
		this.y = pos.getY();
		this.z = pos.getZ();
		this.type = type;
		this.data = data;
	}

	@Override
	public void fromBytes(ByteBuf buf) {
		x = buf.readInt();
		y = buf.readInt();
		z = buf.readInt();
		type = buf.readInt();
		data = buf.readInt();
	}

	@Override
	public void toBytes(ByteBuf buf) {
		buf.writeInt(x);
		buf.writeInt(y);
		buf.writeInt(z);
		buf.writeInt(type);
		buf.writeInt(data);
	}
	
	public static class Handler implements IMessageHandler<KeypadServerPacket, IMessage> {

		@Override
		public IMessage onMessage(KeypadServerPacket m, MessageContext ctx) {
			if(m.type != 0 || m.data < 0) return null;
			EntityPlayerMP player = ctx.getServerHandler().player;
			player.server.addScheduledTask(() -> {
				if(!player.isEntityAlive() || player.isSpectator()) return;
				WorldServer world = player.getServerWorld();
				BlockPos pos = new BlockPos(m.x, m.y, m.z);
				// Match NetHandlerPlayServer.processTryUseItemOnBlock's reach allowance.
				double reach = player.getEntityAttribute(EntityPlayer.REACH_DISTANCE).getAttributeValue() + 3D;
				if(player.getDistanceSq(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D) >= reach * reach
						|| !world.isBlockLoaded(pos)) return;
				TileEntity te = world.getTileEntity(pos);
				if(te instanceof IKeypadHandler) {
					Keypad pad = ((IKeypadHandler) te).getKeypad();
					if(m.data < pad.buttons.length) pad.buttonClicked(m.data);
				}
			});
			return null;
		}
		
	}
	
}
