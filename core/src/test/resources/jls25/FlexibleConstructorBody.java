class FlexibleConstructorBody {

    final int value;

    FlexibleConstructorBody(int value) {
        if (value < 0) {
            throw new IllegalArgumentException("value must not be negative: " + value);
        }
        super();
        this.value = value;
    }

    FlexibleConstructorBody(int value, int fallback) {
        int chosen = value >= 0 ? value : fallback;
        this(chosen);
    }

    static class Bound extends FlexibleConstructorBody {

        Bound(int value, int min, int max) {
            int clamped = Math.max(min, Math.min(max, value));
            super(clamped);
        }

    }

    public static void main(String[] args) {
        System.out.println(new FlexibleConstructorBody(-1, 5).value);
        System.out.println(new Bound(42, 0, 10).value);
    }

}
