package net.hristrix.modexa.api;

@FunctionalInterface
public interface DialogProvider {
    DialogSpec build(DialogContext context) throws Exception;
}
