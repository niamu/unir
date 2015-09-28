#!/usr/bin/python
import re
import os
import sys
import shutil
import json
import yaml
import requests
import transmissionrpc

CFG = "config.yaml"


class Transmission(object):

  def __init__(self, file_):
    f = open(file_, 'r')
    self.config = yaml.load(f)
    f.close()
    self.__dict__.update(self.config["transmission"])

    if len(sys.argv) < 2:
      torrent_id = os.environ["TR_TORRENT_ID"]
      self.get_torrent_by_id(torrent_id)
    else:
      torrent_name = sys.argv[1]
      self.get_torrent_by_name(torrent_name)

    self.media = self.get_media()

  def get_torrent_by_id(self, torrent_id):
    rpc = transmissionrpc.Client('localhost', port=9091)
    self.torrent = rpc.get_torrent(torrent_id)

  def get_torrent_by_name(self, name):
    rpc = transmissionrpc.Client('localhost', port=9091)
    for torrent in rpc.get_torrents():
      if torrent.name == name:
        self.torrent = rpc.get_torrent(torrent.id)

  def get_media(self):
    for tracker in self.torrent.trackers:
      for show_tracker in self.tracker["show"]:
        if show_tracker in tracker["announce"]:
          return "show"
      for movie_tracker in self.tracker["movie"]:
        if movie_tracker in tracker["announce"]:
          return "movie"
    return False

  def clean_name(self, name):
    name = re.sub("[._]", " ", name)
    if self.media == "show":
      name = re.sub("[12][0-9]{3}", "", name)
    return name

  def sort(self, source, destination):
    dirname = os.path.dirname(destination)
    if not os.path.exists(dirname):
      os.makedirs(dirname)
    if not os.path.islink(source):
      shutil.move(source, destination)
      os.symlink(destination, source)


class Trakt(object):

  def __init__(self, file_):
    f = open(file_, 'r')
    self.config = yaml.load(f)
    f.close()
    self.__dict__.update(self.config["trakt"])

  def query_auth(self, code):
    values = {
      "code": code,
      "client_id": self.client_id,
      "client_secret": self.client_secret,
      "redirect_uri": self.redirect_uri,
      "grant_type": "authorization_code"
    }
    headers = {
      'Content-Type': 'application/json',
      'trakt-api-version': 2,
      'trakt-api-key': self.client_id
    }
    r = requests.post('https://api-v2launch.trakt.tv/oauth/token', data=json.dumps(values), headers=headers)
    print(r.status_code)
    print(r.text)
    if r.status_code == 200:
      data = json.loads(r.text)

      f = open(CFG, 'w')
      self.config["trakt"]["access_token"] = "Bearer " + data["access_token"]
      self.config["trakt"]["refresh_token"] = data["refresh_token"]
      y = yaml.dump(self.config, default_flow_style=False)
      f.write(y)
      f.close()

      print("Updated Trakt authentication.")
    else:
      print("Refreshing authentication failed...")
      self.auth()

  def auth(self):
    print("Reqesting new auth token.")
    pin = input("Enter PIN from http://trakt.tv/pin/" + self.config["trakt"]["pin"] + ": ")
    query = self.query_auth(str(pin))

  def reauth(self):
    query = self.query_auth(self.refresh_token)

  def query(self, path, params = {}):
    headers = {
      'Content-Type': 'application/json',
      'trakt-api-version': 2,
      'trakt-api-key': self.client_id,
      'Authorization': self.access_token
    }
    r = requests.get('https://' + self.domain + '/' + path,
      params=params,
      headers=headers)
    if r.status_code != 200:
      self.reauth()
    else:
      return json.loads(r.text)

  def process(self):
    collection = list(self.get_watched(Transmission.media) +
                      self.get_watchlist(Transmission.media))
    source = Transmission.downloads + Transmission.torrent.name
    if os.path.isdir(source):
      for filename in os.listdir(source):
        if not os.path.islink(source + "/" + filename):
          if re.compile(".avi|.mkv|.mp4").search(filename):
            self.match(collection, source + "/" + filename, filename)
    else:
      if not os.path.islink(source):
        self.match(collection, source, Transmission.torrent.name)

  def match(self, collection, source, filename):
    for item in collection:
      title = item[Transmission.media]["title"]
      if re.match(self.clean_name(title),
                  Transmission.clean_name(filename),
                  re.I):
        destination = self.get_destination(item, filename)
        Transmission.sort(source, destination)

  def get_destination(self, item, filename):
    extension = os.path.splitext(filename)[1]
    title = item[Transmission.media]["title"]
    if Transmission.media == "show":
      s, e = self.get_production(filename)
      slug = item[Transmission.media]["ids"]["slug"]
      episode = self.get_episode(slug, s, e)
      destination = (Transmission.destination[Transmission.media] +
             title + "/" +
             "Season " + "%01d" % int(s) + "/" +
             title +
             " - S" + "%02d" % int(s) +
             "E" + "%02d" % int(e) + " - " +
             re.sub(': ', ' - ', episode["title"]) + extension)
    elif Transmission.media == "movie":
      destination = (Transmission.destination[Transmission.media] +
             re.sub(': ', ' - ', title) +
             "(" + item[Transmission.media]["year"] + ")" +
             extension)
    return destination

  def get_watched(self, media):
    return self.query('users/me/watched/' + media + "s")

  def get_watchlist(self, media):
    return self.query('users/me/watchlist/' + media + "s")

  def get_production(self, filename):
    production_code = re.sub("[^0123456789]", "",
      re.search("[sS][0-9]+[eE][0-9]+", filename).group())
    season = production_code[:-2]
    episode = production_code[-2:]
    return season, episode

  def get_episode(self, show, season, episode):
    return self.query('shows/' + show +
               '/seasons/' + season +
               '/episodes/' + episode)

  def clean_name(self, name):
    return re.sub('[:()\']|(US)|([12][0-9]{3})', "",
                  re.sub(':[ ]?', ' - ', re.sub("(\. )", " ", name)))


if __name__ == '__main__':

  Trakt = Trakt(CFG)
  Transmission = Transmission(CFG)

  Trakt.process()
