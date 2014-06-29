(ns friend-oauth2.fixtures
  (:require
   [ring.util.response :refer [response content-type]]
   [friend-oauth2.util :as oauth2-util]))

(def client-config-fixture
  {:client-id "my-client-id"
   :client-secret "my-client-secret"
   :callback {:domain "http://127.0.0.1" :path "/redirect"}})

(def uri-config-fixture
  {:authentication-uri {:url "http://example.com/authenticate"
                        :query {:client_id (:client-id client-config-fixture)
                                :redirect_uri (oauth2-util/format-config-uri client-config-fixture)}}

   :access-token-uri {:url "http://example.com/get-access-token"
                      :query {:client_id (client-config-fixture :client-id)
                              :client_secret (client-config-fixture :client-secret)
                              :redirect_uri (oauth2-util/format-config-uri client-config-fixture)}}})

(def a-client-config-fixture
  {:client-id "a-client-id"
   :client-secret "a-client-secret"
   :callback {:domain "http://127.0.0.1" :path "/auth/a"}})

(def a-uri-config-fixture
  {:authentication-uri {:url "http://aclient.com/authenticate"
                        :query {:client_id (:client-id a-client-config-fixture)
                                :redirect_uri (oauth2-util/format-config-uri a-client-config-fixture)}}

   :access-token-uri {:url "http://aclient.com/get-access-token"
                      :query {:client_id (a-client-config-fixture :client-id)
                              :client_secret (a-client-config-fixture :client-secret)
                              :redirect_uri (oauth2-util/format-config-uri a-client-config-fixture)}}})

(def b-client-config-fixture
  {:client-id "a-client-id"
   :client-secret "a-client-secret"
   :callback {:domain "http://127.0.0.1" :path "/auth/b"}})

(def b-uri-config-fixture
  {:authentication-uri {:url "http://bclient.com/authenticate"
                        :query {:client_id (:client-id b-client-config-fixture)
                                :redirect_uri (oauth2-util/format-config-uri b-client-config-fixture)}}

   :access-token-uri {:url "http://bclient.com/get-access-token"
                      :query {:client_id (b-client-config-fixture :client-id)
                              :client_secret (b-client-config-fixture :client-secret)
                              :redirect_uri (oauth2-util/format-config-uri b-client-config-fixture)}}})

(def providers
  {:a {:client-config a-client-config-fixture
       :uri-config a-uri-config-fixture}
   :b {:client-config b-client-config-fixture
       :uri-config b-uri-config-fixture}})

;; http://tools.ietf.org/html/draft-ietf-oauth-v2-31#section-5.1
(def access-token-response-fixture
  (-> "{\"access_token\": \"my-access-token\"}"
      response
      (content-type "application/json")))

(def identity-fixture
  {:identity "my-access-token"
   :access_token "my-access-token"})
