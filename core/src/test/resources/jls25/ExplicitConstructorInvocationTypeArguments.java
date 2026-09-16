class ExplicitConstructorInvocationTypeArguments {

    <T> ExplicitConstructorInvocationTypeArguments(T value) {
    }

    ExplicitConstructorInvocationTypeArguments() {
        <String>this("value");
    }

    static class Child extends ExplicitConstructorInvocationTypeArguments {

        <T> Child(T value) {
            <String>super(value.toString());
        }

    }

}
