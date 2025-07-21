public class HelloWorld {
	private String foo;
	private String bar;

	public HelloWorld(String foo, int bar) {
		this.foo = foo;
		this.bar = bar;
	}

	public int addFooBar() {
		return foo + bar;
	}
}