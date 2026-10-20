package dev.vsharms.clearance.secrets;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.vault.authentication.TokenAuthentication;
import org.springframework.vault.client.VaultEndpoint;
import org.springframework.vault.core.VaultKeyValueOperations;
import org.springframework.vault.core.VaultKeyValueOperationsSupport.KeyValueBackend;
import org.springframework.vault.core.VaultTemplate;
import org.springframework.vault.support.VaultResponse;

import java.net.URI;
import java.util.Map;
import java.util.Optional;

/** Stores tenant secrets in HashiCorp Vault's KV v2 engine. */
@Component
@ConditionalOnProperty(name = "clearance.secret-store", havingValue = "vault")
public class VaultSecretStore implements SecretStore {

    private final VaultKeyValueOperations kv;

    public VaultSecretStore(@Value("${vault.uri}") String uri, @Value("${vault.token}") String token) {
        VaultTemplate template = new VaultTemplate(VaultEndpoint.from(URI.create(uri)), new TokenAuthentication(token));
        this.kv = template.opsForKeyValue("secret", KeyValueBackend.KV_2);
    }

    @Override
    public void put(String path, String key, String value) {
        kv.put(path, Map.of(key, value));
    }

    @Override
    public Optional<String> get(String path, String key) {
        VaultResponse response = kv.get(path);
        if (response == null || response.getData() == null) {
            return Optional.empty();
        }
        return Optional.ofNullable((String) response.getData().get(key));
    }
}
