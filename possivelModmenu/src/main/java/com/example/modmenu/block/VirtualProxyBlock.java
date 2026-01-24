package com.example.modmenu.block;

import com.example.modmenu.block.entity.VirtualProxyBlockEntity;
import com.example.modmenu.registry.BlockEntityRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

public class VirtualProxyBlock extends Block implements EntityBlock {
    public VirtualProxyBlock(Properties properties) {
        super(properties);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new VirtualProxyBlockEntity(pos, state);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        if (!level.isClientSide && placer instanceof Player player) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof VirtualProxyBlockEntity proxy) {
                proxy.setOwner(player.getUUID());
            }
        }
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!level.isClientSide) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof VirtualProxyBlockEntity proxy) {
                if (player.isShiftKeyDown()) {
                    proxy.cycleChamber();
                    String target = "None";
                    com.example.modmenu.store.StorePriceManager.SkillData data = com.example.modmenu.store.StorePriceManager.getSkills(player.getUUID());
                    if (proxy.getChamberIndex() >= 0 && proxy.getChamberIndex() < data.chambers.size()) {
                        com.example.modmenu.store.StorePriceManager.ChamberData chamber = data.chambers.get(proxy.getChamberIndex());
                        target = chamber.customName != null ? chamber.customName : (chamber.isExcavation ? chamber.lootTableId : chamber.mobId);
                    }
                    player.displayClientMessage(Component.literal("\u00A76[Virtual Proxy] \u00A7aBound to Chamber: \u00A7e" + proxy.getChamberIndex() + " \u00A77(" + target + ")"), true);
                } else {
                    String target = "None";
                    com.example.modmenu.store.StorePriceManager.SkillData data = com.example.modmenu.store.StorePriceManager.getSkills(player.getUUID());
                    if (proxy.getChamberIndex() >= 0 && proxy.getChamberIndex() < data.chambers.size()) {
                        com.example.modmenu.store.StorePriceManager.ChamberData chamber = data.chambers.get(proxy.getChamberIndex());
                        target = chamber.customName != null ? chamber.customName : (chamber.isExcavation ? chamber.lootTableId : chamber.mobId);
                    }
                    player.displayClientMessage(Component.literal("\u00A76[Virtual Proxy] \u00A77Status: \u00A7aOnline"), false);
                    player.displayClientMessage(Component.literal("\u00A77Bound to Chamber: \u00A7e" + proxy.getChamberIndex() + " \u00A77(" + target + ")"), false);
                    player.displayClientMessage(Component.literal("\u00A77Owner UUID: \u00A7b" + player.getUUID().toString()), false);
                    player.displayClientMessage(Component.literal("\u00A7eShift-Right-Click to cycle chambers."), false);
                }
            }
        }
        return InteractionResult.SUCCESS;
    }
}
