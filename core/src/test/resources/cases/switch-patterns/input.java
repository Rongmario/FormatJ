package sample;

sealed interface Shape permits Circle, Square {}
record Circle(double radius) implements Shape {}
record Square(double side) implements Shape {}
class Describe {
String describe(Object value){
return switch(value){
case Circle(double r) when r>10->"big circle";
case Circle c->"circle";
case Square(double side)->"square of "+side;
case Integer i,Long l->"number";
case null,default->{
var text="unknown";
yield text;
}
};
}
}
