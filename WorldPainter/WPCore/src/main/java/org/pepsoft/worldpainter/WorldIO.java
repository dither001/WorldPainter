package org.pepsoft.worldpainter;

import org.pepsoft.minecraft.Direction;
import org.pepsoft.util.PluginManager;
import org.pepsoft.util.WPCustomObjectInputStream;
import org.pepsoft.worldpainter.history.HistoryEntry;
import org.pepsoft.worldpainter.layers.Layer;
import org.pepsoft.worldpainter.layers.Resources;
import org.pepsoft.worldpainter.layers.exporters.ExporterSettings;
import org.pepsoft.worldpainter.layers.exporters.ResourcesExporter;
import org.pepsoft.worldpainter.objects.AbstractObject;
import org.pepsoft.worldpainter.plugins.Plugin;
import org.pepsoft.worldpainter.plugins.WPPluginManager;
import org.pepsoft.worldpainter.vo.EventVO;

import java.io.*;
import java.util.*;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipException;

import static org.pepsoft.minecraft.Constants.*;

/**
 * Created by Pepijn Schmitz on 02-07-15.
 */
public class WorldIO {
    public WorldIO() {
    }

    public WorldIO(World2 world) {
        this.world = world;
    }

    public World2 getWorld() {
        return world;
    }

    public void setWorld(World2 world) {
        this.world = world;
    }

    /**
     * Save the world to a binary stream, such that it can later be loaded using
     * {@link #load(InputStream)}. The stream is closed before returning.
     *
     * @param out The stream to which to save the world.
     * @throws IOException If an I/O error occurred saving the world.
     */
    public void save(OutputStream out) throws IOException {
        try (ObjectOutputStream wrappedOut = new ObjectOutputStream(new GZIPOutputStream(out))) {
            Map<String, Object> metadata = new HashMap<>();
            metadata.put(World2.METADATA_KEY_WP_VERSION, Version.VERSION);
            metadata.put(World2.METADATA_KEY_WP_BUILD, Version.BUILD);
            metadata.put(World2.METADATA_KEY_TIMESTAMP, new Date());
            List<String[]> pluginArray = new ArrayList<>();
            for (Plugin plugin: WPPluginManager.getInstance().getAllPlugins()) {
                if (plugin.getName().equals("Default") || plugin.getName().equals("DefaultLayerEditorProvider")) {
                    // Don't include the system plugins
                    continue;
                }
                pluginArray.add(new String[] {plugin.getName(), plugin.getVersion()});
            }
            if (! pluginArray.isEmpty()) {
                metadata.put(World2.METADATA_KEY_PLUGINS, pluginArray.toArray(new String[pluginArray.size()][]));
            }
            wrappedOut.writeObject(metadata);
            wrappedOut.writeObject(world);
        }
    }

    /**
     * Load a world from a binary stream to which it was previously saved by
     * {@link #save(OutputStream)}, or by a previous version of WorldPainter.
     * The stream is closed before returning.
     *
     * @param in The stream from which to load the world.
     * @throws IOException If an I/O error occurred loading the world.
     * @throws UnloadableWorldException If some other error occurred loading the
     *     world. If metadata was present and could be loaded it will be stored
     *     in the exception.
     */
    public void load(InputStream in) throws IOException, UnloadableWorldException {
        Map<String, Object> metadata = null;
        world = null;
        try {
            try (WPCustomObjectInputStream wrappedIn = new WPCustomObjectInputStream(new GZIPInputStream(in), PluginManager.getPluginClassLoader(), AbstractObject.class)) {
                Object object = wrappedIn.readObject();
                if (object instanceof Map) {
                    metadata = (Map<String, Object>) object;
                    object = wrappedIn.readObject();
                }
                if (object instanceof World2) {
                    world = (World2) object;
                } else if (object instanceof World) {
                    world =  migrate(object);
                } else {
                    throw new UnloadableWorldException("Object of unexpected type " + object.getClass() + " encountered", metadata);
                }
            }
        } catch (ZipException e) {
            throw new UnloadableWorldException("ZipException while loading world", e, metadata);
        } catch (StreamCorruptedException e) {
            throw new UnloadableWorldException("StreamCorruptedException while loading world", e, metadata);
        } catch (EOFException e) {
            throw new UnloadableWorldException("EOFException while loading world", e, metadata);
        } catch (InvalidClassException e) {
            throw new UnloadableWorldException("InvalidClassException while loading world", e, metadata);
        } catch (IOException e) {
            if (e.getMessage().equals("Not in GZIP format")) {
                throw new UnloadableWorldException("Not in GZIP format", e, metadata);
            } else {
                throw e;
            }
        } catch (ClassNotFoundException e) {
            throw new UnloadableWorldException("ClassNotFoundException while loading world", e, metadata);
        } catch (IllegalArgumentException e) {
            throw new UnloadableWorldException("IllegalArgumentException while loading world", e, metadata);
        }
        if (metadata != null) {
            world.setMetadata(metadata);
        }
    }

    private World2 migrate(Object object) {
        if (object instanceof World) {
            World oldWorld = (World) object;
            World2 newWorld = new World2(oldWorld.getMinecraftSeed(), oldWorld.getTileFactory(), 128);
            newWorld.setCreateGoodiesChest(oldWorld.isCreateGoodiesChest());
            newWorld.setImportedFrom(oldWorld.getImportedFrom());
            newWorld.setName(oldWorld.getName());
            newWorld.setSpawnPoint(oldWorld.getSpawnPoint());
            Dimension dim0 = newWorld.getDimension(0);
            Generator generator = Generator.DEFAULT;
            TileFactory tileFactory = dim0.getTileFactory();
            if ((tileFactory instanceof HeightMapTileFactory)
                    && (((HeightMapTileFactory) tileFactory).getWaterHeight() < 32)
                    && (((HeightMapTileFactory) tileFactory).getBaseHeight() < 32)) {
                // Low level
                generator = Generator.FLAT;
            }
            newWorld.setGenerator(generator);
            newWorld.setAskToConvertToAnvil(true);
            newWorld.setUpIs(Direction.WEST);
            newWorld.setAskToRotate(true);
            newWorld.setAllowMerging(false);
            dim0.setEventsInhibited(true);
            try {
                dim0.setBedrockWall(oldWorld.isBedrockWall());
                dim0.setBorder((oldWorld.getBorder() != null) ? Dimension.Border.valueOf(oldWorld.getBorder().name()) : null);
                dim0.setDarkLevel(oldWorld.isDarkLevel());
                for (Map.Entry<Layer, ExporterSettings> entry: oldWorld.getAllLayerSettings().entrySet()) {
                    dim0.setLayerSettings(entry.getKey(), entry.getValue());
                }
                dim0.setMinecraftSeed(oldWorld.getMinecraftSeed());
                dim0.setPopulate(oldWorld.isPopulate());
                dim0.setContoursEnabled(false);
                Terrain subsurfaceMaterial = oldWorld.getSubsurfaceMaterial();
                ResourcesExporter.ResourcesExporterSettings resourcesSettings = (ResourcesExporter.ResourcesExporterSettings) dim0.getLayerSettings(Resources.INSTANCE);
                if (subsurfaceMaterial == Terrain.RESOURCES) {
                    dim0.setSubsurfaceMaterial(Terrain.STONE);
                } else {
                    dim0.setSubsurfaceMaterial(subsurfaceMaterial);
                    resourcesSettings.setMinimumLevel(0);
                }

                // Load legacy settings
                resourcesSettings.setChance(BLK_GOLD_ORE,         1);
                resourcesSettings.setChance(BLK_IRON_ORE,         5);
                resourcesSettings.setChance(BLK_COAL,             9);
                resourcesSettings.setChance(BLK_LAPIS_LAZULI_ORE, 1);
                resourcesSettings.setChance(BLK_DIAMOND_ORE,      1);
                resourcesSettings.setChance(BLK_REDSTONE_ORE,     6);
                resourcesSettings.setChance(BLK_WATER,            1);
                resourcesSettings.setChance(BLK_LAVA,             1);
                resourcesSettings.setChance(BLK_DIRT,             9);
                resourcesSettings.setChance(BLK_GRAVEL,           9);
                resourcesSettings.setChance(BLK_EMERALD_ORE,      0);
                resourcesSettings.setMaxLevel(BLK_GOLD_ORE,         Terrain.GOLD_LEVEL);
                resourcesSettings.setMaxLevel(BLK_IRON_ORE,         Terrain.IRON_LEVEL);
                resourcesSettings.setMaxLevel(BLK_COAL,             Terrain.COAL_LEVEL);
                resourcesSettings.setMaxLevel(BLK_LAPIS_LAZULI_ORE, Terrain.LAPIS_LAZULI_LEVEL);
                resourcesSettings.setMaxLevel(BLK_DIAMOND_ORE,      Terrain.DIAMOND_LEVEL);
                resourcesSettings.setMaxLevel(BLK_REDSTONE_ORE,     Terrain.REDSTONE_LEVEL);
                resourcesSettings.setMaxLevel(BLK_WATER,            Terrain.WATER_LEVEL);
                resourcesSettings.setMaxLevel(BLK_LAVA,             Terrain.LAVA_LEVEL);
                resourcesSettings.setMaxLevel(BLK_DIRT,             Terrain.DIRT_LEVEL);
                resourcesSettings.setMaxLevel(BLK_GRAVEL,           Terrain.GRAVEL_LEVEL);
                resourcesSettings.setMaxLevel(BLK_EMERALD_ORE,      Terrain.GOLD_LEVEL);

                oldWorld.getTiles().forEach(dim0::addTile);
            } finally {
                dim0.setEventsInhibited(false);
            }
            newWorld.setDirty(false);

            // Log event
            Configuration config = Configuration.getInstance();
            if (config != null) {
                config.logEvent(new EventVO(Constants.EVENT_KEY_ACTION_MIGRATE_WORLD).addTimestamp());
            }

            // Record origin of world in history
            newWorld.addHistoryEntry(HistoryEntry.WORLD_LEGACY_PRE_0_2);

            return newWorld;
        } else {
            throw new IllegalArgumentException("Don't know how to migrate object of type " + object.getClass());
        }
    }

    private World2 world;

    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(WorldIO.class);
}