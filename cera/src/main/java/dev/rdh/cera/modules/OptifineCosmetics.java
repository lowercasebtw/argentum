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
import net.minecraft.client.render.model.Box;
import net.minecraft.client.render.model.Model;
import net.minecraft.client.render.model.ModelPart;
import net.minecraft.client.render.model.entity.PlayerModel;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.client.render.platform.MemoryTracker;
import net.minecraft.client.render.texture.DynamicTexture;
import net.minecraft.client.render.texture.HttpImageProcessor;
import net.minecraft.client.render.texture.HttpTexture;
import net.minecraft.client.render.texture.TextureManager;
import net.minecraft.client.render.vertex.BufferBuilder;
import net.minecraft.client.render.vertex.DefaultVertexFormat;
import net.minecraft.client.render.vertex.Tesselator;
import net.minecraft.resource.Identifier;
import org.lwjgl.opengl.GL11;

import javax.imageio.ImageIO;
import java.awt.Graphics;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

public final class OptifineCosmetics {
    private static final String SERVER = "https://optifine.net";
    private static final Pattern USERNAME = Pattern.compile("[A-Za-z0-9_]+");

    private final Map<String, Cape> capes = new ConcurrentHashMap<>();
    private final Map<String, List<Cosmetic>> cosmetics = new ConcurrentHashMap<>();
    private final Set<String> requested = ConcurrentHashMap.newKeySet();

    public Optional<Identifier> cape(ClientPlayerEntity player) {
        if (!Cera.CONFIG.optifineCosmetics) return Optional.empty();
        String name = player.getGameProfile().getName();
        if (name == null || !USERNAME.matcher(name).matches()) return Optional.empty();
        Cape cape = this.capes.computeIfAbsent(name, Cape::new);
        cape.register();
        return cape.loaded ? Optional.of(cape.location) : Optional.empty();
    }

    private List<Cosmetic> cosmetics(ClientPlayerEntity player) {
        if (!Cera.CONFIG.optifineCosmetics) return List.of();
        String name = player.getGameProfile().getName();
        if (name == null || !USERNAME.matcher(name).matches()) return List.of();
        if (this.requested.add(name)) CompletableFuture.runAsync(() -> this.cosmetics.put(name, this.loadCosmetics(name)));
        return this.cosmetics.getOrDefault(name, List.of());
    }

    private List<Cosmetic> loadCosmetics(String name) {
        try {
            JsonObject config = object(json("users/" + name + ".cfg"));
            JsonArray items = config == null ? null : config.getAsJsonArray("items");
            if (items == null) return List.of();
            List<Cosmetic> cosmetics = new ArrayList<>();
            for (int index = 0; index < items.size(); index++) {
                JsonObject item = object(items.get(index));
                if (item == null || !bool(item, "active", true)) continue;
                Cosmetic cosmetic = this.parseCosmetic(item, name, index);
                if (cosmetic != null) cosmetics.add(cosmetic);
            }
            return cosmetics;
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private Cosmetic parseCosmetic(JsonObject item, String player, int index) throws IOException {
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
        BufferedImage image = null;
        if (!playerTexture) {
            String texturePath = string(item, "texture");
            if (texturePath == null) texturePath = "items/" + type + "/users/" + player + ".png";
            image = ImageIO.read(new ByteArrayInputStream(bytes(texturePath)));
            if (image == null) return null;
        }

        String basePath = directory(modelPath);
        Map<String, JsonObject> definitions = new HashMap<>();
        List<Part> parts = new ArrayList<>();
        for (JsonElement element : models) {
            JsonObject part = object(element);
            if (part == null || !"ModelBox".equals(string(part, "type"))) continue;
            String baseId = string(part, "baseId");
            if (baseId != null) {
                JsonObject parent = definitions.get(baseId);
                if (parent == null) continue;
                inherit(part, parent);
            }
            String id = string(part, "id");
            if (id != null) definitions.putIfAbsent(id, part);
            parts.add(new Part(this.parsePart(part, textureSize, basePath), string(part, "attachTo")));
        }
        return parts.isEmpty() ? null : new Cosmetic(parts, playerTexture, image, index);
    }

    private CosmeticPart parsePart(JsonObject json, int[] inheritedTextureSize, String basePath) throws IOException {
        int[] textureSize = ints(json.get("textureSize"), 2);
        if (textureSize == null) textureSize = inheritedTextureSize;
        CosmeticPart part = new CosmeticPart(textureSize[0], textureSize[1], resource(basePath, string(json, "texture")));
        String invert = lower(string(json, "invertAxis"));
        float[] translate = floats(json.get("translate"), 3);
        float[] rotate = floats(json.get("rotate"), 3);
        if (translate == null) translate = new float[3];
        if (rotate == null) rotate = new float[3];
        for (int axis = 0; axis < 3; axis++) if (invert.indexOf("xyz".charAt(axis)) >= 0) {
            translate[axis] = -translate[axis];
            rotate[axis] = -rotate[axis];
        }
        part.x = translate[0];
        part.y = translate[1];
        part.z = translate[2];
        part.rotationX = (float)Math.toRadians(rotate[0]);
        part.rotationY = (float)Math.toRadians(rotate[1]);
        part.rotationZ = (float)Math.toRadians(rotate[2]);
        float scale = number(json, "scale", 1.0F);
        part.scaleX = number(json, "scaleX", scale);
        part.scaleY = number(json, "scaleY", scale);
        part.scaleZ = number(json, "scaleZ", scale);
        String mirror = lower(string(json, "mirrorTexture"));
        boolean mirrorU = mirror.contains("u");
        boolean mirrorV = mirror.contains("v");

        JsonArray boxes = json.getAsJsonArray("boxes");
        if (boxes != null) for (JsonElement element : boxes) {
            JsonObject box = object(element);
            if (box == null) throw new IOException("Invalid box");
            int[] offset = ints(box.get("textureOffset"), 2);
            int[][] faceUvs = faceUvs(box);
            float[] coordinates = floats(box.get("coordinates"), 6);
            if ((offset == null && faceUvs == null) || coordinates == null) throw new IOException("Invalid box");
            invert(coordinates, invert);
            part.boxes.add(new UvBox(part.boxModel, offset, faceUvs, coordinates, number(box, "sizeAdd", 0.0F), mirrorU, mirrorV));
        }

        JsonArray sprites = json.getAsJsonArray("sprites");
        if (sprites != null) for (JsonElement element : sprites) {
            JsonObject sprite = object(element);
            if (sprite == null) throw new IOException("Invalid sprite");
            int[] offset = ints(sprite.get("textureOffset"), 2);
            float[] coordinates = floats(sprite.get("coordinates"), 6);
            if (offset == null || coordinates == null) throw new IOException("Invalid sprite");
            invert(coordinates, invert);
            part.boxes.add(new Sprite(part.boxModel, offset, coordinates, mirrorU, mirrorV));
        }

        JsonObject child = object(json.get("submodel"));
        if (child != null) part.children.add(this.parsePart(child, textureSize, basePath));
        JsonArray children = json.getAsJsonArray("submodels");
        if (children != null) for (JsonElement element : children) {
            JsonObject childJson = object(element);
            if (childJson == null) throw new IOException("Invalid submodel");
            part.children.add(this.parsePart(childJson, textureSize, basePath));
        }
        return part;
    }

    private static void inherit(JsonObject part, JsonObject parent) {
        for (Map.Entry<String, JsonElement> entry : parent.entrySet()) if (!part.has(entry.getKey())) part.add(entry.getKey(), entry.getValue());
    }

    private static void invert(float[] coordinates, String axes) {
        for (int axis = 0; axis < 3; axis++) if (axes.indexOf("xyz".charAt(axis)) >= 0) {
            coordinates[axis] = -coordinates[axis] - coordinates[axis + 3];
        }
    }

    private static int[][] faceUvs(JsonObject box) {
        int[][] uvs = {ints(box.get("uvDown"), 4), ints(box.get("uvUp"), 4), ints(box.get("uvNorth"), 4),
                ints(box.get("uvSouth"), 4), ints(box.get("uvWest"), 4), ints(box.get("uvEast"), 4)};
        if (uvs[2] == null) uvs[2] = ints(box.get("uvFront"), 4);
        if (uvs[3] == null) uvs[3] = ints(box.get("uvBack"), 4);
        if (uvs[4] == null) uvs[4] = ints(box.get("uvLeft"), 4);
        if (uvs[5] == null) uvs[5] = ints(box.get("uvRight"), 4);
        for (int[] uv : uvs) if (uv != null) return uvs;
        return null;
    }

    private static Identifier resource(String basePath, String path) {
        if (path == null) return null;
        if (!path.endsWith(".png")) path += ".png";
        if (!path.contains("/")) path = basePath.isEmpty() ? path : basePath + "/" + path;
        else if (path.startsWith("./")) path = basePath + "/" + path.substring(2);
        else if (path.startsWith("~/")) path = "optifine/" + path.substring(2);
        return new Identifier(path);
    }

    private static String directory(String path) {
        int slash = path.lastIndexOf('/');
        return slash < 0 ? "" : path.substring(0, slash);
    }

    private static JsonElement json(String path) throws IOException {
        return JsonParser.parseString(new String(bytes(path), StandardCharsets.UTF_8));
    }

    private static byte[] bytes(String path) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) URI.create(SERVER + "/" + path).toURL()
                .openConnection(Minecraft.getInstance().getNetworkProxy());
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

    private static String lower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
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

        private Identifier bind(ClientPlayerEntity player, TextureManager textures) {
            if (this.playerTexture) return player.getSkinTextureLocation();
            if (this.texture == null) this.texture = textures.register("cera_cosmetic_" + this.index, new DynamicTexture(this.image));
            return this.texture;
        }
    }

    private record Part(CosmeticPart model, String attachTo) {
    }

    private static final class CosmeticPart {
        private final ModelPart boxModel;
        private final List<Box> boxes = new ArrayList<>();
        private final List<CosmeticPart> children = new ArrayList<>();
        private final Identifier texture;
        private float x;
        private float y;
        private float z;
        private float rotationX;
        private float rotationY;
        private float rotationZ;
        private float scaleX = 1.0F;
        private float scaleY = 1.0F;
        private float scaleZ = 1.0F;
        private boolean compiled;
        private int displayList;

        private CosmeticPart(int textureWidth, int textureHeight, Identifier texture) {
            Model model = new Model() { };
            model.textureWidth = textureWidth;
            model.textureHeight = textureHeight;
            this.boxModel = new ModelPart(model);
            this.boxModel.setTextureSize(textureWidth, textureHeight);
            this.texture = texture;
        }

        private void render(TextureManager textures, Identifier inheritedTexture, float scale) {
            if (!this.compiled) this.compile(scale);
            Identifier boundTexture = this.texture == null ? inheritedTexture : this.texture;
            if (this.texture != null) textures.bind(this.texture);
            GlStateManager.pushMatrix();
            GlStateManager.translatef(this.x * scale, this.y * scale, this.z * scale);
            if (this.rotationZ != 0.0F) GlStateManager.rotatef(this.rotationZ * 180.0F / (float)Math.PI, 0.0F, 0.0F, 1.0F);
            if (this.rotationY != 0.0F) GlStateManager.rotatef(this.rotationY * 180.0F / (float)Math.PI, 0.0F, 1.0F, 0.0F);
            if (this.rotationX != 0.0F) GlStateManager.rotatef(this.rotationX * 180.0F / (float)Math.PI, 1.0F, 0.0F, 0.0F);
            GlStateManager.scalef(this.scaleX, this.scaleY, this.scaleZ);
            GL11.glCallList(this.displayList);
            for (CosmeticPart child : this.children) child.render(textures, boundTexture, scale);
            GlStateManager.popMatrix();
            if (this.texture != null) textures.bind(inheritedTexture);
        }

        private void compile(float scale) {
            this.displayList = MemoryTracker.getLists(1);
            GL11.glNewList(this.displayList, GL11.GL_COMPILE);
            BufferBuilder buffer = Tesselator.getInstance().getBuffer();
            for (Box box : this.boxes) box.compile(buffer, scale);
            GL11.glEndList();
            this.compiled = true;
        }
    }

    private static final class UvBox extends Box {
        private final List<Quad> faces = new ArrayList<>();

        private UvBox(ModelPart model, int[] offset, int[][] faceUvs, float[] coordinates, float sizeAdd, boolean mirrorU, boolean mirrorV) {
            super(model, 0, 0, 0.0F, 0.0F, 0.0F, 1, 1, 1, 0.0F);
            float minX = coordinates[0] - sizeAdd;
            float minY = coordinates[1] - sizeAdd;
            float minZ = coordinates[2] - sizeAdd;
            float maxX = coordinates[0] + coordinates[3] + sizeAdd;
            float maxY = coordinates[1] + coordinates[4] + sizeAdd;
            float maxZ = coordinates[2] + coordinates[5] + sizeAdd;
            if (mirrorU) {
                float swap = minX;
                minX = maxX;
                maxX = swap;
            }
            Vertex p0 = new Vertex(minX, minY, minZ);
            Vertex p1 = new Vertex(maxX, minY, minZ);
            Vertex p2 = new Vertex(maxX, maxY, minZ);
            Vertex p3 = new Vertex(minX, maxY, minZ);
            Vertex p4 = new Vertex(minX, minY, maxZ);
            Vertex p5 = new Vertex(maxX, minY, maxZ);
            Vertex p6 = new Vertex(maxX, maxY, maxZ);
            Vertex p7 = new Vertex(minX, maxY, maxZ);
            int[][] uvs = faceUvs == null ? defaultUvs(offset, (int)coordinates[3], (int)coordinates[4], (int)coordinates[5]) : faceUvs;
            add(uvs[0], p5, p1, p2, p6, model, mirrorU, mirrorV);
            add(uvs[1], p0, p4, p7, p3, model, mirrorU, mirrorV);
            add(uvs[2], p5, p4, p0, p1, model, mirrorU, mirrorV);
            add(uvs[3], p2, p3, p7, p6, model, mirrorU, mirrorV);
            add(uvs[4], p1, p0, p3, p2, model, mirrorU, mirrorV);
            add(uvs[5], p4, p5, p6, p7, model, mirrorU, mirrorV);
        }

        private void add(int[] uv, Vertex a, Vertex b, Vertex c, Vertex d, ModelPart model, boolean mirrorU, boolean mirrorV) {
            if (uv == null) return;
            Quad quad = new Quad(a.withUv(uv[2] / model.textureWidth, uv[1] / model.textureHeight),
                    b.withUv(uv[0] / model.textureWidth, uv[1] / model.textureHeight),
                    c.withUv(uv[0] / model.textureWidth, uv[3] / model.textureHeight),
                    d.withUv(uv[2] / model.textureWidth, uv[3] / model.textureHeight));
            if (mirrorU) quad.flip();
            if (mirrorV) quad.flipV();
            this.faces.add(quad);
        }

        private static int[][] defaultUvs(int[] offset, int sizeX, int sizeY, int sizeZ) {
            int u = offset[0];
            int v = offset[1];
            return new int[][]{{u + sizeZ + sizeX, v + sizeZ, u + sizeZ + sizeX + sizeZ, v + sizeZ + sizeY},
                    {u, v + sizeZ, u + sizeZ, v + sizeZ + sizeY}, {u + sizeZ, v, u + sizeZ + sizeX, v + sizeZ},
                    {u + sizeZ + sizeX, v + sizeZ, u + sizeZ + sizeX + sizeX, v},
                    {u + sizeZ, v + sizeZ, u + sizeZ + sizeX, v + sizeZ + sizeY},
                    {u + sizeZ + sizeX + sizeZ, v + sizeZ, u + sizeZ + sizeX + sizeZ + sizeX, v + sizeZ + sizeY}};
        }

        @Override
        public void compile(BufferBuilder buffer, float scale) {
            for (Quad face : this.faces) face.render(buffer, scale);
        }
    }

    private static final class Sprite extends Box {
        private final int offsetX;
        private final int offsetY;
        private final float x;
        private final float y;
        private final float z;
        private final int sizeX;
        private final int sizeY;
        private final int sizeZ;
        private final boolean mirrorU;
        private final boolean mirrorV;
        private final float textureWidth;
        private final float textureHeight;

        private Sprite(ModelPart model, int[] offset, float[] coordinates, boolean mirrorU, boolean mirrorV) {
            super(model, 0, 0, 0.0F, 0.0F, 0.0F, 1, 1, 1, 0.0F);
            this.offsetX = offset[0]; this.offsetY = offset[1];
            this.x = coordinates[0]; this.y = coordinates[1]; this.z = coordinates[2];
            this.sizeX = (int)coordinates[3]; this.sizeY = (int)coordinates[4]; this.sizeZ = (int)coordinates[5];
            this.mirrorU = mirrorU; this.mirrorV = mirrorV;
            this.textureWidth = model.textureWidth; this.textureHeight = model.textureHeight;
        }

        @Override
        public void compile(BufferBuilder buffer, float scale) {
            float minU = this.offsetX / this.textureWidth, minV = this.offsetY / this.textureHeight;
            float maxU = (this.offsetX + this.sizeX) / this.textureWidth, maxV = (this.offsetY + this.sizeY) / this.textureHeight;
            if (this.mirrorU) { float swap = minU; minU = maxU; maxU = swap; }
            if (this.mirrorV) { float swap = minV; minV = maxV; maxV = swap; }
            float width = Math.max(scale * this.sizeZ, 0.000625F);
            float dimX = Math.abs(maxU - minU) * this.textureWidth / 16.0F;
            float dimY = Math.abs(maxV - minV) * this.textureHeight / 16.0F;
            GlStateManager.translatef(this.x * scale, this.y * scale, this.z * scale);
            Quad.direct(new Vertex(0, dimY, 0, minU, maxV), new Vertex(dimX, dimY, 0, maxU, maxV), new Vertex(dimX, 0, 0, maxU, minV), new Vertex(0, 0, 0, minU, minV)).render(buffer, 1);
            Quad.direct(new Vertex(0, 0, width, minU, minV), new Vertex(dimX, 0, width, maxU, minV), new Vertex(dimX, dimY, width, maxU, maxV), new Vertex(0, dimY, width, minU, maxV)).render(buffer, 1);
            float dU = maxU - minU, dV = maxV - minV, halfU = 0.5F * dU / this.sizeX, halfV = 0.5F * dV / this.sizeY;
            for (int index = 0; index < this.sizeX; index++) {
                float progress = index / (float)this.sizeX, u = minU + dU * progress + halfU, next = (index + 1.0F) / this.sizeX;
                Quad.direct(new Vertex(progress * dimX, dimY, width, u, maxV), new Vertex(progress * dimX, dimY, 0, u, maxV), new Vertex(progress * dimX, 0, 0, u, minV), new Vertex(progress * dimX, 0, width, u, minV)).render(buffer, 1);
                Quad.direct(new Vertex(next * dimX, 0, width, u, minV), new Vertex(next * dimX, 0, 0, u, minV), new Vertex(next * dimX, dimY, 0, u, maxV), new Vertex(next * dimX, dimY, width, u, maxV)).render(buffer, 1);
            }
            for (int index = 0; index < this.sizeY; index++) {
                float progress = index / (float)this.sizeY, v = minV + dV * progress + halfV, next = (index + 1.0F) / this.sizeY;
                Quad.direct(new Vertex(0, next * dimY, width, minU, v), new Vertex(dimX, next * dimY, width, maxU, v), new Vertex(dimX, next * dimY, 0, maxU, v), new Vertex(0, next * dimY, 0, minU, v)).render(buffer, 1);
                Quad.direct(new Vertex(dimX, progress * dimY, width, maxU, v), new Vertex(0, progress * dimY, width, minU, v), new Vertex(0, progress * dimY, 0, minU, v), new Vertex(dimX, progress * dimY, 0, maxU, v)).render(buffer, 1);
            }
            GlStateManager.translatef(-this.x * scale, -this.y * scale, -this.z * scale);
        }
    }

    private static final class Quad {
        private Vertex a, b, c, d;

        private Quad(Vertex a, Vertex b, Vertex c, Vertex d) { this.a = a; this.b = b; this.c = c; this.d = d; }
        private static Quad direct(Vertex a, Vertex b, Vertex c, Vertex d) { return new Quad(a, b, c, d); }
        private void flip() { Vertex swap = this.a; this.a = this.d; this.d = swap; swap = this.b; this.b = this.c; this.c = swap; }
        private void flipV() { float av = this.a.v, bv = this.b.v, cv = this.c.v, dv = this.d.v; this.a = this.a.withUv(this.a.u, dv); this.b = this.b.withUv(this.b.u, cv); this.c = this.c.withUv(this.c.u, bv); this.d = this.d.withUv(this.d.u, av); }

        private void render(BufferBuilder buffer, float scale) {
            float ax = this.b.x - this.a.x, ay = this.b.y - this.a.y, az = this.b.z - this.a.z;
            float bx = this.b.x - this.c.x, by = this.b.y - this.c.y, bz = this.b.z - this.c.z;
            float normalX = by * az - bz * ay, normalY = bz * ax - bx * az, normalZ = bx * ay - by * ax;
            float length = (float)Math.sqrt(normalX * normalX + normalY * normalY + normalZ * normalZ);
            if (length != 0) { normalX /= length; normalY /= length; normalZ /= length; }
            buffer.begin(7, DefaultVertexFormat.ENTITY);
            vertex(buffer, this.a, scale, normalX, normalY, normalZ); vertex(buffer, this.b, scale, normalX, normalY, normalZ);
            vertex(buffer, this.c, scale, normalX, normalY, normalZ); vertex(buffer, this.d, scale, normalX, normalY, normalZ);
            Tesselator.getInstance().end();
        }

        private static void vertex(BufferBuilder buffer, Vertex vertex, float scale, float normalX, float normalY, float normalZ) {
            buffer.vertex(vertex.x * scale, vertex.y * scale, vertex.z * scale).texture(vertex.u, vertex.v).normal(normalX, normalY, normalZ).nextVertex();
        }
    }

    private record Vertex(float x, float y, float z, float u, float v) {
        private Vertex(float x, float y, float z) { this(x, y, z, 0, 0); }
        private Vertex withUv(float u, float v) { return new Vertex(this.x, this.y, this.z, u, v); }
    }

    public final class Layer implements EntityRenderLayer<ClientPlayerEntity> {
        private final PlayerRenderer renderer;
        public Layer(PlayerRenderer renderer) { this.renderer = renderer; }

        @Override
        public void render(ClientPlayerEntity player, float walkAnimationProgress, float walkAnimationSpeed, float tickDelta, float bob, float yaw, float pitch, float scale) {
            if (player.isInvisible()) return;
            TextureManager textures = Minecraft.getInstance().getTextureManager();
            PlayerModel model = this.renderer.getModel();
            for (Cosmetic cosmetic : OptifineCosmetics.this.cosmetics(player)) {
                Identifier texture = cosmetic.bind(player, textures);
                textures.bind(texture);
                for (Part part : cosmetic.parts) {
                    GlStateManager.pushMatrix();
                    if (player.isSneaking()) GlStateManager.translatef(0, 0.2F, 0);
                    ModelPart attachTo = attachment(model, part.attachTo());
                    if (attachTo != null) attachTo.transform(scale);
                    part.model().render(textures, texture, scale);
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
                case "cape" -> null;
                default -> model.body;
            };
        }

        @Override
        public boolean colorsWhenDamaged() {
            return false;
        }
    }
}
