package com.example.modmenu.client.ui.component;

import com.example.modmenu.client.ui.base.UIElement;
import com.example.modmenu.store.logistics.NetworkNode;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.ForgeRegistries;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.function.BiConsumer;

public class BlockSideSelector extends UIElement {
    private final BlockState state;
    private final NetworkNode node;
    private final BiConsumer<Direction, String> onSideToggle;
    
    private float yaw = -45f;
    private float pitch = 30f;
    private double lastMouseX, lastMouseY;
    private boolean dragging = false;
    private Direction hoveredFace = null;
    
    // Popup feedback
    private String popupText = "";
    private int popupTimer = 0;
    private int popupColor = 0xFFFFFFFF;

    public BlockSideSelector(int x, int y, int width, int height, NetworkNode node, BiConsumer<Direction, String> onSideToggle) {
        super(x, y, width, height);
        this.node = node;
        this.onSideToggle = onSideToggle;
        
        BlockState s = Blocks.GILDED_BLACKSTONE.defaultBlockState();
        if (node.blockId != null) {
            var block = ForgeRegistries.BLOCKS.getValue(ResourceLocation.tryParse(node.blockId));
            if (block != null) s = block.defaultBlockState();
        }
        this.state = s;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        // Handle dragging
        if (dragging) {
            yaw += (float) (mx - lastMouseX);
            pitch += (float) (my - lastMouseY);
            lastMouseX = mx;
            lastMouseY = my;
        }

        // Update hovered face
        if (isMouseOver(mx, my)) {
            hoveredFace = pickFace(mx, my);
        } else {
            hoveredFace = null;
        }

        // Draw background box
        g.fill(x, y, x + width, y + height, 0x44000000);
        renderBorder(g, x, y, width, height, 0xFF555555);
        
        renderBlock(g, mx, my);
        
        // Draw instructions
        var font = Minecraft.getInstance().font;
        g.drawString(font, "\u00A77Drag to rotate", x + 5, y + height - 22, 0xFFFFFFFF, false);
        g.drawString(font, "\u00A77Click face to cycle", x + 5, y + height - 12, 0xFFFFFFFF, false);

        // Active Face Text
        if (hoveredFace != null) {
            String faceName = hoveredFace.name().toUpperCase();
            String type = node.sideConfig.getOrDefault(hoveredFace, "NONE");
            String colorCode = switch(type) {
                case "ITEMS" -> "\u00A7b";
                case "ENERGY" -> "\u00A76";
                case "FLUIDS" -> "\u00A7a";
                default -> "\u00A7f";
            };
            g.drawCenteredString(font, "ACTIVE FACE: " + colorCode + "[" + faceName + "]", x + width / 2, y + 5, 0xFFFFFFFF);
            g.drawCenteredString(font, "Capability: " + colorCode + type, x + width / 2, y + 15, 0xFFFFFFFF);

            // Real Capability Detection
            showRealCapabilities(g, hoveredFace, x + width / 2, y + 25);
        }

        // Popup feedback
        if (popupTimer > 0) {
            float alpha = Math.min(1.0f, popupTimer / 20.0f);
            int color = (popupColor & 0x00FFFFFF) | ((int)(alpha * 255) << 24);
            g.drawCenteredString(font, popupText, x + width / 2, y + height / 2 - 20 - (40 - popupTimer), color);
            popupTimer--;
        }
    }

    private void showRealCapabilities(GuiGraphics g, Direction face, int cx, int cy) {
        if (node.pos == null) return;
        var level = Minecraft.getInstance().level;
        if (level == null) return;

        var be = level.getBlockEntity(node.pos);
        if (be == null) return;

        var font = Minecraft.getInstance().font;
        StringBuilder sb = new StringBuilder("Actual: ");
        boolean found = false;

        if (be.getCapability(net.minecraftforge.common.capabilities.ForgeCapabilities.ITEM_HANDLER, face).isPresent()) {
            sb.append("\u00A7b[ITEMS] "); found = true;
        }
        if (be.getCapability(net.minecraftforge.common.capabilities.ForgeCapabilities.ENERGY, face).isPresent()) {
            sb.append("\u00A76[ENERGY] "); found = true;
        }
        if (be.getCapability(net.minecraftforge.common.capabilities.ForgeCapabilities.FLUID_HANDLER, face).isPresent()) {
            sb.append("\u00A7a[FLUIDS] "); found = true;
        }

        if (!found) sb.append("\u00A77None");

        g.drawCenteredString(font, sb.toString(), cx, cy, 0xFFFFFFFF);
    }

    private void renderBorder(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + 1, color);
        g.fill(x, y + h - 1, x + w, y + h, color);
        g.fill(x, y, x + 1, y + h, color);
        g.fill(x + w - 1, y, x + w, y + h, color);
    }

    private void renderBlock(GuiGraphics g, int mx, int my) {
        PoseStack pose = g.pose();
        pose.pushPose();
        
        int centerX = x + width / 2;
        int centerY = y + height / 2;
        
        pose.translate(centerX, centerY, 200);
        float scale = Math.min(width, height) * 0.45f;
        pose.scale(scale, -scale, scale); // Invert Y for GUI
        
        Matrix4f rotation = new Matrix4f().rotateX((float)Math.toRadians(pitch)).rotateY((float)Math.toRadians(yaw));
        pose.last().pose().mul(rotation);
        
        Lighting.setupFor3DItems();
        
        MultiBufferSource.BufferSource buffer = Minecraft.getInstance().renderBuffers().bufferSource();
        
        // Render the block
        Minecraft.getInstance().getBlockRenderer().renderSingleBlock(state, pose, buffer, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
        
        // Render highlights on active sides
        for (Direction dir : Direction.values()) {
            String type = node.sideConfig.getOrDefault(dir, "NONE");
            if (!type.equals("NONE")) {
                renderFaceHighlight(pose, buffer, dir, type);
            }
        }

        // Render hover highlight
        if (hoveredFace != null) {
            renderFaceHighlight(pose, buffer, hoveredFace, "HOVER");
        }
        
        buffer.endBatch();
        pose.popPose();
        Lighting.setupForFlatItems();
    }
    
    private void renderFaceHighlight(PoseStack pose, MultiBufferSource buffer, Direction dir, String type) {
        VertexConsumer vc = buffer.getBuffer(RenderType.guiGhostRecipeOverlay());
        int color = switch(type) {
            case "ITEMS" -> 0x6600AAFF; // Blue
            case "ENERGY" -> 0x66FFAA00; // Orange
            case "FLUIDS" -> 0x6600FF00; // Green
            case "HOVER" -> 0x44FFFFFF; // White
            default -> 0x66FFFFFF;
        };
        
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        int a = (color >> 24) & 0xFF;

        float offset = 0.501f;
        Matrix4f mat = pose.last().pose();
        
        // Quad vertices for each direction
        switch (dir) {
            case UP -> {
                vc.vertex(mat, -0.5f, offset, -0.5f).color(r, g, b, a).endVertex();
                vc.vertex(mat, -0.5f, offset, 0.5f).color(r, g, b, a).endVertex();
                vc.vertex(mat, 0.5f, offset, 0.5f).color(r, g, b, a).endVertex();
                vc.vertex(mat, 0.5f, offset, -0.5f).color(r, g, b, a).endVertex();
            }
            case DOWN -> {
                vc.vertex(mat, -0.5f, -offset, 0.5f).color(r, g, b, a).endVertex();
                vc.vertex(mat, -0.5f, -offset, -0.5f).color(r, g, b, a).endVertex();
                vc.vertex(mat, 0.5f, -offset, -0.5f).color(r, g, b, a).endVertex();
                vc.vertex(mat, 0.5f, -offset, 0.5f).color(r, g, b, a).endVertex();
            }
            case NORTH -> {
                vc.vertex(mat, -0.5f, 0.5f, -offset).color(r, g, b, a).endVertex();
                vc.vertex(mat, 0.5f, 0.5f, -offset).color(r, g, b, a).endVertex();
                vc.vertex(mat, 0.5f, -0.5f, -offset).color(r, g, b, a).endVertex();
                vc.vertex(mat, -0.5f, -0.5f, -offset).color(r, g, b, a).endVertex();
            }
            case SOUTH -> {
                vc.vertex(mat, -0.5f, 0.5f, offset).color(r, g, b, a).endVertex();
                vc.vertex(mat, -0.5f, -0.5f, offset).color(r, g, b, a).endVertex();
                vc.vertex(mat, 0.5f, -0.5f, offset).color(r, g, b, a).endVertex();
                vc.vertex(mat, 0.5f, 0.5f, offset).color(r, g, b, a).endVertex();
            }
            case WEST -> {
                vc.vertex(mat, -offset, 0.5f, 0.5f).color(r, g, b, a).endVertex();
                vc.vertex(mat, -offset, 0.5f, -0.5f).color(r, g, b, a).endVertex();
                vc.vertex(mat, -offset, -0.5f, -0.5f).color(r, g, b, a).endVertex();
                vc.vertex(mat, -offset, -0.5f, 0.5f).color(r, g, b, a).endVertex();
            }
            case EAST -> {
                vc.vertex(mat, offset, 0.5f, -0.5f).color(r, g, b, a).endVertex();
                vc.vertex(mat, offset, 0.5f, 0.5f).color(r, g, b, a).endVertex();
                vc.vertex(mat, offset, -0.5f, 0.5f).color(r, g, b, a).endVertex();
                vc.vertex(mat, offset, -0.5f, -0.5f).color(r, g, b, a).endVertex();
            }
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (isMouseOver(mx, my)) {
            Direction clicked = pickFace(mx, my);
            if (clicked != null) {
                onSideToggle.accept(clicked, button == 1 ? "PREV" : "NEXT");
                Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0f));
                
                // Set popup feedback
                String type = node.sideConfig.getOrDefault(clicked, "NONE");
                popupText = (type.equals("NONE") ? "- " : "+ ") + type;
                popupColor = switch(type) {
                    case "ITEMS" -> 0xFF00AAFF;
                    case "ENERGY" -> 0xFFFFAA00;
                    case "FLUIDS" -> 0xFF00FF00;
                    default -> 0xFFFFFFFF;
                };
                popupTimer = 40;
                
                return true;
            }
            lastMouseX = mx;
            lastMouseY = my;
            dragging = true;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        dragging = false;
        return super.mouseReleased(mx, my, button);
    }

    private Direction pickFace(double mx, double my) {
        // Project ray into block space
        float rx = (float) (mx - (x + width / 2));
        float ry = (float) (my - (y + height / 2));
        float scale = Math.min(width, height) * 0.45f;
        rx /= scale;
        ry /= -scale; // Invert Y

        // Rotation matrices using org.joml
        Matrix4f rotation = new Matrix4f().rotateX((float)Math.toRadians(pitch)).rotateY((float)Math.toRadians(yaw));
        Matrix4f invRotation = new Matrix4f(rotation).invert();

        Direction bestDir = null;
        float maxZ = -Float.MAX_VALUE;

        // The ray is parallel to Z-axis in screen space: R(t) = [rx, ry, t]
        // Transform ray to local space: R_local(t) = InvRot * [rx, ry, t]
        
        Vector4f rayOrigin = new Vector4f(rx, ry, 10f, 1f).mul(invRotation);
        Vector4f rayDir = new Vector4f(0, 0, -1, 0).mul(invRotation);

        for (Direction dir : Direction.values()) {
            // Plane: dot(P, N) = d
            Vector3f normal = new Vector3f(dir.getStepX(), dir.getStepY(), dir.getStepZ());
            float d = 0.5f;

            // t = (d - dot(O, N)) / dot(D, N)
            float dotDN = rayDir.x() * normal.x() + rayDir.y() * normal.y() + rayDir.z() * normal.z();
            if (Math.abs(dotDN) < 0.0001f) continue;

            float dotON = rayOrigin.x() * normal.x() + rayOrigin.y() * normal.y() + rayOrigin.z() * normal.z();
            float t = (d - dotON) / dotDN;

            // Intersection point P_local = O_local + t * D_local
            float px = rayOrigin.x() + t * rayDir.x();
            float py = rayOrigin.y() + t * rayDir.y();
            float pz = rayOrigin.z() + t * rayDir.z();

            // Check if P_local is within face bounds [-0.5, 0.5]
            if (Math.abs(px) <= 0.501f && Math.abs(py) <= 0.501f && Math.abs(pz) <= 0.501f) {
                // Transform P_local back to view space to check Z (depth)
                Vector4f pView = new Vector4f(px, py, pz, 1f).mul(rotation);
                if (pView.z() > maxZ) {
                    maxZ = pView.z();
                    bestDir = dir;
                }
            }
        }

        return bestDir;
    }
}
