package com.example.modmenu.block.entity;

import com.example.modmenu.registry.BlockEntityRegistry;
import com.example.modmenu.store.StorePriceManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public class VirtualProxyBlockEntity extends BlockEntity {
    private UUID owner;
    private int chamberIndex = 0;
    private final LazyOptional<IItemHandler> handler = LazyOptional.of(() -> new ProxyItemHandler());

    public VirtualProxyBlockEntity(BlockPos pos, BlockState state) {
        super(BlockEntityRegistry.VIRTUAL_PROXY_BE.get(), pos, state);
    }

    public void setOwner(UUID owner) {
        this.owner = owner;
        setChanged();
    }

    public void setChamberIndex(int index) {
        this.chamberIndex = index;
        setChanged();
    }

    public int getChamberIndex() {
        return chamberIndex;
    }

    public void cycleChamber() {
        if (owner != null) {
            StorePriceManager.SkillData data = StorePriceManager.getSkills(owner);
            if (!data.chambers.isEmpty()) {
                chamberIndex = (chamberIndex + 1) % data.chambers.size();
                setChanged();
            }
        }
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (tag.hasUUID("Owner")) {
            this.owner = tag.getUUID("Owner");
        }
        this.chamberIndex = tag.getInt("ChamberIndex");
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (owner != null) {
            tag.putUUID("Owner", owner);
        }
        tag.putInt("ChamberIndex", chamberIndex);
    }

    @Override
    public @NotNull <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        if (cap == ForgeCapabilities.ITEM_HANDLER) {
            return handler.cast();
        }
        return super.getCapability(cap, side);
    }

    private class ProxyItemHandler implements IItemHandler {
        private StorePriceManager.ChamberData getChamber() {
            if (owner == null) return null;
            StorePriceManager.SkillData data = StorePriceManager.getSkills(owner);
            if (chamberIndex >= 0 && chamberIndex < data.chambers.size()) {
                return data.chambers.get(chamberIndex);
            }
            return null;
        }

        @Override
        public int getSlots() {
            StorePriceManager.ChamberData chamber = getChamber();
            if (chamber != null) {
                synchronized (chamber.storedLoot) {
                    return chamber.storedLoot.size() + 1; // +1 for "virtual" expansion
                }
            }
            return 0;
        }

        @Override
        public @NotNull ItemStack getStackInSlot(int slot) {
            StorePriceManager.ChamberData chamber = getChamber();
            if (chamber != null) {
                synchronized (chamber.storedLoot) {
                    if (slot >= 0 && slot < chamber.storedLoot.size()) {
                        return chamber.storedLoot.get(slot);
                    }
                }
            }
            return ItemStack.EMPTY;
        }

        @Override
        public @NotNull ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
            // Virtual Proxy is output-only for now, or we could redirect to inputBuffer
            return stack;
        }

        @Override
        public @NotNull ItemStack extractItem(int slot, int amount, boolean simulate) {
            StorePriceManager.ChamberData chamber = getChamber();
            if (chamber != null) {
                synchronized (chamber.storedLoot) {
                    if (slot >= 0 && slot < chamber.storedLoot.size()) {
                        ItemStack existing = chamber.storedLoot.get(slot);
                        int toExtract = Math.min(existing.getCount(), amount);
                        ItemStack result = existing.copy();
                        result.setCount(toExtract);
                        
                        if (!simulate) {
                            existing.shrink(toExtract);
                            if (existing.isEmpty()) {
                                chamber.storedLoot.remove(slot);
                            }
                            chamber.updateVersion++;
                            StorePriceManager.markDirty(owner);
                        }
                        return result;
                    }
                }
            }
            return ItemStack.EMPTY;
        }

        @Override
        public int getSlotLimit(int slot) {
            return 64;
        }

        @Override
        public boolean isItemValid(int slot, @NotNull ItemStack stack) {
            return false;
        }
    }
}
