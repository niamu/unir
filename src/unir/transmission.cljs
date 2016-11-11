(ns unir.transmission
  (:require [unir.config :refer [config]]
            [unir.json :as json]
            [clojure.string :as string]
            [planck.http :as http]))

(def url "http://10.0.0.10:9091/transmission/rpc")

(defn rpc
  ([method args]
   (let [response (http/get url)]
     (rpc method args
          (get-in response [:headers :X-Transmission-Session-Id]))))
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
         (if-let [r (-> (:body response)
                        json/read-str
                        :arguments
                        :torrents)]
           (if-not (empty? r)
             r
             (rpc method args sessionid))
           (rpc method args sessionid))
         (catch js/Error e
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
  (filter #(= (:bytesCompleted %)
              (:length %))
          file))

(defn show?
  [trackers]
  (let [show-trackers (-> config :transmission :tracker :show)]
    (-> (some (fn [show-tracker]
                (some #(string/includes? % show-tracker) trackers))
              show-trackers)
        some?)))

(defn movie?
  [trackers]
  (let [movie-trackers (-> config :transmission :tracker :movie)]
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
