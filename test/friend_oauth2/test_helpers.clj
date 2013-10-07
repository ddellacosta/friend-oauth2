(ns friend-oauth2.test-helpers
  (:use
   compojure.core
   friend-oauth2.fixtures)
  (:require
   [clojure.string :refer [split]]
   [friend-oauth2.workflow :as oauth2]
   [cemerick.friend :as friend]
   [cemerick.url :refer [url]]
   [compojure.handler :as handler]
   [ring.util.response :refer [get-header]]
   [ring.mock.request :as ring-mock]))

(defn extract-cookie
  "Extracts cookie from headers in response and returns map of contents."
  [response]
  (let [cookie-header (get-header response "Set-Cookie")
        cookie-strs   (-> cookie-header first (split #";"))]
    (into {} (map #(split % #"=") cookie-strs))))

(defn extract-ring-session-val
  "Returns ring-session value from Set-Cookie
   header in a ring response."
  [response]
  (get (extract-cookie response) "ring-session"))

(defn make-cookie-request
  "Wraps ring-request with hash-map formatted
   properly to pass a ring-session cookie."
  [request cookie-val]
  (assoc-in request [:cookies "ring-session" :value] cookie-val))

(defn extract-state-from-redirect-url
  "Parses the response's Location redirect url's query string
   to get the 'state' value passed to the OAuth2 endpoint server
   on the authentication request. (Whew.)"
  [response]
  (let [location (-> response (get-header "Location") url)]
    (get-in location [:query "state"])))


;; The following provides testing for ring requests/responses
;; via a "real" friend-authorized/authenticated app.

(declare test-app)

(defn make-session-get-request
  [path params ring-session-val]
  (-> (ring-mock/request :get path params)
      (make-cookie-request ring-session-val)
      test-app))

(defroutes test-app-routes
  (GET "/authlink" request
       (friend/authorize #{::user} "Authorized page.")))

(def test-app
  (handler/site
   (friend/authenticate
    test-app-routes
    {:allow-anon? true
     :workflows [(oauth2/workflow
                  {:client-config client-config-fixture
                   :uri-config uri-config-fixture
                   :credential-fn (fn [token]
                                    {:identity token
                                     :roles #{::user}})})]})))

(defn setup-valid-state
  "Initiates login to provide valid state for later requests.
   Returns hash-map with valid state and ring-session-val."
  []
  (let [response         (test-app (ring-mock/request :get "/login"))
        state            (extract-state-from-redirect-url response)
        ring-session-val (extract-ring-session-val response)]
    {:state state :ring-session-val ring-session-val}))
