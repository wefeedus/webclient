(ns wefeedus.core
  (:require [ajax.core :refer [GET PUT DELETE POST]]
            [reagent.core :as r]
            [clojure.string :as str]
            [cljs-time.core :as t]
            [cljs-time.format :as tf]
            [cljs-time.coerce :as tc]

            ["@material-ui/core" :refer [Avatar Button TextField FormControlLabel
                                         Checkbox Link Grid Box Typography Container
                                         Switch List ListItem ListItemIcon ListItemText
                                         AppBar Toolbar
                                         ThemeProvider createMuiTheme]]
            ["@material-ui/lab" :refer [ToggleButton ToggleButtonGroup]]
            ["@material-ui/core/colors" :refer [green purple red grey yellow
                                                pink deepPurple deepOrange orange indigo]]
            ["leaflet" :refer [Icon]]
            ["react-leaflet" :refer [Map, Marker, Popup, TileLayer]]))


(def remote "wefeedus.topiq.es:3000")

(def mui-formatter (tf/formatter "yyyy-MM-dd")) 


(def empty-event {:url ""
                  :short-summary ""
                  :position [51.02 -0.04]
                  :tags #{}
                  :img-url ""
                  :date (t/now)})

(def default-tags #{:intervention :market-failure :destruction
                    :oversupply :shortage :hunger
                    :bankruptcy :food})

(defonce state (r/atom {:map {:time-selected [(t/date-time 2020) (t/now)]
                              :tags default-tags}
                        :tags default-tags
                        :events []
                        :add-event empty-event
                        :view-state :map}))



(def initial-events
  [{
    :date #inst "2020-04-30T20:21:49.778-00:00",
    :position [43.306 -114.067],
    :short-summary "Over supply of potatoes dumped.",
    :tags #{:market-failure, :destruction :oversupply :food},
    :url "https://twitter.com/idahomolly/status/1253138993811619841"}
   {
    :date #inst "2020-04-30T20:30:04.064-00:00",
    :position [39.107 -94.676],
    :short-summary "Dairy Farmers of America (DFA) estimates 3.7 million gallons of Milk are dumped each day.",
    :tags #{:market-failure :destruction :oversupply :food},
    :url "https://www.nytimes.com/2020/04/11/business/coronavirus-destroying-food.html"}
   #_{
    :date #inst "2020-04-23T00:00:00.000-00:00",
    :position [46.2019 6.1462],
    :short-summary "WTO members pledge to ensure well functioning global food supply chain.",
    :tags #{:intervention :food},
    :url "https://ec.europa.eu/info/news/coronavirus-eu-and-21-other-wto-members-pledge-ensure-well-functioning-global-food-supply-chains-2020-apr-23_en"}])




(defn fetch-events []
  (POST (str "http://" remote "/q")
        {:handler (fn [r]
                    (.log js/console "new events fetched" (pr-str r))
                    (swap! state assoc-in [:events] r))
         ;; TODO with . the return value is a messed up sequence of kv-pairs
         :params {:query '[:find (pull ?e [:*]) :where [?e :url ?b]]}
         :headers {"Content-Type" "application/transit+json"
                   "Accept" "application/transit+json"}}))

(defn transact-events [data]
  (POST (str "http://" remote "/transact")
        {:handler (fn [_]
                    (.log js/console "fetching after transact")
                    (fetch-events))
         :params {:tx-data data}
         :headers {"Content-Type" "application/transit+json"
                   "Accept" "application/transit+json"}}))

(comment
  (transact-events initial-events)

  (get-in @state [:add-event])

  )

(comment
  ;; TODO use marker colors in dependence of tag
  ;; NOTE this approach will not work, because we should calculate tag colors on the fly
  (def red-icon (Icon. #js {:iconUrl "https://cdn.rawgit.com/pointhi/leaflet-color-markers/master/img/marker-icon-red.png",
                            :shadowUrl "https://cdnjs.cloudflare.com/ajax/libs/leaflet/0.7.7/images/marker-shadow.png",
                            :iconSize [25, 41],
                            :iconAnchor [12, 41],
                            :popupAnchor [1, -34],
                            :shadowSize [41, 41]
                            })))

(defn map-component []
  (let [initial [51.505 -0.09]
        {[start-time end-time] :time-selected
         selected-tags :tags} (@state :map)]
    [:div {:style {:width "100%" :height "100%"}}
     (vec
      (concat
       [:> Map {:center initial, :zoom 3
                :style {:height "1000px"}}
        [:> TileLayer {:url "https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
                       #_"https://a.tile.openstreetmap.de/{z}/{x}/{y}.png "
                       #_"http://{s}.tile.openstreetmap.fr/hot/{z}/{x}/{y}.png"
                      #_"http://a.tile.stamen.com/toner/{z}/{x}/{y}.png"
                       :attribution "&copy; <a href=&quot;http://osm.org/copyright&quot;>OpenStreetMap</a> contributors"}]]
       (for [{:keys [url short-summary position img-url tags
                     date]} (map first (@state :events))
             :when (<= start-time (tc/from-date date) end-time)
             :when (some selected-tags tags)
             ]
         [:> Marker {:position position
                     ;:icon red-icon
                     }
          [:> Popup [:div
                     short-summary 
                     [:br]
                     (when-not (empty? img-url)
                       [:img {:src img-url :style {:width "100px"}}])
                     [:div 
                      [:a {:href url :target "_blank"} "source"] " "
                      (tf/unparse mui-formatter (tc/from-date date))]
                     [:div
                      (str/join ", " (map name tags))]
                     ]]])))]
    ))



(defn theme-provider []
  (createMuiTheme (clj->js {:palette #_{:type "dark"} {:type "dark"
                                                     :primary grey
                                                     :secondary purple}
                           ;:typography {:fontFamily ["Ubuntu", "Arial" "sans-serif"]}
                            :status {:danger "red"}})))

(def highlight-color (aget pink 300)) ;; 


(defn main-component []
  [:div.wrapper
   [:> AppBar {:position "static"
               :color highlight-color
               }
       [:> Toolbar
        [:> Typography {:variant "h6"
                        :className "classes.title"
                        :style {:color highlight-color
                                }} "We Feed Us"]
        [:> Button {:on-click #(swap! state assoc :view-state :add-event)
                    :color "inherit"
                    :style {:margin-left "50px"
                            :color highlight-color
                            }}
         "Add event"]
        [:> Button {:color "inherit"
                    :style {:margin-left "20px"
                            :color highlight-color
                            }
                    :on-click (fn [_]
                                (.open js/window "https://t.me/joinchat/BL1x7hgkCwwW8MqOKsFpvg" "_blank")
                                #_(swap! state assoc :view-state :about))}
         "Chat"]
        [:> Button {:color "inherit"
                    :style {:margin-left "20px"
                            :color highlight-color
                            }
                    :on-click (fn [_]
                                (.open js/window "https://github.com/wefeedus/webclient" "_blank")
                                #_(swap! state assoc :view-state :about))}
         "About"]]]
      [:> List
       [:> TextField {:style {:margin-right "10px"}
                      :variant      "outlined"
                      :margin       "normal"
                      :required     false
                      :fullWidth    false
                      :type         "date"
                      :on-change    #(swap! state assoc-in [:map :time-selected 0]
                                           (tf/parse mui-formatter
                                                     (-> % .-target .-value)))
                      :value        (tf/unparse mui-formatter
                                                (get-in @state [:map :time-selected 0]))
                      :id           "start-range"
                      :label        "Start selection"
                      :name         "start-range"
                      :autoComplete "start-range"}]
       [:> TextField {:style {:margin-left "10px"
                              :margin-right "10px"}

                      :variant      "outlined"
                      :margin       "normal"
                      :required     false
                      :fullWidth    false
                      :type         "date"
                      :on-change    #(swap! state assoc-in [:map :time-selected 1]
                                           (tf/parse mui-formatter
                                                     (-> % .-target .-value)))
                      :value        (tf/unparse mui-formatter
                                                (get-in @state [:map :time-selected 1]))
                      :id           "end-range"
                      :label        "End selection"
                      :name         "end-range"
                      :autoComplete "end-range"}]
       (vec (concat
             [:> ToggleButtonGroup {:style {:padding-top "20px"}}]
            (for [t (@state :tags)]
              [:> ToggleButton {:value (name t)
                                :selected (not (nil? ((get-in @state [:map :tags]) t))) 
                                :onClick (fn [e new-value]
                                           (let [op (if ((get-in @state [:map :tags]) t)
                                                      disj conj)]
                                             (swap! state update-in [:map :tags]
                                                    #(op % t))))}
               (name t)])))]
      (map-component)])

(defn add-event-component []
  [:> ThemeProvider {:theme (theme-provider)}
      [:> List {:style {:margin "10px"}}
       [:> TextField {:variant      "outlined"
                      :margin       "normal"
                      :required     true
                      :fullWidth    true
                      :on-change    #(swap! state assoc-in [:add-event :url]
                                           (-> % .-target .-value))
                      :value        (get-in @state [:add-event :url])
                      :id           "url"
                      :label        "URL"
                      :name         "url"
                      :autoComplete "url"
                      :autoFocus    true}]
       [:> Typography {:style {:color "#ffffff"}} "Optional:"]
       [:> TextField {:variant      "outlined"
                      :margin       "normal"
                      :required     false
                      :fullWidth    true
                      :on-change    #(swap! state assoc-in [:add-event :short-summary]
                                           (-> % .-target .-value))
                      :value        (get-in @state [:add-event :short-summary])
                      :id           "short-summary"
                      :label        "One sentence summary"
                      :name         "short-summary"
                      :autoComplete "short-summary"}]
       [:> TextField {:variant      "outlined"
                      :margin       "normal"
                      :required     false
                      :fullWidth    false
                      :type         "date"
                      :on-change    #(swap! state assoc-in [:add-event :date]
                                           (tf/parse mui-formatter
                                                     (-> % .-target .-value)))
                      :value        (tf/unparse mui-formatter
                                                (get-in @state [:add-event :date]))
                      :id           "start-date"
                      :label        "Start date"
                      :name         "start-date"
                      :autoComplete "start-date"}]
       [:> TextField {:variant      "outlined"
                      :margin       "normal"
                      :required     false
                      :fullWidth    true
                      :on-change    #(swap! state assoc-in [:add-event :tags]
                                           (-> % .-target .-value))
                      :value        (get-in  @state [:add-event :tags])
                      :id           "tags"
                      :label        (str "Tags, e.g. "
                                         (->> (@state :tags)
                                            (map name)
                                            (str/join ", " )))
                      :name         "tags"
                      :autoComplete "tags"}]
       [:> TextField {:variant      "outlined"
                      :margin       "normal"
                      :required     false
                      :fullWidth    true
                      :on-change    #(swap! state assoc-in [:add-event :position 0]
                                           (-> % .-target .-value))
                      :value        (get-in @state [:add-event :position 0])
                      :id           "lon"
                      :label        "Longitude"
                      :name         "Longitude"
                      :autoComplete "longitude"}]
       [:> TextField {:variant      "outlined"
                      :margin       "normal"
                      :required     false
                      :fullWidth    true
                      :on-change    #(swap! state assoc-in [:add-event :position 1]
                                           (-> % .-target .-value))
                      :value        (get-in @state [:add-event :position 1])
                      :id           "lat"
                      :label        "Latitude"
                      :name         "Latitude"
                      :autoComplete "latitude"}]
       [:> TextField {:variant      "outlined"
                      :margin       "normal"
                      :required     false
                      :fullWidth    true
                      :on-change    #(swap! state assoc-in [:add-event :img-url]
                                           (-> % .-target .-value))
                      :value        (get-in @state [:add-event :img-url])
                      :id           "img-url"
                      :label        "Image URL"
                      :name         "image-url"
                      :autoComplete "image-url"}]
       #_[:> ListItem "We will review your submission and add it to the map."]
       ;; TODO proper validation
       [:> Button {:on-click (fn [_]
                               (if (empty? (get-in @state [:add-event :url]))
                                 (js/alert "Please provide URL.")
                                 (let [new-event (-> (@state :add-event)
                                                    (update :date tc/to-date)
                                                    (update :tags #(->> (.split % ",")
                                                                     (map str/trim)
                                                                     (map keyword)
                                                                     set))
                                                    (update :position (fn [[lon lat]]
                                                                        [(js/parseFloat lon)
                                                                         (js/parseFloat lat)])))]
                                   (transact-events [new-event])
                                   (swap! state
                                          #(-> %
                                             (assoc :add-event? false)
                                             (assoc :add-event empty-event)
                                             (assoc :view-state :map))))))}
        "Submit"]
       [:> Button {:on-click (fn [_]
                               (swap! state
                                      #(-> %
                                         (assoc :view-state :map))))
                   :style {:margin-left "30px"}
                   }
        "Abort"]]])


(defn wrapper-component []
  [:> ThemeProvider {:theme (theme-provider)}
   (case (@state :view-state) 
     :add-event
     (add-event-component)

     (main-component))])




(defn init! []
  (print "[main]: initializing...")
  (fetch-events)
  (r/render
   [wrapper-component]
   (js/document.getElementById "root")))

(defn reload! []
  (println "[main]: reloading...")
  (r/render
   [wrapper-component]
   (js/document.getElementById "root")))

(comment

  (all-datoms :eavt)

  (POST "http://localhost:3000/q"
        {:handler (fn [r] (swap! state assoc-in [:last-q] r))
         :params {:query '[:find ?e ?b :where [?e :name ?b]]}
         :headers {"Content-Type" "application/transit+json"
                   "Accept" "application/transit+json"}})

  (init!)

  (reload!)


  (-> @state :tx-input)

  (map first (@state :events))

  (-> (:schema @state) :booar)

  (let [table-headers (->> (:schema @state)
                           keys
                           (filter keyword?)
                           (filter (into #{} (keys core-schema)))
                           (#(conj % :db/id))
                           (into #{})
                           vec)
        schema-attrs ]
    )

  (->> (:schema @state)
       keys
       (filter keyword?)
       (remove (into #{} (keys core-schema)))
       (into #{})
       vec)



)

