class Cases {

    void convertible(int n) {
        switch (n) {
            case 1:
            case 2:
                one(n);
                break;
            case 3:
                two(n);
                three(n);
                break;
            default:
                throw new IllegalStateException();
        }
    }

    int valued(int n) {
        return switch (n) {
            case 1: yield 2;
            default: yield 3;
        };
    }

    void fallsThrough(int n) {
        switch (n) {
            case 1:
                one(n);
            case 2:
                two(n);
                break;
        }
    }

    void declares(int n) {
        switch (n) {
            case 1:
                int shared = n;
                one(shared);
                break;
            default:
                break;
        }
    }

    void breaksInside(int n) {
        switch (n) {
            case 1:
                if (n > 0) {
                    break;
                }
                one(n);
                break;
            default:
                break;
        }
    }

}
