package com.example.modmenu.network;

import com.example.modmenu.modmenu;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public class PacketHandler {
    private static final String PROTOCOL_VERSION = "1";
    public static final SimpleChannel INSTANCE = NetworkRegistry.newSimpleChannel(
            ResourceLocation.tryParse("modmenu" + ":main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    private static int packetId = 0;

    public static void register() {
        INSTANCE.messageBuilder(PurchasePacket.class, packetId++, NetworkDirection.PLAY_TO_SERVER)
                .decoder(PurchasePacket::new)
                .encoder(PurchasePacket::encode)
                .consumerMainThread(PurchasePacket::handle)
                .add();

        INSTANCE.messageBuilder(SyncMoneyPacket.class, packetId++, NetworkDirection.PLAY_TO_CLIENT)
                .decoder(SyncMoneyPacket::new)
                .encoder(SyncMoneyPacket::encode)
                .consumerMainThread(SyncMoneyPacket::handle)
                .add();

        INSTANCE.messageBuilder(UpdatePricePacket.class, packetId++, NetworkDirection.PLAY_TO_SERVER)
                .decoder(UpdatePricePacket::new)
                .encoder(UpdatePricePacket::encode)
                .consumerMainThread(UpdatePricePacket::handle)
                .add();

        INSTANCE.messageBuilder(SellPacket.class, packetId++, NetworkDirection.PLAY_TO_SERVER)
                .decoder(SellPacket::new)
                .encoder(SellPacket::encode)
                .consumerMainThread(SellPacket::handle)
                .add();

        INSTANCE.messageBuilder(EnchantItemPacket.class, packetId++, NetworkDirection.PLAY_TO_SERVER)
                .decoder(EnchantItemPacket::new)
                .encoder(EnchantItemPacket::encode)
                .consumerMainThread(EnchantItemPacket::handle)
                .add();

        INSTANCE.messageBuilder(SellAllPacket.class, packetId++, NetworkDirection.PLAY_TO_SERVER)
                .decoder(SellAllPacket::new)
                .encoder(SellAllPacket::encode)
                .consumerMainThread(SellAllPacket::handle)
                .add();

        INSTANCE.messageBuilder(UpdateEnchantPricePacket.class, packetId++, NetworkDirection.PLAY_TO_SERVER)
                .decoder(UpdateEnchantPricePacket::new)
                .encoder(UpdateEnchantPricePacket::encode)
                .consumerMainThread(UpdateEnchantPricePacket::handle)
                .add();

        INSTANCE.messageBuilder(ToggleEffectPacket.class, packetId++, NetworkDirection.PLAY_TO_SERVER)
                .decoder(ToggleEffectPacket::new)
                .encoder(ToggleEffectPacket::encode)
                .consumerMainThread(ToggleEffectPacket::handle)
                .add();

        INSTANCE.messageBuilder(UpdateEffectPricePacket.class, packetId++, NetworkDirection.PLAY_TO_SERVER)
                .decoder(UpdateEffectPricePacket::new)
                .encoder(UpdateEffectPricePacket::encode)
                .consumerMainThread(UpdateEffectPricePacket::handle)
                .add();

        INSTANCE.messageBuilder(ClearEffectsPacket.class, packetId++, NetworkDirection.PLAY_TO_SERVER)
                .decoder(ClearEffectsPacket::new)
                .encoder(ClearEffectsPacket::encode)
                .consumerMainThread(ClearEffectsPacket::handle)
                .add();

        INSTANCE.messageBuilder(UpdateAbilityPacket.class, packetId++, NetworkDirection.PLAY_TO_SERVER)
                .decoder(UpdateAbilityPacket::new)
                .encoder(UpdateAbilityPacket::encode)
                .consumerMainThread(UpdateAbilityPacket::handle)
                .add();

        INSTANCE.messageBuilder(SmeltAllPacket.class, packetId++, NetworkDirection.PLAY_TO_SERVER)
                .decoder(SmeltAllPacket::new)
                .encoder(SmeltAllPacket::encode)
                .consumerMainThread(SmeltAllPacket::handle)
                .add();

        INSTANCE.messageBuilder(SaveLayoutPacket.class, packetId++, NetworkDirection.PLAY_TO_CLIENT)
                .decoder(SaveLayoutPacket::new)
                .encoder(SaveLayoutPacket::encode)
                .consumerMainThread(SaveLayoutPacket::handle)
                .add();

        INSTANCE.messageBuilder(TeleportToMiningDimensionPacket.class, packetId++, NetworkDirection.PLAY_TO_SERVER)
                .decoder(TeleportToMiningDimensionPacket::new)
                .encoder(TeleportToMiningDimensionPacket::encode)
                .consumerMainThread(TeleportToMiningDimensionPacket::handle)
                .add();

        INSTANCE.messageBuilder(AdminActionPacket.class, packetId++, NetworkDirection.PLAY_TO_SERVER)
                .decoder(AdminActionPacket::new)
                .encoder(AdminActionPacket::encode)
                .consumerMainThread(AdminActionPacket::handle)
                .add();

        INSTANCE.messageBuilder(SyncPricesPacket.class, packetId++, NetworkDirection.PLAY_TO_CLIENT)
                .decoder(SyncPricesPacket::new)
                .encoder(SyncPricesPacket::encode)
                .consumerMainThread(SyncPricesPacket::handle)
                .add();

        INSTANCE.messageBuilder(UnlockFeaturePacket.class, packetId++, NetworkDirection.PLAY_TO_SERVER)
                .decoder(UnlockFeaturePacket::new)
                .encoder(UnlockFeaturePacket::encode)
                .consumerMainThread(UnlockFeaturePacket::handle)
                .add();

        INSTANCE.messageBuilder(AttributeUpgradePacket.class, packetId++, NetworkDirection.PLAY_TO_SERVER)
                .decoder(AttributeUpgradePacket::new)
                .encoder(AttributeUpgradePacket::encode)
                .consumerMainThread(AttributeUpgradePacket::handle)
                .add();
    }

    public static void sendToServer(Object msg) {
        INSTANCE.sendToServer(msg);
    }

    public static void sendToPlayer(Object msg, ServerPlayer player) {
        INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), msg);
    }
}
