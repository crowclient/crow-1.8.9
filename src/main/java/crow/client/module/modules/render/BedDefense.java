package crow.client.module.modules.render;

import com.google.common.eventbus.Subscribe;
import crow.client.event.impl.ForgeEvent;
import crow.client.event.impl.EarlyRender2DEvent;
import crow.client.event.impl.GameLoopEvent;
import crow.client.event.impl.Render2DEvent;
import crow.client.module.Module;
import crow.client.module.modules.HUD;
import crow.client.module.setting.impl.SliderSetting;
import crow.client.module.setting.impl.TickSetting;
import crow.client.utils.GUIBlurUtil;
import crow.client.utils.RenderUtils;
import crow.client.utils.Utils;
import crow.client.utils.font.FontUtil;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.BlockPos;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.glu.GLU;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class BedDefense extends Module {

    public static SliderSetting tagScale;
    public static TickSetting showDistance;

    private static final FloatBuffer MODEL_VIEW = BufferUtils.createFloatBuffer(16);
    private static final FloatBuffer PROJECTION = BufferUtils.createFloatBuffer(16);
    private static final IntBuffer VIEWPORT = BufferUtils.createIntBuffer(16);
    private static final FloatBuffer RESULT = BufferUtils.createFloatBuffer(3);

    private static final FloatBuffer DEPTH_SAMPLE = BufferUtils.createFloatBuffer(1);

    private static final int SCAN_INTERVAL = 80;

    private static final int CHUNKS_PER_TICK = 16;

    private static final int SCAN_RADIUS_CHUNKS = 8;
    private int tickCounter = 0;
    private int scanPhase = 0;
    private int scanChunkIdx = 0;
    private int[] scanChunkXs;
    private int[] scanChunkZs;
    private final List<BlockPos> tempBedPositions = new ArrayList<>();

    private static final ItemStack BED_STACK = new ItemStack(Items.bed);

    private static class DefenseEntry {
        final Block block;
        final int meta;
        final double minDist;

        DefenseEntry(Block block, int meta, double minDist) {
            this.block = block;
            this.meta = meta;
            this.minDist = minDist;
        }
    }

    private static class BedData {
        final BlockPos pos;
        final List<DefenseEntry> entries;
        final String teamName;
        final int teamColor;
        BedData(BlockPos pos, List<DefenseEntry> entries, String teamName, int teamColor) {
            this.pos = pos;
            this.entries = entries;
            this.teamName = teamName;
            this.teamColor = teamColor;
        }
    }

    private static class TagRenderData {
        final float screenX;
        final float screenY;
        final float dist;
        final List<DefenseEntry> entries;
        final String teamName;
        final int teamColor;

        TagRenderData(float screenX, float screenY, float dist, List<DefenseEntry> entries,
                      String teamName, int teamColor) {
            this.screenX = screenX;
            this.screenY = screenY;
            this.dist = dist;
            this.entries = entries;
            this.teamName = teamName;
            this.teamColor = teamColor;
        }
    }

    private final List<BedData> cachedBeds = new ArrayList<>();
    private final List<TagRenderData> pendingTags = new ArrayList<>();
    private final BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();

    private static boolean isDefenseBlock(Block b) {
        return b == Blocks.wool
            || b == Blocks.stained_hardened_clay || b == Blocks.hardened_clay
            || b == Blocks.stained_glass
            || b == Blocks.end_stone
            || b == Blocks.ladder
            || b == Blocks.planks
            || b == Blocks.obsidian;
    }

    private static String groupKey(Block b) {
        if (b == Blocks.stained_hardened_clay || b == Blocks.hardened_clay) return "clay";
        return Block.blockRegistry.getNameForObject(b).toString();
    }

    private static final String[] TEAM_NAMES = {
        "White", "Orange", "Magenta", "Aqua", "Yellow", "Green", "Pink", "Gray",
        "Silver", "Cyan", "Purple", "Blue", "Brown", "Dark Green", "Red", "Black"
    };
    private static final int[] TEAM_COLORS = {
        0xFFFFFFFF, 0xFFFFAA00, 0xFFFF55FF, 0xFF55FFFF, 0xFFFFFF55, 0xFF55FF55, 0xFFFF69B4, 0xFFAAAAAA,
        0xFFCCCCCC, 0xFF00AAAA, 0xFFAA00AA, 0xFF5555FF, 0xFFAA5500, 0xFF00AA00, 0xFFFF5555, 0xFF555555
    };

    public BedDefense() {
        super("Bed Defense", ModuleCategory.render);
        this.registerSetting(tagScale = new SliderSetting("Size", 1.0D, 0.5D, 2.0D, 0.05D));
        this.registerSetting(showDistance = new TickSetting("Show distance", true));
    }

    @Override
    public void onDisable() {
        cachedBeds.clear();
        tempBedPositions.clear();
        pendingTags.clear();
        scanPhase = 0;
        tickCounter = 0;
    }

    @Subscribe
    public void onTick(GameLoopEvent event) {
        if (!Utils.Player.isPlayerInGame() || mc.theWorld == null) return;

        if (scanPhase == 0) {
            if (++tickCounter < SCAN_INTERVAL) return;
            tickCounter = 0;
            int r = Math.min(SCAN_RADIUS_CHUNKS, mc.gameSettings.renderDistanceChunks);
            int pcx = (int) Math.floor(mc.thePlayer.posX) >> 4;
            int pcz = (int) Math.floor(mc.thePlayer.posZ) >> 4;
            int d = r * 2 + 1;
            scanChunkXs = new int[d * d];
            scanChunkZs = new int[d * d];
            int idx = 0;
            for (int cx = -r; cx <= r; cx++)
                for (int cz = -r; cz <= r; cz++) {
                    scanChunkXs[idx] = pcx + cx;
                    scanChunkZs[idx] = pcz + cz;
                    idx++;
                }
            tempBedPositions.clear();
            scanChunkIdx = 0;
            scanPhase = 1;
            return;
        }

        if (scanPhase == 1) {
            int end = Math.min(scanChunkIdx + CHUNKS_PER_TICK, scanChunkXs.length);
            for (int i = scanChunkIdx; i < end; i++)
                scanChunkForBeds(scanChunkXs[i], scanChunkZs[i]);
            scanChunkIdx = end;
            if (scanChunkIdx >= scanChunkXs.length) {
                finalizeScan();
                scanPhase = 0;
            }
        }
    }

    private void scanChunkForBeds(int cx, int cz) {
        if (!mc.theWorld.getChunkProvider().chunkExists(cx, cz)) return;
        Chunk chunk = mc.theWorld.getChunkFromChunkCoords(cx, cz);
        ExtendedBlockStorage[] arr = chunk.getBlockStorageArray();
        int bx = cx << 4, bz = cz << 4;
        for (int s = 0; s < arr.length; s++) {
            if (arr[s] == null) continue;
            int by = s << 4;
            for (int ly = 0; ly < 16; ly++)
                for (int lx = 0; lx < 16; lx++)
                    for (int lz = 0; lz < 16; lz++) {
                        if (arr[s].get(lx, ly, lz).getBlock() != Blocks.bed) continue;
                        BlockPos bp = new BlockPos(bx + lx, by + ly, bz + lz);
                        boolean dup = false;
                        for (int j = tempBedPositions.size() - 1; j >= 0; j--)
                            if (tempBedPositions.get(j).distanceSq(bp) < 4) { dup = true; break; }
                        if (!dup) tempBedPositions.add(bp);
                    }
        }
    }

    private void finalizeScan() {
        List<BedData> newBeds = new ArrayList<>();
        for (BlockPos bed : tempBedPositions) {
            double cx = bed.getX() + 0.5, cy = bed.getY() + 0.5, cz = bed.getZ() + 0.5;

            int[] colorCounts = new int[16];
            List<String> seenKeys = new ArrayList<>();
            List<Block> seenBlocks = new ArrayList<>();
            List<int[]> seenMeta = new ArrayList<>();
            List<Double> seenDist = new ArrayList<>();

            for (int dy = 0; dy <= 4; dy++)
                for (int dx = -3; dx <= 3; dx++)
                    for (int dz = -3; dz <= 3; dz++) {
                        mutablePos.set(bed.getX() + dx, bed.getY() + dy, bed.getZ() + dz);
                        IBlockState st = mc.theWorld.getBlockState(mutablePos);
                        Block bl = st.getBlock();
                        if (!isDefenseBlock(bl)) continue;
                        if (bl == Blocks.wool || bl == Blocks.stained_hardened_clay) {
                            int cm = bl.getMetaFromState(st);
                            if (cm >= 0 && cm < 16) colorCounts[cm]++;
                        }
                        String key = groupKey(bl);
                        double dist = Math.sqrt(
                            (mutablePos.getX() + 0.5 - cx) * (mutablePos.getX() + 0.5 - cx)
                          + (mutablePos.getY() + 0.5 - cy) * (mutablePos.getY() + 0.5 - cy)
                          + (mutablePos.getZ() + 0.5 - cz) * (mutablePos.getZ() + 0.5 - cz));
                        int idx = seenKeys.indexOf(key);
                        if (idx == -1) {
                            seenKeys.add(key);
                            seenBlocks.add(bl);
                            seenMeta.add(new int[]{ bl.getMetaFromState(st) });
                            seenDist.add(dist);
                        } else {
                            if (dist < seenDist.get(idx)) seenDist.set(idx, dist);
                        }
                    }

            List<DefenseEntry> entries = new ArrayList<>();
            for (int i = 0; i < seenKeys.size(); i++)
                entries.add(new DefenseEntry(seenBlocks.get(i), seenMeta.get(i)[0], seenDist.get(i)));
            entries.sort(Comparator.comparingDouble(e -> e.minDist));

            int bestColorIdx = -1;
            int bestColorCount = 0;
            for (int ci = 0; ci < 16; ci++) {
                if (colorCounts[ci] > bestColorCount) {
                    bestColorCount = colorCounts[ci];
                    bestColorIdx = ci;
                }
            }
            String teamName = bestColorIdx >= 0 ? TEAM_NAMES[bestColorIdx] : null;
            int teamColor = bestColorIdx >= 0 ? TEAM_COLORS[bestColorIdx] : 0xFFFFFFFF;

            newBeds.add(new BedData(bed, entries, teamName, teamColor));
        }
        cachedBeds.clear();
        cachedBeds.addAll(newBeds);
        tempBedPositions.clear();
    }

    @Subscribe
    public void onForgeEvent(ForgeEvent fe) {
        if (!(fe.getEvent() instanceof RenderWorldLastEvent) || !Utils.Player.isPlayerInGame()) return;
        if (mc.currentScreen != null) { pendingTags.clear(); return; }

        pendingTags.clear();
        if (cachedBeds.isEmpty()) return;

        RenderWorldLastEvent event = (RenderWorldLastEvent) fe.getEvent();
        MODEL_VIEW.rewind(); PROJECTION.rewind(); VIEWPORT.rewind();
        GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, MODEL_VIEW);
        GL11.glGetFloat(GL11.GL_PROJECTION_MATRIX, PROJECTION);
        GL11.glGetInteger(GL11.GL_VIEWPORT, VIEWPORT);

        ScaledResolution sr = new ScaledResolution(mc);
        int sf = sr.getScaleFactor();
        double camX = mc.getRenderManager().viewerPosX;
        double camY = mc.getRenderManager().viewerPosY;
        double camZ = mc.getRenderManager().viewerPosZ;

        for (BedData data : cachedBeds) {
            double dx = data.pos.getX() + 0.5 - camX;
            double dy = data.pos.getY() + 1.6 - camY;
            double dz = data.pos.getZ() + 0.5 - camZ;
            float dist = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (dist > 80.0F) continue;

            RESULT.rewind(); MODEL_VIEW.rewind(); PROJECTION.rewind(); VIEWPORT.rewind();
            if (!GLU.gluProject((float) dx, (float) dy, (float) dz, MODEL_VIEW, PROJECTION, VIEWPORT, RESULT))
                continue;
            float depth = RESULT.get(2);
            if (depth < 0.0F || depth > 1.0F) continue;

            int rawX = Math.round(RESULT.get(0));
            int rawY = Math.round(RESULT.get(1));
            int vpX = VIEWPORT.get(0), vpY = VIEWPORT.get(1);
            int vpW = VIEWPORT.get(2), vpH = VIEWPORT.get(3);
            if (rawX >= vpX && rawX < vpX + vpW && rawY >= vpY && rawY < vpY + vpH) {
                DEPTH_SAMPLE.rewind();
                GL11.glReadPixels(rawX, rawY, 1, 1,
                        GL11.GL_DEPTH_COMPONENT, GL11.GL_FLOAT, DEPTH_SAMPLE);
                float fbDepth = DEPTH_SAMPLE.get(0);

                if (fbDepth < depth - 0.0005F) continue;
            }

            float sx = RESULT.get(0) / sf;
            float sy = (VIEWPORT.get(3) - RESULT.get(1)) / sf;
            pendingTags.add(new TagRenderData(sx, sy, dist, data.entries, data.teamName, data.teamColor));
        }
    }

    @Subscribe
    public void onEarlyRender2D(EarlyRender2DEvent event) {
        if (!Utils.Player.isPlayerInGame() || mc.currentScreen != null || pendingTags.isEmpty()) return;

        for (TagRenderData tag : pendingTags) drawTag(tag);

        GlStateManager.enableDepth();
        GlStateManager.enableAlpha();
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private void drawTag(TagRenderData tag) {
        float scale = (float) tagScale.getInput();
        int iconSize = 16;
        int iconGap = 2;
        int pad = 5;
        int cornerR = 5;
        int lineSpacing = 2;

        boolean useFont = FontUtil.hasLoaded() && FontUtil.small != null;
        boolean useNormal = FontUtil.hasLoaded() && FontUtil.normal != null;

        boolean hasTeam = tag.teamName != null;
        int teamW = 0, teamH = 0;
        if (hasTeam) {
            teamW = useNormal ? (int) FontUtil.normal.getStringWidth(tag.teamName)
                              : mc.fontRendererObj.getStringWidth(tag.teamName);
            teamH = (useNormal ? FontUtil.normal.getHeight() : mc.fontRendererObj.FONT_HEIGHT) + lineSpacing;
        }

        boolean showDist = showDistance.isToggled();
        String distStr = showDist ? String.format("%.0fm", tag.dist) : "";
        int distW = 0, distH = 0;
        if (showDist) {
            distW = useFont ? (int) FontUtil.small.getStringWidth(distStr)
                            : mc.fontRendererObj.getStringWidth(distStr);
            distH = (useFont ? FontUtil.small.getHeight() : mc.fontRendererObj.FONT_HEIGHT) + lineSpacing;
        }

        int totalIcons = 1 + tag.entries.size();
        int stripW = totalIcons * iconSize + (totalIcons - 1) * iconGap;

        int contentW = Math.max(stripW, Math.max(teamW, distW));
        int panelW = contentW + pad * 2;
        int panelH = pad + (hasTeam ? teamH : 0) + (showDist ? distH : 0) + iconSize + pad;

        float panelX = tag.screenX - (panelW * scale) / 2.0F;
        float panelY = tag.screenY - (panelH * scale);

        GlStateManager.pushMatrix();
        GlStateManager.translate(panelX, panelY, 0.0F);
        GlStateManager.scale(scale, scale, 1.0F);

        if (HUD.enableBlur != null && HUD.enableBlur.isToggled()) {
            GlStateManager.popMatrix();
            GUIBlurUtil.drawBlurredBackground(
                    (int) panelX, (int) panelY,
                    (int) (panelW * scale), (int) (panelH * scale),
                    (int) HUD.blurRadius.getInput(), cornerR, 0.7F);
            mc.entityRenderer.setupOverlayRendering();
            GlStateManager.pushMatrix();
            GlStateManager.translate(panelX, panelY, 0.0F);
            GlStateManager.scale(scale, scale, 1.0F);
        }

        GlStateManager.disableDepth();
        GlStateManager.disableLighting();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);

        RenderUtils.drawGlassPanel(0, 0, panelW, panelH, cornerR, 0xBE0E1014,
                HUD.dropShadow == null || HUD.dropShadow.isToggled() ? RenderUtils.GLASS_SHADOW_CHROME : 0);

        float yOff = pad;

        if (hasTeam) {
            GlStateManager.enableTexture2D();
            GlStateManager.enableAlpha();
            float teamX = (panelW - teamW) / 2.0F;
            if (useNormal) {
                FontUtil.normal.drawSmoothString(tag.teamName, teamX, yOff, tag.teamColor);
            } else {
                mc.fontRendererObj.drawStringWithShadow(tag.teamName, teamX, yOff, tag.teamColor);
            }
            yOff += teamH;
        }

        if (showDist) {
            GlStateManager.disableLighting();
            GlStateManager.enableTexture2D();
            GlStateManager.enableAlpha();
            GlStateManager.enableBlend();
            GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);

            float distX = (panelW - distW) / 2.0F;
            if (useFont) {
                FontUtil.small.drawSmoothString(distStr, distX, yOff, 0x99FFFFFF);
            } else {
                mc.fontRendererObj.drawStringWithShadow(distStr, distX, yOff, 0x99FFFFFF);
            }
            yOff += distH;
        }

        float startX = (panelW - stripW) / 2.0F;

        org.lwjgl.opengl.GL20.glUseProgram(0);
        net.minecraft.client.renderer.entity.RenderItem ri = mc.getRenderItem();
        float prevZLevel = ri.zLevel;
        ri.zLevel = 100.0F;

        GlStateManager.pushMatrix();
        GlStateManager.enableTexture2D();
        GlStateManager.enableAlpha();
        GlStateManager.alphaFunc(org.lwjgl.opengl.GL11.GL_GREATER, 0.1F);
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(
                org.lwjgl.opengl.GL11.GL_SRC_ALPHA, org.lwjgl.opengl.GL11.GL_ONE_MINUS_SRC_ALPHA,
                org.lwjgl.opengl.GL11.GL_ONE,       org.lwjgl.opengl.GL11.GL_ZERO);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.enableDepth();
        GlStateManager.depthMask(true);
        org.lwjgl.opengl.GL11.glDepthFunc(org.lwjgl.opengl.GL11.GL_LEQUAL);
        GlStateManager.enableRescaleNormal();
        RenderHelper.enableGUIStandardItemLighting();

        ri.renderItemIntoGUI(BED_STACK, (int) startX, (int) yOff);
        float x = startX + iconSize + iconGap;

        for (DefenseEntry entry : tag.entries) {
            ItemStack stack = new ItemStack(entry.block, 1, entry.meta);
            ri.renderItemIntoGUI(stack, (int) x, (int) yOff);
            x += iconSize + iconGap;
        }

        RenderHelper.disableStandardItemLighting();
        GlStateManager.disableRescaleNormal();
        GlStateManager.disableDepth();
        GlStateManager.disableLighting();
        ri.zLevel = prevZLevel;
        GlStateManager.popMatrix();

        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);

        GlStateManager.popMatrix();
    }
}
