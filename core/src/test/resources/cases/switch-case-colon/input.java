class Cases {

    void convertible(int n) {
        switch (n) {
            case 1, 2 -> one(n);
            default -> throw new IllegalStateException();
        }
    }

    int valued(int n) {
        return switch (n) {
            case 1 -> 2;
            default -> 3;
        };
    }

    void blocked(int n) {
        switch (n) {
            case 1 -> {
                one(n);
                two(n);
            }
            default -> {
            }
        }
    }

}
