package dev.by1337.particle.flow.connection;

import dev.by1337.yaml.decoder.YamlDecoder;

import java.util.Objects;

@FunctionalInterface
public interface ConnectionCondition {
    YamlDecoder<ConnectionCondition> DECODER = YamlDecoder.STRING.map(ConnectionCondition::parse);

    boolean test(int tick);

    default ConnectionCondition and(ConnectionCondition o) {
        return t -> test(t) && o.test(t);
    }

    static ConnectionCondition parse(String input) {
        String expression = Objects.requireNonNull(input, "input").replaceAll("\\s+", "");
        if (expression.isEmpty()) return tick -> true;

        ConnectionCondition result = tick -> true;
        for (String part : expression.split("&", -1)) {
            result = result.and(parsePart(part));
        }
        return result;
    }

    private static ConnectionCondition parsePart(String part) {
        String operator;
        if (part.startsWith("<=") || part.startsWith(">=")) operator = part.substring(0, 2);
        else if (part.startsWith("<") || part.startsWith(">") || part.startsWith("%")) operator = part.substring(0, 1);
        else throw new IllegalArgumentException("Invalid connection condition: " + part);

        String value = part.substring(operator.length());
        final int number;
        try {
            number = Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid connection condition: " + part, e);
        }

        return switch (operator) {
            case "<" -> tick -> tick < number;
            case ">" -> tick -> tick > number;
            case "<=" -> tick -> tick <= number;
            case ">=" -> tick -> tick >= number;
            case "%" -> {
                if (number <= 0) throw new IllegalArgumentException("Period must be positive: " + part);
                yield tick -> tick % number == 0;
            }
            default -> throw new IllegalArgumentException("Invalid connection condition: " + part);
        };
    }
}
