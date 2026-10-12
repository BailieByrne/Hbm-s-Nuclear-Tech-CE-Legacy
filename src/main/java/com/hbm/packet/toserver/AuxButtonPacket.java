package com.hbm.packet.toserver;

import com.hbm.api.energymk2.IEnergyReceiverMK2;
import com.hbm.config.GeneralConfig;
import com.hbm.entity.mob.EntityDuck;
import com.hbm.items.weapon.ItemCrucible;
import com.hbm.items.weapon.ItemMissile.PartSize;
import com.hbm.inventory.container.ContainerCoreEmitter;
import com.hbm.inventory.container.ContainerCoreStabilizer;
import com.hbm.inventory.container.ContainerForceField;
import com.hbm.inventory.container.ContainerBarrel;
import com.hbm.inventory.container.ContainerLaunchTable;
import com.hbm.inventory.container.ContainerFEL;
import com.hbm.inventory.container.ContainerMachineFluidTank;
import com.hbm.inventory.container.ContainerMicrowave;
import com.hbm.inventory.container.ContainerNukeBalefire;
import com.hbm.inventory.container.ContainerSILEX;
import com.hbm.inventory.container.ContainerMachineBattery;
import com.hbm.inventory.container.ContainerMachineMiningLaser;
import com.hbm.inventory.container.ContainerMachineMissileAssembly;
import com.hbm.inventory.container.ContainerRailgun;
import com.hbm.inventory.container.ContainerSoyuzLauncher;
import com.hbm.inventory.container.ContainerTurretBase;
import com.hbm.items.tool.ItemTurretMobFilter;
import com.hbm.lib.HBMSoundHandler;
import com.hbm.packet.PacketDispatcher;
import com.hbm.packet.toclient.RailgunCallbackPacket;
import com.hbm.packet.toclient.RailgunFirePacket;
import com.hbm.tileentity.bomb.TileEntityLaunchTable;
import com.hbm.tileentity.bomb.TileEntityRailgun;
import com.hbm.tileentity.bomb.TileEntityNukeBalefire;
import com.hbm.tileentity.turret.TileEntityTurretArty;
import com.hbm.tileentity.turret.TileEntityTurretBaseNT;
import com.hbm.tileentity.turret.TileEntityTurretHIMARS;
import com.hbm.tileentity.machine.*;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

public class AuxButtonPacket implements IMessage {

    int x;
    int y;
    int z;
    int value;
    int id;

    public AuxButtonPacket() {

    }

    public AuxButtonPacket(int x, int y, int z, int value, int id) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.value = value;
        this.id = id;
    }

    public AuxButtonPacket(BlockPos pos, int value, int id) {
        this(pos.getX(), pos.getY(), pos.getZ(), value, id);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        x = buf.readInt();
        y = buf.readInt();
        z = buf.readInt();
        value = buf.readInt();
        id = buf.readInt();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(x);
        buf.writeInt(y);
        buf.writeInt(z);
        buf.writeInt(value);
        buf.writeInt(id);
    }

    public static class Handler implements IMessageHandler<AuxButtonPacket, IMessage> {

        @Override
        public IMessage onMessage(AuxButtonPacket m, MessageContext ctx) {
            ctx.getServerHandler().player.getServer().addScheduledTask(() -> {
                EntityPlayerMP p = ctx.getServerHandler().player;
                BlockPos pos = new BlockPos(m.x, m.y, m.z);

                //why make new packets when you can just abuse and uglify the existing ones?
                if (m.value == 999 && m.id == 0 && m.x == 0 && m.y == 0 && m.z == 0) {
                    if (!p.isEntityAlive() || p.isSpectator()) return;
                    if (GeneralConfig.duckButton) {
                        NBTTagCompound perDat = p.getEntityData().getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG);
                        if (!perDat.getBoolean("hasDucked")) {
                            EntityDuck ducc = new EntityDuck(p.world);
                            ducc.setPosition(p.posX, p.posY + p.eyeHeight, p.posZ);

                            Vec3d vec = p.getLookVec();
                            ducc.motionX = vec.x;
                            ducc.motionY = vec.y;
                            ducc.motionZ = vec.z;

                            p.world.spawnEntity(ducc);
                            p.world.playSound(null, p.posX, p.posY, p.posZ, HBMSoundHandler.ducc, SoundCategory.PLAYERS, 1.0F, 1.0F);

                            perDat.setBoolean("hasDucked", true);

                            p.getEntityData().setTag(EntityPlayer.PERSISTED_NBT_TAG, perDat);
                        }
                    }
                    return;
                }
                if (m.id == 1000) {
                    if (m.x != 0 || m.y != 0 || m.z != 0 || (m.value != 0 && m.value != 1)) return;
                    if (!p.isEntityAlive() || p.isSpectator()) {
                        ItemCrucible.setSpecialClick(p, false);
                        return;
                    }
                    if (m.value == 0) ItemCrucible.setSpecialClick(p, false);
                    else if (p.getHeldItemMainhand().getItem() instanceof ItemCrucible) ItemCrucible.setSpecialClick(p, true);
                    return;
                }
				/*if(m.value == 1000){
					NBTTagCompound perDat = p.getEntityData().getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG);
					int lightning = perDat.getInteger("lightningCharge");
					if(lightning == 0){
						perDat.setInteger("lightningCharge", 1);
					}
				}*/

                if (!p.world.isBlockLoaded(pos))
                    return;
                //try {
                TileEntity te = p.world.getTileEntity(pos);
					/*if (te instanceof TileEntityRadioRec) {
						TileEntityRadioRec radio = (TileEntityRadioRec)te;
						
						if(m.id == 0) {
							radio.isOn = (m.value == 1);
						}
						
						if(m.id == 1) {
							radio.freq = ((double)m.value) / 10D;
						}
					}
					
					*/
                if (te instanceof TileEntityForceField) {
                    TileEntityForceField field = (TileEntityForceField) te;
                    if (!p.isEntityAlive() || p.isSpectator()
                            || !(p.openContainer instanceof ContainerForceField menu)
                            || menu.diFurnace != field || !menu.canInteractWith(p)
                            || m.id != 0 || m.value != 0) return;
                    field.isOn = !field.isOn;
                    return;
                }


                if (te instanceof TileEntityMachineMissileAssembly) {
                    TileEntityMachineMissileAssembly assembly = (TileEntityMachineMissileAssembly) te;
                    if (!p.isEntityAlive() || p.isSpectator()
                            || !(p.openContainer instanceof ContainerMachineMissileAssembly menu)
                            || menu.missileAssembly != assembly || !menu.canInteractWith(p)
                            || m.id != 0 || m.value != 0) return;
                    assembly.construct();
                    return;
                }

                if (te instanceof TileEntityLaunchTable) {
                    TileEntityLaunchTable launcher = (TileEntityLaunchTable) te;
                    if (!p.isEntityAlive() || p.isSpectator()
                            || !(p.openContainer instanceof ContainerLaunchTable menu)
                            || menu.nukeBoy != launcher || !menu.canInteractWith(p) || m.id != 0) return;
                    if (m.value == PartSize.SIZE_10.ordinal()) launcher.padSize = PartSize.SIZE_10;
                    else if (m.value == PartSize.SIZE_15.ordinal()) launcher.padSize = PartSize.SIZE_15;
                    else if (m.value == PartSize.SIZE_20.ordinal()) launcher.padSize = PartSize.SIZE_20;
                    return;
                }

                if (te instanceof TileEntityRailgun) {
                    TileEntityRailgun gun = (TileEntityRailgun) te;
                    if (!p.isEntityAlive() || p.isSpectator()
                            || !(p.openContainer instanceof ContainerRailgun menu)
                            || menu.diFurnace != gun || !menu.canInteractWith(p)
                            || m.value != 0 || (m.id != 0 && m.id != 1)) return;
                    if (m.id == 0) {
                        if (gun.setAngles(false)) {
                            p.world.playSound(null, m.x, m.y, m.z, HBMSoundHandler.buttonYes, SoundCategory.BLOCKS, 1.0F, 1.0F);
                            p.world.playSound(null, m.x, m.y, m.z, HBMSoundHandler.railgunOrientation, SoundCategory.BLOCKS, 1.0F, 1.0F);
                            PacketDispatcher.wrapper.sendToAll(new RailgunCallbackPacket(m.x, m.y, m.z, gun.pitch, gun.yaw));
                        } else {
                            p.world.playSound(null, m.x, m.y, m.z, HBMSoundHandler.buttonNo, SoundCategory.BLOCKS, 1.0F, 1.0F);
                        }
                    }

                    if (m.id == 1) {
                        if (gun.canFire()) {
                            gun.fireDelay = TileEntityRailgun.cooldownDurationTicks;
                            PacketDispatcher.wrapper.sendToAll(new RailgunFirePacket(m.x, m.y, m.z));
                            p.world.playSound(null, m.x, m.y, m.z, HBMSoundHandler.buttonYes, SoundCategory.BLOCKS, 1.0F, 1.0F);
                            p.world.playSound(null, m.x, m.y, m.z, HBMSoundHandler.railgunCharge, SoundCategory.BLOCKS, 10.0F, 1.0F);
                        } else {
                            p.world.playSound(null, m.x, m.y, m.z, HBMSoundHandler.buttonNo, SoundCategory.BLOCKS, 1.0F, 1.0F);
                        }
                    }
                    return;
                }
                if (te instanceof TileEntityBarrel) {
                    TileEntityBarrel barrel = (TileEntityBarrel) te;
                    if (!p.isEntityAlive() || p.isSpectator()
                            || !(p.openContainer instanceof ContainerBarrel menu)
                            || menu.barrel != barrel || !menu.canInteractWith(p)
                            || m.id != 0 || m.value != 0) return;
                    barrel.mode = (short) ((barrel.mode + 1) % TileEntityBarrel.modes);
                    barrel.markDirty();
                    return;
                }
                if (te instanceof TileEntityCoreEmitter) {
                    TileEntityCoreEmitter core = (TileEntityCoreEmitter) te;
                    if (!p.isEntityAlive() || p.isSpectator()
                            || !(p.openContainer instanceof ContainerCoreEmitter menu)
                            || menu.nukeBoy != core || !menu.canInteractWith(p)) return;
                    if (m.id == 0) {
                        if (m.value < 1 || m.value > 100) return;
                        core.watts = m.value;
                    }
                    if (m.id == 1 && m.value == 0) {
                        core.isOn = !core.isOn;
                    }
                    return;
                }

                if (te instanceof TileEntityCoreStabilizer) {
                    TileEntityCoreStabilizer core = (TileEntityCoreStabilizer) te;
                    if (!p.isEntityAlive() || p.isSpectator()
                            || !(p.openContainer instanceof ContainerCoreStabilizer menu)
                            || menu.nukeBoy != core || !menu.canInteractWith(p)) return;
                    if (m.id == 0) {
                        if (m.value < 1 || m.value > 100) return;
                        core.watts = m.value;
                    }
                    return;
                }

                if (te instanceof TileEntitySoyuzLauncher) {
                    TileEntitySoyuzLauncher launcher = (TileEntitySoyuzLauncher) te;
                    if (!p.isEntityAlive() || p.isSpectator()
                            || !(p.openContainer instanceof ContainerSoyuzLauncher menu)
                            || menu.launcher != launcher || !menu.canInteractWith(p)) return;
                    if (m.id != 0 && m.id != 1 || m.id == 0 && m.value != 0 && m.value != 1 || m.id == 1 && m.value != 0) return;
                    if (m.id == 0)
                        launcher.mode = (byte) m.value;
                    if (m.id == 1)
                        launcher.startCountdown();
                    return;
                }
                if (te instanceof TileEntityMachineBattery) {
                    TileEntityMachineBattery bat = (TileEntityMachineBattery) te;
                    if (!p.isEntityAlive() || p.isSpectator()
                            || !(p.openContainer instanceof ContainerMachineBattery menu)
                            || menu.machineBattery != bat || !menu.canInteractWith(p)
                            || m.value != 0 || m.id < 0 || m.id > 2) return;
                    if (m.id == 0) {
                        bat.redLow = (short) ((bat.redLow + 1) % 4);
                        bat.markDirty();
                    }

                    if (m.id == 1) {
                        bat.redHigh = (short) ((bat.redHigh + 1) % 4);
                        bat.markDirty();
                    }

                    if (m.id == 2) {
                        switch (bat.priority) {
                            case LOW:
                                bat.priority = IEnergyReceiverMK2.ConnectionPriority.NORMAL;
                                break;
                            case NORMAL:
                                bat.priority = IEnergyReceiverMK2.ConnectionPriority.HIGH;
                                break;
                            case HIGH:
                                bat.priority = IEnergyReceiverMK2.ConnectionPriority.LOW;
                                break;
                        }
                        bat.markDirty();
                    }
                    return;
                }
                if (te instanceof TileEntityMachineMiningLaser) {
                    TileEntityMachineMiningLaser laser = (TileEntityMachineMiningLaser) te;
                    if (!p.isEntityAlive() || p.isSpectator()
                            || !(p.openContainer instanceof ContainerMachineMiningLaser menu)
                            || menu.laser != laser || !menu.canInteractWith(p)
                            || m.id != 0 || m.value != 0) return;
                    laser.isOn = !laser.isOn;
                    return;
                }

                if (te instanceof TileEntityMicrowave microwave) {
                    if (!p.isEntityAlive() || p.isSpectator()
                            || !(p.openContainer instanceof ContainerMicrowave menu)
                            || menu.microwave != microwave || !menu.canInteractWith(p)
                            || m.id != 0 || (m.value != 0 && m.value != 1)) return;
                    microwave.handleButtonPacket(m.value, m.id);
                    return;
                }
                if (te instanceof TileEntityFEL fel) {
                    if (!p.isEntityAlive() || p.isSpectator()
                            || !(p.openContainer instanceof ContainerFEL menu)
                            || menu.fel != fel || !menu.canInteractWith(p)
                            || m.id != 2 || m.value != 0) return;
                    fel.handleButtonPacket(m.value, m.id);
                    return;
                }
                if (te instanceof TileEntitySILEX silex) {
                    if (!p.isEntityAlive() || p.isSpectator()
                            || !(p.openContainer instanceof ContainerSILEX menu)
                            || menu.silex != silex || !menu.canInteractWith(p)
                            || m.id != 0 || m.value != 0) return;
                    silex.handleButtonPacket(m.value, m.id);
                    return;
                }
                if (te instanceof TileEntityMachineFluidTank tank) {
                    if (!p.isEntityAlive() || p.isSpectator()
                            || !(p.openContainer instanceof ContainerMachineFluidTank menu)
                            || menu.fluidTank != tank || !menu.canInteractWith(p)
                            || m.id != 0 || m.value != 0) return;
                    tank.handleButtonPacket(m.value, m.id);
                    return;
                }
                if (te instanceof TileEntityNukeBalefire bomb) {
                    if (!p.isEntityAlive() || p.isSpectator()
                            || !(p.openContainer instanceof ContainerNukeBalefire menu)
                            || menu.balefireBomb != bomb || !menu.canInteractWith(p)) return;
                    if (m.id == 0 && m.value == 0 || m.id == 1 && m.value >= 1 && m.value <= 999) {
                        bomb.handleButtonPacket(m.value, m.id);
                    }
                    return;
                }
                if (te instanceof TileEntityTurretBaseNT turret) {
                    if (!p.isEntityAlive() || p.isSpectator() || m.value != 0) return;
                    if (m.id == 6) {
                        if (!turret.isUseableByPlayer(p)
                                || !(p.getHeldItemMainhand().getItem() instanceof ItemTurretMobFilter)
                                && !(p.getHeldItemOffhand().getItem() instanceof ItemTurretMobFilter)) return;
                    } else {
                        if (!(p.openContainer instanceof ContainerTurretBase menu)
                                || menu.turret != turret || !menu.canInteractWith(p)
                                || m.id < 0 || m.id > 5
                                || m.id == 5 && !(turret instanceof TileEntityTurretArty) && !(turret instanceof TileEntityTurretHIMARS)) return;
                    }
                    turret.handleButtonPacket(m.value, m.id);
                    return;
                }
                //} catch (Exception x) { }
            });


            return null;
        }
    }

}
