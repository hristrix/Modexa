package net.hristrix.modexa.api;

@FunctionalInterface
public interface ActionHandler {
    void execute(ActionContext context) throws Exception;
}
