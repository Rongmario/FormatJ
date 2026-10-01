class Reflow {

    // A run of line comments the author left ragged, which the reflow
    // rule refills to the margin without changing a word of it. Short
    // tail.
    void refilled() { }

    //do(); not prose, so this run is left ragged
    //other();
    void code() { }

    // {@code stays  exactly   as written} and the words round it move freely
    void sample() { }

    void trailing() { } // never refilled, because the second line would land under the code

}
