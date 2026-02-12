package com.radiance.client.vertex;

import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.render.model.BakedQuad;

public class EmissiveDelegatingVertexConsumer implements VertexConsumer {
    private final VertexConsumer delegate;
    private final float emission;
    private final PBRVertexConsumer pbrDelegate;

    public EmissiveDelegatingVertexConsumer(VertexConsumer delegate, float emission) {
        this.delegate = delegate;
        this.emission = emission;
        this.pbrDelegate = (delegate instanceof PBRVertexConsumer) ? (PBRVertexConsumer) delegate : null;
    }

    @Override
    public VertexConsumer vertex(float x, float y, float z) {
        delegate.vertex(x, y, z);
        if (pbrDelegate != null) {
            pbrDelegate.albedoEmission(emission);
        }
        return this;
    }

    @Override
    public VertexConsumer color(int red, int green, int blue, int alpha) {
        delegate.color(red, green, blue, alpha);
        return this;
    }

    @Override
    public VertexConsumer texture(float u, float v) {
        delegate.texture(u, v);
        return this;
    }

    @Override
    public VertexConsumer overlay(int u, int v) {
        delegate.overlay(u, v);
        return this;
    }

    @Override
    public VertexConsumer light(int u, int v) {
        delegate.light(u, v);
        return this;
    }

    @Override
    public VertexConsumer normal(float x, float y, float z) {
        delegate.normal(x, y, z);
        return this;
    }
}
