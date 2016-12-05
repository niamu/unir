(ns clj-trakt.auth
  (:require [cljs-trakt.core :as trakt]))

(defn refresh-token
  [credentials]
  (trakt/request :post credentials [:oauth :token]
                 {:body
                  {:refresh_token (:refresh_token credentials)
                   :client_id (:client_id credentials)
                   :client_secret (:client_secret credentials)
                   :redirect_uri "urn:ietf:wg:oauth:2.0:oob"
                   :grant_type "refresh_token"}}))
