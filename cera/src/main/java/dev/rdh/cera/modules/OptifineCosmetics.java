package dev.rdh.cera.modules;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.rdh.cera.Cera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.living.player.ClientPlayerEntity;
import net.minecraft.client.render.entity.PlayerRenderer;
import net.minecraft.client.render.entity.layer.EntityRenderLayer;
import net.minecraft.client.render.model.Model;
import net.minecraft.client.render.model.ModelPart;
import net.minecraft.client.render.model.entity.PlayerModel;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.client.render.texture.DynamicTexture;
import net.minecraft.client.render.texture.HttpImageProcessor;
import net.minecraft.client.render.texture.HttpTexture;
import net.minecraft.client.render.texture.TextureManager;
import net.minecraft.resource.Identifier;

import javax.imageio.ImageIO;
import java.awt.Graphics;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

public final class OptifineCosmetics {
    private static final String SERVER = "https://s.optifine.net";
    private static final Pattern USERNAME = Pattern.compile("[A-Za-z0-9_]+");
    private static final Map<String, Cape> CAPES = new ConcurrentHashMap<>();
    private static final Map<String, List<Cosmetic>> COSMETICS = new ConcurrentHashMap<>();
    private static final Set<String> REQUESTED = ConcurrentHashMap.newKeySet();

    private OptifineCosmetics() {
    }

    public static Identifier cape(ClientPlayerEntity player, Identifier vanilla) {
        if (!Cera.CONFIG.optifineCosmetics) return vanilla;
        String name = player.getGameProfile().getName();
        if (!USERNAME.matcher(name).matches()) return vanilla;

        Cape cape = CAPES.computeIfAbsent(name, Cape::new);
        cape.register();
        return cape.loaded ? cape.location : vanilla;
    }

    private static List<Cosmetic> cosmetics(ClientPlayerEntity player) {
        if (!Cera.CONFIG.optifineCosmetics) return List.of();
        String name = player.getGameProfile().getName();
        if (!USERNAME.matcher(name).matches()) return List.of();
        if (REQUESTED.add(name)) {
            CompletableFuture.runAsync(() -> COSMETICS.put(name, loadCosmetics(name)));
        }
        return COSMETICS.getOrDefault(name, List.of());
    }

    private static List<Cosmetic> loadCosmetics(String name) {
        try {
            JsonObject config = object(json("users/" + name + ".cfg"));
            JsonArray items = config == null ? null : config.getAsJsonArray("items");
            if (items == null) return List.of();

            List<Cosmetic> cosmetics = new ArrayList<>();
            for (int index = 0; index < items.size(); index++) {
                JsonObject item = object(items.get(index));
                if (item == null || !bool(item, "active", true)) continue;
                Cosmetic cosmetic = parseCosmetic(item, name, index);
                if (cosmetic != null) cosmetics.add(cosmetic);
            }
            return cosmetics;
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private static Cosmetic parseCosmetic(JsonObject item, String player, int index) throws IOException {
        String type = string(item, "type");
        if (type == null) return null;
        String modelPath = string(item, "model");
        if (modelPath == null) modelPath = "items/" + type + "/model.cfg";
        JsonObject model = object(json(modelPath));
        if (model == null || !"PlayerItem".equals(string(model, "type"))) return null;

        int[] textureSize = ints(model.get("textureSize"), 2);
        JsonArray models = model.getAsJsonArray("models");
        if (textureSize == null || models == null) return null;

        boolean playerTexture = bool(model, "usePlayerTexture", false);
        BufferedImage texture = null;
        if (!playerTexture) {
            String texturePath = string(item, "texture");
            if (texturePath == null) texturePath = "items/" + type + "/users/" + player + ".png";
            texture = ImageIO.read(new ByteArrayInputStream(bytes(texturePath)));
            if (texture == null) return null;
        }

        Model base = new Model() { };
        base.textureWidth = textureSize[0];
        base.textureHeight = textureSize[1];
        Map<String, JsonObject> definitions = new ConcurrentHashMap<>();
        List<Part> parts = new ArrayList<>();
        for (JsonElement element : models) {
            JsonObject part = object(element);
            if (part == null || !"ModelBox".equals(string(part, "type"))) continue;
            String baseId = string(part, "baseId");
            if (baseId != null) inherit(part, definitions.get(baseId));
            String id = string(part, "id");
            if (id != null) definitions.putIfAbsent(id, part);
            ModelPart root = parsePart(part, base, textureSize);
            if (root != null) parts.add(new Part(root, string(part, "attachTo"), number(part, "scale", 1.0F)));
        }
        return parts.isEmpty() ? null : new Cosmetic(parts, playerTexture, texture, index);
    }

    private static void inherit(JsonObject part, JsonObject parent) {
        if (parent == null) return;
        for (Map.Entry<String, JsonElement> entry : parent.entrySet()) {
            if (!part.has(entry.getKey())) part.add(entry.getKey(), entry.getValue());
        }
    }

    private static ModelPart parsePart(JsonObject json, Model base, int[] inheritedTextureSize) {
        int[] textureSize = ints(json.get("textureSize"), 2);
        if (textureSize == null) textureSize = inheritedTextureSize;
        ModelPart part = new ModelPart(base);
        part.setTextureSize(textureSize[0], textureSize[1]);

        String invert = string(json, "invertAxis");
        float[] translate = floats(json.get("translate"), 3);
        float[] rotate = floats(json.get("rotate"), 3);
        if (translate == null) translate = new float[3];
        if (rotate == null) rotate = new float[3];
        for (int axis = 0; axis < 3; axis++) {
            if (invert != null && invert.toLowerCase(Locale.ROOT).indexOf("xyz".charAt(axis)) >= 0) {
                translate[axis] = -translate[axis];
                rotate[axis] = -rotate[axis];
            }
        }
        part.setPos(translate[0], translate[1], translate[2]);
        part.rotationX = (float)Math.toRadians(rotate[0]);
        part.rotationY = (float)Math.toRadians(rotate[1]);
        part.rotationZ = (float)Math.toRadians(rotate[2]);
        String mirror = string(json, "mirrorTexture");
        part.flipped = mirror != null && mirror.toLowerCase(Locale.ROOT).contains("u");

        JsonArray boxes = json.getAsJsonArray("boxes");
        if (boxes != null) {
            for (JsonElement element : boxes) {
                JsonObject box = object(element);
                if (box == null) continue;
                int[] offset = ints(box.get("textureOffset"), 2);
                float[] coordinates = floats(box.get("coordinates"), 6);
                if (offset == null || coordinates == null) continue;
                for (int axis = 0; axis < 3; axis++) {
                    if (invert != null && invert.toLowerCase(Locale.ROOT).indexOf("xyz".charAt(axis)) >= 0) {
                        coordinates[axis] = -coordinates[axis] - coordinates[axis + 3];
                    }
                }
                part.setTextureCoords(offset[0], offset[1]);
                part.addBox(coordinates[0], coordinates[1], coordinates[2], (int)coordinates[3],
                        (int)coordinates[4], (int)coordinates[5], number(box, "sizeAdd", 0.0F));
            }
        }

        JsonObject child = object(json.get("submodel"));
        if (child != null) part.addChild(parsePart(child, base, textureSize));
        JsonArray children = json.getAsJsonArray("submodels");
        if (children != null) {
            for (JsonElement element : children) {
                JsonObject childJson = object(element);
                if (childJson != null) part.addChild(parsePart(childJson, base, textureSize));
            }
        }
        return part;
    }

    private static JsonElement json(String path) throws IOException {
        return new JsonParser().parse(new String(bytes(path), StandardCharsets.UTF_8));
    }

    private static byte[] bytes(String path) throws IOException {
        HttpURLConnection connection = (HttpURLConnection)new URL(SERVER + "/" + path).openConnection(Minecraft.getInstance().getNetworkProxy());
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(5000);
        try {
            if (connection.getResponseCode() / 100 != 2) throw new IOException("HTTP " + connection.getResponseCode());
            try (InputStream input = connection.getInputStream()) {
                return input.readAllBytes();
            }
        } finally {
            connection.disconnect();
        }
    }

    private static JsonObject object(JsonElement element) {
        return element != null && element.isJsonObject() ? element.getAsJsonObject() : null;
    }

    private static String string(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value != null && value.isJsonPrimitive() ? value.getAsString() : null;
    }

    private static boolean bool(JsonObject object, String key, boolean fallback) {
        JsonElement value = object.get(key);
        return value != null && value.isJsonPrimitive() ? value.getAsBoolean() : fallback;
    }

    private static float number(JsonObject object, String key, float fallback) {
        JsonElement value = object.get(key);
        return value != null && value.isJsonPrimitive() ? value.getAsFloat() : fallback;
    }

    private static int[] ints(JsonElement value, int length) {
        if (value == null || !value.isJsonArray() || value.getAsJsonArray().size() != length) return null;
        int[] values = new int[length];
        for (int index = 0; index < length; index++) values[index] = value.getAsJsonArray().get(index).getAsInt();
        return values;
    }

    private static float[] floats(JsonElement value, int length) {
        if (value == null || !value.isJsonArray() || value.getAsJsonArray().size() != length) return null;
        float[] values = new float[length];
        for (int index = 0; index < length; index++) values[index] = value.getAsJsonArray().get(index).getAsFloat();
        return values;
    }

    private static final class Cape implements HttpImageProcessor {
        private final Identifier location;
        private final String url;
        private boolean registered;
        private volatile boolean loaded;

        private Cape(String name) {
            this.location = new Identifier("cera", "capes/" + name.toLowerCase(Locale.ROOT));
            this.url = SERVER + "/capes/" + name + ".png";
        }

        private void register() {
            if (this.registered) return;
            Minecraft.getInstance().getTextureManager().register(this.location, new HttpTexture(null, this.url, null, this));
            this.registered = true;
        }

        @Override
        public BufferedImage process(BufferedImage image) {
            int width = 64;
            int height = 32;
            while (width < image.getWidth() || height < image.getHeight()) {
                width *= 2;
                height *= 2;
            }
            BufferedImage cape = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            Graphics graphics = cape.getGraphics();
            graphics.drawImage(image, 0, 0, null);
            graphics.dispose();
            return cape;
        }

        @Override
        public void onTextureDownloaded() {
            this.loaded = true;
        }
    }

    private static final class Cosmetic {
        private final List<Part> parts;
        private final boolean playerTexture;
        private final BufferedImage image;
        private final int index;
        private Identifier texture;

        private Cosmetic(List<Part> parts, boolean playerTexture, BufferedImage image, int index) {
            this.parts = parts;
            this.playerTexture = playerTexture;
            this.image = image;
            this.index = index;
        }

        private boolean bind(ClientPlayerEntity player, TextureManager textures) {
            if (this.playerTexture) {
                textures.bind(player.getSkinTextureLocation());
                return true;
            }
            if (this.texture == null) this.texture = textures.register("cera_cosmetic_" + this.index, new DynamicTexture(this.image));
            textures.bind(this.texture);
            return true;
        }
    }

    private record Part(ModelPart model, String attachTo, float scale) {
    }

    public static final class Layer implements EntityRenderLayer<ClientPlayerEntity> {
        private final PlayerRenderer renderer;

        public Layer(PlayerRenderer renderer) {
            this.renderer = renderer;
        }

        @Override
        public void render(ClientPlayerEntity player, float walkAnimationProgress, float walkAnimationSpeed,
                           float tickDelta, float bob, float yaw, float pitch, float scale) {
            if (player.isInvisible()) return;
            TextureManager textures = Minecraft.getInstance().getTextureManager();
            PlayerModel model = this.renderer.getModel();
            for (Cosmetic cosmetic : cosmetics(player)) {
                if (!cosmetic.bind(player, textures)) continue;
                for (Part part : cosmetic.parts) {
                    GlStateManager.pushMatrix();
                    if (player.isSneaking()) GlStateManager.translatef(0.0F, 0.2F, 0.0F);
                    ModelPart attachTo = attachment(model, part.attachTo());
                    if (attachTo != null) attachTo.transform(scale);
                    if (part.scale() != 1.0F) GlStateManager.scalef(part.scale(), part.scale(), part.scale());
                    part.model().render(scale);
                    GlStateManager.popMatrix();
                }
            }
        }

        private static ModelPart attachment(PlayerModel model, String name) {
            if (name == null || "body".equals(name)) return model.body;
            return switch (name) {
                case "head" -> model.head;
                case "leftArm" -> model.leftArm;
                case "rightArm" -> model.rightArm;
                case "leftLeg" -> model.leftLeg;
                case "rightLeg" -> model.rightLeg;
                default -> null;
            };
        }

        @Override
        public boolean colorsWhenDamaged() {
            return false;
        }
    }
}
