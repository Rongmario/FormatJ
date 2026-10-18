class A {
    private static final ConcurrentHashMap<Path, CompletableFuture<@Nullable Void>> INGESTING = new ConcurrentHashMap<>();
    private static final Set<String> REENTRANT_EXCLUSIONS = Sets.newHashSet("net.minecraftforge.fml.common.asm.transformers.EventSubscriptionTransformer", "net.minecraftforge.fml.common.asm.transformers.TerminalTransformer");
}
