package com.hbm.packet.toclient;

import com.hbm.main.MainRegistry;
import com.hbm.packet.threading.PrecompiledPacket;
import com.hbm.tileentity.IBufPacketReceiver;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

public class BufPacket extends PrecompiledPacket {

    int x;
    int y;
    int z;
    IBufPacketReceiver rec;
    ByteBuf buf;

    public BufPacket() { }

    public BufPacket(int x, int y, int z, IBufPacketReceiver rec) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.rec = rec;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.x = buf.readInt();
        this.y = buf.readInt();
        this.z = buf.readInt();
        this.buf = buf.retainedSlice();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(x);
        buf.writeInt(y);
        buf.writeInt(z);
        this.rec.serialize(buf);
    }

    public static class Handler implements IMessageHandler<BufPacket, IMessage> {

        @Override
        @SideOnly(Side.CLIENT)
        public IMessage onMessage(BufPacket m, MessageContext ctx) {
            Minecraft minecraft = Minecraft.getMinecraft();
            if (minecraft.isCallingFromMinecraftThread()) {
                deserializeOnClient(m, minecraft);
            } else {
                try {
                    minecraft.addScheduledTask(() -> deserializeOnClient(m, minecraft));
                } catch (RuntimeException | Error failure) {
                    m.buf.release();
                    throw failure;
                }
            }
            return null;
        }

        @SideOnly(Side.CLIENT)
        private static void deserializeOnClient(BufPacket m, Minecraft minecraft) {
            try {
                if (minecraft.world == null) return;
                BlockPos position = new BlockPos(m.x, m.y, m.z);
                TileEntity te = minecraft.world.getTileEntity(position);
                if (te instanceof IBufPacketReceiver receiver) {
                    try {
                        // Vanilla initial tile updates use the same client queue. Preserve their arrival order.
                        receiver.deserialize(m.buf);
                    } catch (Exception failure) {
                        MainRegistry.logger.warn("Failed to apply tile sync for {} at {}", te.getBlockType().getTranslationKey(), position, failure);
                    }
                }
            } finally {
                m.buf.release();
            }
        }
    }
}
