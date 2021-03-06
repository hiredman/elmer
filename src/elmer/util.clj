(ns elmer.util
  (:use [clojure.java.io :only [file]]))

(defn test-dir []
  (str "tmp/pastes-"
       (.substring
        (str (java.util.UUID/randomUUID)) 0 8)))

(defn make-dir [dir]
  (.mkdirs (file dir)))

(defn delete-dir [dir]
  (let [f (file dir)]
    (if (.isDirectory f)
      (do
        (doseq [g (reverse (file-seq f))]
          (.delete g))
        true)
      false)))
