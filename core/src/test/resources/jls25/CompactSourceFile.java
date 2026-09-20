int counter = 0;

class module {
}

module value = new module();

void increment() {
    counter++;
}

void main() {
    increment();
    increment();
    System.out.println(counter);
}
