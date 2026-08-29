package dev.rdh.cera.ext;

import dev.rdh.cera.modules.AnimatedTextures;
import dev.rdh.cera.modules.CustomGuis;

public interface CeraTextureManagerExtension {
	default CustomGuis cera$getCustomGuis() {
		throw new UnsupportedOperationException();
	}

	default AnimatedTextures cera$getAnimatedTextures() {
		throw new UnsupportedOperationException();
	}
}
