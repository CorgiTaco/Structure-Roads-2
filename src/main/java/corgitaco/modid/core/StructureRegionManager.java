package corgitaco.modid.core;

import com.mojang.datafixers.util.Pair;
import corgitaco.modid.mixin.access.ChunkManagerAccess;
import corgitaco.modid.mixin.access.StructureAccess;
import corgitaco.modid.structure.AdditionalStructureContext;
import corgitaco.modid.util.DataForChunk;
import corgitaco.modid.world.path.IPathGenerator;
import corgitaco.modid.world.path.PathfindingPathGenerator;
import it.unimi.dsi.fastutil.longs.Long2ReferenceOpenHashMap;
import net.daporkchop.lib.primitive.map.concurrent.LongObjConcurrentHashMap;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.util.SharedSeedRandom;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.SectionPos;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeManager;
import net.minecraft.world.biome.provider.BiomeProvider;
import net.minecraft.world.gen.feature.structure.Structure;
import net.minecraft.world.gen.settings.StructureSeparationSettings;
import net.minecraft.world.server.ServerWorld;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CompletionService;

public class StructureRegionManager {
    private static final int HARBOUR_SEARCH_DISTANCE = 12;
    public static final String[] NAMES = new String[]{
            "Perthlochry",
            "Bournemouth",
            "Wimborne",
            "Bredon",
            "Ballachulish",
            "Sudbury",
            "Emall",
            "Bellmare",
            "Garrigill",
            "Polperro",
            "Lakeshore",
            "Wolfden",
            "Aberuthven",
            "Warrington",
            "Northwich",
            "Ascot",
            "Coalfell",
            "Calchester",
            "Stanmore",
            "Clacton",
            "Wanborne",
            "Alnwick",
            "Rochdale",
            "Gormsey",
            "Favorsham",
            "Clare View Point",
            "Aysgarth",
            "Wimborne",
            "Tarrin",
            "Arkmunster",
            "Mirefield",
            "Banrockburn",
            "Acrine",
            "Oldham",
            "Glenarm",
            "Pathstow",
            "Ballachulish",
            "Dumbarton",
            "Carleone",
            "Llanybydder",
            "Norwich",
            "Banrockburn",
            "Auchendale",
            "Arkaley",
            "Aeberuthey",
            "Peltragow",
            "Clarcton",
            "Garigill",
            "Nantwich",
            "Zalfari",
            "Portsmouth",
            "Transmere",
            "Blencathra",
            "Bradford",
            "Thorpeness",
            "Swordbreak",
            "Thorpeness",
            "Aeston",
            "Azmarin",
            "Haran"
    };

    private final Path savePath;
    private final ServerWorld world;
    private final Long2ReferenceOpenHashMap<StructureRegion> structureRegions;
    LongObjConcurrentHashMap<LongObjConcurrentHashMap<DataForChunk>> dataForLocation = new LongObjConcurrentHashMap<>();

    public StructureRegionManager(ServerWorld world) {
        this.savePath = ((ChunkManagerAccess) world.getChunkSource().chunkMap).getStorageFolder().toPath().resolve("structures");
        this.world = world;
        try {
            Files.createDirectories(savePath);
        } catch (IOException e) {
            e.printStackTrace();
        }

        this.structureRegions = new Long2ReferenceOpenHashMap<>();
    }

    @Nullable
    public CompoundNBT getRegionNbt(long regionPos) {
        int x = getX(regionPos);
        int z = getZ(regionPos);

        File file = savePath.resolve(String.format("%s,%s.2dr", x, z)).toFile();

        try {
            return file.exists() ? CompressedStreamTools.read(file) : null;
        } catch (IOException e) {
            return null;
        }
    }

    private void saveRegion(StructureRegion region) {
        long regionPos = region.getPos();
        int x = getX(regionPos);
        int z = getZ(regionPos);

        try {
            CompressedStreamTools.write(region.saveTag(), savePath.resolve(String.format("%s,%s.2dr", x, z)).toFile());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public StructureRegion getStructureRegion(long regionKey) {
        return this.structureRegions.computeIfAbsent(regionKey, (key) -> {
            CompoundNBT disk = getRegionNbt(regionKey);
            return disk != null ? new StructureRegion(disk, this.world) : new StructureRegion(key, this.world);
        });
    }

    public void unloadStructureRegion(long regionKey) {
        saveRegion(this.structureRegions.remove(regionKey));
    }

    public void saveAllStructureRegions() {
        for (StructureRegion region : this.structureRegions.values()) {
            saveRegion(region);
        }
    }

    public void collectRegionStructures(long seed, BiomeProvider biomeSource, Structure<?> structure, StructureSeparationSettings structureSeparationSettings, long regionKey, List<ChunkPos> harbours) {
        long startTime = System.currentTimeMillis();

        int regionX = getX(regionKey);
        int regionZ = getZ(regionKey);

        int spacing = structureSeparationSettings.spacing();

        int activeMinChunkX = regionToChunk(regionX);
        int activeMinChunkZ = regionToChunk(regionZ);

        int activeMaxChunkX = regionToMaxChunk(regionX);
        int activeMaxChunkZ = regionToMaxChunk(regionZ);

        int activeMinGridX = Math.floorDiv(activeMinChunkX, spacing);
        int activeMinGridZ = Math.floorDiv(activeMinChunkZ, spacing);

        int activeMaxGridX = Math.floorDiv(activeMaxChunkX, spacing);
        int activeMaxGridZ = Math.floorDiv(activeMaxChunkZ, spacing);

        collectRegionStructures(seed, biomeSource, structure, harbours, structureSeparationSettings, activeMinGridX, activeMinGridZ, activeMaxGridX, activeMaxGridZ);

        System.out.println("Generated links and paths for region (" + regionX + ", " + regionZ + ") in " + (System.currentTimeMillis() - startTime) + " ms");
    }


    private void collectRegionStructures(long seed, BiomeProvider biomeSource, Structure<?> structure, List<ChunkPos> harbours, StructureSeparationSettings structureSeparationSettings, int activeMinGridX, int activeMinGridZ, int activeMaxGridX, int activeMaxGridZ) {
        Random random = new Random(seed);
        int neighborRange = 1;

        for (int structureGridX = activeMinGridX; structureGridX <= activeMaxGridX; structureGridX++) {
            for (int structureGridZ = activeMinGridZ; structureGridZ <= activeMaxGridZ; structureGridZ++) {
                long structurePosFromGrid = getStructurePosFromGrid(structureGridX, structureGridZ, seed, biomeSource, structure, structureSeparationSettings);

                long regionKey = chunkToRegionKey(structurePosFromGrid);

                StructureRegion structureRegion = getStructureRegion(regionKey);

                if (structurePosFromGrid == Long.MIN_VALUE) {
                    continue;
                }

                AdditionalStructureContext additionalStructureContext = new AdditionalStructureContext(NAMES[random.nextInt(NAMES.length - 1)]);
                additionalStructureContext.setHarbourPos(getHarbourPos(structurePosFromGrid));
                if(additionalStructureContext.getHarbourPos() != null){
                    harbours.add(additionalStructureContext.getHarbourPos());
                }

                Long2ReferenceOpenHashMap<AdditionalStructureContext> structureData = structureRegion.structureData(structure).getLocationContextData(false);
                structureData.putIfAbsent(structurePosFromGrid, additionalStructureContext);
            }
        }
    }

    /**
     * Submits the generation of paths to the {@code CompletionService}
     * @param world The {@code ServerWorld} the paths should generate in
     * @param seed The world's seed
     * @param biomeSource The world's {@code BiomeSource}
     * @param structure The structures to link
     * @param structureSeparationSettings
     * @param dataForLocation Noise cache
     * @param name
     * @param neighborRange
     * @param structureGridX
     * @param structureGridZ
     * @param structurePosFromGrid
     * @param additionalStructureContext
     * @param completionService The service to submit creation to
     * @return The amount of path generators submitted to the {@code CompletionService}
     */
    public int linkNeighbors(ServerWorld world, long seed, BiomeProvider biomeSource, Structure<?> structure, StructureSeparationSettings structureSeparationSettings, LongObjConcurrentHashMap<LongObjConcurrentHashMap<DataForChunk>> dataForLocation, String name, int neighborRange, int structureGridX, int structureGridZ, long structurePosFromGrid, AdditionalStructureContext additionalStructureContext, CompletionService<IPathGenerator<?>> completionService) {
        int pathsSubmitted = 0;
        Random random = new Random(structurePosFromGrid);
        if(additionalStructureContext.getHarbourPos() != null){
            completionService.submit(() -> {
                PathfindingPathGenerator pathGenerator = new PathfindingPathGenerator(
                        world,
                        new IPathGenerator.Point<>(structure, getPosFromChunk(structurePosFromGrid)),
                        new IPathGenerator.Point<>(structure, getPosFromChunk(additionalStructureContext.getHarbourPos().toLong())),
                        dataForLocation);
                if(pathGenerator.dispose()){
                    long currentRegionKey = chunkToRegionKey(structurePosFromGrid);
                    StructureData structureData = structureRegions.computeIfAbsent(currentRegionKey, (key) -> new StructureRegion(key, world)).structureData(structure);
                    synchronized (structureData.getHarbours()){
                        structureData.getHarbours().remove(additionalStructureContext.getHarbourPos());
                    }
                    additionalStructureContext.setHarbourPos(null);
                }
                return pathGenerator;
            });
            pathsSubmitted++;
        }

        for (int neighborStructureGridX = -neighborRange; neighborStructureGridX < neighborRange; neighborStructureGridX++) {
            for (int neighborStructureGridZ = -neighborRange; neighborStructureGridZ < neighborRange; neighborStructureGridZ++) {
                if (neighborStructureGridX == 0 && neighborStructureGridZ == 0) {
                    continue;
                }
                long neighborStructurePosFromGrid = getStructurePosFromGrid(structureGridX + neighborStructureGridX, structureGridZ + neighborStructureGridZ, seed, biomeSource, structure, structureSeparationSettings);


                if (neighborStructurePosFromGrid == Long.MIN_VALUE) {
                    continue;
                }

                long neighborStructureRegionKey = chunkToRegionKey(neighborStructurePosFromGrid);

                //AdditionalStructureContext neighborAdditionalStructureContext = new AdditionalStructureContext(name);
                Long2ReferenceOpenHashMap<AdditionalStructureContext> neighborStructureData = this.structureRegions.computeIfAbsent(neighborStructureRegionKey, (key) -> new StructureRegion(key, world)).structureData(structure).getLocationContextData(true);

                AdditionalStructureContext neighborAdditionalStructureContext = neighborStructureData.putIfAbsent(neighborStructurePosFromGrid, additionalStructureContext);

                additionalStructureContext.getConnections().add(neighborStructurePosFromGrid);
                neighborAdditionalStructureContext.getConnections().add(structurePosFromGrid);


                SharedSeedRandom structureRandom = new SharedSeedRandom();
                structureRandom.setLargeFeatureWithSalt(seed, ChunkPos.getX(structurePosFromGrid) + ChunkPos.getX(neighborStructurePosFromGrid), ChunkPos.getZ(structurePosFromGrid) + ChunkPos.getZ(neighborStructurePosFromGrid), structureSeparationSettings.salt());

                completionService.submit(() -> new PathfindingPathGenerator(world, new IPathGenerator.Point<>(structure, getPosFromChunk(structurePosFromGrid)), new IPathGenerator.Point<>(structure, getPosFromChunk(neighborStructurePosFromGrid)), dataForLocation));
                pathsSubmitted++;
            }
        }

        return pathsSubmitted;
    }

    public void addPath(IPathGenerator pathGenerator, Structure<?> structure){
        if (!pathGenerator.dispose()) {
            long saveRegionKey = pathGenerator.saveRegion();
            StructureData.PathKey pathGeneratorKey = new StructureData.PathKey(getChunkFromPos(pathGenerator.getStart().getPos()), getChunkFromPos(pathGenerator.getEnd().getPos()));
            StructureData saveRegionStructureData = this.structureRegions.computeIfAbsent(saveRegionKey, (key) -> new StructureRegion(key, world)).structureData(structure);
            saveRegionStructureData.getPathGenerators(false).put(pathGeneratorKey, pathGenerator);

            for (long aLong : pathGenerator.getNodesByRegion().keySet()) {
                if (saveRegionKey == aLong) {
                    continue;
                }
                StructureRegion neighborRegion = this.structureRegions.computeIfAbsent(aLong, (key) -> new StructureRegion(key, world));
                StructureData structureData = neighborRegion.structureData(structure);
                structureData.getPathGeneratorReferences().computeIfAbsent(saveRegionKey, (key) -> new HashSet<>()).add(pathGeneratorKey);
//                structureData.getPathGeneratorNeighbors().putIfAbsent(pathGeneratorKey, pathGenerator);
            }
        }
    }
    private @Nullable ChunkPos getHarbourPos(long position) {
        Random random = new Random(position);

        if(true) {
            final int chunkX = ChunkPos.getX(position);
            final int chunkZ = ChunkPos.getZ(position);

            for(int i = 0; i < 5; i++){
                final float angle = (float) (random.nextFloat() * 2 * Math.PI);
                final int dx = (int) (HARBOUR_SEARCH_DISTANCE * Math.cos(angle));
                final int dz = (int) (HARBOUR_SEARCH_DISTANCE * Math.sin(angle));

                final int maxDelta = Math.max(Math.abs(dx), Math.abs(dz));

                int initialTestX = chunkX + dx;
                int initialTestZ = chunkZ + dz;

                Biome biome = world.getUncachedNoiseBiome((initialTestX << 2) + 2, 60, (initialTestZ << 2) + 2);
                if(biome.getBiomeCategory().equals(Biome.Category.OCEAN)){
                    for(int testDistance = maxDelta - 1; testDistance > 0; testDistance--){
                        float t = testDistance / (float) maxDelta;

                        if(!world.getNoiseBiome(((int) (chunkX + dx * t) << 2) + 2, 60, ((int) (chunkZ + dz * t) << 2) + 2).getBiomeCategory().equals(Biome.Category.OCEAN)){
                            ChunkPos pos = new ChunkPos((int) (chunkX + dx * t), (int) (chunkZ + dz * t));
                            System.out.println("Village at " + chunkToBlock(new ChunkPos(position)) + " has a harbour at " + chunkToBlock(pos));
                            System.out.println("Detected ocean at " + chunkToBlock(initialTestX, initialTestZ));
                            return pos;
                        }
                    }
                }
            }
        }

        return null;
    }


    public static Pair<Integer, Integer> getGridXZ(long structureChunkPos, int spacing) {
        int chunkX = ChunkPos.getX(structureChunkPos);
        int chunkZ = ChunkPos.getZ(structureChunkPos);
        int activeMaxGridX = Math.floorDiv(chunkX, spacing);
        int activeMaxGridZ = Math.floorDiv(chunkZ, spacing);


        return Pair.of(activeMaxGridX, activeMaxGridZ);
    }

    public static long getStructurePosFromGrid(int structureGridX, int structureGridZ, long worldSeed, BiomeProvider biomeSource, Structure<?> structure, StructureSeparationSettings structureSeparationSettings) {
        long structureChunkPos = getStructureChunkPos(structure, worldSeed, structureSeparationSettings.salt(), new SharedSeedRandom(), structureSeparationSettings.spacing(), structureSeparationSettings.separation(), structureGridX, structureGridZ);
        int structureChunkPosX = ChunkPos.getX(structureChunkPos);
        int structureChunkPosZ = ChunkPos.getZ(structureChunkPos);

        // Verify this biome is a valid biome for this structure
        if (!sampleAndTestChunkBiomesForStructure(structureChunkPosX, structureChunkPosZ, biomeSource, structure)) {
            return Long.MIN_VALUE;
        }
        long chunkPos = structure.getPotentialFeatureChunk(structureSeparationSettings, worldSeed, new SharedSeedRandom(), structureChunkPosX, structureChunkPosZ).toLong();

        return chunkPos == structureChunkPos ? structureChunkPos : Long.MIN_VALUE;
    }


    public static boolean sampleAndTestChunkBiomesForStructure(int chunkX, int chunkZ, BiomeManager.IBiomeReader biomeReader, Structure<?> structure) {
        return biomeReader.getNoiseBiome((chunkX << 2) + 2, 0, (chunkZ << 2) + 2).getGenerationSettings().isValidStart(structure);
    }

    private static long getStructureChunkPos(Structure<?> structure, long worldSeed, int structureSalt, SharedSeedRandom seedRandom, int spacing, int separation, int floorDivX, int floorDivZ) {
        int x;
        int z;
        seedRandom.setLargeFeatureWithSalt(worldSeed, floorDivX, floorDivZ, structureSalt);

        if (((StructureAccess) structure).invokeLinearSeparation()) {
            x = seedRandom.nextInt(spacing - separation);
            z = seedRandom.nextInt(spacing - separation);
        } else {
            x = (seedRandom.nextInt(spacing - separation) + seedRandom.nextInt(spacing - separation)) / 2;
            z = (seedRandom.nextInt(spacing - separation) + seedRandom.nextInt(spacing - separation)) / 2;
        }
        return ChunkPos.asLong(floorDivX * spacing + x, floorDivZ * spacing + z);
    }


    public static int getX(long regionPos) {
        return (int) (regionPos & 4294967295L);
    }

    public static int getZ(long regionPos) {
        return (int) (regionPos >>> 32 & 4294967295L);
    }

    // Region size is 256 x 256 chunks or 4096 x 4096 blocks
    public static long regionKey(int regionX, int regionZ) {
        return (long) regionX & 4294967295L | ((long) regionZ & 4294967295L) << 32;
    }

    public static int chunkToRegion(int coord) {
        return coord >> 8;
    }

    public static int blockToRegion(int coord) {
        return coord >> 12;
    }

    public static int regionToBlock(int coord) {
        return coord << 12;
    }

    public static int regionToChunk(int coord) {
        return coord << 8;
    }

    public static int regionToMaxChunk(int coord) {
        return regionToChunk(coord + 1) - 1;
    }

    public static long chunkToRegionKey(long chunk) {
        return regionKey(chunkToRegion(ChunkPos.getX(chunk)), chunkToRegion(ChunkPos.getZ(chunk)));
    }

    public static BlockPos getPosFromChunk(long chunk) {
        int chunkX = ChunkPos.getX(chunk);
        int chunkZ = ChunkPos.getZ(chunk);
        return new BlockPos(SectionPos.sectionToBlockCoord(chunkX), 0, SectionPos.sectionToBlockCoord(chunkZ));
    }

    public static long getChunkFromPos(BlockPos pos) {
        int chunkX = ChunkPos.getX(SectionPos.blockToSectionCoord(pos.getX()));
        int chunkZ = ChunkPos.getZ(SectionPos.blockToSectionCoord(pos.getZ()));
        return ChunkPos.asLong(chunkX, chunkZ);
    }

    private static BlockPos chunkToBlock(ChunkPos pos){
        return new BlockPos(pos.x * 16 + 8, 0, pos.z * 16 + 8);
    }

    private static BlockPos chunkToBlock(int x, int z){
        return new BlockPos(x * 16 + 8, 0, z * 16 + 8);
    }

    public interface Access {

        StructureRegionManager getStructureRegionManager();
    }
}
