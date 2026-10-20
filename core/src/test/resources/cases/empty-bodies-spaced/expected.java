class A {

    void a() {
        if (ready) { }
        try {
            run();
        } catch (IllegalStateException e) { }
        synchronized (lock) { }
        if (someCondition && anotherCondition && aThirdConditionThatIsLong12)
        { }
    }

    void someMethodWithAVeryLongName(String firstParameter, int second12) { }

}
