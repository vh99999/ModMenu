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

        INSTANCE.messageBuilder(SyncSkillsPacket.class, packetId++, NetworkDirection.PLAY_TO_CLIENT)
                .decoder(SyncSkillsPacket::new)
                .encoder(SyncSkillsPacket::encode)
                .consumerMainThread(SyncSkillsPacket::handle)
                .add();

        INSTANCE.messageBuilder(SkillUpgradePacket.class, packetId++, NetworkDirection.PLAY_TO_SERVER)
                .decoder(SkillUpgradePacket::new)
                .encoder(SkillUpgradePacket::encode)
                .consumerMainThread(SkillUpgradePacket::handle)
                .add();

        INSTANCE.messageBuilder(ToggleSkillPacket.class, packetId++, NetworkDirection.PLAY_TO_SERVER)
                .decoder(ToggleSkillPacket::new)
                .encoder(ToggleSkillPacket::encode)
                .consumerMainThread(ToggleSkillPacket::handle)
                .add();

        INSTANCE.messageBuilder(PurgeSatietyPacket.class, packetId++, NetworkDirection.PLAY_TO_SERVER)
                .decoder(PurgeSatietyPacket::new)
                .encoder(PurgeSatietyPacket::encode)
                .consumerMainThread(PurgeSatietyPacket::handle)
                .add();

        INSTANCE.messageBuilder(TotalChunkLiquidationPacket.class, packetId++, NetworkDirection.PLAY_TO_SERVER)
                .decoder(TotalChunkLiquidationPacket::new)
                .encoder(TotalChunkLiquidationPacket::encode)
                .consumerMainThread(TotalChunkLiquidationPacket::handle)
                .add();

        INSTANCE.messageBuilder(AtmosphericControlPacket.class, packetId++, NetworkDirection.PLAY_TO_SERVER)
                .decoder(AtmosphericControlPacket::new)
                .encoder(AtmosphericControlPacket::encode)
                .consumerMainThread(AtmosphericControlPacket::handle)
                .add();

        INSTANCE.messageBuilder(WormholePacket.class, packetId++, NetworkDirection.PLAY_TO_SERVER)
                .decoder(WormholePacket::new)
                .encoder(WormholePacket::encode)
                .consumerMainThread(WormholePacket::handle)
                .add();

        INSTANCE.messageBuilder(SetSkillRankPacket.class, packetId++, NetworkDirection.PLAY_TO_SERVER)
                .decoder(SetSkillRankPacket::new)
                .encoder(SetSkillRankPacket::encode)
                .consumerMainThread(SetSkillRankPacket::handle)
                .add();

        INSTANCE.messageBuilder(ActionChamberPacket.class, packetId++, NetworkDirection.PLAY_TO_SERVER)
                .decoder(ActionChamberPacket::new)
                .encoder(ActionChamberPacket::encode)
                .consumerMainThread(ActionChamberPacket::handle)
                .add();

        INSTANCE.messageBuilder(OpenCaptureGuiPacket.class, packetId++, NetworkDirection.PLAY_TO_CLIENT)
                .decoder(OpenCaptureGuiPacket::new)
                .encoder(OpenCaptureGuiPacket::encode)
                .consumerMainThread(OpenCaptureGuiPacket::handle)
                .add();

        INSTANCE.messageBuilder(CaptureMobPacket.class, packetId++, NetworkDirection.PLAY_TO_SERVER)
                .decoder(CaptureMobPacket::new)
                .encoder(CaptureMobPacket::encode)
                .consumerMainThread(CaptureMobPacket::handle)
                .add();

        INSTANCE.messageBuilder(OpenLootRecalibrationPacket.class, packetId++, NetworkDirection.PLAY_TO_CLIENT)
                .decoder(OpenLootRecalibrationPacket::new)
                .encoder(OpenLootRecalibrationPacket::encode)
                .consumerMainThread(OpenLootRecalibrationPacket::handle)
                .add();

        INSTANCE.messageBuilder(RerollLootPacket.class, packetId++, NetworkDirection.PLAY_TO_SERVER)
                .decoder(RerollLootPacket::new)
                .encoder(RerollLootPacket::encode)
                .consumerMainThread(RerollLootPacket::handle)
                .add();

        INSTANCE.messageBuilder(ExecuteProtocolPacket.class, packetId++, NetworkDirection.PLAY_TO_SERVER)
                .decoder(ExecuteProtocolPacket::new)
                .encoder(ExecuteProtocolPacket::encode)
                .consumerMainThread(ExecuteProtocolPacket::handle)
                .add();

        INSTANCE.messageBuilder(SynthesisPacket.class, packetId++, NetworkDirection.PLAY_TO_SERVER)
                .decoder(SynthesisPacket::new)
                .encoder(SynthesisPacket::encode)
                .consumerMainThread(SynthesisPacket::handle)
                .add();

        INSTANCE.messageBuilder(ExchangePacket.class, packetId++, NetworkDirection.PLAY_TO_SERVER)
                .decoder(ExchangePacket::new)
                .encoder(ExchangePacket::encode)
                .consumerMainThread(ExchangePacket::handle)
                .add();

        INSTANCE.messageBuilder(AcceptLootPacket.class, packetId++, NetworkDirection.PLAY_TO_SERVER)
                .decoder(AcceptLootPacket::new)
                .encoder(AcceptLootPacket::encode)
                .consumerMainThread(AcceptLootPacket::handle)
                .add();

        INSTANCE.messageBuilder(RequestChamberLootPacket.class, packetId++, NetworkDirection.PLAY_TO_SERVER)
                .decoder(RequestChamberLootPacket::new)
                .encoder(RequestChamberLootPacket::encode)
                .consumerMainThread(RequestChamberLootPacket::handle)
                .add();

        INSTANCE.messageBuilder(SyncChamberLootPacket.class, packetId++, NetworkDirection.PLAY_TO_CLIENT)
                .decoder(SyncChamberLootPacket::new)
                .encoder(SyncChamberLootPacket::encode)
                .consumerMainThread(SyncChamberLootPacket::handle)
                .add();

        INSTANCE.messageBuilder(ToggleItemLockPacket.class, packetId++, NetworkDirection.PLAY_TO_SERVER)
                .decoder(ToggleItemLockPacket::new)
                .encoder(ToggleItemLockPacket::encode)
                .consumerMainThread(ToggleItemLockPacket::handle)
                .add();
    }

    public static void sendToServer(Object msg) {
        INSTANCE.sendToServer(msg);
    }

    public static void sendToPlayer(Object msg, ServerPlayer player) {
        INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), msg);
    }
}
