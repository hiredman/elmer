(ns elmer.store.fs
  (:require [clojure.tools.logging :as log])
  (:use [clojure.java.io :only [file]]
        [elmer.util :only [make-dir delete-dir]]
        [elmer.store :only [PasteStore]]))

(defmacro with-tmp-fs-store [[store dir keep] & body]
  `(let [dir# ~dir
         ~store (fs-store {:publish-root dir#
                           :key-root dir#})]
     (make-dir dir#)
     ~@body
     (when-not keep
       (delete-dir dir#))))

(defn get-key [key-root name]
  (-> (format "%s/%s.key" key-root name) file slurp .trim))

(defn key-exists? [key-root name]
  (-> (format "%s/%s.key" key-root name) file .isFile))

(defn key-valid? [key-root name key]
  (= key (get-key key-root name)))

(defn store-key [key-root name key]
  (let [keyfile (-> (format "%s/%s.key" key-root name)
                    file .getAbsolutePath)]
    (log/debug "store" keyfile)
    (spit keyfile key)))

(defn getf [root _ name]
  (let [path (format "%s/%s" root name)
        _ (log/debug "load" (-> path file .getAbsolutePath))]
    (when (.exists (java.io.File. path))
      (slurp path))))

(defn authorized?f [root key-root name key]
  (let [yes? (if (key-exists? key-root name)
               (key-valid? key-root name key)
               true)
        keyfile (-> (format "%s/%s" key-root name) file .getAbsolutePath)]
    (log/debug "auth" keyfile "->" (if yes? "YES" "NO"))
    yes?))

(defn putf [root key-root name key is]
  (let [f (file (format "%s/%s" root name))]
    (when (authorized?f root key-root name key)
      (store-key key-root name key)
      (log/debug "store" (-> root file .getAbsolutePath) name)
      (clojure.java.io/copy is f :buffer-size 4096)
      (.length f))))

(deftype FsStore [root key-root]
  PasteStore
  (get [_ name]
    (getf root key-root name))
  (put [_ name key is]
    (putf root key-root name key is))
  (authorized? [_ name key]
    (authorized?f root key-root name key)))

(defn fs-store [opts]
  (log/debug "pastes" (:publish-root opts)
             "keys" (:key-root opts))
  (make-dir (:publish-root opts))
  (make-dir (:key-root opts))
  (FsStore. (:publish-root opts) (:key-root opts)))
