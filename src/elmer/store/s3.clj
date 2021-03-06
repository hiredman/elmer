(ns elmer.store.s3
  (:require [clojure.tools.logging :as log]
            [aws.sdk.s3 :as s3])
  (:use [elmer.store :only [PasteStore]])
  (:import (org.apache.commons.io.input CountingInputStream)))

(defmacro with-s3-store [[store conf] & body]
  `(let [~store (s3-store ~conf)]
     ~@body))

(defn abs-path [loc root name]
  (format "%s/%s/%s" (:bucket loc) root name))

(defn key-path [key-root name]
  (format "%s/%s.key" key-root name))

(defn store-key [loc key-root name key]
  (s3/put-object (:creds loc)
                 (:bucket loc)
                 (key-path key-root name)
                 key
                 {:content-type "text/plain"}))

(defn get-key [loc key-root name]
  (slurp
   (:content (s3/get-object (:creds loc) (:bucket loc)
                            (key-path key-root name)))))

(defn key-exists? [loc key-root name]
  (s3/object-exists? (:creds loc) (:bucket loc)
                     (key-path key-root name)))

(defn key-valid? [loc key-root name key]
  (= key (get-key loc key-root name)))

(defn authorized?* [loc root key-root name key]
  (let [yes? (if (key-exists? loc key-root name)
               (key-valid? loc key-root name key)
               true)]
    (log/debug "auth" (key-path key-root name) "->" (if yes? "YES" "NO"))
    yes?))

(defn get* [loc pre name]
  (log/debug 'get* name)
  (slurp
   (:content
    (s3/get-object (:creds loc) (:bucket loc) (format "%s/%s" pre name)))))

(defn put* [loc root key-root name key is]
  (let [f (format "%s/%s" root name)]
    (when (authorized?* loc root key-root name key)
      (let [cis (CountingInputStream. is)]
        (store-key loc key-root name key)
        (log/debug "store" (abs-path loc root name))
        (s3/put-object (:creds loc) (:bucket loc)
                       (format "%s/%s" root name) cis)
        (.getByteCount cis)))))

(deftype S3Store [loc root key-root]
  PasteStore
  (get [_ name]
    (get* loc root name))
  (put [_ name key is]
    (put* loc root key-root name key is))
  (authorized? [_ name key]
    (authorized?*)))

(defrecord S3Location [creds bucket])

(defn make-s3loc [m]
  (S3Location. (select-keys m [:access-key :secret-key])
               (:bucket m)))

(defn s3-store [{:keys [access-key secret-key
                        bucket key-pastes key-keys]
                 :as opts}]
  (log/debug (format "S3Store (%s):" access-key)
             "bucket" bucket
             "pastes" key-pastes
             "keys" key-keys)
  (S3Store. (make-s3loc opts) key-pastes key-keys))
