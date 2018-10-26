(ns friend-oauth2.workflow
  (:require [cemerick.friend :as friend]
            [clj-http.client :as client]
            [clojure.tools.logging :as log]
            [clojusc.twig :as logger]
            [friend-oauth2.config :as config]
            [friend-oauth2.util :as util]
            [ring.util.request :as request]))

(defn default-credential-fn
  [creds]
  {:identity (:access-token creds)})

(defn is-oauth2-callback?
  [config req]
  (or (= (request/path-info req)
         (get-in config [:client-config :callback :path]))
      (= (request/path-info req)
         (or (:login-uri config)
             (-> req
                 ::friend/auth-config
                 :login-uri)))))

(defn request-token
  "POSTs request to OAauth2 provider for authorization token."
  [{:keys [uri-config access-token-parsefn]} code]
  (let [access-token-uri (:access-token-uri uri-config)
        query-map        (-> (util/replace-authz-code access-token-uri code)
                             (assoc :grant_type "authorization_code"))
        token-parse-fn   (or access-token-parsefn util/extract-access-token)]
    (log/debug "Requesting token ...")
    (log/debug "access-token-uri:" access-token-uri)
    (log/trace "query-map:" query-map)
    (token-parse-fn
      (client/post (:url access-token-uri)
                   {:form-params query-map
                    ;; XXX pass additional opts to client/*
                    ;;     here, e.g., :basic-auth [user pass]
                    }))))

(defn redirect-to-provider!
  "Redirects user to OAuth2 provider.

  The `code` parameter should be in the response."
  [{:keys [uri-config]} request]
  (log/debug "Redirecting to provider ...")
  (let [anti-forgery-token (util/generate-anti-forgery-token)
        session-with-af-token (assoc (:session request) :state anti-forgery-token)]
    (log/debug "anti-forgery-token:" anti-forgery-token)
    (log/debug "session-with-af-token:" session-with-af-token)
    (-> uri-config
        (util/format-authn-uri anti-forgery-token)
        ring.util.response/redirect
        (assoc :session session-with-af-token))))

(defn process-config?
  "Check whether the config needs to be processed."
  [workflow-args]
  (and (not (nil? (:config workflow-args)))
       (and (nil? (:uri-config workflow-args))
            (nil? (:client-config workflow-args)))))

(defn not-process-config?
  "Check whether the config doesn't need to be processed."
  [workflow-args]
  (not (process-config? workflow-args)))

(defn process-config
  "This function allows workflow to support legacy parameters `:client-config`
  and `uri-config` while also supporting the new configuration key `:config`.
  It does this by generating the legacy configurations when the new
  configuration data is present. It not, it assumes that legacy configuration
  is being utilized."
  [workflow-args]
  (log/debug "Preparing to process config ...")
  (let [cfg (:config workflow-args)]
    (if (not-process-config? workflow-args)
      workflow-args
      (assoc
        workflow-args
        :client-config (config/->client-cfg cfg)
        :uri-config (config/->uri-cfg cfg)))))

(defn- apply-workflow
  "Workflow for OAuth2"
  [cfg request]
  (when (is-oauth2-callback? cfg request)
    ;; Extracts code from request if we are getting here via OAuth2 callback.
    ;; http://tools.ietf.org/html/draft-ietf-oauth-v2-31#section-4.1.2
    (let [{:keys [state code error]} (:params request)
          session-state (util/extract-anti-forgery-token request)]
      (log/debug "state:" state)
      (log/debug "code:" code)
      (log/debug "error:" error)
      (log/debug "session-state:" session-state)
      (if (and (not (nil? code))
               (= state session-state))
        (when-let [access-token (request-token cfg code)]
          (when-let [auth-map ((:credential-fn cfg
                                               default-credential-fn)
                               {:access-token access-token})]
            (vary-meta auth-map merge {::friend/workflow :oauth2
                                       ::friend/redirect-on-auth? true
                                       :type ::friend/auth})))
        (let [auth-error-fn (:auth-error-fn cfg)]
          (if (and error auth-error-fn)
            (auth-error-fn error)
            (redirect-to-provider! cfg request)))))))

(defn workflow
  "Workflow for OAuth2"
  [args]
  (let [cfg (process-config args)]
    (log/trace "Config:\n" (logger/pprint cfg))
    (fn 
      ([request] 
        (log/trace "Request:\n" (logger/pprint request))
        (apply-workflow cfg request))
      ([request respond raise]
        (log/trace "Request:\n" (logger/pprint request))
        (try
          (let [response (apply-workflow cfg request)]
            (respond response))
          (catch Exception e
            (raise e)))))))