package sample;

import java.util.List;
import java.util.Map;

public class Messy {

    private final List<String> items;
    private static final int LIMIT = 10;

    public Messy(List<String> items) {
        this.items = items;
    }

    public int total() {
        int sum = 0;
        for (String item : items) {
            sum += item.length();
        }
        return sum;
    }

    public boolean isBig() {
        return total() > LIMIT && !items.isEmpty();
    }

}
