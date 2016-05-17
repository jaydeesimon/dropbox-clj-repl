# Dropbox Clojure REPL

I'm getting a lot of mileage out of my Dropbox account lately and it's working out great. Unfortunately, it's getting difficult to manage all of the crap that gets uploaded there.

I'm envisioning this project as a starting point for helping me manage that crap.

# Usage

0. Create a Dropbox account. Better instructions with pictures to come!
1. [Create an app on Dropbox](https://www.dropbox.com/developers/apps). 
2. Make sure you choose the following options from **Create a new app on the Dropbox Platform** form:
	1. Choose an API - **Dropbox API**
	2. Choose the type of access you need - **Full Dropbox**
	3. Name your app - I believe the name needs to be unique across all Dropbox apps so something like **\<Your Full Name\>'s Clojure REPL** would work fine.
3. After you've created the app, select it, and go to the **Settings** tab.
4. Find the **OAuth 2** subsection and in that section there should be something named **Generated access token** with a button labeled **Generate**.
5. Press the **Generate** button which should yield a string of 64 characters.
6. Create a file called **profiles.clj** (it will not be tracked by git) in the root dir of the project and add the following contents. Make sure to substitute in your access token.

```
{:dev {:env {:access-token "YOUR_64_CHAR_GENERATED_DROPBOX_ACCESS_TOKEN_GOES_HERE"}}}
```

Now let's see if it worked. In the root of the project directory, start a REPL using Leiningen and try to execute the **(get-current-account)** function. See below.

```
$ lein repl

dropbox-repl.core=> (get-current-account)
{:email "tismyemail@gmail.com", :account_type {:.tag "pro"}, :disabled false, :account_id "dbid:AACS-nNMCgsomedropboxidmkB9skqsx1Y", :is_paired false, :locale "en", :name {:given_name "Jeffrey", :surname "Simon", :familiar_name "Jeffrey", :display_name "Jeffrey Simon"}, :email_verified true, :referral_link "https://db.tt/tismyreferrallink", :country "US"}
```

Hopefully, it worked for you!

# Examples

* Sum the size of a directory

```
(reduce +' (map :size (list-entries "/Camera Uploads")))
```

* Find the largest mp4s in your entire account

```
(sort-by :size > (filter #(clojure.string/ends-with? (:path_lower %) "mp4") (list-entries "/")))
```

Feel free to pass on some more useful snippets.

# Future Work
I spit this project out in a day so I'm sure there's much more you could do with it. There are a lot of API endpoints that are not included but that's mostly because I didn't find them useful for my own needs. There's no reason they can't be added in the future. Feel free to send me pull requests.
