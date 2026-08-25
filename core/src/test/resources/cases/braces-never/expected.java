class Never {

    void run(int n) {
        if (n > 0)
            n--;
        while (n > 0)
            n--;
        for (int i = 0; i < n; i++)
            log(i);
        if (n > 0) {
            int x = 1;
            log(x);
        }
        if (n > 0) {
            int only = 1;
        }
        if (n > 0) {
            if (n > 1)
                log(n);
        } else
            log(0);
        if (n > 0)
            // keep me
            log(n);
    }

}
