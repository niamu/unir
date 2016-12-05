# unir

`Unir` is Spanish for `Unite`
>to join together to do or achieve something

Unir is a Clojure(script) application that automates TV and Movie sorting.

## Overview

`resources/config.yaml` needs to be filled in with all relevant paths for
Transmission and Flexget.

You'll need to create an application on Trakt.tv. These scripts assume that
you'll want all of your shows in the quality of 720p. Shows are added to
Flexget's `resources/series.yaml` which will be comprised of TV shows that
you've already seen or have added to your watchlist on Trakt.tv. Any shows that
you want removed can be added to a list called `dropped-shows`.

`script/sort.sh` is triggered by Transmission`s script on torrent completion.
Torrents media types are determined by trackers currently which aids the sorting
functions.

## Usage

Use `script/sort.sh` as the Transmission script on torrent completion and create
a scheduled task to execute `script/flexget.sh` at your preferred interval. This
assumes that you've already configured Flexget to run on its own prior to
setting up these scripts.
