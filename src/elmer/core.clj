(ns elmer.core
  (:require [elmer.store :as store])
  (:require [clojure.tools.logging :as log])
  (:use [clojure.string :only [replace-first]]
        [clojure.contrib.duck-streams :only [slurp*]]
        [compojure.core :only [defroutes GET POST ANY]]
        [elmer.config]
        [elmer.template :only [render-template]]
        [hiccup.core :only [html]]))

(defn get-time []
  (-> (java.util.Date.) .getTime))

(defn unique []
  (format "%s%s" (get-time) (.substring (str (java.util.UUID/randomUUID)) 0 8)))

(defn make-key []
  (let [r (java.security.SecureRandom.)
        bs (byte-array 6)]
    (.nextBytes r bs)
    (.encode (sun.misc.BASE64Encoder.) bs)))

(defn serve-paste [store paste]
  (let [bytes (store/get store paste)]
    (if bytes
      {:status 200
       :headers {"Content-Type" "text/plain"}
       :body bytes}
      {:status 404
       :body (format "%s not found" paste)})))

(defn save-as [request]
  (let [uri (replace-first (:uri request) "/" "")
        uri (if (seq uri) uri nil)]
    (or uri
        (-> request :headers (get "x-save-as"))
        (format "%s.txt" (unique)))) )

(defn post-paste [{:keys [uri body store] :as req}]
  (let [paste (save-as req)
        key (or (-> req :headers (get "x-key"))
                (make-key))
        paste-url (format "%s/%s" (config :public-url) paste)
        body* (slurp* body)
        success {:status 200
                 :headers {"X-Key" key}
                 :body (format "%s %s %s\n" (count body*) key paste-url)}]
    (try
      (if (store/put store paste key body*)
        (do
          (log/info "store" paste (count body*))
          success)
        {:status 401
         :body (format "unauthorized: %s\n" paste)})
      (catch Exception e
        (log/error (type e) (.getMessage e))
        {:status 500
         :body (format "FAIL %s\n" paste)}))))

(defn info-paste [request]
  (render-template "paste.sh" {:url (config :public-url)}))

(defn home [request]
  (html
   [:p {:style "text-align:center;margin-top:200px"}
    [:img
     {:src "/img/ralph2.gif"}]]))

(defn not-found [request]
  (html
   [:h1 (format "Page not found: %s" (:uri request))]))

(defroutes app
  (GET "/:paste.:ext" {{paste "paste"
                        ext "ext"} :params
                        store :store}
       (serve-paste store (format "%s.%s" paste ext)))
  (GET "/sh" request
       (info-paste request))
  (GET "/" request
       (home request))

  (POST "/:paste.:ext" request
        (post-paste request))
  (POST "/" request
        (post-paste request))

  (ANY "*" request
       {:status 404, :body (not-found request)}))
