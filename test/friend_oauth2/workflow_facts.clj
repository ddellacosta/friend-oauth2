(ns friend-oauth2.workflow-facts
  (:use
   midje.sweet
   friend-oauth2.test-helpers
   friend-oauth2.fixtures)
  (:require
   [friend-oauth2.workflow :as oauth2]
   [cemerick.friend :as friend]
   [cemerick.url :as url]
   [ring.util.response :refer [get-header]]
   [ring.mock.request :as ring-mock]))

(fact
 "A user can authenticate via OAuth2
 (tests 'happy path' for the whole process)."

 (with-redefs [clj-http.client/post (constantly access-token-response-fixture)]
   (let [authlink-response  (test-app (ring-mock/request :get "/authlink"))
         ring-session-val   (extract-ring-session-val authlink-response)

         login-response     (make-session-get-request "/login" {} ring-session-val)
         state              (extract-state-from-redirect-url login-response)

         authlink-redirect  (make-session-get-request
                             "/redirect" {:code "my-code" :state state} ring-session-val)
         authlink           (get-header authlink-redirect "Location")
         authed-response    (make-session-get-request authlink {} ring-session-val)]

     (:status authlink-response)                                   => 302
     (re-find #"/login" (get-header authlink-response "Location")) => "/login"

     (:status login-response)                                      => 302
     (re-find #"/authenticate"
              (get-header login-response "Location"))              => "/authenticate"

     (:status authlink-redirect)                                   => 303
     (re-find #"/authlink"
              (get-header authlink-redirect "Location"))           => "/authlink"

     (:status authed-response)                                     => 200
     (:body authed-response)                                       => "Authorized page.")))

(fact
 "A login request redirects to the authorization uri"

 (let [auth-redirect  (test-app (ring-mock/request :get "/login"))
       location       (get-header auth-redirect "Location")
       redirect-query (-> location url/url :query clojure.walk/keywordize-keys)]

   (:status auth-redirect)                               => 302
   (re-find #"/redirect" (:redirect_uri redirect-query)) => "/redirect"
   (:client_id redirect-query)                           => "my-client-id"
   (nil? (:state redirect-query))                        => false))

(fact
 "On error response from authentication provider execute error function"
 
 (let [authlink-response  (test-app (ring-mock/request :get "/authlink"))
       ring-session-val   (extract-ring-session-val authlink-response)

       login-response     (make-session-get-request "/login" {} ring-session-val)
       state              (extract-state-from-redirect-url login-response)

       authlink-error  (make-session-get-request
                        "/redirect" {:error "auth-error" :state state} ring-session-val)]

   (:status authlink-response)                                   => 302
   (re-find #"/login" (get-header authlink-response "Location")) => "/login"

   (:status login-response)                                      => 302
   (re-find #"/authenticate"
            (get-header login-response "Location"))              => "/authenticate"
            
            
    (:status authlink-error)                                     => 200
    (:body authlink-error)                                       => "auth-error"))



(future-fact
 "access-token-parsefn is used for the token if provided."

 (let [{state :state
        ring-session-val :ring-session-val} (setup-valid-state)
        authlink-redirect  (make-session-get-request
                            "/redirect"
                            {:code "my-code" :state state}
                            ring-session-val)]))

(future-fact
 "If the session state is not the same as the auth-response state, it does not proceed")


(fact
 "Login using Oauth2 through multiple providers"

 (with-redefs [clj-http.client/post (constantly access-token-response-fixture)]
   (let [authlink-response  (test-app-multi (ring-mock/request :get "/authlink"))        
         ring-session-val   (extract-ring-session-val authlink-response)
                
         login-response     (make-session-get-request-multi "/login" {} ring-session-val)
         callback-response  (make-session-get-request-multi "/auth/a" {} ring-session-val)
         state              (extract-state-from-redirect-url callback-response)
       
         authlink-redirect  (make-session-get-request-multi
                             "/auth/a" {:code "my-code" :state state} ring-session-val)
         authlink           (get-header authlink-redirect "Location")
         authed-response    (make-session-get-request-multi authlink {} ring-session-val)]

     (:status authlink-response)                                   => 302
     (re-find #"/login" (get-header authlink-response "Location")) => "/login"

     (:status login-response)                                      => 200
     (:body login-response)                                        => "select provider"

     (:status callback-response)                                   => 302
     (re-find #"/authenticate"
              (get-header callback-response "Location"))           => "/authenticate"

     (:status authlink-redirect)                                   => 303
     (re-find #"/authlink"
              (get-header authlink-redirect "Location"))           => "/authlink"

     (:status authed-response)                                     => 200
     (:body authed-response)                                       => "Authorized page.")))
