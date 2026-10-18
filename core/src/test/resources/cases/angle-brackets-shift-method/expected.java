class A {

    private static final ConcurrentHashMap
        <Path, CompletableFuture<@Nullable Void>> INGESTING = new ConcurrentHashMap<>();

    @Override
    <T extends SomeVeryLongBoundTypeOneTwoThree, R extends SomeVeryLongBoundTypeFourFiveSix, S extends SomeVeryLongBoundSeven> R convert() {
        return null;
    }

}
