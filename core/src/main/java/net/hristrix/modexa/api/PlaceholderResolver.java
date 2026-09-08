package net.hristrix.modexa.api;

@FunctionalInterface
public interface PlaceholderResolver {
    String resolve(PlaceholderContext context) throws Exception;
}
