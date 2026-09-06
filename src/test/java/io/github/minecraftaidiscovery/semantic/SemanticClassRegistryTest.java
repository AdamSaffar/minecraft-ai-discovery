package io.github.minecraftaidiscovery.semantic;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import java.util.HashSet;
import java.util.Set;
import org.junit.Test;

public class SemanticClassRegistryTest {
    @Test
    public void assignsStableClassIdsToExactRegistryIds() {
        assertEquals(1, SemanticClassRegistry.classIdForRegistryId("minecraft:stone"));
        assertEquals(2, SemanticClassRegistry.classIdForRegistryId("minecraft:oak_log"));
        assertEquals(0, SemanticClassRegistry.classIdForRegistryId("minecraft:birch_log"));
    }

    @Test
    public void roundTripsClassMarkersWithoutUsingUnclassifiedAlpha() {
        Set<Integer> classIds = new HashSet<>();
        Set<Integer> markerAlphas = new HashSet<>();
        for (SemanticClass semanticClass : SemanticClassRegistry.getClasses()) {
            assertNotEquals(SemanticClassRegistry.UNCLASSIFIED_MARKER_ALPHA, semanticClass.getMarkerAlpha());
            assertTrue(classIds.add(semanticClass.getClassId()));
            assertTrue(markerAlphas.add(semanticClass.getMarkerAlpha()));
            assertEquals(
                semanticClass.getClassId(),
                SemanticClassRegistry.classIdForMarkerAlpha(semanticClass.getMarkerAlpha())
            );
        }
        assertEquals(0, SemanticClassRegistry.classIdForMarkerAlpha(255));
        assertEquals(0, SemanticClassRegistry.classIdForMarkerAlpha(0));
    }

    @Test
    public void restoresNestedVertexEncodingState() {
        assertEquals(SemanticVertexEncoding.INACTIVE, SemanticVertexEncoding.getActiveMarkerAlpha());
        int inactive = SemanticVertexEncoding.beginBlock("minecraft:stone");
        try {
            int stoneMarker = SemanticVertexEncoding.getActiveMarkerAlpha();
            int previous = SemanticVertexEncoding.beginBlock("minecraft:oak_log");

            assertEquals(SemanticVertexEncoding.INACTIVE, inactive);
            assertEquals(stoneMarker, previous);
            assertEquals(
                SemanticClassRegistry.markerAlphaForRegistryId("minecraft:oak_log"),
                SemanticVertexEncoding.getActiveMarkerAlpha()
            );

            SemanticVertexEncoding.restore(previous);
            assertEquals(stoneMarker, SemanticVertexEncoding.getActiveMarkerAlpha());
        } finally {
            SemanticVertexEncoding.restore(inactive);
        }
        assertEquals(SemanticVertexEncoding.INACTIVE, SemanticVertexEncoding.getActiveMarkerAlpha());
    }
}
