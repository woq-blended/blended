package blended.security.ssl.internal

import java.security.KeyStore
import blended.security.ssl.CertificateHolder

case class ServerKeyStore(keyStore: KeyStore, serverCertificates: Map[String, CertificateHolder])
