package com.hbm.main.client;

import com.hbm.Tags;
import com.hbm.blocks.ModBlocks;
import com.hbm.render.chunk.SectionGeometry;
import com.hbm.render.icon.PaddedSpriteUtil;
import com.hbm.render.icon.PaddedSpriteUtil.TextureInfo;
import com.hbm.render.loader.HFRWavefrontObject;
import com.hbm.render.model.*;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.ModelBase;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.registry.IRegistry;

import javax.vecmath.Matrix4f;
import java.util.function.Function;

import static com.hbm.render.model.BakedModelMatrixUtil.*;
import static net.minecraft.util.EnumFacing.HORIZONTALS;

public final class StaticDecoBakedModels {
    private static final double G = 0.0625D;
    private static final double Q = G * 2.0D + G / 3.0D;

    private StaticDecoBakedModels() {
    }

    public static void registerSprites(TextureMap map) {
        register(map, "models/deco/steelroof");
        register(map, "models/misc/modelstatue");
        register(map, "models/modelgun");
        register(map, "items/watch");

        register(map, "models/misc/boxcar");
        register(map, "models/misc/duchessgambit");
    }

    public static void bakeModels(IRegistry<ModelResourceLocation, IBakedModel> registry) {
        TextureMap atlas = Minecraft.getMinecraft().getTextureMapBlocks();

        bakeLegacyFacingModel(registry, atlas, ModBlocks.steel_roof, new ModelSteelRoof(), "models/deco/steelroof",
                facing -> compose(
                        translate(0.5D, 1.5D, 0.5D),
                        rotateZ(180.0D)
                ));

        bakeWavefrontFacing(registry, atlas, ModBlocks.boxcar,
                "models/boxcar.obj", "models/misc/boxcar",
                facing -> compose(
                        translate(0.5D, 1.5D, 0.5D),
                        rotateZ(180.0D),
                        rotateZ(180.0D),
                        translate(0.0D, -1.5D, 0.0D),
                        rotateY(boxcarYaw(facing))
                ));
        bakeWavefrontFacing(registry, atlas, ModBlocks.boat,
                "models/duchessgambit.obj", "models/misc/duchessgambit",
                facing -> compose(
                        translate(0.5D, 1.5D, 0.5D),
                        rotateZ(180.0D),
                        rotateZ(180.0D),
                        translate(0.0D, 0.0D, -1.5D),
                        translate(0.0D, 0.5D, 0.0D)
                ));



        bakeStatueModels(registry, atlas);
    }

    public static void graphicsChanged() {
        SectionGeometry.refreshModels(Minecraft.getMinecraft().getBlockRendererDispatcher().getBlockModelShapes(),
                ModBlocks.statue_elb, ModBlocks.statue_elb_g, ModBlocks.statue_elb_w, ModBlocks.statue_elb_f);
    }

    private static void bakeStatueModels(IRegistry<ModelResourceLocation, IBakedModel> registry, TextureMap atlas) {
        SectionGeometry.candidate(ModBlocks.statue_elb);
        SectionGeometry.candidate(ModBlocks.statue_elb_g);
        SectionGeometry.candidate(ModBlocks.statue_elb_w);
        SectionGeometry.candidate(ModBlocks.statue_elb_f);
        TextureInfo statueTexture = inspectTexture("models/misc/modelstatue");
        TextureInfo gunTexture = inspectTexture("models/modelgun");
        TextureInfo watchTexture = inspectTexture("items/watch");
        TextureAtlasSprite statueSprite = sprite(atlas, statueTexture);
        TextureAtlasSprite gunSprite = sprite(atlas, gunTexture);
        TextureAtlasSprite watchSprite = sprite(atlas, watchTexture);

        for (EnumFacing facing : HORIZONTALS) {
            Matrix4f base = compose(
                    translate(0.5D, 1.5D, 0.5D),
                    rotateZ(180.0D),
                    rotateY(legacyFacingYaw(facing))
            );

            IBakedModel body = new FixedTransformModelRendererBakedModel(ModelStatue::new, statueSprite, base, 0.0625F, statueTexture.uScale, statueTexture.vScale);
            IBakedModel gun = new FixedTransformModelRendererBakedModel(ModelGun::new, gunSprite,
                    compose(
                            base,
                            translate(0.0D, -2.0D * G, Q),
                            rotateZ(180.0D),
                            translate(0.0D, 2.0D * G, -Q),
                            rotateZ(180.0D),
                            rotateY(90.0D),
                            scale(0.5D),
                            translate(-20.0D * G, 4.0D * G, 11.0D * G),
                            rotateZ(-20.0D)
                    ),
                    0.0625F,
                    gunTexture.uScale,
                    gunTexture.vScale
            );
            IBakedModel watch = new StatueWatchBakedModel(watchSprite,
                    compose(base, translate(0.0D, -2.0D * G, Q), rotateZ(180.0D)),
                    watchTexture.uScale, watchTexture.vScale);

            registry.putObject(facingLocation(ModBlocks.statue_elb, facing), body);
            registry.putObject(facingLocation(ModBlocks.statue_elb_g, facing), new CompositeBakedModel(body, gun));
            registry.putObject(facingLocation(ModBlocks.statue_elb_w, facing), new CompositeBakedModel(body, watch));
            registry.putObject(facingLocation(ModBlocks.statue_elb_f, facing), new CompositeBakedModel(body, gun, watch));
        }
    }

    private static void bakeWavefrontFacing(IRegistry<ModelResourceLocation, IBakedModel> registry, TextureMap atlas, Block block,
                                            String modelPath, String spritePath, Function<EnumFacing, Matrix4f> transformFactory) {
        SectionGeometry.candidate(block);
        HFRWavefrontObject model = new HFRWavefrontObject(new ResourceLocation(Tags.MODID, modelPath));
        TextureInfo texture = inspectTexture(spritePath);
        TextureAtlasSprite sprite = sprite(atlas, texture);
        for (EnumFacing facing : HORIZONTALS) {
            registry.putObject(facingLocation(block, facing), new FixedTransformWavefrontBakedModel(model, sprite, null, transformFactory.apply(facing), true, texture.uScale, texture.vScale));
        }
    }

    private static void bakeLegacyFacingModel(IRegistry<ModelResourceLocation, IBakedModel> registry, TextureMap atlas, Block block, ModelBase model,
                                              String spritePath, Function<EnumFacing, Matrix4f> transformFactory) {
        SectionGeometry.candidate(block);
        TextureInfo texture = inspectTexture(spritePath);
        TextureAtlasSprite sprite = sprite(atlas, texture);
        for (EnumFacing facing : HORIZONTALS) {
            registry.putObject(facingLocation(block, facing),
                    new FixedTransformModelRendererBakedModel(() -> model, sprite, transformFactory.apply(facing), 0.0625F, texture.uScale, texture.vScale));
        }
    }

    private static TextureAtlasSprite sprite(TextureMap atlas, TextureInfo texture) {
        return PaddedSpriteUtil.sprite(atlas, texture);
    }

    private static void register(TextureMap map, String path) {
        PaddedSpriteUtil.register(map, PaddedSpriteUtil.inspectTexture(new ResourceLocation(Tags.MODID, path)));
    }

    private static TextureInfo inspectTexture(String path) {
        return PaddedSpriteUtil.inspectTexture(new ResourceLocation(Tags.MODID, path));
    }

    private static ModelResourceLocation facingLocation(Block block, EnumFacing facing) {
        return new ModelResourceLocation(block.getRegistryName(), "facing=" + facing.getName());
    }

    private static double legacyFacingYaw(EnumFacing facing) {
        return switch (facing) {
            case NORTH -> 180.0D;
            case SOUTH -> 0.0D;
            case WEST -> 90.0D;
            case EAST -> 270.0D;
            default -> 0.0D;
        };
    }

    private static double boxcarYaw(EnumFacing facing) {
        return switch (facing) {
            case NORTH -> 270.0D;
            case SOUTH -> 90.0D;
            case WEST -> 0.0D;
            case EAST -> 180.0D;
            default -> 0.0D;
        };
    }


}
