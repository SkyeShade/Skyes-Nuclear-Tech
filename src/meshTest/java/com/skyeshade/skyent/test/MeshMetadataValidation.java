package com.skyeshade.skyent.test;

import com.google.gson.*;
import com.mojang.blaze3d.platform.Lighting;
import com.skyeshade.skyent.SkyesNuclearTech;
import com.skyeshade.skyent.client.model.*;
import com.skyeshade.skyent.content.model.MeshMachineDefinition;
import com.skyeshade.skyent.content.model.blockbench.SkyentModelMetadata;
import com.skyeshade.skyent.content.shape.MeshMachineShapeCache;
import com.skyeshade.skyent.registry.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.ClientCommandHandler;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import static com.skyeshade.skyent.test.MeshVisualTest.*;

/** Focused author-metadata fixture: eight native contexts, actual resource reload, placed-model visibility. */
final class MeshMetadataValidation {
    private static final ItemStack ITEM = new ItemStack(ModItems.LARGE_STEAM_TURBINE.get());
    private static BakedModel original;
    private static volatile boolean reloaded, restored;
    private static List<String> previousPacks;
    private static String canonicalAudit;
    private static BakedModel model(Minecraft mc) { return mc.getItemRenderer().getModel(ITEM,mc.level,mc.player,0); }

    static void tick(Minecraft mc,int tick) throws Exception {
        if(tick==0) {
            original=model(mc); canonicalAudit=MeshMachineShapeCache.data().audit();
            check(original instanceof MeshBakedModel,"Item is a mesh baked model");
            check(Math.abs(original.getTransforms().gui.scale.x-.35f)<1e-6,"Authored GUI scale overrides legacy JSON");
            check(Math.abs(original.getTransforms().gui.rotation.y+135)<1e-6,"Authored GUI rotation");
            check(!MeshMachineShapeCache.data().excludedGeometry().isEmpty(),"Excluded geometry diagnostics");
            var world=com.skyeshade.skyent.client.renderer.blockentity.LargeSteamTurbineRenderer.model();
            check(world.parts(net.minecraft.core.Direction.NORTH).keySet().stream().anyMatch(p->p.contains("/rotor/")),"Excluded rotor remains rendered");
            camera(mc,0);
            SkyesNuclearTech.LOGGER.info("METADATA_SMOKE {}",MeshMachineShapeCache.data().metadataAudit());
        }
        if(tick==35)capture(mc,"metadata-placed");
        if(tick==45)ClientCommandHandler.runCommand("skyent_mesh_debug metadata");
        if(tick==75)capture(mc,"metadata-excluded");
        if(tick==85)ClientCommandHandler.runCommand("skyent_mesh_debug hybrid");
        if(tick==115)capture(mc,"metadata-collision");
        if(tick==125) { ClientCommandHandler.runCommand("skyent_mesh_debug off"); mc.setScreen(new DisplayScreen()); }
        if(tick==155)capture(mc,"metadata-display-contexts");
        if(tick==165) {
            mc.setScreen(null);
            // Override a copy through a test-only resource pack; never edit the authored source or server shapes.
            JsonObject json;
            try(var reader=mc.getResourceManager().openAsReader(MeshMachineDefinition.LARGE_STEAM_TURBINE.source())) {
                json=JsonParser.parseReader(reader).getAsJsonObject();
            }
            json.getAsJsonObject("skyent").getAsJsonObject("display").getAsJsonObject("gui")
                    .add("rotation",JsonParser.parseString("[17,61,-9]"));
            Path pack=mc.gameDirectory.toPath().resolve("resourcepacks/metadata-reload.zip");
            Files.createDirectories(pack.getParent());
            try(var zip=new ZipOutputStream(Files.newOutputStream(pack))) {
                entry(zip,"pack.mcmeta","{\"pack\":{\"pack_format\":34,\"description\":\"Metadata reload test\"}}");
                entry(zip,"assets/skyent/models/block/mv_steam_turbine_mesh.bbmodel",json.toString());
            }
            var repository=mc.getResourcePackRepository();
            previousPacks=new ArrayList<>(repository.getSelectedIds());
            repository.reload();
            var selected=new ArrayList<>(previousPacks); selected.add("file/metadata-reload.zip"); repository.setSelected(selected);
            mc.reloadResourcePacks().thenRun(()->reloaded=true);
        }
        if(reloaded && !restored) {
            var changed=model(mc);
            check(changed!=original,"Resource reload rebaked item model");
            check(Math.abs(changed.getTransforms().gui.rotation.y-61)<1e-6,"Reload consumed changed authored metadata");
            check(MeshMachineShapeCache.data().audit().equals(canonicalAudit),"Visual pack cannot replace canonical collision");
            restored=true; reloaded=false;
            mc.getResourcePackRepository().setSelected(previousPacks);
            mc.reloadResourcePacks().thenRun(()->reloaded=true);
        } else if(restored && reloaded) {
            check(Math.abs(model(mc).getTransforms().gui.rotation.y+135)<1e-6,"Original authored display restored");
            SkyesNuclearTech.LOGGER.info("METADATA_VISUAL COMPLETE: placement/load, visible excluded rotor/stators, eight item contexts, changed-metadata reload and restore");
            mc.stop();
        }
    }
    private static void entry(ZipOutputStream zip,String path,String value) throws Exception {
        zip.putNextEntry(new ZipEntry(path));zip.write(value.getBytes(StandardCharsets.UTF_8));zip.closeEntry();
    }

    private static final class DisplayScreen extends Screen {
        private static final ItemDisplayContext[] CONTEXTS={ItemDisplayContext.GUI,ItemDisplayContext.GROUND,ItemDisplayContext.FIXED,
                ItemDisplayContext.HEAD,ItemDisplayContext.FIRST_PERSON_RIGHT_HAND,ItemDisplayContext.FIRST_PERSON_LEFT_HAND,
                ItemDisplayContext.THIRD_PERSON_RIGHT_HAND,ItemDisplayContext.THIRD_PERSON_LEFT_HAND};
        private static final String[] LABELS={"GUI","Ground","Fixed","Head (default)","First person R","First person L","Third person R","Third person L"};
        DisplayScreen(){super(Component.literal("Authored display metadata"));}
        @Override public boolean isPauseScreen(){return false;}
        @Override public void render(GuiGraphics g,int mx,int my,float delta) {
            g.fill(0,0,width,height,0xff202530);
            g.drawString(font,"Authored turbine / native item contexts",8,8,0xffffff);
            g.renderItem(ITEM,width-22,5);
            var mc=Minecraft.getInstance();
            for(int i=0;i<CONTEXTS.length;i++) {
                int x=(i%4)*width/4,y=25+(i/4)*(height-35)/2;
                int w=width/4,h=(height-35)/2;
                g.fill(x+3,y,x+w-3,y+h-3,0xff343c48);
                g.drawString(font,LABELS[i],x+7,y+5,0xffffff);
                g.enableScissor(x+4,y+20,x+w-4,y+h-4);
                g.pose().pushPose();
                g.pose().translate(x+w/2.0,y+h*.63,160);
                float size=Math.min(w,h)*.68f;
                g.pose().scale(size,-size,size);
                Lighting.setupFor3DItems();
                boolean left=CONTEXTS[i]==ItemDisplayContext.FIRST_PERSON_LEFT_HAND||CONTEXTS[i]==ItemDisplayContext.THIRD_PERSON_LEFT_HAND;
                mc.getItemRenderer().render(ITEM,CONTEXTS[i],left,g.pose(),g.bufferSource(),15728880,OverlayTexture.NO_OVERLAY,model(mc));
                g.flush(); g.pose().popPose(); g.disableScissor();
            }
        }
    }
}
