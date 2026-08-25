class Comments {

    void run(int n) {
        if (n > 0) { // trailing the brace
            log(n);
        }
        while (n > 0) {
            log(n);
            // before the closing brace
        }
    }

}
