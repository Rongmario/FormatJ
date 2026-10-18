class T {
    private static final Map<String, Integer> SHORT = Map.of("a", 1);
    private static final Map<String, List<Map<String, Integer>>> NESTED = Map.of();
    private static final Map<String, Integer> EMPTY = new HashMap<>();
    private static final Map</* key */ String, Integer> LEAD = new HashMap<>();
    private static final Map<String, Integer /* value */> TRAIL = new HashMap<>();
    private static final ConcurrentHashMap<Path, CompletableFuture<@Nullable Void>> LONG = new ConcurrentHashMap<>();
}
