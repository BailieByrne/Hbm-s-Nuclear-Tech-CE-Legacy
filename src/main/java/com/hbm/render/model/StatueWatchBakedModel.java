package com.hbm.render.model;

import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.EnumFacing;
import javax.vecmath.Matrix4f;
import javax.vecmath.Vector3f;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import static com.hbm.render.model.BakedModelMatrixUtil.*;

/** Cached vanilla1.7 RenderItem frame geometry for the statue's ordinary watch item. */
public final class StatueWatchBakedModel extends AbstractBakedModel {
    private final TextureAtlasSprite sprite;
    private final List<BakedQuad> flat;
    private final List<BakedQuad> fancy;
    private final float uScale;
    private final float vScale;

    public StatueWatchBakedModel(TextureAtlasSprite sprite, Matrix4f caller, float uScale, float vScale) {
        super(ItemCameraTransforms.DEFAULT);
        this.sprite = sprite; this.uScale = uScale; this.vScale = vScale;
        Matrix4f item = compose(caller, scale(0.5128205F), translate(0D, -0.05F, 0D));
        List<BakedQuad> simple = new ArrayList<>(1);
        quad(simple, item, 0F,1F,0F, new float[][]{{-.5F,-.25F,0F,0F,1F},{.5F,-.25F,0F,1F,1F},{.5F,.75F,0F,1F,0F},{-.5F,.75F,0F,0F,0F}});
        flat = Collections.unmodifiableList(simple);
        Matrix4f extruded = compose(item, rotateY(180D), translate(-.5F,-.25F,.0421875F));
        List<BakedQuad> volume = new ArrayList<>(); float depth=.0625F;
        quad(volume,extruded,0F,0F,1F,new float[][]{{0,0,0,1,1},{1,0,0,0,1},{1,1,0,0,0},{0,1,0,1,0}});
        quad(volume,extruded,0F,0F,-1F,new float[][]{{0,1,-depth,1,0},{1,1,-depth,0,0},{1,0,-depth,0,1},{0,0,-depth,1,1}});
        // Vanilla ItemRenderer.renderItemIn2D emits two sides for every authored texture column and row.
        int width=Math.round(sprite.getIconWidth()*uScale),height=Math.round(sprite.getIconHeight()*vScale);
        for(int i=0;i<width;i++) {
            float x=(float)i/width, right=x+1F/width, u=1F-x-.5F/width;
            quad(volume,extruded,-1F,0F,0F,new float[][]{{x,0,-depth,u,1},{x,0,0,u,1},{x,1,0,u,0},{x,1,-depth,u,0}});
            quad(volume,extruded,1F,0F,0F,new float[][]{{right,1,-depth,u,0},{right,1,0,u,0},{right,0,0,u,1},{right,0,-depth,u,1}});
        }
        for(int i=0;i<height;i++) {
            float y=(float)i/height, top=y+1F/height, v=1F-y-.5F/height;
            quad(volume,extruded,0F,1F,0F,new float[][]{{0,top,0,1,v},{1,top,0,0,v},{1,top,-depth,0,v},{0,top,-depth,1,v}});
            quad(volume,extruded,0F,-1F,0F,new float[][]{{1,y,0,0,v},{0,y,0,1,v},{0,y,-depth,1,v},{1,y,-depth,0,v}});
        }
        fancy=Collections.unmodifiableList(volume);
    }
    @Override public List<BakedQuad> getQuads(IBlockState state,EnumFacing side,long rand) {
        if(side!=null)return Collections.emptyList();
        return Minecraft.getMinecraft().gameSettings.fancyGraphics?fancy:flat;
    }
    @Override public TextureAtlasSprite getParticleTexture(){return sprite;}
    private void quad(List<BakedQuad> output,Matrix4f frame,float nx,float ny,float nz,float[][] points) {
        Vector3f normal=transformNormal(frame,nx,ny,nz); int shade=GeometryBakeUtil.computeShade(normal.x,normal.y,normal.z);
        int[] data=new int[DefaultVertexFormats.BLOCK.getIntegerSize()*4]; float[] scratch=new float[4];
        for(int i=0;i<4;i++) {
            float[] point=points[i];Vector3f position=transformPosition(frame,point[0],point[1],point[2]);
            GeometryBakeUtil.putVertex(DefaultVertexFormats.BLOCK,data,i,position.x,position.y,position.z,point[3]*16F*uScale,point[4]*16F*vScale,
                    shade,shade,shade,normal,sprite,scratch);
        }
        output.add(new HbmBakedQuad(data,-1,EnumFacing.getFacingFromVector(normal.x,normal.y,normal.z),sprite,DefaultVertexFormats.BLOCK));
    }
}