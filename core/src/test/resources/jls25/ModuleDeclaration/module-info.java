module com.example.greeter {

    requires transitive java.logging;
    requires static java.compiler;
    exports com.example.greeter.api;
    // Qualified exports may name several consumers.
    exports com.example.greeter.internal to com.example.greeter.tests, com.example.greeter.tools;
    opens com.example.greeter.api;
    opens com.example.greeter.internal to com.example.greeter.tests;
    uses com.example.greeter.api.Greeter;
    provides com.example.greeter.api.Greeter with com.example.greeter.internal.EnglishGreeter, com.example.greeter.internal.FrenchGreeter, com.example.greeter.internal.GermanGreeter;

}
