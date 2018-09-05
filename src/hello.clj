(ns hello
  (:require [clojure.data.json :as json]
            [io.pedestal.http :as http]
            [io.pedestal.http.route :as route]
            [io.pedestal.http.content-negotiation :as conneg]))

;hash-map de mots interdit
(def unmentionable #{"Voldemort" "Sarbacane" "Osborne" "Flagada"})

;definition des types supportés
(def supported-types ["text/html" "application/edn" "application/json" "text/plain"])

;on passe les types supportés a la fonctions de negotiation.
(def content-neg-intc (conneg/negotiate-content supported-types))

;-------------------------------------------------------------------------------------------------

;validation de la requete qui prend en paramètre le body de la resp.
(defn ok [body]
  {:status 200 :body body
   :headers {"Content-Type" "text/html"}}) ;on force l'intercepteur de servlet a de definir le type de contenu "text/html"

;fonction simple de verification que le params fournis par le client n'est pas vide
;plus facile a tester du fait qui soit extrait et autonome par rapport au gestionnaire.
(defn greeting-for [nm]
  (cond                                                     ;condition macro
    (unmentionable nm) nil                                  ;si le params fait partie des mots interdit alors il vaut nil
    (empty? nm)        "Hello, world!\n"                    ;sinon si le params est vide alors on affiche "Hello, world!".
    :else              (str "Hello, " nm "\n")))            ;sinon on affiche "Hello " params

;gestionnaire qui coordonne le reste.
(defn respond-hello [request]
  (let [nm (get-in request [:query-params :name])
        resp (greeting-for nm)]
    (if resp                                                ;si il y a une reponse
      (ok resp)                                             ;on appel la fonction (ok)
      )))                                                   ;sinon on retourne (not-found)


;définition de echo comme interceptor
(def echo
  {:name ::echo                                             ;on donne un nom a l'interceptor pour nous aider au deboggage
   :enter (fn [context]                                     ;on créer une fonction d'entrée qui prend un context en paramètre
            (let [request (:request context)                ;on extrait la map de requete de la map de context
                  response (ok request)]                    ;on prend la "request" que l'on a extrait précedemment et on en fait une réponse.
              (assoc context :response response)))})        ;enfin on attache la réponse à notre context


;interceptor qui permet de gerer les differents changement de content type de façon dynamique
(def coerce-body
  {:name ::coerce-body
   :leave
         (fn [context]
           (let [accepted   (get-in context [:request :accept :field] "text/plain")
                 response   (get context :response)
                 body       (get response :body)
                 coerce-body (case accepted
                               "text/html"        body
                               "text/plain"       body
                               "application/edn"  (pr-str body)
                               "application/json" (json/write-str body))
                 update-response (assoc response
                                   :headers {"Content-Type" accepted}
                                   :body    coerce-body)]
             (assoc context :response update-response)))})

;----------------------------------------------------------------------------------------------------------

;definition des routes
(def routes
  (route/expand-routes
    #{["/greet" :get [coerce-body content-neg-intc respond-hello] :route-name :greet]     ;cet route a un vecteur d'intercepteur a invoquer
      ["/echo" :get echo]}))

;----------------------------------------------------------------------------------------------------------

;connection au serveur
(defn create-server []
  (http/create-server
    {::http/routes routes
     ::http/type :jetty
     ::http/port 8090}))


;lancement du serveur
(defn start []
  (http/start (create-server)))
