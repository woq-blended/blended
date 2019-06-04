package blended.security.ssl

class NoPrivateKeyException(msg : String) extends Exception(msg)
class EmptyCertificateChainException extends Exception("The certificate chain can't be empty.")
class CertificateChainException(msg : String) extends Exception(msg)
class MissingRootCertificateException extends Exception("The certificate chain must have a root certificate.")
class InconsistentKeystoreException(m : String) extends Exception(m)
class InitialCertificateProvisionException(msg : String) extends Exception(msg)
