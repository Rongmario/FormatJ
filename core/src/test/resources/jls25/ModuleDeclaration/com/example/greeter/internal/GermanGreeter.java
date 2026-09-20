package com.example.greeter.internal;

import com.example.greeter.api.Greeter;

public final class GermanGreeter implements Greeter {

    @Override
    public String greet(String name) {
        return "Hallo, " + name + "!";
    }

}
