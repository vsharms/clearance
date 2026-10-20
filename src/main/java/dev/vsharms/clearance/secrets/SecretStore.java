package dev.vsharms.clearance.secrets;

import java.util.Optional;

/** Where tenant credentials live. The database only ever stores a pointer, never the secret. */
public interface SecretStore {
    void put(String path, String key, String value);
    Optional<String> get(String path, String key);
}
