package dev.by1337.fparticle;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import net.minecraft.core.DefaultedRegistry;
import net.minecraft.core.IdMapper;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.ProtocolInfo;
import net.minecraft.network.protocol.PacketType;
import net.minecraft.network.protocol.game.GamePacketTypes;
import net.minecraft.network.protocol.game.GameProtocols;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Writer;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;

public class Main extends JavaPlugin {
    public static final Map<Integer, String[]> PROTOCOL2ID = Map.ofEntries(
            Map.entry(754, new String[]{"1.16.5"}),
            Map.entry(755, new String[]{"1.17"}),
            Map.entry(756, new String[]{"1.17.1"}),
            Map.entry(757, new String[]{"1.18", "1.18.1"}),
            Map.entry(758, new String[]{"1.18.2"}),
            Map.entry(759, new String[]{"1.19"}),
            Map.entry(760, new String[]{"1.19.1", "1.19.2"}),
            Map.entry(761, new String[]{"1.19.3"}),
            Map.entry(762, new String[]{"1.19.4"}),
            Map.entry(763, new String[]{"1.20", "1.20.1"}),
            Map.entry(764, new String[]{"1.20.2"}),
            Map.entry(765, new String[]{"1.20.3", "1.20.4"}),
            Map.entry(766, new String[]{"1.20.5", "1.20.6"}),
            Map.entry(767, new String[]{"1.21", "1.21.1"}),
            Map.entry(768, new String[]{"1.21.2", "1.21.3"}),
            Map.entry(769, new String[]{"1.21.4"}),
            Map.entry(770, new String[]{"1.21.5"}),
            Map.entry(771, new String[]{"1.21.6"}),
            Map.entry(772, new String[]{"1.21.7", "1.21.8"}),
            Map.entry(773, new String[]{"1.21.9", "1.21.10"}),
            Map.entry(774, new String[]{"1.21.11"})
    );


    private final Gson gson = new Gson();
    private Path home;

    @Override
    public void onLoad() {
        super.onLoad();
    }

    @Override
    public void onEnable() {
        home = getDataFolder().toPath();
    }

    @Override
    public void onDisable() {
        super.onDisable();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        getDataFolder().mkdirs();
        updatePackets();
        updateDefaultedRegistry("BlockType", "blocks.json", toMapBlockStates());
        updateDefaultedRegistry("ItemType", "items.json", toMap(BuiltInRegistries.ITEM));
        updateParticles();
        return true;
    }

    private void updateParticles() {
        try (InputStreamReader in = new InputStreamReader(getResource("mappings/particles.json"), StandardCharsets.UTF_8)) {
            Map<String, Map<Integer, ParticleEntry>> mappings = gson.fromJson(in, new TypeToken<Map<String, Map<Integer, ParticleEntry>>>() {
            }.getType());
            Integer protocolVersion = Version.VERSION.protocolVersion();
            for (Field field : ParticleTypes.class.getFields()) {
                if (!Modifier.isStatic(field.getModifiers())) continue;
                field.setAccessible(true);
                Object val = field.get(null);
                if (val instanceof ParticleType<?> particleType) {
                    int intId = BuiltInRegistries.PARTICLE_TYPE.getId(particleType);
                    String key = BuiltInRegistries.PARTICLE_TYPE.getKey(particleType).toString();
                    var type = field.getType();
                    if (type == SimpleParticleType.class) {
                        mappings.computeIfAbsent(key, k -> new HashMap<>()).put(protocolVersion, new ParticleEntry(intId, "~"));
                    } else {
                        mappings.computeIfAbsent(key, k -> new HashMap<>()).put(protocolVersion,
                                new ParticleEntry(intId, getSingleGeneric(field).getSimpleName()));
                    }

                }
            }
            try (Writer out = Files.newBufferedWriter(
                    home.resolve("particles.json"),
                    StandardCharsets.UTF_8
            )) {
                gson.toJson(mappings, out);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    static Class<?> getSingleGeneric(Field field) {
        Type type = field.getGenericType();
        if (!(type instanceof ParameterizedType pt)) {
            return null;
        }

        Type arg = pt.getActualTypeArguments()[0];
        return arg instanceof Class<?> c ? c : null;
    }

    private static class ParticleEntry {
        private int protocol_id;
        private String extra;

        public ParticleEntry(int protocol_id, String extra) {
            this.protocol_id = protocol_id;
            this.extra = extra;
        }

        public ParticleEntry() {
        }
    }

    private <T> void updateDefaultedRegistry(String name, String file, Map<String, Integer> key2Id) {
        try (InputStreamReader in = new InputStreamReader(getResource("mappings/" + file), StandardCharsets.UTF_8)) {

            Map<String, Map<Integer, Integer>> map = new HashMap<>();
            JsonObject o = gson.fromJson(in, JsonObject.class);
            for (Map.Entry<String, JsonElement> item : o.entrySet()) {
                Map<Integer, Integer> protocol2id = new HashMap<>();
                for (Map.Entry<String, JsonElement> version : item.getValue().getAsJsonObject().entrySet()) {
                    protocol2id.put(Integer.parseInt(version.getKey()), version.getValue().getAsInt());
                }
                map.put(item.getKey(), protocol2id);
            }

            Integer protocolVersion = Version.VERSION.protocolVersion();
            for (String s : map.keySet()) {
                Integer actualId = key2Id.remove(s);
                Map<Integer, Integer> mappings = map.get(s);
                mappings.entrySet().removeIf(entry -> entry.getValue() == -1); //todo удалю просто хочу почистить так как в текущей версии там -1
                if (actualId != null) {
                    mappings.put(protocolVersion, actualId);
                }
            }
            key2Id.forEach((key, id) -> {
                Map<Integer, Integer> mappings = new HashMap<>();
                mappings.put(protocolVersion, id);
                map.put(key, mappings);
            });
            try (Writer out = Files.newBufferedWriter(
                    home.resolve(file),
                    StandardCharsets.UTF_8
            )) {
                gson.toJson(map, out);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

            StringBuilder generatedEnum = new StringBuilder();
            generatedEnum.append("""
                    package dev.by1337.fparticle;
                    
                    import dev.by1337.fparticle.via.Mappings;
                    import org.bukkit.Keyed;
                    import org.bukkit.NamespacedKey;
                    import org.bukkit.Registry;
                    import org.jetbrains.annotations.NotNull;
                    import org.jetbrains.annotations.Nullable;
                    
                    import java.util.Collections;
                    import java.util.HashMap;
                    import java.util.Iterator;
                    import java.util.Map;
                    
                    public enum {NAME} implements Keyed {
                    
                    """.replace("{NAME}", name));
            generateEnum(map, generatedEnum);
            generatedEnum.append("""
                         ;
                         public static final int SIZE = values().length;
                         private static final Map<String, {NAME}> BY_ID;
                         private static final Map<NamespacedKey, {NAME}> BY_NAMESPACED_KEY;
                         public static final Registry<{NAME}> REGISTRY = new Registry<>() {
                             @Override
                             public @NotNull Iterator<{NAME}> iterator() {
                                 return BY_ID.values().iterator();
                             }
                    \s
                             @Override
                             public @Nullable {NAME} get(@NotNull NamespacedKey namespacedKey) {
                                 return BY_NAMESPACED_KEY.get(namespacedKey);
                             }
                         };
                         private final String id;
                         private final NamespacedKey key;
                    \s
                         {NAME}(String id) {
                             this.id = id;
                             key = NamespacedKey.fromString(id);
                         }
                    \s
                         public String id() {
                             return id;
                         }
                    \s
                         public static {NAME} getById(String id) {
                             return BY_ID.get(id);
                         }
                    \s
                         public int getProtocolId(int version) {
                             return Mappings.getBlockId(this, version); //todo getBlockId or getItemId
                         }
                        \s
                         public boolean isAir(){
                             return this == AIR; //todo for blocks return this == AIR || this == CAVE_AIR || this == VOID_AIR;
                         }
                    \s
                         static {
                             Map<String, {NAME}> by_id = new HashMap<>();
                             BY_NAMESPACED_KEY = new HashMap<>();
                             for ({NAME} value : values()) {
                                 by_id.put(value.id, value);
                                 BY_NAMESPACED_KEY.put(value.key, value);
                             }
                             BY_ID = Collections.unmodifiableMap(by_id);
                         }
                    \s
                         @Override
                         public @NotNull NamespacedKey getKey() {
                             return key;
                         }
                     }
                    \s
                    \s""".replace("{NAME}", name));
            try (Writer out = Files.newBufferedWriter(
                    home.resolve(name.concat(".java")),
                    StandardCharsets.UTF_8
            )) {
                out.write(generatedEnum.toString());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

        } catch (IOException e) {
            throw new RuntimeException(e);
        }


    }

    private void updateBlocks() {
    }

    private <T> Map<String, Integer> toMap(DefaultedRegistry<@NotNull T> registry) {
        Map<String, Integer> map = new HashMap<>();
        for (T t : registry) {
            map.put(
                    registry.getKey(t).toString(),
                    registry.getId(t)
            );
        }
        return map;
    }

    private Map<String, Integer> toMapBlockStates() {
        Map<String, Integer> map = new HashMap<>();
        for (BlockState t : Block.BLOCK_STATE_REGISTRY) {
            map.put(
                    BuiltInRegistries.BLOCK.getKey(t.getBlock()).toString(),
                    Block.BLOCK_STATE_REGISTRY.getId(t)
            );
        }
        return map;
    }

    private void updatePackets() {
        Map<Integer, Integer> packets = gson.fromJson(
                new InputStreamReader(getResource("mappings/packets.json"), StandardCharsets.UTF_8),
                new TypeToken<Map<Integer, Integer>>() {
                }.getType()
        );
        Map<PacketType<?>, Integer> registry = toMap(GameProtocols.CLIENTBOUND_TEMPLATE);
        packets.put(Version.VERSION.protocolVersion(), Objects.requireNonNull(registry.get(GamePacketTypes.CLIENTBOUND_LEVEL_PARTICLES), "failed to get CLIENTBOUND_LEVEL_PARTICLES pakcet id!"));

        try (Writer out = Files.newBufferedWriter(home.resolve("packets.json"), StandardCharsets.UTF_8)) {
            gson.toJson(packets, out);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static Map<PacketType<?>, Integer> toMap(ProtocolInfo.DetailsProvider protocol) {
        Map<PacketType<?>, Integer> result = new IdentityHashMap<>();
        protocol.details().listPackets(result::put);
        return result;
    }

    private static void generateEnum(Map<String, Map<Integer, Integer>> map, StringBuilder out) {

        for (String item : map.keySet().stream().sorted().toList()) {
            Map<Integer, Integer> data = map.get(item);
            String name = item.substring(item.lastIndexOf(":") + 1).toUpperCase();
            int max = Integer.MIN_VALUE;
            int min = Integer.MAX_VALUE;
            for (int protocol : data.keySet().stream().sorted().toList()) {
                int id = data.get(protocol);
                if (id == -1) continue;
                max = Math.max(max, protocol);
                min = Math.min(min, protocol);
            }

            if (max != Version.VERSION.protocolVersion()) {
                String[] lastVersion = PROTOCOL2ID.get(max);
                out.append(String.format("\t//since %s(%s) - %s(%s)\n",
                        min,
                        PROTOCOL2ID.get(min)[0],
                        max,
                        lastVersion[lastVersion.length - 1]
                ));
                out.append("\t@Deprecated\n");
            } else {
                out.append(String.format("\t//since %s(%s)\n", min, PROTOCOL2ID.get(min)[0]));
            }
            out.append(String.format("\t%s(\"%s\"),\n", name, item));
        }
    }
}
