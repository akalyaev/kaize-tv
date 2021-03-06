(ns kaize-tv.core
  (:require-macros [cljs.core.async.macros :refer [go alt!]])
  (:require [goog.events :as events]
            [cljs.core.async :refer [put! <! >! chan timeout]]
            [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [cljs-http.client :as http]
            [clojure.string :as string]))

(enable-console-print!)

(def app-state
  (atom {}))

(defn fetch-presentations
  [url]
  (let [c (chan)]
    (go (let [{presentations :body} (<! (http/get url))]
          (>! c (vec presentations))))
    c))

(defn presentation [presentation owner opts]
  (om/component
   (dom/div nil
            (dom/h1 nil (:title presentation))
            (dom/p nil (str "by "
                            (string/join ", " (map (fn [s] (:title s)) (:speakers presentation) ))
                            " at "
                            (:title (:location presentation) )))
            (dom/p nil (:description presentation))
            (dom/ul nil
                    (apply (fn [t] (dom/li nil (:title t))) (:tags presentation)) ))))

(defn presentation-list [{:keys [presentations]}]
  (om/component
    (apply dom/div nil
           (om/build-all presentation presentations))))

(defn presentation-box [app owner opts]
  (reify
    om/IInitState
    (init-state [_]
                (do
                  (om/transact! app [:presentations] (fn [] []))
                  {}))
    om/IWillMount
    (will-mount [_]
                (go (while true
                      (let [presentations (<! (fetch-presentations (:url opts)))]
                        (.log js/console (pr-str presentations))
                        (om/update! app [:presentations] presentations))
                      (<! (timeout (:poll-interval opts))))))
    om/IRender
    (render [_]
      (dom/div nil
               (dom/h1 nil "Presentations")
               (om/build presentation-list app)))))

(defn om-app [app owner]
  (reify
    om/IRender
    (render [_]
      (dom/div nil
               (om/build presentation-box app
                         {:opts {:url "/api/presentations"
                                 :poll-interval 2000}})))))

(om/root om-app app-state
         {:target (. js/document (getElementById "content"))})
