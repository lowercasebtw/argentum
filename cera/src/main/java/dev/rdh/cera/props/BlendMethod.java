package dev.rdh.cera.props;

import org.lwjgl.opengl.GL11;

import net.minecraft.client.render.platform.GlStateManager;

/**
 * A texture blending operation, as described in the "Blending methods" section of
 * {@code properties_files.txt}.
 */
public enum BlendMethod {
	REPLACE(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA),
	ALPHA(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA),
	OVERLAY(GL11.GL_DST_COLOR, GL11.GL_SRC_COLOR),
	ADD(GL11.GL_SRC_ALPHA, GL11.GL_ONE),
	SUBTRACT(GL11.GL_ONE_MINUS_DST_COLOR, GL11.GL_ZERO),
	MULTIPLY(GL11.GL_DST_COLOR, GL11.GL_ONE_MINUS_SRC_ALPHA),
	DODGE(GL11.GL_ONE, GL11.GL_ONE),
	BURN(GL11.GL_ZERO, GL11.GL_ONE_MINUS_SRC_COLOR),
	SCREEN(GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_COLOR);

	private final int src, dst;

	BlendMethod(int src, int dst) {
		this.src = src;
		this.dst = dst;
	}

	public void apply(float brightness) {
		GlStateManager.blendFunc(src, dst);

		if (this == ALPHA || this == ADD || this == REPLACE) {
			GlStateManager.color4f(1.0f, 1.0f, 1.0f, brightness);
		} else if (this == MULTIPLY) {
			GlStateManager.color4f(brightness, brightness, brightness, brightness);
		} else {
			GlStateManager.color4f(brightness, brightness, brightness, 1.0f);
		}
	}

	public static BlendMethod byName(String name) {
		if (name.equalsIgnoreCase("color")) {
			return OVERLAY;
		}
		for (BlendMethod method : values()) {
			if (method.name().equalsIgnoreCase(name)) {
				return method;
			}
		}
		return null;
	}
}
