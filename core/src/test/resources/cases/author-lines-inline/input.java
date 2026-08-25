class Inline {

    void run(int n) {
        if (n > 0) { report(n); } else { report(-n); }
        for (int i = 0; i < n; i++) { report(i); }
        Runnable r = () -> { report(n); };
        while (n > 0) {
            n--;
        }
    }

    int compact() { return 1; }

    static class Small { int x; }

}
