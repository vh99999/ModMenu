package com.example.modmenu.network;

import com.example.modmenu.store.logistics.*;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

public class RuleManagementPacket {
    public enum Type {
        ADD_UPDATE, REMOVE, TEST, ADD_UPDATE_TEMPLATE, REMOVE_TEMPLATE, APPLY_TEMPLATE
    }

    private static final java.util.Map<java.util.UUID, Long> testRuleCooldowns = new java.util.HashMap<>();

    private final Type type;
    private final UUID networkId;
    private UUID targetId;
    private boolean isGroup;
    private LogisticsRule ruleData;
    private RuleTemplate templateData;

    public RuleManagementPacket(Type type, UUID networkId) {
        this.type = type;
        this.networkId = networkId;
    }

    public static RuleManagementPacket addUpdate(UUID networkId, LogisticsRule rule) {
        RuleManagementPacket p = new RuleManagementPacket(Type.ADD_UPDATE, networkId);
        p.ruleData = rule;
        return p;
    }

    public static RuleManagementPacket remove(UUID networkId, UUID ruleId) {
        RuleManagementPacket p = new RuleManagementPacket(Type.REMOVE, networkId);
        p.targetId = ruleId;
        return p;
    }

    public static RuleManagementPacket test(UUID networkId, UUID ruleId) {
        RuleManagementPacket p = new RuleManagementPacket(Type.TEST, networkId);
        p.targetId = ruleId;
        return p;
    }

    public static RuleManagementPacket addUpdateTemplate(UUID networkId, RuleTemplate template) {
        RuleManagementPacket p = new RuleManagementPacket(Type.ADD_UPDATE_TEMPLATE, networkId);
        p.templateData = template;
        return p;
    }

    public static RuleManagementPacket removeTemplate(UUID networkId, UUID templateId) {
        RuleManagementPacket p = new RuleManagementPacket(Type.REMOVE_TEMPLATE, networkId);
        p.targetId = templateId;
        return p;
    }

    public static RuleManagementPacket applyTemplate(UUID networkId, UUID targetId, boolean isGroup, UUID templateId) {
        RuleManagementPacket p = new RuleManagementPacket(Type.APPLY_TEMPLATE, networkId);
        p.targetId = targetId;
        p.isGroup = isGroup;
        // Re-using templateData for carrying the ID of the template to apply for simplicity in this packet structure
        p.templateData = new RuleTemplate();
        p.templateData.templateId = templateId;
        return p;
    }

    public RuleManagementPacket(FriendlyByteBuf buf) {
        this.type = buf.readEnum(Type.class);
        this.networkId = buf.readUUID();
        if (buf.readBoolean()) this.targetId = buf.readUUID();
        this.isGroup = buf.readBoolean();
        if (buf.readBoolean()) this.ruleData = LogisticsUtil.readRule(buf);
        if (buf.readBoolean()) this.templateData = readTemplate(buf);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeEnum(type);
        buf.writeUUID(networkId);
        buf.writeBoolean(targetId != null);
        if (targetId != null) buf.writeUUID(targetId);
        buf.writeBoolean(isGroup);
        buf.writeBoolean(ruleData != null);
        if (ruleData != null) LogisticsUtil.writeRule(buf, ruleData);
        buf.writeBoolean(templateData != null);
        if (templateData != null) writeTemplate(buf, templateData);
    }

    private RuleTemplate readTemplate(FriendlyByteBuf buf) {
        RuleTemplate t = new RuleTemplate();
        t.templateId = buf.readUUID();
        t.name = buf.readUtf();
        if (buf.readBoolean()) t.rule = LogisticsUtil.readRule(buf);
        return t;
    }

    private void writeTemplate(FriendlyByteBuf buf, RuleTemplate t) {
        buf.writeUUID(t.templateId);
        buf.writeUtf(t.name != null ? t.name : "");
        buf.writeBoolean(t.rule != null);
        if (t.rule != null) LogisticsUtil.writeRule(buf, t.rule);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            LogisticsCapability.getNetworks(player).ifPresent(data -> {
                NetworkData network = data.getNetworks().stream().filter(n -> n.networkId.equals(networkId)).findFirst().orElse(null);
                if (network == null) return;

                switch (type) {
                    case ADD_UPDATE -> {
                        boolean updated = false;
                        for (int i = 0; i < network.rules.size(); i++) {
                            if (network.rules.get(i).ruleId.equals(ruleData.ruleId)) {
                                network.rules.set(i, ruleData);
                                updated = true;
                                break;
                            }
                        }
                        if (!updated) {
                            if (network.rules.size() >= LogisticsUtil.MAX_RULES_PER_NETWORK) {
                                player.displayClientMessage(net.minecraft.network.chat.Component.literal("\u00A7cNetwork rule limit reached!"), true);
                                return;
                            }
                            network.rules.add(ruleData);
                        }
                        network.needsSorting = true;
                    }
                    case REMOVE -> {
                        network.rules.removeIf(rule -> rule.ruleId.equals(targetId));
                        network.needsSorting = true;
                    }
                    case TEST -> {
                        long now = System.currentTimeMillis();
                        if (testRuleCooldowns.getOrDefault(player.getUUID(), 0L) > now) return;
                        testRuleCooldowns.put(player.getUUID(), now + 500);

                        LogisticsRule rule = network.rules.stream().filter(r -> r.ruleId.equals(targetId)).findFirst().orElse(null);
                        if (rule != null) {
                            boolean moved = NetworkTickHandler.processRule(player, network, rule, true);
                            float pitch = moved ? 1.0f : 0.5f;
                            player.level().playSound(null, player.getX(), player.getY(), player.getZ(), net.minecraft.sounds.SoundEvents.NOTE_BLOCK_CHIME.get(), net.minecraft.sounds.SoundSource.PLAYERS, 1.0f, pitch);
                        }
                    }
                    case ADD_UPDATE_TEMPLATE -> {
                        network.ruleTemplates.removeIf(t -> t.templateId.equals(templateData.templateId));
                        network.ruleTemplates.add(templateData);
                    }
                    case REMOVE_TEMPLATE -> {
                        network.ruleTemplates.removeIf(t -> t.templateId.equals(targetId));
                    }
                    case APPLY_TEMPLATE -> {
                        RuleTemplate template = network.ruleTemplates.stream().filter(t -> t.templateId.equals(templateData.templateId)).findFirst().orElse(null);
                        if (template != null) {
                            LogisticsRule newRule = template.rule.snapshot();
                            newRule.ruleId = UUID.randomUUID();
                            boolean sourceIsPerm = LogisticsUtil.isPermanent(network, targetId, isGroup);

                            if (sourceIsPerm) {
                                newRule.destNodeId = targetId;
                                newRule.destIsGroup = isGroup;
                            } else {
                                newRule.sourceNodeId = targetId;
                                newRule.sourceIsGroup = isGroup;
                            }
                            network.rules.add(newRule);
                            network.needsSorting = true;
                        }
                    }
                }
                LogisticsUtil.syncAndNotify(player, data);
            });
        });
        ctx.get().setPacketHandled(true);
    }
}
