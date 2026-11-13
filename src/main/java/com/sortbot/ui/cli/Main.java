package com.sortbot.ui.cli;

/** Command-line entry point: java -cp target/classes com.sortbot.ui.cli.Main <command> ... */
public final class Main {
    private Main() {}

    public static void main(String[] args) {
        System.exit(new CliApp(System.in, System.out, null).run(args));
    }
}
