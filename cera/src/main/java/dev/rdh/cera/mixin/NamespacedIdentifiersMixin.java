package dev.rdh.cera.mixin;

import net.ornithemc.osl.core.api.util.NamespacedIdentifier;
import net.ornithemc.osl.core.api.util.NamespacedIdentifiers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

import net.minecraft.resource.Identifier;

@Mixin(NamespacedIdentifiers.class)
public class NamespacedIdentifiersMixin {
	/**
	 * @author rdh
	 * @reason be more lenient with identifier parsing
	 */
	@Overwrite
	public static NamespacedIdentifier from(String namespace, String identifier) {
		return new Identifier(namespace, identifier);
	}
}
