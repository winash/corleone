/*
 * Copyright [2015] Zalando SE
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package controllers

import javax.inject.Inject

import play.api._
import play.api.libs.ws.WSResponse
import play.api.mvc._
import security.{OAuth2CredentialsProvider, OAuth2Helper, OAuth2Constants}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future


/**
 * This controller provides a callback endpoint for handling the flow as described in 
 * http://tools.ietf.org/html/rfc6749#section-4.1.3
 */
class OAuth2Controller @Inject()(oauth2: OAuth2Helper, credentialsProvider: OAuth2CredentialsProvider) extends Controller {

  /**
   * This callback is usually called by the authorization server when the user has authenticated successfully
   * (see http://tools.ietf.org/html/rfc6749#section-4.1).
   * According to http://tools.ietf.org/html/rfc6749#section-4.1.2, we can extract the supplied state as well as the
   * code from the query parameters. We use the received code to perform an access token request 
   * (http://tools.ietf.org/html/rfc6749#section-4.1.3). If the request is successful, we save the access token to
   * the session and redirect the user to her actual target URL. Note: Also the redirect goes through OAuth2Filter
   * where the access token is validated for each request.
   */
  def callback = Action.async { request =>
    val originalRequestOption = request.session.get(OAuth2Constants.SESSION_KEY_ORIGINAL_REQUEST_URL)

    if (originalRequestOption.isEmpty) {
      Future(handleError(s"no redirect URL was supplied via session [sessionKey=${OAuth2Constants.SESSION_KEY_ORIGINAL_REQUEST_URL}]",
        BAD_REQUEST))
    }
    else {
      val originalRequest = originalRequestOption.get

      // check if Authorization server reported an error
      val possibleErrorOption = request.getQueryString("error")
      if (possibleErrorOption.isEmpty) {

        val stateReceivedByCallbackOption = request.getQueryString("state")
        val stateInSessionOption = request.session.get(OAuth2Constants.SESSION_KEY_STATE)

        
        (stateReceivedByCallbackOption, stateInSessionOption) match {

          case (None, None)       => Future(handleError("Did not receive any OAUTH2 state from callback or from session", BAD_REQUEST))
          case (None, Some(_))    => Future(handleError("Did not receive any OAUTH2 state from callback", BAD_REQUEST))
          case (Some(_), None)    => Future(handleError("Could not find OAUTH2 state from session", BAD_REQUEST))
          case (Some(stateReceivedByCallback), Some(stateInSession)) => {
              // check for possible CSRF attack
              if (stateReceivedByCallback == stateInSession) {
                val codeReceivedByCallbackOption = request.getQueryString("code")
                if (codeReceivedByCallbackOption.isEmpty) {
                  Future(handleError("Did not receive OAUTH2 code from callback", BAD_REQUEST))
                }
                else {
                  // finally, request access token with the code delivered via the callback performed by the authorization server
                  val futureResponse = oauth2.requestAccessToken(codeReceivedByCallbackOption.get)
                  futureResponse.flatMap { response => processAccessTokenRequestResponse(response, originalRequest) }
                }
              }
              else {
                Future(handleError("OAUTH2 state in session does not match state received from callback", CONFLICT))
              }
          }
        }
      }
      else {
        handleErrorCommunicatedByAuthorizationServer(request)
      }
    }
  }


  private def handleError(message: String, errorStatus: Int): Result = {
    Logger.warn(message)

    if (errorStatus == BAD_REQUEST)   BadRequest(message).withNewSession
    else if (errorStatus == CONFLICT) Conflict(message).withNewSession
    else                              InternalServerError(message).withNewSession
  }
  

  private def handleErrorCommunicatedByAuthorizationServer(request: Request[AnyContent]): Future[Result] = {
    val possibleErrorOption = request.getQueryString("error")
    val error = possibleErrorOption.get
    val errorDescription = request.getQueryString("error_description").get

    val isRetryAfterAuthError = !request.session.get(OAuth2Constants.SESSION_KEY_RETRY_AFTER_AUTH_ERROR).isEmpty
    if (isRetryAfterAuthError) {
      Future(handleError(s"authorization server reported error via callback: [error=$error, errorDescription=$errorDescription]",
        INTERNAL_SERVER_ERROR))
    }
    else {
      Logger.warn(s"authorization server reported error via callback: [error=$error, errorDescription=$errorDescription]" +
        "-> retrying again while invalidating credentials cache and letting the user authenticate again")
      credentialsProvider.invalidateCache()
      val originalRequestOption = request.session.get(OAuth2Constants.SESSION_KEY_ORIGINAL_REQUEST_URL)
      Future(Redirect(originalRequestOption.get).withNewSession.withSession((OAuth2Constants.SESSION_KEY_RETRY_AFTER_AUTH_ERROR, "true")))
    }
  }

  private def processAccessTokenRequestResponse(response: WSResponse, originalRequest: String): Future[Result] = {
    if (response.status == OK) {
      val json = response.json
      val accessTokenOption = (json \ "access_token").asOpt[String]

      if (accessTokenOption.isEmpty) {
        Future(handleError("Did not receive any access token from access token request", INTERNAL_SERVER_ERROR))
      }
      else {
        val accessToken = accessTokenOption.get
        val refreshTokenOption = (json \ "refresh_token").asOpt[String]

        // redirect user back to his actual target URL together with the access token (and refresh token)
        if (refreshTokenOption.isEmpty) {
          Logger.warn("Did not receive any refresh token from access token request response -> access token can not be refreshed")

          Future(
            Redirect(originalRequest)
              .withNewSession
              .withSession((OAuth2Constants.SESSION_KEY_ACCESS_TOKEN, accessToken)))
        }
        else {
          val refreshToken = refreshTokenOption.get

          Future(Redirect(originalRequest)
            .withNewSession
            .withSession((OAuth2Constants.SESSION_KEY_ACCESS_TOKEN, accessToken),
              (OAuth2Constants.SESSION_KEY_REFRESH_TOKEN, refreshToken)))
        }
      }
    }
    else {
      Future(handleError(s"access token request was not successful: ${response.body}", INTERNAL_SERVER_ERROR))
    }
  }
}
