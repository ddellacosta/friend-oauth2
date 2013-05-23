(ns friend-oauth2.workflow-facts
  (:use midje.sweet)
  (:require [friend-oauth2.workflow :as friend-oauth2]
            [cemerick.friend :as friend]
            [clj-http.client :as client]
            [ring.middleware.params :as ring-params]
            [ring.middleware.keyword-params :as ring-keyword-params]
            [ring.mock.request :as ring-mock]
            [ring.util.codec :as codec]
            [cheshire.core :as j]))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
;; Configuration fixtures
;;

(def client-config-fixture
  {:client-id "my-client-id"
   :client-secret "my-client-secret"
   :callback {:domain "http://127.0.0.1" :path "/redirect"}})

(def uri-config-fixture
  {:authentication-uri {:url "http://example.com"
                        :query {:client_id (:client-id client-config-fixture)
                                :redirect_uri (friend-oauth2/format-config-uri client-config-fixture)}}

   :access-token-uri {:url "http://example.com"
                      :query {:client_id (client-config-fixture :client-id)
                              :client_secret (client-config-fixture :client-secret)
                              :redirect_uri (friend-oauth2/format-config-uri client-config-fixture)
                              :code ""}}})

;; Default workflow function with above config
(defn default-workflow-function [request-or-response]
  ((friend-oauth2/workflow :client-config client-config-fixture
                            :uri-config uri-config-fixture
                            :login-uri "/login")  ;; Friend provides this normally.
   request-or-response))

(def identity-fixture
  {:identity "my-access-token", :access_token "my-access-token"})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
;; Dummy responses/requests for various OAuth2 endpoints
;;

;; http://tools.ietf.org/html/draft-ietf-oauth-v2-31#section-4.1.2
(defn redirect-request-fixture
  [redirect-uri]
  (ring-keyword-params/keyword-params-request
   (ring-params/params-request
    (ring-mock/content-type
     (ring-mock/request :get redirect-uri
                        {:code "my-code"})
     "application/x-www-form-urlencoded"))))

(def default-redirect "/redirect")

(defn redirect-with-default-redirect-uri []
  (redirect-request-fixture default-redirect))

(defn query-string-to-params [request]
  (assoc-in
   request
   [:query-params]
   (codec/form-decode (request :query-string))))

;; http://tools.ietf.org/html/draft-ietf-oauth-v2-31#section-5.1
(def access-token-response-fixture
  (ring.util.response/content-type
   (ring.util.response/response
    "{\"access_token\": \"my-access-token\"}")
   "application/json"))

;; Initial redirect to login
(defn login-request
  [login-path]
  (ring-mock/request :get login-path))

(def default-login-path "/login")

(defn default-login-request []
  (login-request default-login-path))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
;; Stubs
;;

(background
 (client/post
  "http://example.com"
  {:form-params {:client_id "my-client-id", :client_secret "my-client-secret", :redirect_uri "http://127.0.0.1/redirect", :code "my-code"}})
 => access-token-response-fixture,
 (ring.util.response/redirect anything)
 => (redirect-with-default-redirect-uri))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
;; Facts (helper functions)
;;

(fact
 "Extracts the access token from a JSON access token response"
 (friend-oauth2/extract-access-token access-token-response-fixture)
 => "my-access-token")

(fact
 "Returns nil if there is no code in the request"
 ;; No longer necessary since ring params/keyword-params handles this for us.
 ;; Not sure if this test is necessary anymore either, but leaving in for now.
 ;; (friend-oauth2/extract-code (ring-mock/request :get default-redirect))
 (-> (ring-mock/request :get default-redirect) :params :code)
 => nil)

(fact
 "Formats URI from domain and path pairs in a map"
 (friend-oauth2/format-config-uri client-config-fixture)
 => "http://127.0.0.1/redirect")

(fact
 "Formats the client authentication uri"
 (friend-oauth2/format-authentication-uri uri-config-fixture)
 => "http://example.com?client_id=my-client-id&redirect_uri=http%3A%2F%2F127.0.0.1%2Fredirect")

(fact
 "Replaces the authorization code"
 ((friend-oauth2/replace-authorization-code (uri-config-fixture :access-token-uri) "my-code") :code)
 => "my-code")

(fact
 "Creates the auth-map for Friend with proper meta-data"
 (meta (friend-oauth2/make-auth identity-fixture))
 =>
 {:type ::friend/auth
  ::friend/workflow :email-login
  ::friend/redirect-on-auth? true})



;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
;; Facts (core workflow)
;;

(fact
 "A login request redirects to the authorization uri"
 (default-workflow-function (default-login-request))
 => (redirect-with-default-redirect-uri))

(fact
 "extract-access-token is used for access-token-parsefn if none is passed in."
 (default-workflow-function
   (redirect-with-default-redirect-uri))
 => {:identity "my-access-token", :access_token "my-access-token"}
 (provided
  (friend-oauth2/extract-access-token access-token-response-fixture)
  => "my-access-token" :times 1))

(fact
 "If there is a code in the request it posts to the token-uri"
 (default-workflow-function
   (redirect-with-default-redirect-uri))
 => {:identity "my-access-token", :access_token "my-access-token"}
 (provided
  (client/post "http://example.com"
               {:form-params (friend-oauth2/replace-authorization-code
                              (uri-config-fixture :access-token-uri) "my-code")})
  => access-token-response-fixture :times 1))
