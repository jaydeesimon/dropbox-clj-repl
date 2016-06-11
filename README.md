# Dropbox Clojure REPL

A Clojure library designed to maximize your Dropbox account usage from a REPL.

# Usage
* Create a Dropbox account, if you don't already have one and generate an access token. See **How to Create an Access Token** below.
* In your project.clj, add the dropbox-repl dependency. The latest version is
```
[dropbox-repl "0.1.0"]
```
* In your project.clj, add the lein-environ plugin. Your project.clj should look something like this:

```clojure
(defproject example-dropbox-scripts "0.1.0-SNAPSHOT"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [com.jaydeesimon/dropbox-repl "0.1.0"]]
  :plugins [[lein-environ "1.0.3"]])
```
* Create a file called **profiles.clj** in the root directory of your project with the following contents.

```clojure
{:dev {:env {:access-token "YOUR_64_CHAR_GENERATED_DROPBOX_ACCESS_TOKEN_GOES_HERE"}}}
```
Now let's see if it worked. In the root of the project directory, start a REPL using Leiningen and try to execute the **(get-current-account)** function. See below.

```sh
$ lein repl

dropbox-repl.core=> (get-current-account)
{:email "tismyemail@gmail.com", :account_type {:.tag "pro"}, :disabled false, :account_id "dbid:AACS-nNMCgsomedropboxidmkB9skqsx1Y", :is_paired false, :locale "en", :name {:given_name "Jeffrey", :surname "Simon", :familiar_name "Jeffrey", :display_name "Jeffrey Simon"}, :email_verified true, :referral_link "https://db.tt/tismyreferrallink", :country "US"}
```

Hopefully, it worked! If not, please contact me by opening an issue. I will be happy to help.


# How to Create an Access Token

0. Create a Dropbox account. Better instructions with pictures to come!
1. [Create an app on Dropbox](https://www.dropbox.com/developers/apps). 
2. Make sure you choose the following options from **Create a new app on the Dropbox Platform** form:
	1. Choose an API - **Dropbox API**
	2. Choose the type of access you need - **Full Dropbox**
	3. Name your app - I believe the name needs to be unique across all Dropbox apps so something like **\<Your Full Name\>'s Clojure REPL** would work fine.
3. After you've created the app, select it, and go to the **Settings** tab.
4. Find the **OAuth 2** subsection and in that section there should be something named **Generated access token** with a button labeled **Generate**.
5. Press the **Generate** button which should yield a string of 64 characters.


# Examples

* Sum the size of a directory

```clojure
(reduce +' (map :size (list-entries "/Camera Uploads")))
```

* Find the largest mp4s in your entire account

```clojure
(sort-by :size > (filter #(clojure.string/ends-with? (:path_lower %) "mp4") (list-entries "/")))
```

Feel free to pass on some more useful snippets.

# Future Work
I spit this project out in a day so I'm sure there's much more you could do with it. There are a lot of API endpoints that are not included but that's mostly because I didn't find them useful for my own needs. There's no reason they can't be added in the future. Feel free to send me pull requests.
