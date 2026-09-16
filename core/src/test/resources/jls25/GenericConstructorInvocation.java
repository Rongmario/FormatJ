class GenericConstructorInvocation {

    static class Box<T> {

        <U> Box(U seed) {
        }

    }

    void use() {
        Box<Integer> a = new <String>Box<Integer>("seed");
        GenericConstructorInvocation.Box<Integer> b =
                new <String>GenericConstructorInvocation.Box<Integer>("seed");
    }

}
