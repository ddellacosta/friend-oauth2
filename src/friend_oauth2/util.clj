(ns friend-oauth2.util
  (:require
   [cheshire.core :refer [parse-string]]
   [ring.util.codec :as ring-codec]
   [ring.util.request :as request]
   [clojure.string :refer [split join]]
   [crypto.random :as random]))

(defn format-config-uri
  "Formats URI from domain and path pairs in a map"
  [{{:keys [domain path]} :callback}]
  (str domain path))

(defn format-authn-uri
  "Formats the client authentication uri"
  [{{:keys [query url]} :authentication-uri} anti-forgery-token]
  (->> (assoc query :state anti-forgery-token)
       ring-codec/form-encode
       (str url "?")))

(defn replace-authz-code
  "Formats the token uri with the authorization code"
  [{:keys [query]} code]
  (assoc-in query [:code] code))

;; http://tools.ietf.org/html/draft-ietf-oauth-v2-31#section-5.1
(defn extract-access-token
  "Returns the access token from a JSON response body"
  [{body :body}]
  (-> body (parse-string true) :access_token))

(defn get-access-token-from-params
  "Alternate function to allow retrieve
   access_token when passed in as form params."
  [{body :body}]
  (-> body ring.util.codec/form-decode (get "access_token")))

(defn extract-anti-forgery-token
  "Extracts the anti-csrf state key from the response"
  [{session :session}]
  (if-let [pairs (first (filter #(= (second %1) "state") session))]
    (-> pairs first name)))

(defn generate-anti-forgery-token
  "Generates random string for anti-forgery-token."
  []
  (-> (random/base64 60) (split #"/") join))

(defn get-provider
  [request multi-config]
  (let [path (request/path-info request)
        providers (:providers multi-config)]
    
    (first (first (filter #(= path
                       (get-in (second %) [:client-config :callback :path])) providers)))))
