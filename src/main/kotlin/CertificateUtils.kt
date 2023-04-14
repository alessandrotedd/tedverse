import java.io.File
import java.security.KeyStore

object CertificateUtils {
    val keyStoreFile = File("keystore.jks")
    val keyStorePassword = System.getProperty("keyStorePassword").toCharArray()
    val keyAlias = System.getProperty("keyAlias")!!
    val privateKeyPassword = System.getProperty("privateKeyPassword").toCharArray()

    val keyStore: KeyStore by lazy {
        val ks = KeyStore.getInstance(KeyStore.getDefaultType())
        ks.load(keyStoreFile.inputStream(), keyStorePassword)
        ks
    }
}