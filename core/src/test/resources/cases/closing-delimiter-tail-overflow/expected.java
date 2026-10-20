class A {

    void a() {
        assertThat(ConventionsFile.checkstyle(license, ConventionsDefaults.AUTHOR
        )).contains(license.javaHeaderPattern(ConventionsDefaults.AUTHOR).lines().findFirst().orElseThrow());
    }

}
