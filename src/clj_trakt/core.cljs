(ns clj-trakt.core
  (:require [planck.http :as http]
            [unir.json :as json]
            [clojure.string :refer [split]]))

(defn date
  "Construct Trakt date format yyyy-MM-dd"
  [[^Number year ^Number month ^Number day]]
  (apply str (interpose "-" [year month day])))

(defn url
  [^String domain & [path]]
  (str "https://" domain "/"
       (->> path
            (remove #(or (nil? %) (= "" %)))
            (map #(if (keyword? %) (name %) %))
            (interpose "/")
            (apply str))))

(declare request)

(defn- handle
  [response retry]
  (condp = (:status response)
    200 (try
          (json/read-str (:body response))
          (catch js/Error e
            (js/setTimeout (fn [] (apply request retry)) 2500)))
    (throw (js/Error. response))))

(defn request
  ([^clojure.lang.Keyword method credentials path]
   (request method credentials path nil handle))
  ([^clojure.lang.Keyword method credentials path data]
   (request method credentials path data handle))
  ([^clojure.lang.Keyword method credentials path data handler-fn]
   (let [headers {"Content-Type" "application/json"}
         options {:headers
                  (if (:access_token credentials)
                    (merge headers
                           {"Authorization" (str "Bearer "
                                                 (:access_token credentials))
                            "trakt-api-key" (:client_id credentials)
                            "trakt-api-version" 2})
                    headers)}]
     (-> ((condp = method
            :get http/get
            :post http/post
            http/get)
          (url (:domain credentials) path)
          (merge (if (contains? data :body)
                   (assoc data :body (json/write-str (:body data)))
                   data)
                 options))
         (handler-fn [method credentials path data handler-fn])))))

(defn refresh-token
  [credentials]
  (request :post credentials [:oauth :token]
           {:body
            {:refresh_token (:refresh_token credentials)
             :client_id (:client_id credentials)
             :client_secret (:client_secret credentials)
             :redirect_uri "urn:ietf:wg:oauth:2.0:oob"
             :grant_type "refresh_token"}}))
