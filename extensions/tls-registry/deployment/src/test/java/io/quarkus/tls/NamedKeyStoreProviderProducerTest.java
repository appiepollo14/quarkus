package io.quarkus.tls;

import static org.assertj.core.api.Assertions.assertThat;

import java.security.KeyStoreException;
import java.security.cert.CertificateParsingException;
import java.security.cert.X509Certificate;

import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusUnitTest;
import io.quarkus.tls.runtime.KeyStoreAndKeyCertOptions;
import io.quarkus.tls.runtime.KeyStoreProvider;
import io.smallrye.certs.Format;
import io.smallrye.certs.junit5.Certificate;
import io.smallrye.certs.junit5.Certificates;
import io.smallrye.common.annotation.Identifier;
import io.vertx.core.net.PemKeyCertOptions;

@Certificates(baseDir = "target/certs", certificates = {
        @Certificate(name = "test-formats", password = "password", formats = { Format.JKS, Format.PEM, Format.PKCS12 })
})
public class NamedKeyStoreProviderProducerTest {

    private static final String configuration = """
            # no configuration by default
            """;

    @RegisterExtension
    static final QuarkusUnitTest config = new QuarkusUnitTest().setArchiveProducer(
            () -> ShrinkWrap.create(JavaArchive.class)
                    .add(new StringAsset(configuration), "application.properties"));

    @Inject
    TlsConfigurationRegistry certificates;

    @Test
    void test() throws KeyStoreException, CertificateParsingException {
        TlsConfiguration def = certificates.getDefault().orElseThrow();
        TlsConfiguration named = certificates.get("http").orElseThrow();

        assertThat(def.getKeyStoreOptions()).isNull();
        assertThat(def.getKeyStore()).isNull();

        assertThat(named.getKeyStoreOptions()).isNotNull();
        assertThat(named.getKeyStore()).isNotNull();

        X509Certificate certificate = (X509Certificate) named.getKeyStore().getCertificate("dummy-entry-0");
        assertThat(certificate).isNotNull();
        assertThat(certificate.getSubjectAlternativeNames()).anySatisfy(l -> {
            assertThat(l.get(0)).isEqualTo(2);
            assertThat(l.get(1)).isEqualTo("localhost");
        });
    }

    static class KeyStoreProviderFactory {

        @Produces
        @Identifier("http")
        KeyStoreProvider keyStoreProvider() {
            return vertx -> {
                var options = new PemKeyCertOptions()
                        .addCertPath("target/certs/test-formats.crt")
                        .addKeyPath("target/certs/test-formats.key");
                try {
                    return new KeyStoreAndKeyCertOptions(options.loadKeyStore(vertx), options);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            };
        }
    }
}
