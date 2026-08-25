class Braces {

    void run(int n) {
        if (n > 0)
            System.out.println(n);
        else
            System.out.println(-n);
        for (int i = 0; i < n; i++)
            System.out.println(i);
        for (String s : names())
            System.out.println(s);
        while (n > 0)
            n--;
        do
            n++;
        while (n < 10);
        if (n > 1)
            if (n > 2)
                System.out.println(n);
    }

}
