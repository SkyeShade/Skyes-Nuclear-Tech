package com.skyeshade.skyent.content.model;

import com.google.gson.*;
import com.mojang.blaze3d.vertex.PoseStack;
import com.skyeshade.skyent.client.model.*;
import com.skyeshade.skyent.content.model.blockbench.*;
import com.skyeshade.skyent.content.multiblock.ModelMultiblockDefinition;
import net.minecraft.client.renderer.block.model.*;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.RenderTypeGroup;
import org.joml.Vector3f;
import org.joml.Quaternionf;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;

class MeshDisplayMetadataTest {
    private static final Gson VANILLA = new GsonBuilder().registerTypeAdapter(ItemTransform.class,new ItemTransform.Deserializer())
            .registerTypeAdapter(ItemTransforms.class,new ItemTransforms.Deserializer()).create();
    private static BlockbenchScene withDisplay(String display) {
        var json=MeshMetadataTest.smallModel(); json.add("skyent",JsonParser.parseString("{\"display\":"+display+"}"));
        return MeshMetadataTest.parse(json);
    }
    @Test void everyContextUsesNativeTransformsAndBakedModelApplication() {
        JsonObject json=new JsonObject();
        int i=1;
        for(var c:SkyentModelMetadata.Context.values()) {
            json.add(c.key,JsonParser.parseString("{\"rotation\":["+(11*i)+",-23,37],\"translation\":[2,-3,5],\"scale\":[0.4,0.6,0.8]}")); i++;
        }
        var scene=withDisplay(json.toString());
        assertEquals(8,scene.display().contexts().size());
        var nativeTransforms=VANILLA.fromJson(json,ItemTransforms.class);
        var adapted=MeshItemPresentation.transforms(scene.display(),ItemTransforms.NO_TRANSFORMS);
        var model=new MeshBakedModel(Map.of(Direction.NORTH,List.of()),Map.of(),Map.of(),Map.of(),null,adapted,ItemOverrides.EMPTY,RenderTypeGroup.EMPTY);
        for(var context:ItemDisplayContext.values()) {
            if(context==ItemDisplayContext.NONE||context.isModded())continue;
            for(boolean left:new boolean[]{false,true}) {
                PoseStack expected=new PoseStack(),actual=new PoseStack();
                nativeTransforms.getTransform(context).apply(left,expected);
                assertSame(model,model.applyTransform(context,actual,left));
                assertTrue(expected.last().pose().equals(actual.last().pose(),1e-6f),context+" left="+left);
            }
        }
        assertEquals(new Vec3(2,-3,5),scene.display().contexts().get(SkyentModelMetadata.Context.GUI).translation());
        assertEquals(2f/16,adapted.gui.translation.x,1e-7);
    }
    @Test void omittedComponentsAndEmptyContextsUseNativeDefaults() {
        var transforms=MeshItemPresentation.transforms(withDisplay("{\"gui\":{},\"ground\":{\"scale\":[0.2,0.3,0.4]}}").display(),ItemTransforms.NO_TRANSFORMS);
        assertEquals(new Vector3f(),transforms.gui.rotation);
        assertEquals(new Vector3f(),transforms.gui.translation);
        assertEquals(new Vector3f(1),transforms.gui.scale);
        assertSame(ItemTransform.NO_TRANSFORM,transforms.head);
        assertEquals(new Vector3f(.2f,.3f,.4f),transforms.ground.scale);
    }
    @Test void missingLeftHandInheritsRightButExplicitEmptyLeftDoesNot() {
        var a=MeshItemPresentation.transforms(withDisplay("{\"firstperson_righthand\":{\"rotation\":[10,20,30]}}").display(),ItemTransforms.NO_TRANSFORMS);
        assertSame(a.firstPersonRightHand,a.firstPersonLeftHand);
        var b=MeshItemPresentation.transforms(withDisplay("{\"firstperson_righthand\":{\"rotation\":[10,20,30]},\"firstperson_lefthand\":{}}").display(),ItemTransforms.NO_TRANSFORMS);
        assertEquals(new Vector3f(),b.firstPersonLeftHand.rotation);
    }
    @Test void absentDisplayPreservesLegacyTransformsAndGeometry() {
        var scene=MeshMetadataTest.parse(MeshMetadataTest.smallModel());
        var legacy=VANILLA.fromJson("{\"gui\":{\"scale\":[0.2,0.2,0.2]}}",ItemTransforms.class);
        assertSame(legacy,MeshItemPresentation.transforms(scene.display(),legacy));
        var world=MeshMachineDefinition.LARGE_STEAM_TURBINE.transform(); var point=new Vec3(-7,13,5);
        assertEquals(world.authoredRelative(point,Direction.NORTH),MeshItemPresentation.position(point,world,false));
        assertSame(ItemTransform.NO_TRANSFORM,MeshItemPresentation.transforms(withDisplay("{}").display(),legacy).gui);
    }
    @Test void malformedComponentsWarnWithoutLosingValidComponents() {
        for(String bad:List.of("null","[1,2]","[true,0,0]","[\"1\",2,3]","[1e999,0,0]","[1e100,0,0]")) {
            var scene=withDisplay("{\"gui\":{\"rotation\":"+bad+",\"translation\":[1,2,3],\"scale\":[0.5,0.5,0.5]}}");
            assertFalse(scene.metadataWarnings().isEmpty());
            var gui=MeshItemPresentation.transforms(scene.display(),ItemTransforms.NO_TRANSFORMS).gui;
            assertEquals(new Vector3f(),gui.rotation); assertEquals(2f/16,gui.translation.y,1e-7);
        }
        assertFalse(withDisplay("null").display().present());
        assertFalse(withDisplay("{\"gui\":false,\"unknown\":{}}").metadataWarnings().isEmpty());
    }
    @Test void nativeClampsAreAppliedOnlyAtClientConversion() {
        var scene=withDisplay("{\"gui\":{\"translation\":[160,-160,8],\"scale\":[9,-9,0]}}");
        assertEquals(160,scene.display().contexts().get(SkyentModelMetadata.Context.GUI).translation().x);
        var gui=MeshItemPresentation.transforms(scene.display(),ItemTransforms.NO_TRANSFORMS).gui;
        assertEquals(new Vector3f(5,-5,.5f),gui.translation); assertEquals(new Vector3f(4,-4,0),gui.scale);
    }
    @Test void scaleOneAndTwoUseTheSameAuthoredItemGeometryAndApplyDisplayOnce() {
        var def=MeshMachineDefinition.LARGE_STEAM_TURBINE.multiblock(); var point=new Vec3(-8,16,24);
        var display=MeshItemPresentation.transforms(withDisplay("{\"gui\":{\"rotation\":[30,-45,7],\"translation\":[1,2,3],\"scale\":[0.625,0.5,0.75]}}").display(),ItemTransforms.NO_TRANSFORMS);
        Vector3f previous=null;
        for(double scale:new double[]{1,2}) {
            var world=new MachineModelTransform(new ModelMultiblockDefinition(def.id(),def.sizeX(),def.sizeY(),def.sizeZ(),def.controllerLocal(),
                    scale,def.modelOrigin(),def.modelTranslation(),def.collisionMode(),def.renderMode(),def.orientation()));
            Vec3 item=MeshItemPresentation.position(point,world,true);
            assertEquals(new Vec3(0,1,2),item);
            PoseStack pose=new PoseStack(); display.gui.apply(false,pose); pose.translate(-.5,-.5,-.5);
            Vector3f actual=pose.last().pose().transformPosition(new Vector3f((float)item.x,(float)item.y,(float)item.z));
            Vector3f expected=new Matrix4f().translation(1f/16,2f/16,3f/16)
                    .rotate(new Quaternionf().rotationXYZ((float)Math.toRadians(30),(float)Math.toRadians(-45),(float)Math.toRadians(7)))
                    .scale(.625f,.5f,.75f).transformPosition(new Vector3f(-.5f,.5f,1.5f));
            assertTrue(expected.equals(actual,1e-6f));
            if(previous!=null)assertTrue(previous.equals(actual,1e-6f)); previous=actual;
        }
    }
    @Test void savingAndResourceReparseDoNotRetainOldDisplayState() {
        var json=MeshMetadataTest.smallModel();
        json.add("skyent",JsonParser.parseString("{\"display\":{\"gui\":{\"rotation\":[10,20,30]}}}"));
        var before=MeshMetadataTest.parse(json);
        assertEquals(before.display(),MeshMetadataTest.parse(JsonParser.parseString(json.toString()).getAsJsonObject()).display());
        json.getAsJsonObject("skyent").getAsJsonObject("display").add("gui",JsonParser.parseString("{\"rotation\":[40,50,60]}"));
        var after=MeshMetadataTest.parse(json);
        assertEquals(new Vector3f(40,50,60),MeshItemPresentation.transforms(after.display(),ItemTransforms.NO_TRANSFORMS).gui.rotation);
        assertEquals(new Vec3(10,20,30),before.display().contexts().get(SkyentModelMetadata.Context.GUI).rotation());
    }

    @Test void everyContextMatchesIndependentInstalledBlockbenchPreview() throws Exception {
        try(var in=getClass().getResourceAsStream("/mesh/blockbench-display-reference.json")) {
            assertNotNull(in);
            var reference=JsonParser.parseReader(new InputStreamReader(in,StandardCharsets.UTF_8)).getAsJsonObject();
            var scene=withDisplay(reference.get("display").toString());
            var transforms=MeshItemPresentation.transforms(scene.display(),ItemTransforms.NO_TRANSFORMS);
            var a=reference.getAsJsonArray("authoredPoint");
            Vec3 authored=new Vec3(a.get(0).getAsDouble(),a.get(1).getAsDouble(),a.get(2).getAsDouble());
            var item=MeshItemPresentation.position(authored,MeshMachineDefinition.LARGE_STEAM_TURBINE.transform(),true);
            for(var entry:reference.getAsJsonArray("samples")) {
                var sample=entry.getAsJsonObject(); String key=sample.get("context").getAsString();
                var context=Arrays.stream(ItemDisplayContext.values()).filter(c->c.getSerializedName().equals(key)).findFirst().orElseThrow();
                PoseStack pose=new PoseStack();transforms.getTransform(context).apply(key.contains("lefthand"),pose);pose.translate(-.5,-.5,-.5);
                var actual=pose.last().pose().transformPosition(new Vector3f((float)item.x,(float)item.y,(float)item.z));
                var p=sample.getAsJsonArray("point");
                assertEquals(p.get(0).getAsDouble(),actual.x,1e-6,key+" X");
                assertEquals(p.get(1).getAsDouble(),actual.y,1e-6,key+" Y");
                assertEquals(p.get(2).getAsDouble(),actual.z,1e-6,key+" Z");
            }
        }
    }
}
