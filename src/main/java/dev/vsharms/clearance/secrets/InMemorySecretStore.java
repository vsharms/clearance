package dev.vsharms.clearance.secrets;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Used for tests and quick local runs without Vault. Not for real secrets. */
@Component
@ConditionalOnProperty(name = "clearance.secret-store", havingValue = "memory", matchIfMissing = true)
public class InMemorySecretStore implements SecretStore {

    private final Map<String, String> store = new ConcurrentHashMap<>();

    @Override
    public void put(String path, String key, String value) {
        store.put(path + "#" + key, value);
    }

    @Override
    public Optional<String> get(String path, String key) {
        return Optional.ofNullable(store.get(path + "#" + key));
    }
}
