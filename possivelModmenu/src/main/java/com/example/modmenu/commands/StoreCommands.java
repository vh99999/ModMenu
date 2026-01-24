package com.example.modmenu.commands;

import com.example.modmenu.network.PacketHandler;
import com.example.modmenu.network.SyncMoneyPacket;
import com.example.modmenu.store.StorePriceManager;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.math.BigDecimal;

public class StoreCommands {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("setcurrentcurrency")
            .requires(source -> source.hasPermission(2))
            .then(Commands.argument("player", EntityArgument.player())
                .then(Commands.argument("amount", StringArgumentType.string())
                    .executes(context -> {
                        ServerPlayer player = EntityArgument.getPlayer(context, "player");
                        String amountStr = StringArgumentType.getString(context, "amount");
                        try {
                            BigDecimal amount = new BigDecimal(amountStr);
                            StorePriceManager.setMoney(player.getUUID(), amount);
                            StorePriceManager.sync(player);
                            context.getSource().sendSuccess(() -> Component.literal("Set currency for " + player.getName().getString() + " to " + StorePriceManager.formatCurrency(amount)), true);
                            return 1;
                        } catch (NumberFormatException e) {
                            context.getSource().sendFailure(Component.literal("Invalid amount format: " + amountStr));
                            return 0;
                        }
                    })
                )
            )
        );

        dispatcher.register(Commands.literal("setsp")
            .requires(source -> source.hasPermission(2))
            .then(Commands.argument("player", EntityArgument.player())
                .then(Commands.argument("amount", StringArgumentType.string())
                    .executes(context -> {
                        ServerPlayer player = EntityArgument.getPlayer(context, "player");
                        String amountStr = StringArgumentType.getString(context, "amount");
                        try {
                            BigDecimal amount = new BigDecimal(amountStr);
                            StorePriceManager.getSkills(player.getUUID()).totalSP = amount;
                            StorePriceManager.markDirty(player.getUUID());
                            StorePriceManager.sync(player);
                            context.getSource().sendSuccess(() -> Component.literal("Set SP for " + player.getName().getString() + " to " + amount), true);
                            return 1;
                        } catch (NumberFormatException e) {
                            context.getSource().sendFailure(Component.literal("Invalid amount format: " + amountStr));
                            return 0;
                        }
                    })
                )
            )
        );

        dispatcher.register(Commands.literal("seteditor")
            .requires(source -> source.hasPermission(2))
            .then(Commands.argument("player", EntityArgument.player())
                .executes(context -> {
                    ServerPlayer player = EntityArgument.getPlayer(context, "player");
                    boolean newStatus = !StorePriceManager.isEditor(player.getUUID());
                    StorePriceManager.setEditor(player.getUUID(), newStatus);
                    StorePriceManager.sync(player);
                    context.getSource().sendSuccess(() -> Component.literal("Editor status for " + player.getName().getString() + " is now " + newStatus), true);
                    return 1;
                })
            )
        );

        dispatcher.register(Commands.literal("emcdump")
            .requires(source -> source.hasPermission(2))
            .executes(context -> EMCDumpCommand.dumpEmc(context.getSource()))
        );

        dispatcher.register(Commands.literal("applyemc")
            .requires(source -> source.hasPermission(2))
            .executes(context -> EMCDumpCommand.applyEmc(context.getSource()))
        );

        dispatcher.register(Commands.literal("savelayouts")
            .requires(source -> source.hasPermission(2))
            .executes(context -> {
                if (context.getSource().getEntity() instanceof ServerPlayer player) {
                    PacketHandler.sendToPlayer(new com.example.modmenu.network.SaveLayoutPacket(), player);
                    context.getSource().sendSuccess(() -> Component.literal("Layout save signal sent to client."), true);
                } else {
                    context.getSource().sendFailure(Component.literal("This command must be run by a player."));
                }
                return 1;
            })
        );

        dispatcher.register(Commands.literal("setspmultiplier")
            .requires(source -> source.hasPermission(2))
            .then(Commands.argument("multiplier", DoubleArgumentType.doubleArg(0.0))
                .executes(context -> {
                    double mult = DoubleArgumentType.getDouble(context, "multiplier");
                    StorePriceManager.formulas.spMultiplier = mult;
                    StorePriceManager.save();
                    context.getSource().sendSuccess(() -> Component.literal("Skill Point multiplier set to " + mult), true);
                    return 1;
                })
            )
        );
    }
}
