#!/usr/bin/python
import json
import yaml
import re

from automation import Trakt

CFG = "config.yaml"


class Flexget(object):

  def __init__(self, file_):
    f = open(file_, 'r')
    y = yaml.load(f)
    f.close()
    self.__dict__.update(y["flexget"])
    self.build()

  def build(self):
    collection = list(Trakt.get_watched("show") +
                      Trakt.get_watchlist("show"))
    shows = self.filter_shows(collection)

    data = yaml.dump({'series' : {'720p' : shows}},
                      allow_unicode=True,
                      default_flow_style=False)

    f = open(self.destination, 'w')
    f.write(data)
    f.close()

  def is_returning(self, slug):
    show = Trakt.query("shows/" + slug + "?extended=full")
    if (show["status"] == "returning series" or
        show["status"] == "in production"):
      return True
    return False

  def filter_shows(self, shows):
    filtered_shows = []
    dropped_shows = self.get_dropped()
    for show in shows:
      if (show["show"]["title"] not in dropped_shows and
          self.is_returning(show["show"]["ids"]["slug"])):
        show_stripped = re.sub(" \(\)| \(US\)", "",
                          re.sub("[0-9]{4}","",
                            show["show"]["title"]))
        filtered_shows.append(show_stripped)
    return filtered_shows

  def get_dropped(self):
    dropped_shows = Trakt.query("users/me/lists/" +
                                self.dropped_slug + "/items")
    return (list(map(lambda show: show["show"]["title"], dropped_shows)))


if __name__ == '__main__':
  
  Trakt = Trakt(CFG)
  Flexget = Flexget(CFG)
