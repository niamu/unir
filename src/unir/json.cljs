(ns unir.json)

(defn write-str
  [s]
  (.stringify js/JSON (clj->js s)))

(defn read-str
  [s]
  (js->clj (.parse js/JSON s) :keywordize-keys true))
