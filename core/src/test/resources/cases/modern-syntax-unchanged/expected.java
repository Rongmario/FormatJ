package zone.rong.formatj.sample;

/// A case that must survive the formatter untouched while the engine is a passthrough.
public sealed interface Shape permits Shape.Circle, Shape.Square {

    record Circle(double radius) implements Shape {}

    record Square(double side) implements Shape {}

    static String describe(Shape shape) {
        return switch (shape) {
            case Circle(double radius) when radius > 10 -> "big circle";
            case Circle circle -> "circle";
            case Square(double side) -> "square of " + side;
        };
    }
}
