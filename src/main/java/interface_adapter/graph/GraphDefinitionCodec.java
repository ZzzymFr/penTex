package interface_adapter.graph;

import entity.graph.GraphDefinition;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/** Stable, versioned text representation for persisted graph sources and windows. */
public final class GraphDefinitionCodec {
    private GraphDefinitionCodec() { }
    public static String encode(GraphDefinition definition) {
        StringBuilder out = new StringBuilder("graph-v2;");
        out.append(range(definition.x())).append(';').append(range(definition.y())).append(';')
                .append(range(definition.z())).append(';').append(definition.camera() == null
                        ? "auto" : definition.camera().yaw() + "," + definition.camera().pitch());
        for (String function : definition.functions()) out.append(';').append(Base64.getUrlEncoder()
                .withoutPadding().encodeToString(function.getBytes(StandardCharsets.UTF_8)));
        return out.toString();
    }
    private static String range(GraphDefinition.Range range) {
        return range == null ? "auto" : range.min() + "," + range.max();
    }
    private static GraphDefinition.Range parseRange(String source) {
        if ("auto".equals(source)) return null;
        String[] parts = source.split(",", -1);
        if (parts.length != 2) throw new IllegalArgumentException("Invalid graph range");
        return new GraphDefinition.Range(Double.parseDouble(parts[0]), Double.parseDouble(parts[1]));
    }
    public static GraphDefinition decode(String value) {
        if (value == null || (!value.startsWith("graph-v1;") && !value.startsWith("graph-v2;")))
            throw new IllegalArgumentException("Unsupported graph definition");
        String[] fields = value.split(";", -1);
        boolean v2 = "graph-v2".equals(fields[0]);
        int start = v2 ? 5 : 4;
        if (fields.length <= start || fields.length > 37) throw new IllegalArgumentException("Invalid graph definition");
        List<String> formulas = new ArrayList<>();
        for (int i = start; i < fields.length; i++) formulas.add(new String(
                Base64.getUrlDecoder().decode(fields[i]), StandardCharsets.UTF_8));
        GraphDefinition.Camera camera = null;
        if (v2 && !"auto".equals(fields[4])) {
            String[] angles = fields[4].split(",", -1);
            if (angles.length != 2) throw new IllegalArgumentException("Invalid graph camera");
            camera = new GraphDefinition.Camera(Double.parseDouble(angles[0]), Double.parseDouble(angles[1]));
        }
        return new GraphDefinition(formulas, parseRange(fields[1]), parseRange(fields[2]),
                parseRange(fields[3]), camera);
    }
}
