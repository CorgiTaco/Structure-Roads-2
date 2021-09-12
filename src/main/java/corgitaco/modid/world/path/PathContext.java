package corgitaco.modid.world.path;

import corgitaco.modid.structure.AdditionalStructureContext;
import it.unimi.dsi.fastutil.longs.Long2ObjectArrayMap;
import it.unimi.dsi.fastutil.longs.Long2ReferenceOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArraySet;
import it.unimi.dsi.fastutil.longs.LongSet;

import java.util.List;

public class PathContext {

    public static final int MAX_CAPACITY = 4096;

    private final Long2ReferenceOpenHashMap<Long2ObjectArrayMap<AdditionalStructureContext>> contextCacheForLevel;
    private final LongSet completedRegionStructureCachesForLevel;
    private final LongSet completedLinkedRegionsForLevel;
    private final Long2ObjectArrayMap<Long2ObjectArrayMap<AdditionalStructureContext>> surroundingRegionStructureCachesForRegionForLevel;
    private final Long2ObjectArrayMap<List<PathGenerator>> pathGenerators;

    public PathContext() {
        this(new Long2ReferenceOpenHashMap<>(), new LongArraySet(), new LongArraySet(), new Long2ObjectArrayMap<>(), new Long2ObjectArrayMap<>());
    }

    public PathContext(Long2ReferenceOpenHashMap<Long2ObjectArrayMap<AdditionalStructureContext>> contextCacheForLevel, LongSet completedRegionStructureCachesForLevel, LongSet completedLinkedRegionsForLevel, Long2ObjectArrayMap<Long2ObjectArrayMap<AdditionalStructureContext>> surroundingRegionStructureCachesForRegionForLevel, Long2ObjectArrayMap<List<PathGenerator>> pathGenerators) {
        this.contextCacheForLevel = contextCacheForLevel;
        this.completedRegionStructureCachesForLevel = completedRegionStructureCachesForLevel;
        this.completedLinkedRegionsForLevel = completedLinkedRegionsForLevel;
        this.surroundingRegionStructureCachesForRegionForLevel = surroundingRegionStructureCachesForRegionForLevel;
        this.pathGenerators = pathGenerators;
    }

    public Long2ReferenceOpenHashMap<Long2ObjectArrayMap<AdditionalStructureContext>> getContextCacheForLevel() {
        if (contextCacheForLevel.size() > MAX_CAPACITY) {
            contextCacheForLevel.clear();
        }
        return contextCacheForLevel;
    }

    public LongSet getCompletedRegionStructureCachesForLevel() {
        if (completedRegionStructureCachesForLevel.size() > MAX_CAPACITY) {
            completedRegionStructureCachesForLevel.clear();
        }
        return completedRegionStructureCachesForLevel;
    }

    public LongSet getCompletedLinkedRegionsForLevel() {
        if (completedLinkedRegionsForLevel.size() > MAX_CAPACITY) {
            completedLinkedRegionsForLevel.clear();
        }
        return completedLinkedRegionsForLevel;
    }

    public Long2ObjectArrayMap<Long2ObjectArrayMap<AdditionalStructureContext>> getSurroundingRegionStructureCachesForRegionForLevel() {
        if (surroundingRegionStructureCachesForRegionForLevel.size() > MAX_CAPACITY) {
            surroundingRegionStructureCachesForRegionForLevel.clear();
        }
        return surroundingRegionStructureCachesForRegionForLevel;
    }

    public Long2ObjectArrayMap<List<PathGenerator>> getPathGenerators() {
        if (pathGenerators.size() > MAX_CAPACITY) {
            pathGenerators.clear();
        }
        return pathGenerators;
    }

    public interface Access {

        PathContext getPathContext();
    }
}