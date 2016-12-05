(ns unir.transmission
  (:require [unir.config :refer [config]]
            [unir.json :as json]
            [clojure.string :as string]
            #?(:clj [clj-http.client :as http]
               :cljs [planck.http :as http]))
  #?(:clj (:import [java.lang Exception])))

(def url
  (str "http://"
       (get-in @config [:transmission :hostname])
       ":9091/transmission/rpc"))

(defn rpc
  ([method args]
   (if-let [session-id (try (-> (http/get url)
                                (get-in [:headers :X-Transmission-Session-Id]))
                            (catch #?(:clj Exception :cljs js/Error) e
                              (get-in (.getData e)
                                      [:headers :X-Transmission-Session-Id])))]
     (rpc method args session-id)))
  ([method args sessionid]
   (let [response (http/post url
                             {:headers {:X-Transmission-Session-Id sessionid}
                              :body (json/write-str {:method method
                                                     :arguments args})
                              :accept :json
                              :content-type :json})]
     (condp = (:status response)
       409 (rpc method args
                (get-in response [:headers :X-Transmission-Session-Id]))
       (try
         (-> (:body response)
             json/read-str
             :arguments
             :torrents)
         (catch #?(:clj Exception :cljs js/Error) e
           (rpc method args sessionid)))))))

(defn torrents
  [fields]
  (rpc :torrent-get {:fields fields}))

(defn torrent-by-id
  [fields ids]
  (first (rpc :torrent-get {:fields fields :ids ids})))

(defn torrent-by-name
  [fields name]
  (as-> (torrents (conj fields :name)) t
    (filter #(= name (:name %)) t)
    (first t)
    (select-keys t (map keyword fields))))

(defn file-completed?
  [file]
  (= (:bytesCompleted file)
     (:length file)))

(defn show?
  [trackers]
  (let [show-trackers (-> @config :transmission :tracker :show)]
    (-> (some (fn [show-tracker]
                (some #(string/includes? % show-tracker) trackers))
              show-trackers)
        some?)))

(defn movie?
  [trackers]
  (let [movie-trackers (-> @config :transmission :tracker :movie)]
    (-> (some (fn [movie-tracker]
                (some #(string/includes? % movie-tracker) trackers))
              movie-trackers)
        some?)))

(defn media-type
  [torrent]
  (let [trackers (->> torrent :trackers (map :announce))]
    (assoc torrent :media-type
           (cond
             (movie? trackers) :movie
             (show? trackers) :show))))

(defn normalize-torrent
  "Add :clean-name and :media-type"
  [torrent]
  (when (:name torrent)
    (-> torrent
        media-type
        (assoc :clean-name (string/replace (:name torrent) #"[._]" " ")))))
