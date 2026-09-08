package net.hristrix.modexa.api;

@FunctionalInterface
public interface ConditionHandler {
    boolean test(ConditionContext context) throws Exception;
}
