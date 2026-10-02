package dev.by1337.particle.flow.connection;

import dev.by1337.particle.flow.emit.EmitterGraph;
import dev.by1337.yaml.decoder.YamlDecoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class Connections {
    public static final YamlDecoder<Connections> DECODER = YamlDecoder.mapOf(
            YamlDecoder.STRING,
            ConnectionCondition.DECODER
    ).map(Connections::new);
    private static final Logger log = LoggerFactory.getLogger(Connections.class);

    private final Map<String, ConnectionCondition> connections;

    public Connections(Map<String, ConnectionCondition> connections) {
        this.connections = connections;
    }

    public Map<String, ConnectionCondition> connections() {
        return connections;
    }
    public ParticleEmitterConditionPair[] toPairArray(EmitterGraph graph){
        List<ParticleEmitterConditionPair> list = new ArrayList<>(connections.size());
        connections.forEach((name, c) -> {
            var v = graph.find(name);
            if (v == null) log.info("unknown connection {}", name);
            else list.add(new ParticleEmitterConditionPair(v, c));
        });
        return list.toArray(new ParticleEmitterConditionPair[0]);
    }
}
