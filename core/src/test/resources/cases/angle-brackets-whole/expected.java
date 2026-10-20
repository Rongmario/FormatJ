class A {

    private static final Map<String, Integer> SHORT = Map.of("a", 1);
    private static final Set<String> REENTRANT_EXCLUSIONS = Sets.newHashSet("net.minecraftforge.fml.common.asm.transformers.EventSubscriptionTransformer",
        "net.minecraftforge.fml.common.asm.transformers.TerminalTransformer"
    );

}
