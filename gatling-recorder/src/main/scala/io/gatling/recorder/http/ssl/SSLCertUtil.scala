package io.gatling.recorder.http.ssl

import java.io._
import java.math.BigInteger
import java.security.cert.X509Certificate
import java.security.{KeyPairGenerator, KeyStore, PrivateKey}
import java.util.Date
import java.util.concurrent.TimeUnit
import javax.security.auth.x500.X500Principal

import com.typesafe.scalalogging.StrictLogging
import io.gatling.core.util.IO.withCloseable
import org.bouncycastle.cert.jcajce.{JcaX509CertificateConverter, JcaX509CertificateHolder}
import org.bouncycastle.cert.{X509CertificateHolder, X509v3CertificateBuilder}
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter
import org.bouncycastle.openssl.{PEMKeyPair, PEMParser}
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.bouncycastle.pkcs.PKCS10CertificationRequest
import org.bouncycastle.pkcs.jcajce.JcaPKCS10CertificationRequestBuilder
import org.jboss.netty.example.securechat.SecureChatSslContextFactory._

import scala.util.Try


/**
 * Utility class to create SSL server certificate on the fly for the recorder keystore
 */
object SSLCertUtil extends StrictLogging {

  private val keyfile = new File(ClassLoader.getSystemResource("gatlingCA.key.pem").toURI)
  private val crtFile = new File(ClassLoader.getSystemResource("gatlingCA.cert.pem").toURI)
  private[this] var gatlingKeystore : Option[File] = None

  def getKeystoreCredentials(domainAlias: String) : Try[File] = {
    this.synchronized{
      for{
        (keyCA, crtCA) <- getInfoCA()
        (csr, privKey) <- createCSR(domainAlias)
        servCrt <- createServerCert(keyCA, crtCA, csr)
        keystoreFile <- putInKeystore(servCrt, privKey, crtCA, domainAlias)
      } yield keystoreFile
    }
  }

  //TODO Cache
  private def getInfoCA(keyFile: File = keyfile, crtFile: File = crtFile): Try[(PrivateKey, X509Certificate)] = {
    def extractFileInfo[A](file: File, extractFunc: PEMParser => A) : Try[A] = {
      Try(
        withCloseable(new FileReader(file)) { reader =>
          withCloseable(new PEMParser(reader)) { pemReader =>
            extractFunc(pemReader)
          }
        }
      )
    }
    for{
      keyInfo <- extractFileInfo(keyFile, pemParser => pemParser.readObject().asInstanceOf[PEMKeyPair].getPrivateKeyInfo)
      certHolder <- extractFileInfo(crtFile, pemParser => pemParser.readObject().asInstanceOf[X509CertificateHolder])
    } yield (new JcaPEMKeyConverter().getPrivateKey(keyInfo), new JcaX509CertificateConverter().setProvider("BC").getCertificate(certHolder))
  }

  private def createCSR(DnHostName: String) : Try[(PKCS10CertificationRequest, PrivateKey)] = {
    Try {
      val kpGen = KeyPairGenerator.getInstance("RSA")
      kpGen.initialize(1024)
      val pair = kpGen.generateKeyPair
      val dn = s"C=FR, ST=Val de marne, O=GatlingCA, OU=Gatling, CN=$DnHostName"
      val builder = new JcaPKCS10CertificationRequestBuilder(new X500Principal(dn), pair.getPublic)
      val csBuilder = new JcaContentSignerBuilder("SHA256withRSA")
      val signer = csBuilder.build(pair.getPrivate)
      (builder.build(signer), pair.getPrivate)
    }
  }

  private def createServerCert(keyCA: PrivateKey, certCA: X509Certificate, csr: PKCS10CertificationRequest) : Try[X509Certificate] = {
    Try {
      val certBuilder = new X509v3CertificateBuilder(
        new JcaX509CertificateHolder(certCA).getSubject,
        BigInteger.valueOf(System.currentTimeMillis),
        new Date(System.currentTimeMillis),
        new Date(System.currentTimeMillis + TimeUnit.MILLISECONDS.convert(1, TimeUnit.DAYS)), //Temps validité cert
        csr.getSubject,
        csr.getSubjectPublicKeyInfo
      )
      val signer = new JcaContentSignerBuilder("SHA256withRSA").build(keyCA)
      new JcaX509CertificateConverter().setProvider("BC").getCertificate(certBuilder.build(signer))
    }
  }

  private def putInKeystore(servCert: X509Certificate, privKey: PrivateKey, certCA: X509Certificate, alias: String) : Try[File] = {
    Try {
      implicit val keyStore = KeyStore.getInstance("JKS")
      implicit val password = "gatling".toCharArray
      def loadPreviousKeystore(stream: InputStream)(implicit keyStore: KeyStore, password: Array[Char]) = {
        withCloseable(stream) { in =>
          keyStore.load(in, password)
        }
      }
      //If first load, take default keystore, otherwise take previous one.
      gatlingKeystore.fold(loadPreviousKeystore(defaultKeyStore))(ks => loadPreviousKeystore(new FileInputStream(ks)))
      //loadPreviousKeystore(defaultKeyStore)
      keyStore.setCertificateEntry(alias, servCert)
      keyStore.setKeyEntry(alias, privKey, password, Array(servCert, certCA))

      val enumeration = keyStore.aliases()
      logger.debug("--- Cert Beginning --- ")
      var counter = 0
      while (enumeration.hasMoreElements) {
        val alias = enumeration.nextElement()
        val certificate = keyStore.getCertificate(alias)
        logger.debug("--- CERT --- ")
        logger.debug(certificate.toString)
        counter = counter + 1
      }
      if (keyStore.isKeyEntry(alias)) logger.debug(s"PRIVATE KEY IN FOR $alias !")
      logger.debug(s"--- CERT COUNTER : $counter ---")

      val file = File.createTempFile("tempStore", "jks")
      withCloseable(new FileOutputStream(file)) { out =>
        keyStore.store(out, password)
      }
      gatlingKeystore = Some(file)
      file
    }
  }

  def defaultKeyStore: InputStream = {
    logger.info(s"Loading default keystore: '$DefaultKeyStore'")
    Option(ClassLoader.getSystemResourceAsStream(DefaultKeyStore))
      .orElse(Option(getClass.getResourceAsStream(DefaultKeyStore)))
      .getOrElse(throw new IllegalStateException(s"Couldn't load $DefaultKeyStore neither from System ClassLoader nor from current one"))
  }

}
