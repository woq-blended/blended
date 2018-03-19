package blended.security.ssl.internal

import java.security.KeyStore
import blended.security.ssl.ServerCertificate

case class ServerKeyStore(keyStore: KeyStore, serverCertificates: Map[String, ServerCertificate])
