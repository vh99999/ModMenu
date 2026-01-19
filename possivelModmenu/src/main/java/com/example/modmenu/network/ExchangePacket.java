package com.example.modmenu.network;

import com.example.modmenu.store.StorePriceManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.math.BigDecimal;
import java.util.function.Supplier;

public class ExchangePacket {
    private final boolean moneyToSP;
    private final BigDecimal amount;

    public ExchangePacket(boolean moneyToSP, BigDecimal amount) {
        this.moneyToSP = moneyToSP;
        this.amount = amount;
    }

    public ExchangePacket(FriendlyByteBuf buf) {
        this.moneyToSP = buf.readBoolean();
        this.amount = new BigDecimal(buf.readUtf());
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBoolean(moneyToSP);
        buf.writeUtf(amount.toString());
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) {
                StorePriceManager.SkillData data = StorePriceManager.getSkills(player.getUUID());
                BigDecimal currentMoney = StorePriceManager.getMoney(player.getUUID());
                
                if (moneyToSP) {
                    BigDecimal cost = amount.multiply(BigDecimal.valueOf(1000000000L));
                    if (currentMoney.compareTo(cost) >= 0) {
                        StorePriceManager.addMoney(player.getUUID(), cost.negate());
                        data.totalSP = data.totalSP.add(amount);
                        StorePriceManager.sync(player);
                    }
                } else {
                    if (data.totalSP.subtract(data.spentSP).compareTo(amount) >= 0) {
                        data.spentSP = data.spentSP.add(amount);
                        StorePriceManager.addMoney(player.getUUID(), amount.multiply(BigDecimal.valueOf(100000000L)));
                        StorePriceManager.sync(player);
                    }
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
