class IntersectionCast {

    void use(Object o) {
        Runnable r = (Runnable & java.io.Serializable) o;
        r.run();
    }

}
