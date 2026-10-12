package com.hbm.render.loader;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.IResource;
import net.minecraft.client.resources.IResourceManager;

/** Reloads live immediate meshes and stable GPU groups before the1.12 model bake. */
public final class HFRModelReloader {
    private HFRModelReloader() { }

    public static void reloadModels() {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (!minecraft.isCallingFromMinecraftThread()) {
            throw new IllegalStateException("Model reload must run on the client thread");
        }
        IResourceManager resources = minecraft.getResourceManager();
        List<HFRWavefrontObject> models;
        synchronized (HFRWavefrontObject.LIVE_MODELS) {
            models = new ArrayList<>(HFRWavefrontObject.LIVE_MODELS);
        }
        for (HFRWavefrontObject model : models) {
            try (IResource resource = resources.getResource(model.resource)) {
                model.destroy();
                model.loadObjModel(resource.getInputStream());
            } catch (IOException error) {
                throw new ModelFormatException("Cannot reload model " + model.resource, error);
            }
        }
        for (WaveFrontObjectVAO model : WaveFrontObjectVAO.allVBOs) model.reload();
    }
}
