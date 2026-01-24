package com.example.modmenu.store.logistics;

import com.example.modmenu.modmenu;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.capabilities.*;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class LogisticsCapability {
    public static final Capability<PlayerNetworkData> PLAYER_NETWORKS = CapabilityManager.get(new CapabilityToken<>() {});

    public static LazyOptional<PlayerNetworkData> getNetworks(Player player) {
        return player.getCapability(PLAYER_NETWORKS);
    }

    @Mod.EventBusSubscriber(modid = modmenu.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static class ModEvents {
        @SubscribeEvent
        public static void onRegisterCapabilities(RegisterCapabilitiesEvent event) {
            event.register(PlayerNetworkData.class);
        }
    }

    @Mod.EventBusSubscriber(modid = modmenu.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
    public static class ForgeEvents {
        @SubscribeEvent
        public static void onAttachCapabilities(AttachCapabilitiesEvent<Entity> event) {
            if (event.getObject() instanceof Player) {
                event.addCapability(new ResourceLocation(modmenu.MODID, "networks"), new PlayerNetworkProvider());
            }
        }

        @SubscribeEvent
        public static void onRightClickBlock(net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickBlock event) {
            Player player = event.getEntity();
            getNetworks(player).ifPresent(data -> {
                    if (data.linkingNetworkId != null) {
                        if (player.isShiftKeyDown()) {
                            event.setCanceled(true);
                            event.setResult(net.minecraftforge.eventbus.api.Event.Result.DENY);
                        } else {
                            data.linkingNetworkId = null;
                        }
                    }
                });
        }

        @SubscribeEvent
        public static void onPlayerClone(net.minecraftforge.event.entity.player.PlayerEvent.Clone event) {
            event.getOriginal().getCapability(PLAYER_NETWORKS).ifPresent(oldData -> {
                event.getEntity().getCapability(PLAYER_NETWORKS).ifPresent(newData -> {
                    newData.copyFrom(oldData);
                });
            });
        }
    }

    public static class PlayerNetworkProvider implements ICapabilitySerializable<CompoundTag> {
        private final PlayerNetworkData data = new PlayerNetworkData();
        private final LazyOptional<PlayerNetworkData> optional = LazyOptional.of(() -> data);

        @Override
        public @NotNull <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
            if (cap == PLAYER_NETWORKS) {
                return optional.cast();
            }
            return LazyOptional.empty();
        }

        @Override
        public CompoundTag serializeNBT() {
            CompoundTag nbt = new CompoundTag();
            data.saveNBT(nbt);
            return nbt;
        }

        @Override
        public void deserializeNBT(CompoundTag nbt) {
            data.loadNBT(nbt);
        }
    }
}
