final class UnicodeEscapeTranslation {

    static String value(int input) \u007b
        \u002f\u002f The comment delimiters and this line ending are translated.\u000a
        int \uD835\uDC82 = input >\u003e 1;
        String text = \u0022ok\u0022;
        String literalEscape = "\\u005a";
        String escapedBackslashes = "\u005c\u005c\u006e";
        int first = 1;\u000d\u000a        int second = 2;
        /* This comment ends with an escaped slash. *\u002f
        return text + \uuuu0028\uD835\uDC82 + first + second\u0029 + literalEscape + escapedBackslashes\u003b
    \u007d

}
