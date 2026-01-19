package com.example.modmenu.store;

import com.google.gson.Gson;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.world.item.ItemStack;

import java.io.IOException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Optional;

public class GsonAdapters {

    public static class ItemStackAdapter extends TypeAdapter<ItemStack> {
        @Override
        public void write(JsonWriter out, ItemStack value) throws IOException {
            if (value == null || value.isEmpty()) {
                out.nullValue();
            } else {
                CompoundTag nbt = value.save(new CompoundTag());
                nbt.putInt("FullCount", value.getCount()); // Store full int count to avoid byte truncation
                out.value(nbt.toString());
            }
        }

        @Override
        public ItemStack read(JsonReader in) throws IOException {
            if (in.peek() == JsonToken.NULL) {
                in.nextNull();
                return ItemStack.EMPTY;
            } else {
                try {
                    CompoundTag nbt = TagParser.parseTag(in.nextString());
                    ItemStack stack = ItemStack.of(nbt);
                    if (nbt.contains("FullCount")) {
                        int count = nbt.getInt("FullCount");
                        // Reconstruct stack if it was truncated to 0 (Air)
                        if (stack.isEmpty() && count > 0) {
                            net.minecraft.world.item.Item item = net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(new net.minecraft.resources.ResourceLocation(nbt.getString("id")));
                            if (item != null) {
                                stack = new ItemStack(item, count);
                                if (nbt.contains("tag")) stack.setTag(nbt.getCompound("tag"));
                            }
                        } else {
                            stack.setCount(count);
                        }
                    }
                    return stack;
                } catch (Exception e) {
                    return ItemStack.EMPTY;
                }
            }
        }
    }

    public static class CompoundTagAdapter extends TypeAdapter<CompoundTag> {
        @Override
        public void write(JsonWriter out, CompoundTag value) throws IOException {
            if (value == null) {
                out.nullValue();
            } else {
                out.value(value.toString());
            }
        }

        @Override
        public CompoundTag read(JsonReader in) throws IOException {
            if (in.peek() == JsonToken.NULL) {
                in.nextNull();
                return null;
            } else {
                try {
                    return TagParser.parseTag(in.nextString());
                } catch (CommandSyntaxException e) {
                    return new CompoundTag();
                }
            }
        }
    }

    public static class OptionalAdapterFactory implements TypeAdapterFactory {
        @Override
        @SuppressWarnings("unchecked")
        public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> typeToken) {
            if (typeToken.getRawType() != Optional.class) {
                return null;
            }

            Type type = typeToken.getType();
            if (!(type instanceof ParameterizedType)) {
                return (TypeAdapter<T>) new OptionalAdapter<>(gson, gson.getAdapter(Object.class));
            }

            Type innerType = ((ParameterizedType) type).getActualTypeArguments()[0];
            return (TypeAdapter<T>) new OptionalAdapter<>(gson, gson.getAdapter(TypeToken.get(innerType)));
        }

        private static class OptionalAdapter<T> extends TypeAdapter<Optional<T>> {
            private final Gson gson;
            private final TypeAdapter<T> delegate;

            private OptionalAdapter(Gson gson, TypeAdapter<T> delegate) {
                this.gson = gson;
                this.delegate = delegate;
            }

            @Override
            public void write(JsonWriter out, Optional<T> value) throws IOException {
                if (value == null || !value.isPresent()) {
                    out.nullValue();
                } else {
                    delegate.write(out, value.get());
                }
            }

            @Override
            public Optional<T> read(JsonReader in) throws IOException {
                if (in.peek() == JsonToken.NULL) {
                    in.nextNull();
                    return Optional.empty();
                } else {
                    T value = delegate.read(in);
                    return Optional.ofNullable(value);
                }
            }
        }
    }
}
