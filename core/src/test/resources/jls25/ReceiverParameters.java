class ReceiverParameters {

    void m(ReceiverParameters this) {
    }

    class Inner {

        Inner(ReceiverParameters ReceiverParameters.this) {
        }

    }

    void use() {
        m();
        new Inner();
    }

}
