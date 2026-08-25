class Cases {

    void convertible(int n) {
        switch (n) {
            case 1, 2:
                one(n);
                break;
            default:
                throw new IllegalStateException();
        }
    }

    int valued(int n) {
        return switch (n) {
            case 1:
                yield 2;
            default:
                yield 3;
        };
    }

    void blocked(int n) {
        switch (n) {
            case 1 -> {
                one(n);
                two(n);
            }
            default -> { }
        }
    }

}
