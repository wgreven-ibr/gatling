package io.gatling.recorder.http.ssl

import java.net.Socket
import java.security.{PrivateKey, Principal}
import java.security.cert.X509Certificate
import javax.net.ssl.{X509KeyManager, SSLEngine, X509ExtendedKeyManager}

import com.typesafe.scalalogging.StrictLogging

/**
 * Instructs to send server certificate according to the alias
 */
class KeyManagerDelegate(manager: X509KeyManager, alias: String) extends X509ExtendedKeyManager with StrictLogging {

  override def chooseEngineClientAlias(p1: Array[String], p2: Array[Principal], p3: SSLEngine): String = super.chooseEngineClientAlias(p1, p2, p3)

  override def chooseEngineServerAlias(keyType: String, issuers: Array[Principal], engine: SSLEngine): String = alias

  override def getClientAliases(p1: String, p2: Array[Principal]): Array[String] = manager.getClientAliases(p1, p2)

  override def getPrivateKey(p1: String): PrivateKey = manager.getPrivateKey(p1)

  override def getCertificateChain(p1: String): Array[X509Certificate] = manager.getCertificateChain(p1)

  override def getServerAliases(p1: String, p2: Array[Principal]): Array[String] = manager.getServerAliases(p1, p2)

  override def chooseClientAlias(p1: Array[String], p2: Array[Principal], p3: Socket): String = manager.chooseClientAlias(p1, p2, p3)

  override def chooseServerAlias(p1: String, p2: Array[Principal], p3: Socket): String = manager.chooseServerAlias(p1, p2, p3)
}
