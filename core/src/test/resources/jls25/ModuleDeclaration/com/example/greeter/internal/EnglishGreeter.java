package com.example.greeter.internal;

import com.example.greeter.api.Greeter;

public final class EnglishGreeter implements Greeter {

    @Override
    public String greet(String name) {
        return "Hello, " + name + "!";
    }

}
