package net.citizensnpcs.nms.v1_19_R3.util;

import java.lang.invoke.MethodHandle;
import java.util.Set;

import org.bukkit.Bukkit;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

import com.google.common.collect.ForwardingSet;

import net.citizensnpcs.Settings.Setting;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.nms.v1_19_R3.entity.EntityHumanNPC;
import net.citizensnpcs.npc.ai.NPCHolder;
import net.citizensnpcs.util.NMS;
import net.minecraft.network.protocol.game.ClientboundAnimatePacket;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ChunkMap.TrackedEntity;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

public class CitizensEntityTracker extends ChunkMap.TrackedEntity {
    private ServerPlayer lastUpdatedPlayer;
    private final Entity tracker;

    public CitizensEntityTracker(ChunkMap map, Entity entity, int i, int j, boolean flag) {
        map.super(entity, i, j, flag);
        this.tracker = entity;
        try {
            Set set = (Set) TRACKING_SET_GETTER.invoke(this);
            TRACKING_SET_SETTER.invoke(this, new ForwardingSet() {
                @Override
                public boolean add(Object conn) {
                    boolean res = super.add(conn);
                    if (res) {
                        updateLastPlayer();
                    }
                    return res;
                }

                @Override
                protected Set delegate() {
                    return set;
                }
            });
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    public CitizensEntityTracker(ChunkMap map, TrackedEntity entry) {
        this(map, getTracker(entry), getTrackingDistance(entry), getE(entry), getF(entry));
    }

    public void updateLastPlayer() {
        if (tracker.isRemoved() || lastUpdatedPlayer == null
                || tracker.getBukkitEntity().getType() != EntityType.PLAYER)
            return;
        final ServerPlayer entityplayer = lastUpdatedPlayer;
        boolean sendTabRemove = NMS.sendTabListAdd(entityplayer.getBukkitEntity(), (Player) tracker.getBukkitEntity());
        if (!sendTabRemove || !Setting.DISABLE_TABLIST.asBoolean()) {
            Bukkit.getScheduler().scheduleSyncDelayedTask(CitizensAPI.getPlugin(),
                    () -> NMSImpl.sendPacket(entityplayer.getBukkitEntity(), new ClientboundAnimatePacket(tracker, 0)),
                    1);
            return;
        }
        Bukkit.getScheduler().scheduleSyncDelayedTask(CitizensAPI.getPlugin(), () -> {
            NMS.sendTabListRemove(entityplayer.getBukkitEntity(), (Player) tracker.getBukkitEntity());
            NMSImpl.sendPacket(entityplayer.getBukkitEntity(), new ClientboundAnimatePacket(tracker, 0));
        }, Setting.TABLIST_REMOVE_PACKET_DELAY.asTicks());
    }

    @Override
    public void updatePlayer(final ServerPlayer entityplayer) {
        if ((entityplayer instanceof EntityHumanNPC) || (tracker instanceof NPCHolder
                && ((NPCHolder) tracker).getNPC().isHiddenFrom(entityplayer.getBukkitEntity())))
            return;

        this.lastUpdatedPlayer = entityplayer;
        super.updatePlayer(entityplayer);
    }

    private static int getE(TrackedEntity entry) {
        try {
            return (int) E.invoke(TRACKER_ENTRY.invoke(entry));
        } catch (Throwable e) {
            e.printStackTrace();
        }
        return 0;
    }

    private static boolean getF(TrackedEntity entry) {
        try {
            return (boolean) F.invoke(TRACKER_ENTRY.invoke(entry));
        } catch (Throwable e) {
            e.printStackTrace();
        }
        return false;
    }

    private static Entity getTracker(TrackedEntity entry) {
        try {
            return (Entity) TRACKER.invoke(entry);
        } catch (Throwable e) {
            e.printStackTrace();
        }
        return null;
    }

    private static int getTrackingDistance(TrackedEntity entry) {
        try {
            Entity entity = getTracker(entry);
            if (entity instanceof NPCHolder) {
                return ((NPCHolder) entity).getNPC().data().get(NPC.Metadata.TRACKING_RANGE,
                        (Integer) TRACKING_RANGE.invoke(entry));
            }
            return (Integer) TRACKING_RANGE.invoke(entry);
        } catch (Throwable e) {
            e.printStackTrace();
        }
        return 0;
    }

    private static final MethodHandle E = NMS.getGetter(ServerEntity.class, "e");
    private static final MethodHandle F = NMS.getGetter(ServerEntity.class, "f");
    private static final MethodHandle TRACKER = NMS.getFirstGetter(TrackedEntity.class, Entity.class);
    private static final MethodHandle TRACKER_ENTRY = NMS.getFirstGetter(TrackedEntity.class, ServerEntity.class);
    private static final MethodHandle TRACKING_RANGE = NMS.getFirstGetter(TrackedEntity.class, int.class);
    private static final MethodHandle TRACKING_SET_GETTER = NMS.getFirstGetter(TrackedEntity.class, Set.class);
    private static final MethodHandle TRACKING_SET_SETTER = NMS.getFirstFinalSetter(TrackedEntity.class, Set.class);
}