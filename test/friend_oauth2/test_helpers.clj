(ns friend-oauth2.test-helpers
  (:use
   compojure.core
   friend-oauth2.fixtures)
  (:require
   [friend-oauth2.workflow :as friend-oauth2]
   [cemerick.friend :as friend]
   [cemerick.url :as url]
   [compojure.handler :as handler]
   [ring.mock.request :as ring-mock]))

(defn extract-header
  "Extracts header value from headers in response."
  [header response]
  (-> response :headers (get header)))

(defn extract-cookie
  "Extracts cookie from headers in response."
  [response]
  (first
   (extract-header "Set-Cookie" response)))

(defn extract-ring-session-val
  "Returns ring-session value from Set-Cookie
   header in a ring response."
  [response]
  (let [cookie      (extract-cookie response)
        cookie-vars (mapcat
                     #(clojure.string/split % #"=")
                     (clojure.string/split cookie #";"))]
    (get (apply hash-map cookie-vars) "ring-session")))

(defn make-cookie-request
  "Wraps ring-request with hash-map formatted
   properly to pass a ring-session cookie."
  [request cookie-val]
  (merge
   request
   {:cookies {"ring-session" {:value cookie-val}}}))

(defn extract-location
  "Extracts location from headers from redirect response."
  [response]
  (extract-header "Location" response))

(defn extract-state-from-redirect-url
  "Parses the response's Location redirect url's query string
   to get the 'state' value passed to the OAuth2 endpoint server
   on the authentication request. (Whew.)"
  [response]
  (-> response
      extract-location
      url/url
      :query
      (get "state")))

;; The following provides testing for ring requests/responses
;; via a "real" friend-authorized/authenticated app.

(declare test-app)

(defn make-ring-session-get-request
  [path params ring-session-val]
  (test-app
   (make-cookie-request
    (ring-mock/request :get path params)
    ring-session-val)))

(defroutes test-app-routes
  (GET "/authlink" request
       (friend/authorize #{::user} "Authorized page.")))

(def test-app
  (handler/site
   (friend/authenticate
    test-app-routes
    {:allow-anon? true
     :workflows [(friend-oauth2/workflow
                  :client-config client-config-fixture
                  :uri-config uri-config-fixture
                  :config-auth {:roles #{::user}})]})))

(defn setup-valid-state
  "Initiates login to provide valid state for later requests."
  []
  (let [response         (test-app (ring-mock/request :get "/login"))
        state            (extract-state-from-redirect-url response)
        ring-session-val (extract-ring-session-val response)]
    {:state state :ring-session-val ring-session-val}))
