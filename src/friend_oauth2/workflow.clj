(ns friend-oauth2.workflow
  (:require [cemerick.friend :as friend]
            [clj-http.client :as client]
            [ring.util.codec :as ring-codec]
            [ring.util.request :as ring-request]
            [cheshire.core :as j]
            [crypto.random :as random]))

(defn format-config-uri
  "Formats URI from domain and path pairs in a map"
  [client-config]
  (reduce
   #(str %1 (get-in client-config [:callback %2]))
   "" [:domain :path]))

(defn format-authentication-uri
  "Formats the client authentication uri"
  [{:keys [authentication-uri]} anti-forgery-token]
  (let [{:keys [url query]} authentication-uri
        params  (-> query
                    (assoc :state anti-forgery-token) ; overrides any :state in query
                    ring-codec/form-encode)]
    (str url "?" params))) ;; TODO: use cemerick/url


(defn replace-authorization-code
  "Formats the token uri with the authorization code"
  [uri-config code]
  (assoc-in (:query uri-config) [:code] code))

;; http://tools.ietf.org/html/draft-ietf-oauth-v2-31#section-5.1
(defn extract-access-token
  "Returns the access token from a JSON response body"
  [response]
  (-> response
      :body
      (j/parse-string true)
      :access_token ))

(defn extract-anti-forgery-token
  "Extracts the anti-csrf state key from the response"
  [response]
  (if-let [state-pairs (first (filter
                               #(= (second %1) "state")
                               (:session response)))]
    (-> state-pairs first name)
    nil))

(defn generate-anti-forgery-token []
  (-> 60
      random/base64
      (clojure.string/split  #"/")
      clojure.string/join
      keyword))



(defn make-auth
  "Creates the auth-map for Friend"
  [identity]
  (with-meta identity
    {:type ::friend/auth
     ::friend/workflow :email-login
     ::friend/redirect-on-auth? true}))

(defn workflow
  "Workflow for OAuth2"
  [& {:keys [login-uri uri-config client-config
             access-token-parsefn config-auth ] :as config
      :or {login-uri nil
           access-token-parsefn extract-access-token
           config-auth nil}}]

  (fn [request]
    (let [path-info (ring-request/path-info request)]
      ;; If we have a callback for this workflow
      ;; or a login URL in the request, process it.
      (if (some (partial = path-info)
                [(-> client-config :callback :path)
                 login-uri
                 (-> request ::friend/auth-config :login-uri)])

        ;; Steps 2 and 3:
        ;; accept auth code callback, get access_token (via POST)

        ;; http://tools.ietf.org/html/draft-ietf-oauth-v2-31#section-4.1.2
        (let [{:keys [params code session]
               :or {code nil
                    session nil}} request
                    session-state  (extract-anti-forgery-token request)]

          (if (and (not (nil? code))
                   (= (:state params) session-state))

            (let [access-token-uri (:access-token-uri uri-config )
                  token-url (assoc-in access-token-uri [:query]
                                      (merge {:grant_type "authorization_code"}
                                             (replace-authorization-code access-token-uri code)))
                  ;; Step 4:
                  ;; access_token response. Custom function for handling
                  ;; response body is passed in via the :access-token-parsefn
                  access-token (-> token-url
                                   :url
                                   (client/post {:form-params (:query token-url)})
                                   access-token-parsefn)]


              ;; The auth map for a successful authentication:
              (make-auth (merge {:identity access-token
                                 :access_token access-token}
                                config-auth)))

            ;; Step 1: redirect to OAuth2 provider.  Code will be in response.
            (let [anti-forgery-token    (generate-anti-forgery-token)
                  session-with-af-token (assoc session anti-forgery-token "state")]
              (-> uri-config
                  (format-authentication-uri  anti-forgery-token)
                  ring.util.response/redirect
                  (assoc :session session-with-af-token)))))))))

