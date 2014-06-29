(ns friend-oauth2.workflow
  (:require
   [friend-oauth2.util :as util]
   [cemerick.friend :as friend]
   [clj-http.client :as client]
   [ring.util.request :as request]))

(defn- default-credential-fn
  [creds]
  {:identity (:access-token creds)})

(defn- is-oauth2-callback?
  [config request]
  (or (= (request/path-info request)
         (get-in config [:client-config :callback :path]))
      (= (request/path-info request)
         (or (:login-uri config) (-> request ::friend/auth-config :login-uri)))))

(defn- request-token
  "Exchanges authorization code for access token & refresh token if provided. Response
   from provider is parsed with response-parse-fn if provided, otherwise access-token-parsefn
   is used if given."
  [{:keys [uri-config access-token-parsefn response-parse-fn]} code]
  (let [access-token-uri (:access-token-uri uri-config)
        query-map        (assoc (util/replace-authz-code access-token-uri code)
                           :grant_type "authorization_code")
        token-parse-fn (or response-parse-fn
                           (util/make-access-token-parser (or access-token-parsefn
                                                                util/extract-access-token)))]
    (token-parse-fn (client/post (:url access-token-uri)
                                 {:form-params query-map}))))

(defn- redirect-to-provider!
  "Redirects user to OAuth2 provider. Code should be in response."
  [{:keys [uri-config]} request]
  (let [anti-forgery-token    (util/generate-anti-forgery-token)
        session-with-af-token (assoc (:session request) (keyword anti-forgery-token) "state")]
    (-> uri-config
        (util/format-authn-uri anti-forgery-token)
        ring.util.response/redirect
        (assoc :session session-with-af-token))))

(defn workflow
  "Workflow for OAuth2"
  [config]
  (fn [request]
    (if (is-oauth2-callback? config request)
      ;; Extracts code from request if we are getting here via OAuth2 callback.
      ;; http://tools.ietf.org/html/draft-ietf-oauth-v2-31#section-4.1.2
      (let [{:keys [state code]} (:params request)
            session-state        (util/extract-anti-forgery-token request)]
        (if (and (not (nil? code))
                 (= state session-state))
          (when-let [token (request-token config code)]
            (when-let [auth-map ((:credential-fn config default-credential-fn)
                                 token)]
              (vary-meta auth-map merge {::friend/workflow :oauth2
                                         ::friend/redirect-on-auth? true
                                         :type ::friend/auth})))
          (redirect-to-provider! config request))))))
