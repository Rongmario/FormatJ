class A {

    void a() {
        List<Entry> entries = pipeline.process("""
            a preserved text block is measured by its first line, so the declaration in front of it stays whole
            """);
    }

}
