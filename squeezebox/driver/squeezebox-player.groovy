/**
 *  Squeezebox Player
 *
 *  Copyright 2017 Ben Deitch
 *
 */

/* ChangeLog:
 * 13/10/2018 - Added support for password protection
 * 14/10/2018 - Added support for player synchronization
 * 14/10/2018 - Bugfix - Track resume not taking into account previous track time position
 * 14/10/2018 - Added transferPlaylist
 */
metadata {
  definition (name: "Squeezebox Player", namespace: "xap", author: "Ben Deitch") {
    capability "Actuator"
    capability "MusicPlayer"
    capability "Refresh"
    capability "Sensor"
    capability "Speech Synthesis"
    capability "Switch"

    attribute "serverHostAddress", "STRING"
    attribute "playerMAC", "STRING"
    attribute "syncGroup", "STRING"
      
    command "fav1"
    command "fav2"
    command "fav3"
    command "fav4"
    command "fav5"
    command "fav6"
    command "playFavorite", ["NUMBER"]
    command "playTextAndRestore", ["STRING","NUMBER"]
    command "playTextAndResume", ["STRING","NUMBER"]
    command "playTrackAndRestore", ["STRING", "NUMBER", "NUMBER"]
    command "playTrackAndResume", ["STRING", "NUMBER", "NUMBER"]
    command "playTrackAtVolume", ["STRING","NUMBER"]
    command "speak", ["STRING"]
    command "sync", ["STRING"]
    command "transferPlaylist", ["STRING"]
    command "unsync"
    command "unsyncAll"
  }
}

def log(message) {
  if (getParent().debugLogging) {
    log.debug message
  }
}

def configure(serverHostAddress, playerMAC, auth) {
    
  state.serverHostAddress = serverHostAddress
  sendEvent(name: "serverHostAddress", value: state.serverHostAddress, displayed: false, isStateChange: true)

  state.playerMAC = playerMAC
  sendEvent(name: "playerMAC", value: state.playerMAC, displayed: false, isStateChange: true)
    
  state.auth = auth
    
  log "Configured with [serviceHostAddress: ${serverHostAddress}, playerMAC: ${playerMAC}, auth: ${auth}]"
}

def processJsonMessage(msg) {

  log "Squeezebox Player Received [${device.name}]: ${msg}"

  def command = msg.params[1][0]

  switch (command) {
    case "status":
      processStatus(msg)
      break
    case "time":
      processTime(msg)
      break
  }
}

private processStatus(msg) {

  updatePower(msg.result?.get("power"))
  updateVolume(msg.result?.get("mixer volume"))
  updatePlayPause(msg.result?.get("mode"))
  updateSyncGroup(msg.result?.get("sync_master"), msg.result?.get("sync_slaves"))
    
  def trackDetails = msg.result?.playlist_loop?.get(0)
  updateTrackUri(trackDetails?.url)
  String trackDescription
  if (trackDetails) {
    trackDescription = trackDetails.artist ? "${trackDetails.title} by ${trackDetails.artist}" : trackDetails.title
  }
  updateTrackDescription(trackDescription)
}

private processTime(msg) {
  state.trackTime = msg.result?.get("_time")
}

private updatePower(onOff) {

  String current = device.currentValue("switch")
  String onOffString = String.valueOf(onOff) == "1" ? "on" : "off"

  if (current != onOffString) {
    sendEvent(name: "switch", value: onOffString, displayed: true)
    return true
 
  } else {
    return false
  }
}

private updateVolume(volume) {
  String absVolume = Math.abs(Integer.valueOf(volume)).toString()
  sendEvent(name: "level", value: absVolume, displayed: true)
}

private updatePlayPause(playpause) {

  String status
  switch (playpause) {
    case "play":
      status = "playing"
      break
    case "pause":
      status = "paused"
      break
    case "stop":
      status = "stopped"
      break
    default:
      status = playpause
  }

  sendEvent(name: "status", value: status, displayed: true)
}

private updateTrackUri(trackUri) {
  sendEvent(name: "trackUri", value: trackUri, displayed: true)
}

private updateTrackDescription(trackDescription) {
  sendEvent(name: "trackDescription", value: trackDescription, displayed: true)
}

private updateSyncGroup(syncMaster, syncSlaves) {

  def parent = getParent()

  def syncGroup = syncMaster
    ? "${syncMaster},${syncSlaves}"
      .tokenize(",")
      .collect { parent.getChildDeviceName(it) }
    : null

  state.syncGroup = syncGroup
  sendEvent(name: "syncGroup", value: syncGroup, displayed: true)
}

/************
 * Commands *
 ************/

def refresh() {
  executeCommand(["status", "-", 1, "tags:abclsu"]) 
}

//--- Power
def on() {
  log "on()"
  executeCommand(["power", 1])
  refresh()
}

def off() {
  log "off()"
  executeCommand(["power", 0])
  refresh()  
}

//--- Volume
private setVolume(volume) {
  executeCommand(["mixer", "volume", volume])
}

def setLevel(level) {
  log "setLevel(${level})"
  setVolume(level)
  refresh()
}

def mute() {
  log "mute()"
  executeCommand(["mixer", "muting", 1])
  refresh() 
}

def unmute() {
  log "unmute()"
  executeCommand(["mixer", "muting", 0])
  refresh() 
}

//--- Playback
private executePlayAndRefresh(uri) {
  executeCommand(["playlist", "play", uri])
  refresh()  
}

def play() {
  log "play()"
  executeCommand(["play"])
  refresh()
}

def pause() {
  log "pause()"
  executeCommand(["pause"])
  refresh() 
}

def stop() {
  log "stop()"
  executeCommand(["stop"])
  refresh() 
}

def nextTrack() {
  log "nextTrack()"
  executeCommand(["playlist", "jump", "+1"])
  refresh()  
}

def previousTrack() {
  log "previousTrack()"
  executeCommand(["playlist", "jump", "-1"])
  refresh() 
}

def setTrack(trackToSet) {
  log "setTrack(\"${trackToSet}\")"
  executeCommand(["playlist", "stop", trackToSet])
  stop()  
}

def resumeTrack(trackToResume) {
  log "resumeTrack(\"${trackToResume}\")"
  executePlayAndRefresh(trackToResume)
}

def restoreTrack(trackToRestore) {
  log "restoreTrack(\"${trackToRestore}\")"
  executePlayAndRefresh(trackToRestore)
}

def playTrack(trackToPlay) {
  log "playTrack(\"${trackToPlay}\")"
  executePlayAndRefresh(trackToPlay)
}

def playTrackAtVolume(uri, volume) {
  log "playTrackAtVolume(\"${uri}\", ${volume})"
  setVolume(volume)
  executePlayAndRefresh(uri)
}

def playUri(uri) {
  log "playUri(\"${uri}\")"
  executePlayAndRefresh(uri)
}

//--- resume/restore methods
private captureTime() {
  executeCommand(["time", "?"])
}

private clearCapturedTime() {
  state.remove("trackTime")
}

private captureAndChangeVolume(volume) {
  if (volume != null) {
    state.previousVolume = device.currentValue("level");
    setVolume(volume)
  } else {
    state.previousVolume = null
  }
}

private clearCapturedVolume() {
  state.remove("previousVolume")
}

private previewAndGetDelay(uri, duration, volume=null) {
  captureTime()
  executeCommand(["playlist", "preview", "url:${uri}", "silent:1"])
  captureAndChangeVolume(volume)    
  return 2 + duration as int
}

private restoreVolumeAndRefresh() {
  if (state.previousVolume) {
    setVolume(state.previousVolume)
    clearCapturedVolume()
  }
  refresh()
}

// this method is also used by the server when sending a playlist to this player
def resumeTempPlaylistAtTime(tempPlaylist, time=null) {
  executeCommand(["playlist", "resume", tempPlaylist, "wipePlaylist:1"])
  if (time) {
    executeCommand(["time", time])
  }
}

def resume() {
  log "resume()"
  def tempPlaylist = "tempplaylist_" + state.playerMAC.replace(":", "")
  resumeTempPlaylistAtTime(tempPlaylist, state.trackTime)
  clearCapturedTime()
  restoreVolumeAndRefresh()
}

def restore() {
  log "restore()"
  def tempPlaylist = "tempplaylist_" + state.playerMAC.replace(":", "")
  executeCommand(["playlist", "preview", "cmd:stop"])
  restoreVolumeAndRefresh()
}

def playTrackAndResume(uri, duration, volume=null) {
  log "playTrackAndResume(\"${uri}\", ${duration}, ${volume})"
  def delay = previewAndGetDelay(uri, duration, volume)
  runIn(delay, resume)
}

def playTrackAndRestore(uri, duration, volume=null) {
  log "playTrackAndRestore(\"${uri}\", ${duration}, ${volume})"
  def delay = previewAndGetDelay(uri, duration, volume)
  runIn(delay, restore)
}

//--- Favorites
def playFavorite(index) {
  log "playFavorite(${index})"
  executeCommand(["favorites", "playlist", "play", "item_id:${index - 1}"])
  refresh() 
}

def fav1() { playFavorite(1) }
def fav2() { playFavorite(2) }
def fav3() { playFavorite(3) }
def fav4() { playFavorite(4) }
def fav5() { playFavorite(5) }
def fav6() { playFavorite(6) }

//--- Speech
private getTts(text) {
  if (text) {
    textToSpeech(text)
  } else {
    log.error "No text provided for speak() method"
  }
}

def playText(text) {
  log "playText(\"${text}\")"
  def tts = getTts(text)
  if (tts) {
    executePlayAndRefresh(tts.uri)
  }
}

def playTextAndRestore(text, volume=null) {
  log "playTextAndRestore(\"${text}\", ${volume})"
  def tts = getTts(text)
  if (tts) {
    playTrackAndRestore(tts.uri, tts.duration, volume)
  }
}

def playTextAndResume(text, volume=null) {
  log "playTextAndResume(\"${text}\", ${volume})"
  def tts = getTts(text)
  if (tts) {
    playTrackAndResume(tts.uri, tts.duration, volume)
  }
}

def speak(text) {
  log "speak(\"${text}\")"
  playText(text)
}

//--- Synchronization
private getPlayerMacs(players) {
  players?.collect { parent.getChildDeviceMac(it) }
    .findAll { it != null }
}

def sync(slaves) {
  log "sync(\"${slaves}\")"
  def parent = getParent()
  def slaveMacs = getPlayerMacs(slaves.tokenize(","))
  if (slaveMacs) {
    slaveMacs.each { executeCommand(["sync", it]) }
    refresh()
  }
}

def unsync() {
  log "unsync()"
  executeCommand(["sync", "-"])
  refresh()
}

def unsyncAll() {
  log "unsyncAll()"
  def slaves = state.syncGroup?.findAll { it != device.name }
  def syncGroupMacs = getPlayerMacs(slaves)
  if (syncGroupMacs) {
    getParent().unsyncAll(syncGroupMacs)
  }
}

//--- Playlist
def transferPlaylist(destination) {
  log "transferPlaylist(\"${destination}\")"
  def tempPlaylist = "tempplaylist_from_" + state.playerMAC.replace(":", "")
  executeCommand(["playlist", "save", tempPlaylist])
  captureTime()
  if (getParent().transferPlaylist(destination, tempPlaylist, state.trackTime)) {
    executeCommand(["playlist", "clear"])
  }
  clearCapturedTime()
  refresh()
}

/*******************
 * Utility Methods *
 *******************/
private executeCommand(params) {
  log "Squeezebox Player Send [${device.name}]: ${params}"
    
  def jsonBody = buildJsonRequest(params)
   
  def postParams = [
    uri: "http://${state.serverHostAddress}",
    path: "jsonrpc.js",
    body: jsonBody.toString()
  ]
    
  if (state.auth) {
    postParams.headers = ["Authorization": "Basic ${state.auth}"]
  }
     
  httpPost(postParams) { resp ->
    processJsonMessage(resp.data)
  }
}
 
private buildJsonRequest(params) {
 
  def request = [
    id: 1,
    method: "slim.request",
    params: [state.playerMAC, params]
  ]
    
  def json = new groovy.json.JsonBuilder(request)

  json
}
