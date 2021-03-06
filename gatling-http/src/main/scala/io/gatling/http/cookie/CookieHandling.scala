/**
 * Copyright 2011-2014 eBusiness Information, Groupe Excilys (www.ebusinessinformation.fr)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * 		http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.gatling.http.cookie

import com.ning.http.client.cookie.Cookie
import com.ning.http.client.uri.Uri
import io.gatling.core.session.{ Session, SessionPrivateAttributes }
import io.gatling.core.validation.SuccessWrapper
import io.gatling.core.session.Expression

object CookieHandling {

  val CookieJarAttributeName = SessionPrivateAttributes.PrivateAttributePrefix + "http.cookies"

  def cookieJar(session: Session): Option[CookieJar] = session(CookieJarAttributeName).asOption[CookieJar]

  def getStoredCookies(session: Session, url: String): List[Cookie] = getStoredCookies(session, Uri.create(url))

  def getStoredCookies(session: Session, uri: Uri): List[Cookie] =
    cookieJar(session) match {
      case Some(cookieJar) => cookieJar.get(uri)
      case _               => Nil
    }

  private def getOrCreateCookieJar(session: Session) =
    cookieJar(session) match {
      case Some(cookieJar) => cookieJar
      case _               => CookieJar(Map.empty)
    }

  def storeCookies(session: Session, uri: Uri, cookies: List[Cookie]): Session = {
    val cookieJar = getOrCreateCookieJar(session)
    session.set(CookieJarAttributeName, cookieJar.add(uri, cookies))
  }

  def storeCookie(session: Session, domain: String, path: String, cookie: Cookie): Session = {
    val cookieJar = getOrCreateCookieJar(session)
    session.set(CookieJarAttributeName, cookieJar.add(domain, path, List(cookie)))
  }

  val FlushSessionCookies: Expression[Session] = session =>
    cookieJar(session) match {
      case None => session.success
      case Some(cookieJar) =>
        val storeWithOnlyPersistentCookies = cookieJar.store.filter { case (_, storeCookie) => storeCookie.persistent }
        session.set(CookieJarAttributeName, CookieJar(storeWithOnlyPersistentCookies)).success
    }

  val FlushCookieJar: Expression[Session] = _.remove(CookieJarAttributeName).success
}
